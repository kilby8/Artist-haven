package com.artisthaven.app.presentation.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.DrawingStroke
import com.artisthaven.app.domain.model.StrokePoint
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
    var getCanvasRenderingManager: (() -> CanvasRenderingManager)? = null

    private val currentStrokePoints = mutableListOf<StrokePoint>()
    private var currentStrokeId: String? = null
    private var isDrawing = false
    private var lastPreviewRenderIndex = 0  // Track last rendered point for incremental renders

    private var previewBitmap: Bitmap? = null
    private var previewCanvas: AndroidCanvas? = null
    private val layerPaint = AndroidPaint()
    private val previewBrushRenderer = MasterPaintBrush(context)
    private val previewBounds = RectF()
    private val predictionBounds = RectF()
    private val clearBounds = RectF()
    private val invalidateBounds = RectF()
    private val invalidateRect = Rect()

    // Zero-Latency Engine Components
    private var strokeComputeThread: StrokeComputeThread? = null
    private val predictionRenderer = PredictionRenderer()
    private var renderNodeCache: RenderNodeStrokeCache? = null
    private var previewComputationGeneration = 0L
    private var lastCachedPreviewStampIndex = 0
    private val activePreviewTailStampCount = 24

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

        // Initialize zero-latency components
        if (strokeComputeThread == null) {
            strokeComputeThread = StrokeComputeThread(MasterPaintBrush(context))
        }
        if (renderNodeCache == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNodeCache = RenderNodeStrokeCache(w, h)
        }
        previewBounds.setEmpty()
        predictionBounds.setEmpty()
        lastCachedPreviewStampIndex = 0

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
            layerPaint.alpha = (opacity * 255).toInt()
            canvas.drawBitmap(bitmap, 0f, 0f, layerPaint)
        }

        // Replay cached active-stroke segments above persistent layers.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNodeCache?.replayCachedSegments(canvas)
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
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
                if (!strokeStarted && event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
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
        lastPreviewRenderIndex = 0  // Reset preview render tracking
        previewComputationGeneration++
        lastCachedPreviewStampIndex = 0
        previewBounds.setEmpty()
        predictionBounds.setEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNodeCache?.clearAllCaches()
        }
        clearPreview()
        addCurrentPoint(event)
    }

    private fun addHistoricalPoint(event: MotionEvent, historyIndex: Int) {
        val pressure = event.getHistoricalPressure(0, historyIndex).coerceIn(0f, 1f)
        // getHistoricalAxisValue reads the tilt for the specific historical sample —
        // using getAxisValue here would incorrectly read the *current* event's tilt.
        // AXIS_TILT is in radians (0 = perpendicular, π/2 = flat against screen).
        val tiltDeg = Math.toDegrees(
            event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, 0, historyIndex).toDouble()
        ).toFloat()

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
        val tiltDeg = Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble()).toFloat()

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

        // Front-Buffer Prediction: Draw instant feedback line while compute catches up
        if (points.size >= 2) {
            val lastPoint = points[points.size - 2]
            val currPoint = points[points.size - 1]
            predictionRenderer.renderPredictionLine(
                canvas = canvas,
                lastX = lastPoint.x,
                lastY = lastPoint.y,
                currentX = currPoint.x,
                currentY = currPoint.y,
                brush = brush,
            )
            updateLineDirtyBounds(lastPoint.x, lastPoint.y, currPoint.x, currPoint.y, brush.size, predictionBounds)
            invalidateCanvasBounds(predictionBounds)
        }

        // Off-Main-Thread Computation: Asynchronously compute high-fidelity stamps on background thread
        if (lastPreviewRenderIndex < points.size - 1) {
            val generation = ++previewComputationGeneration
            strokeComputeThread?.computeStrokeAsync(
                points = points,
                brush = brush,
                onComplete = { precomputed ->
                    if (generation != previewComputationGeneration || !isDrawing) return@computeStrokeAsync
                    applyPrecomputedPreview(precomputed, brush)
                }
            )
            lastPreviewRenderIndex = points.size
        }

        if (points.size < 2) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * Apply precomputed stamp positions to the active front buffer while older segments are
     * optionally replayed from RenderNode cache.
     */
    private fun applyPrecomputedPreview(
        precomputed: StrokeComputeThread.PrecomputedStroke,
        brush: Brush,
    ) {
        val canvas = previewCanvas ?: return

        cacheFinishedPreviewSegments(precomputed, brush)

        clearBounds.set(previewBounds)
        if (!predictionBounds.isEmpty) {
            if (clearBounds.isEmpty) clearBounds.set(predictionBounds) else clearBounds.union(predictionBounds)
        }
        clearPreview(clearBounds.takeIf { !it.isEmpty })

        val tailStart = lastCachedPreviewStampIndex.coerceAtMost(precomputed.stampPositions.size)
        val tailStamps = precomputed.stampPositions.subList(tailStart, precomputed.stampPositions.size)

        previewBounds.setEmpty()
        if (tailStamps.isNotEmpty()) {
            previewBrushRenderer.renderStampPositions(
                canvas = canvas,
                stampPositions = tailStamps,
                brush = brush,
                isPreview = true,
                outBounds = previewBounds,
            )
        } else if (precomputed.predictedPath.size >= 2) {
            predictionRenderer.renderPredictionCurve(canvas, precomputed.predictedPath, brush)
            updatePathDirtyBounds(precomputed.predictedPath, brush.size, previewBounds)
        }

        predictionBounds.setEmpty()
        invalidateBounds.set(previewBounds)
        if (!clearBounds.isEmpty) {
            if (invalidateBounds.isEmpty) invalidateBounds.set(clearBounds) else invalidateBounds.union(clearBounds)
        }
        invalidateCanvasBounds(invalidateBounds)
    }

    private fun cacheFinishedPreviewSegments(
        precomputed: StrokeComputeThread.PrecomputedStroke,
        brush: Brush,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val cache = renderNodeCache ?: return
        val cacheUntil = (precomputed.stampPositions.size - activePreviewTailStampCount).coerceAtLeast(0)
        if (cacheUntil <= lastCachedPreviewStampIndex) return

        val segment = precomputed.stampPositions.subList(lastCachedPreviewStampIndex, cacheUntil)
        if (segment.isEmpty()) return

        val range = lastCachedPreviewStampIndex until cacheUntil
        cache.cacheSegment(range) { recordingCanvas ->
            previewBrushRenderer.renderStampPositions(
                canvas = recordingCanvas,
                stampPositions = segment,
                brush = brush,
                isPreview = true,
            )
        }
        lastCachedPreviewStampIndex = cacheUntil
    }

    private fun finishStroke(brush: Brush) {
        if (!isDrawing) return
        isDrawing = false

        val strokeId = currentStrokeId ?: return
        val points = currentStrokePoints.toList()
        currentStrokePoints.clear()
        lastPreviewRenderIndex = 0  // Reset preview render tracking
        previewComputationGeneration++
        lastCachedPreviewStampIndex = 0
        previewBounds.setEmpty()
        predictionBounds.setEmpty()

        // Clear RenderNode cache for finished stroke
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNodeCache?.clearAllCaches()
        }
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up background compute thread
        strokeComputeThread?.release()
        strokeComputeThread = null
        // Clean up bitmaps
        previewBitmap?.recycle()
        previewBitmap = null
        previewCanvas = null
    }

    private fun clearPreview() {
        clearPreview(null)
    }

    private fun clearPreview(bounds: RectF?) {
        val canvas = previewCanvas ?: return
        if (bounds == null || bounds.isEmpty) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            return
        }

        val saveCount = canvas.save()
        canvas.clipRect(bounds)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        canvas.restoreToCount(saveCount)
    }

    private fun updateLineDirtyBounds(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        brushSize: Float,
        outBounds: RectF,
    ) {
        val pad = brushSize * 1.5f + 6f
        outBounds.set(
            minOf(startX, endX) - pad,
            minOf(startY, endY) - pad,
            maxOf(startX, endX) + pad,
            maxOf(startY, endY) + pad,
        )
    }

    private fun updatePathDirtyBounds(points: List<Pair<Float, Float>>, brushSize: Float, outBounds: RectF) {
        if (points.isEmpty()) {
            outBounds.setEmpty()
            return
        }

        val pad = brushSize * 1.5f + 6f
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        points.forEach { (x, y) ->
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
        }

        outBounds.set(minX - pad, minY - pad, maxX + pad, maxY + pad)
    }

    private fun invalidateCanvasBounds(bounds: RectF) {
        if (bounds.isEmpty || width == 0 || height == 0) {
            postInvalidateOnAnimation()
            return
        }

        val left = ((bounds.left * viewportScale) + viewportPanX).toInt() - 4
        val top = ((bounds.top * viewportScale) + viewportPanY).toInt() - 4
        val right = ((bounds.right * viewportScale) + viewportPanX).toInt() + 4
        val bottom = ((bounds.bottom * viewportScale) + viewportPanY).toInt() + 4

        invalidateRect.set(
            left.coerceIn(0, width),
            top.coerceIn(0, height),
            right.coerceIn(0, width),
            bottom.coerceIn(0, height),
        )

        if (invalidateRect.isEmpty) {
            postInvalidateOnAnimation()
        } else {
            postInvalidateOnAnimation(
                invalidateRect.left,
                invalidateRect.top,
                invalidateRect.right,
                invalidateRect.bottom,
            )
        }
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
}
