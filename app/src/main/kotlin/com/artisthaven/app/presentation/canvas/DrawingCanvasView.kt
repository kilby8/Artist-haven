package com.artisthaven.app.presentation.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.os.Build
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.DrawingStroke
import com.artisthaven.app.domain.model.StrokePoint
import com.artisthaven.app.presentation.canvas.shaders.BrushShaderFactory
import java.util.UUID


/**
 * Custom Android View for low-latency drawing input.
 *
 * Architecture follows the same separation-of-concerns as the androidx.ink API
 * (ink-authoring + ink-rendering, Apache 2.0), which is declared as a dependency:
 *
 *  • ink-authoring  — captures raw MotionEvent input with stroke smoothing
 *  • ink-rendering  — renders in-progress and committed strokes efficiently
 *  • ink-geometry   — path/bezier utilities used for Catmull-Rom spline fitting
 *  • ink-brush      — configurable brush model (tip shape, size, opacity)
 *
 * This View mirrors that three-phase design:
 *   1. Input phase  : collects historical + current MotionEvent points (pressure & tilt)
 *   2. Preview phase: draws the live stroke on a hardware-accelerated preview bitmap
 *   3. Commit phase : hands the finished stroke to CanvasViewModel for undo/redo
 *
 * Hardware acceleration (android:hardwareAccelerated="true" in AndroidManifest) ensures
 * the GPU composites layer bitmaps and the preview at 60/120 Hz without CPU stalls.
 */
class DrawingCanvasView(context: Context) : View(context) {

    private val palmClassificationConstant: Int? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            MotionEvent::class.java.getField("CLASSIFICATION_PALM").getInt(null)
        }.getOrNull()
    }

    private val palmToolTypeConstant: Int? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            MotionEvent::class.java.getField("TOOL_TYPE_PALM").getInt(null)
        }.getOrNull()
    }

    var onStrokeCommitted: ((DrawingStroke) -> Unit)? = null
    var onSizeAvailable: ((width: Int, height: Int) -> Unit)? = null
    var getLayerBitmaps: (() -> List<Pair<Bitmap, Float>>)? = null
    var getActiveBrush: (() -> Brush)? = null
    var getActiveLayerBitmap: (() -> Bitmap?)? = null
    var getCanvasRenderingManager: (() -> CanvasRenderingManager)? = null

    private val currentStrokePoints = mutableListOf<StrokePoint>()
    private var currentStrokeId: String? = null
    private var isDrawing = false

    private var previewBitmap: Bitmap? = null
    private var previewCanvas: AndroidCanvas? = null
    private val previewPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

    // Minimum pixel distance the pointer must travel before a stroke begins.
    // Prevents single-tap jitter from creating unwanted micro-strokes.
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var strokeStartX = 0f
    private var strokeStartY = 0f
    private var strokeStarted = false

    // Viewport transform state (screen = canvas * scale + pan)
    private var viewportScale = 1f
    private var viewportPanX = 0f
    private var viewportPanY = 0f
    private val minViewportScale = 0.5f
    private val maxViewportScale = 4f
    private var lastGestureFocusX = 0f
    private var lastGestureFocusY = 0f
    private var isTransformGesture = false
    private val brushEngine = BrushEngine(context)

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isTransformGesture = true
                lastGestureFocusX = detector.focusX
                lastGestureFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = viewportScale
                val targetScale = (viewportScale * detector.scaleFactor)
                    .coerceIn(minViewportScale, maxViewportScale)

                if (targetScale == oldScale) return false

                // Zoom around the focal point to keep pinch target stationary.
                val focusX = detector.focusX
                val focusY = detector.focusY
                val scaleRatio = targetScale / oldScale
                viewportPanX = focusX - (focusX - viewportPanX) * scaleRatio
                viewportPanY = focusY - (focusY - viewportPanY) * scaleRatio
                viewportScale = targetScale
                constrainViewportPan()
                invalidate()
                return true
            }
        }
    )

    // AGSL RuntimeShader factory for organic brush textures on Android 13+ (API 33).
    // @SuppressLint("NewApi") is safe here — instantiation is guarded by the SDK_INT check.
    @SuppressLint("NewApi")
    private val shaderFactory: BrushShaderFactory? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) BrushShaderFactory()
        else null

    init {
        // Hardware layer type delegates compositing to the GPU — essential for low-latency
        // stroke rendering on pen-input tablets running at 120 Hz.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        previewBitmap?.recycle()
        previewBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        previewCanvas = AndroidCanvas(previewBitmap!!)
        onSizeAvailable?.invoke(w, h)
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(viewportPanX, viewportPanY)
        canvas.scale(viewportScale, viewportScale)

        // Render canvas background first (texture, tooth, lighting)
        getCanvasRenderingManager?.invoke()?.renderBackground(canvas)

        getLayerBitmaps?.invoke()?.forEach { (bitmap, opacity) ->
            val paint = AndroidPaint()
            paint.alpha = (opacity * 255).toInt()
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        }

        previewBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.restore()
    }

    fun zoomIn(step: Float = 1.2f) {
        applyZoom(step, width / 2f, height / 2f)
    }

    fun zoomOut(step: Float = 1.2f) {
        applyZoom(1f / step, width / 2f, height / 2f)
    }

    fun resetZoom() {
        viewportScale = 1f
        viewportPanX = 0f
        viewportPanY = 0f
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPalmContact(event)) return false

        scaleGestureDetector.onTouchEvent(event)

        if (event.pointerCount >= 2) {
            handleTransformPan(event)
            return true
        }

        if (isTransformGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                isTransformGesture = false
            }
            return true
        }

        val brush = getActiveBrush?.invoke() ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                startStroke(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Touch-slop guard: only commit stroke points once the pointer
                // has moved further than scaledTouchSlop from the start position.
                // This prevents tap jitter from being recorded as micro-strokes.
                if (!strokeStarted) {
                    val dx = event.x - strokeStartX
                    val dy = event.y - strokeStartY
                    if (dx * dx + dy * dy < touchSlop * touchSlop) return true
                    strokeStarted = true
                    isDrawing = true
                }
                // Process all historical points first to minimise input lag —
                // equivalent to ink-authoring's batched MotionEvent processing
                for (i in 0 until event.historySize) {
                    addHistoricalPoint(event, i)
                }
                addCurrentPoint(event)
                updatePreview(brush)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                finishStroke(brush)
                return true
            }
        }
        return false
    }

    private fun isPalmContact(event: MotionEvent): Boolean {
        if (event.pointerCount <= 0) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            palmClassificationConstant != null &&
            event.classification == palmClassificationConstant
        ) {
            return true
        }

        return palmToolTypeConstant?.let { palmToolType ->
            event.getToolType(0) == palmToolType
        } ?: false
    }

    private fun startStroke(event: MotionEvent) {
        if (isTransformGesture) return

        // Record start position for touch-slop guard
        strokeStartX = event.x
        strokeStartY = event.y
        strokeStarted = false
        currentStrokeId = UUID.randomUUID().toString()
        currentStrokePoints.clear()
        clearPreview()
        addCurrentPoint(event)
    }

    private fun addHistoricalPoint(event: MotionEvent, historyIndex: Int) {
        val pressure = event.getHistoricalPressure(0, historyIndex).coerceIn(0f, 1f)
        // getHistoricalAxisValue reads the tilt for the specific historical sample —
        // using getAxisValue here would incorrectly read the *current* event's tilt.
        // AXIS_TILT is in radians (0 = perpendicular, π/2 = flat against screen).
        val tiltDeg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Math.toDegrees(
                event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, 0, historyIndex).toDouble()
            ).toFloat()
        else 0f

        currentStrokePoints.add(
            StrokePoint(
                x = screenToCanvasX(event.getHistoricalX(0, historyIndex)),
                y = screenToCanvasY(event.getHistoricalY(0, historyIndex)),
                pressure = pressure,
                tiltX = tiltDeg,
                timestamp = event.getHistoricalEventTime(historyIndex),
            )
        )
    }

    private fun addCurrentPoint(event: MotionEvent) {
        val pressure = event.pressure.coerceIn(0f, 1f)
        // AXIS_TILT is in radians (0 = perpendicular, π/2 = flat against screen).
        val tiltDeg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble()).toFloat()
        else 0f

        currentStrokePoints.add(
            StrokePoint(
                x = screenToCanvasX(event.x),
                y = screenToCanvasY(event.y),
                pressure = pressure,
                tiltX = tiltDeg,
                timestamp = event.eventTime,
            )
        )
    }

    private fun updatePreview(brush: Brush) {
        val canvas = previewCanvas ?: return
        val points = currentStrokePoints
        if (points.isEmpty()) return

        // Redraw the live preview from stroke samples so all brush dynamics/styles
        // (texture, velocity mapping, glow, pattern stamps) match committed rendering.
        clearPreview()
        brushEngine.renderStroke(
            canvas = canvas,
            points = points,
            brush = brush,
            isPreview = true,
        )

        invalidate()
    }

    private fun finishStroke(brush: Brush) {
        if (!isDrawing) return
        isDrawing = false

        val strokeId = currentStrokeId ?: return
        val points = currentStrokePoints.toList()
        currentStrokePoints.clear()
        clearPreview()

        if (points.isNotEmpty()) {
            val stroke = DrawingStroke(
                id = strokeId,
                layerId = "",
                brushSnapshot = brush,
                points = points,
            )
            onStrokeCommitted?.invoke(stroke)
        }

        invalidate()
    }

    private fun clearPreview() {
        previewCanvas?.drawColor(0, PorterDuff.Mode.CLEAR)
    }

    private fun handleTransformPan(event: MotionEvent) {
        val focusX = averagePointerX(event)
        val focusY = averagePointerY(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    isTransformGesture = true
                    lastGestureFocusX = focusX
                    lastGestureFocusY = focusY
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTransformGesture && !scaleGestureDetector.isInProgress) {
                    viewportPanX += focusX - lastGestureFocusX
                    viewportPanY += focusY - lastGestureFocusY
                    constrainViewportPan()
                    invalidate()
                }
                lastGestureFocusX = focusX
                lastGestureFocusY = focusY
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.pointerCount <= 2) {
                    isTransformGesture = false
                }
            }
        }
    }

    private fun applyZoom(scaleFactor: Float, focusX: Float, focusY: Float) {
        if (width == 0 || height == 0) return

        val oldScale = viewportScale
        val targetScale = (viewportScale * scaleFactor).coerceIn(minViewportScale, maxViewportScale)
        if (targetScale == oldScale) return

        val scaleRatio = targetScale / oldScale
        viewportPanX = focusX - (focusX - viewportPanX) * scaleRatio
        viewportPanY = focusY - (focusY - viewportPanY) * scaleRatio
        viewportScale = targetScale
        constrainViewportPan()
        invalidate()
    }

    private fun constrainViewportPan() {
        if (width == 0 || height == 0) return
        val scaledWidth = width * viewportScale
        val scaledHeight = height * viewportScale
        // Zoomed in: scaledWidth > width → allow scrolling → pan range [width-scaledWidth .. 0]
        // Zoomed out: scaledWidth <= width → no scroll needed → clamp to 0
        if (scaledWidth > width) {
            viewportPanX = viewportPanX.coerceIn(width - scaledWidth, 0f)
        } else {
            viewportPanX = 0f
        }
        if (scaledHeight > height) {
            viewportPanY = viewportPanY.coerceIn(height - scaledHeight, 0f)
        } else {
            viewportPanY = 0f
        }
    }

    private fun averagePointerX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getX(i)
        }
        return sum / event.pointerCount
    }

    private fun averagePointerY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getY(i)
        }
        return sum / event.pointerCount
    }

    private fun screenToCanvasX(screenX: Float): Float = (screenX - viewportPanX) / viewportScale

    private fun screenToCanvasY(screenY: Float): Float = (screenY - viewportPanY) / viewportScale


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        previewBitmap?.recycle()
        previewBitmap = null
        previewCanvas = null
    }
}
