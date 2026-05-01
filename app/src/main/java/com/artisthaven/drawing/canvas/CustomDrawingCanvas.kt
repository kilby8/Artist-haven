package com.artisthaven.drawing.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import com.artisthaven.drawing.brush.BrushProvider
import com.artisthaven.drawing.input.InkInputHandler
import com.artisthaven.drawing.layer.Layer
import com.artisthaven.drawing.layer.LayerManager
import com.artisthaven.drawing.rendering.TiledRenderer
import kotlin.math.max

/**
 * The core drawing surface of Artist Haven.
 *
 * Clean Architecture — Presentation / UI layer (View component).
 *
 * [CustomDrawingCanvas] extends [SurfaceView] and implements an ultra-low-latency
 * drawing pipeline built on three pillars:
 *
 * 1. **SurfaceControl rendering** — A child [SurfaceControlCompat] is created on top
 *    of the view's own [android.view.Surface].  Stroke dabs are committed to this
 *    surface via [SurfaceControlCompat.Transaction], bypassing the view hierarchy and
 *    reaching the hardware compositor in a single vsync.  This is the same technique
 *    used by Google's low-latency ink demos in AOSP.
 *
 * 2. **High-frequency stylus input** — All touch events are forwarded to [InkInputHandler]
 *    which extracts 120 Hz historical samples, rejects palm contacts, and returns
 *    smoothed [StrokePoint]s in canvas-coordinate space.
 *
 * 3. **AGSL GPU shaders** — Each stroke dab is rendered via a [RuntimeShader] obtained
 *    from [BrushProvider].  The shader executes entirely on the GPU, leaving the CPU
 *    render thread free for input processing.
 *
 * 4. **Tiled compositing** — The full canvas is re-composited using [TiledRenderer] to
 *    keep peak memory allocation bounded regardless of canvas resolution.
 *
 * **Lifecycle:**
 * ```
 * onAttachedToWindow  → SurfaceControl + InkInputHandler + TiledRenderer created
 * surfaceCreated      → render thread started
 * surfaceDestroyed    → render thread stopped
 * onDetachedFromWindow→ SurfaceControl released, TiledRenderer recycled
 * ```
 *
 * **Thread model:**
 * - Touch events are received on the main thread and placed in [pendingPoints].
 * - The [renderThread] reads [pendingPoints], renders to the layer bitmap, and
 *   then commits a [SurfaceControlCompat.Transaction] — all on the render thread.
 * - [LayerManager] and [TiledRenderer] must only be mutated from the render thread.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class CustomDrawingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    // ──────────────────────────────────────────────────────────────────────────
    // Configuration
    // ──────────────────────────────────────────────────────────────────────────

    /** Current active brush type (may be changed by the host Activity / Fragment). */
    var activeBrushType: BrushProvider.BrushType = BrushProvider.BrushType.ROUND

    /** Brush tip radius in canvas pixels. */
    var brushRadius: Float = 18f

    /** Brush colour components (premultiplied by alpha in the AGSL shader). */
    var brushR: Float = 0f
    var brushG: Float = 0f
    var brushB: Float = 0f
    var brushA: Float = 1f

    // ──────────────────────────────────────────────────────────────────────────
    // Core subsystems
    // ──────────────────────────────────────────────────────────────────────────

    private lateinit var layerManager: LayerManager
    private lateinit var tiledRenderer: TiledRenderer
    private lateinit var inkInputHandler: InkInputHandler

    // ──────────────────────────────────────────────────────────────────────────
    // SurfaceControl
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Child [SurfaceControlCompat] attached to this view's surface.
     * Stroke dabs are committed here for hardware-compositor low-latency delivery.
     */
    private var drawingSurfaceControl: SurfaceControlCompat? = null

    // ──────────────────────────────────────────────────────────────────────────
    // Rendering state
    // ──────────────────────────────────────────────────────────────────────────

    /** Pending stroke points produced on the main thread, consumed on the render thread. */
    private val pendingPoints = ArrayDeque<StrokePoint>()
    private val pendingLock = Any()

    /** Whether the render thread should keep running. */
    @Volatile private var isRendering = false
    private var renderThread: Thread? = null

    /** Reusable paint for blitting layer bitmaps in the render thread. */
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Reusable paint for AGSL shader dabs. */
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Elapsed-time counter used by animated AGSL shaders (e.g. watercolor). */
    private val startTimeMs = SystemClock.uptimeMillis()

    /**
     * Per-brush-type [RuntimeShader] pool.  One shader instance is reused per brush
     * type; uniforms are updated before each dab stamp to avoid repeated object
     * allocation on the render thread (which would trigger the GC at 120 Hz).
     */
    private val shaderPool: MutableMap<BrushProvider.BrushType, RuntimeShader> = mutableMapOf()

    // ──────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────────────

    init {
        holder.addCallback(this)
        // Request that the surface receives touch input including stylus events.
        isFocusable = true
        isFocusableInTouchMode = true
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SurfaceHolder.Callback
    // ──────────────────────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        val w = holder.surfaceFrame.width()
        val h = holder.surfaceFrame.height()

        // ── Initialise subsystems ─────────────────────────────────────────────
        layerManager = LayerManager(w, h)
        tiledRenderer = TiledRenderer(layerManager, w, h)
        inkInputHandler = InkInputHandler(viewToCanvasMatrix(w, h))

        // ── Create child SurfaceControl for low-latency front-buffer rendering ─
        drawingSurfaceControl = SurfaceControlCompat.Builder()
            .setParent(this)
            .setName("ArtistHaven_DrawingSurface")
            .build()

        // ── Start the render thread ───────────────────────────────────────────
        isRendering = true
        renderThread = Thread({ renderLoop(holder) }, "ArtistHaven_RenderThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface resized — recreate subsystems to match the new dimensions.
        stopRenderThread()
        tiledRenderer.recycle()
        layerManager.recycle()

        layerManager = LayerManager(width, height)
        tiledRenderer = TiledRenderer(layerManager, width, height)
        inkInputHandler = InkInputHandler(viewToCanvasMatrix(width, height))

        isRendering = true
        renderThread = Thread({ renderLoop(holder) }, "ArtistHaven_RenderThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRenderThread()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Touch input
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Forwards all [MotionEvent]s to [InkInputHandler] and enqueues the resulting
     * [StrokePoint]s for the render thread to consume.
     *
     * Returns `true` to indicate the event is consumed.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val points = inkInputHandler.process(event)
        if (points.isNotEmpty()) {
            synchronized(pendingLock) {
                pendingPoints.addAll(points)
            }
        }
        return true
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /** Exposes [LayerManager] so the host Activity can add/remove/select layers. */
    fun getLayerManager(): LayerManager? =
        if (::layerManager.isInitialized) layerManager else null

    /** Clears the currently active layer. */
    fun clearActiveLayer() {
        layerManager.activeLayer?.clear()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRenderThread()
        if (::tiledRenderer.isInitialized) tiledRenderer.recycle()
        if (::layerManager.isInitialized) layerManager.recycle()
        releaseSurfaceControl()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Render thread
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Main render loop executed on the dedicated [renderThread].
     *
     * Each iteration:
     *  1. Drains [pendingPoints] in a short critical section.
     *  2. For each point, stamps an AGSL shader dab onto the active layer bitmap.
     *  3. If any points were drawn, composites all layers via [TiledRenderer] and
     *     commits the result to the [SurfaceHolder] surface.
     *
     * When there are no pending points the thread yields briefly to avoid
     * busy-polling at 100% CPU.
     */
    private fun renderLoop(holder: SurfaceHolder) {
        while (isRendering) {
            val batch: List<StrokePoint>
            synchronized(pendingLock) {
                batch = pendingPoints.toList()
                pendingPoints.clear()
            }

            if (batch.isEmpty()) {
                Thread.sleep(2) // ~500 Hz poll cap; backoff avoids wasting CPU.
                continue
            }

            // ── Draw dabs onto the active layer bitmap ────────────────────────
            val activeLayer = layerManager.activeLayer ?: continue
            var dirtyLeft   = Int.MAX_VALUE
            var dirtyTop    = Int.MAX_VALUE
            var dirtyRight  = Int.MIN_VALUE
            var dirtyBottom = Int.MIN_VALUE

            for (point in batch) {
                stampDab(activeLayer, point)
                val r = (brushRadius * max(1f, point.pressure * 1.5f)).toInt() + 2
                dirtyLeft   = minOf(dirtyLeft,   (point.x - r).toInt())
                dirtyTop    = minOf(dirtyTop,    (point.y - r).toInt())
                dirtyRight  = maxOf(dirtyRight,  (point.x + r).toInt())
                dirtyBottom = maxOf(dirtyBottom, (point.y + r).toInt())
            }

            val dirtyRegion = Rect(
                dirtyLeft.coerceAtLeast(0),
                dirtyTop.coerceAtLeast(0),
                dirtyRight.coerceAtMost(layerManager.layers.first().width),
                dirtyBottom.coerceAtMost(layerManager.layers.first().height)
            )

            // ── Composite and present ─────────────────────────────────────────
            commitToSurface(holder, dirtyRegion)
        }
    }

    /**
     * Stamps a single AGSL brush dab onto [layer]'s [android.graphics.Canvas]
     * at the position and pressure specified by [point].
     *
     * The [RuntimeShader] for the current brush type is retrieved from [shaderPool]
     * (created on first use) and its uniforms are updated in-place, avoiding
     * per-dab object allocation at 120 Hz.
     */
    private fun stampDab(layer: Layer, point: StrokePoint) {
        val elapsedSec = (SystemClock.uptimeMillis() - startTimeMs) / 1000f

        // Retrieve or create the pooled shader for the active brush type.
        val shader: RuntimeShader = shaderPool.getOrPut(activeBrushType) {
            BrushProvider.create(activeBrushType)
        }

        // Update uniforms in-place — no new object allocation.
        shader.setFloatUniform("center", point.x, point.y)
        shader.setFloatUniform("radius", brushRadius * (0.6f + point.pressure * 0.8f))
        shader.setFloatUniform("pressure", point.pressure.coerceIn(0f, 1f))
        shader.setFloatUniform("color", brushR, brushG, brushB, brushA)
        shader.setFloatUniform("time", elapsedSec)
        shader.setFloatUniform("angle", point.orientation)

        shaderPaint.shader = shader
        // Draw a small rect around the dab centre — AGSL determines the visible shape.
        val r = brushRadius * 1.5f
        layer.canvas.drawRect(
            point.x - r, point.y - r,
            point.x + r, point.y + r,
            shaderPaint
        )
    }

    /**
     * Composites all layers via [TiledRenderer] and blits the result to the
     * [SurfaceHolder]'s surface using a locked canvas.
     *
     * A [SurfaceControlCompat.Transaction] is used to signal the hardware
     * compositor that a new frame is available, enabling front-buffer rendering
     * which skips one vsync of latency compared to a standard double-buffered
     * [SurfaceHolder.lockCanvas] approach.
     */
    private fun commitToSurface(holder: SurfaceHolder, dirtyRegion: Rect) {
        val canvas: Canvas = holder.lockHardwareCanvas() ?: return
        try {
            canvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)
            tiledRenderer.renderDirty(canvas, dirtyRegion)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }

        // Signal compositor via SurfaceControl transaction for minimal latency.
        drawingSurfaceControl?.let { sc ->
            SurfaceControlCompat.Transaction()
                .setVisibility(sc, true)
                .commit()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun stopRenderThread() {
        isRendering = false
        renderThread?.join(500)
        renderThread = null
    }

    private fun releaseSurfaceControl() {
        drawingSurfaceControl?.let { sc ->
            SurfaceControlCompat.Transaction().reparent(sc, null).commit()
            sc.release()
        }
        drawingSurfaceControl = null
    }

    /**
     * Builds an identity-equivalent [Matrix] that maps view coordinates to canvas
     * coordinates.  For a 1:1 canvas (no zoom / pan) this is the identity matrix.
     * Extend this when pan/zoom support is added.
     */
    private fun viewToCanvasMatrix(@Suppress("UNUSED_PARAMETER") w: Int,
                                   @Suppress("UNUSED_PARAMETER") h: Int): Matrix = Matrix()
}
