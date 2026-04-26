package com.artisthaven.app.presentation.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.BrushType
import com.artisthaven.app.domain.model.DrawingStroke
import com.artisthaven.app.domain.model.StrokePoint
import com.artisthaven.app.presentation.canvas.shaders.BrushShaderFactory
import java.util.UUID

/**
 * Custom Android View for low-latency drawing input.
 *
 * Design inspired by androidx.ink API principles:
 * - Batched MotionEvent processing for low-latency rendering
 * - Pressure-sensitive input handling for both touch and stylus
 * - Separate in-progress stroke rendering from committed layer bitmaps
 * - Uses hardware acceleration for smooth 60/120Hz rendering
 */
class DrawingCanvasView(context: Context) : View(context) {

    var onStrokeCommitted: ((DrawingStroke) -> Unit)? = null
    var onSizeAvailable: ((width: Int, height: Int) -> Unit)? = null
    var getLayerBitmaps: (() -> List<Pair<Bitmap, Float>>)? = null
    var getActiveBrush: (() -> Brush)? = null
    var getActiveLayerBitmap: (() -> Bitmap?)? = null

    private val currentStrokePoints = mutableListOf<StrokePoint>()
    private var currentStrokeId: String? = null
    private var isDrawing = false

    private var previewBitmap: Bitmap? = null
    private var previewCanvas: AndroidCanvas? = null
    private val previewPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

    @Suppress("UNUSED")
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    @Suppress("DEPRECATION")
    private val shaderFactory: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        BrushShaderFactory()
    } else null

    init {
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

        getLayerBitmaps?.invoke()?.forEach { (bitmap, opacity) ->
            val paint = AndroidPaint()
            paint.alpha = (opacity * 255).toInt()
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        }

        previewBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_PALM) return false

        val brush = getActiveBrush?.invoke() ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                startStroke(event, brush)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
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

    private fun startStroke(event: MotionEvent, brush: Brush) {
        isDrawing = true
        currentStrokeId = UUID.randomUUID().toString()
        currentStrokePoints.clear()
        clearPreview()
        addCurrentPoint(event)
    }

    private fun addHistoricalPoint(event: MotionEvent, historyIndex: Int) {
        val pressure = event.getHistoricalPressure(0, historyIndex).coerceIn(0f, 1f)
        val tiltX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble()).toFloat() else 0f

        currentStrokePoints.add(
            StrokePoint(
                x = event.getHistoricalX(0, historyIndex),
                y = event.getHistoricalY(0, historyIndex),
                pressure = pressure,
                tiltX = tiltX,
                timestamp = event.getHistoricalEventTime(historyIndex),
            )
        )
    }

    private fun addCurrentPoint(event: MotionEvent) {
        val pressure = event.pressure.coerceIn(0f, 1f)
        val tiltX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble()).toFloat() else 0f

        currentStrokePoints.add(
            StrokePoint(
                x = event.x,
                y = event.y,
                pressure = pressure,
                tiltX = tiltX,
                timestamp = event.eventTime,
            )
        )
    }

    private fun updatePreview(brush: Brush) {
        val canvas = previewCanvas ?: return
        val points = currentStrokePoints
        if (points.isEmpty()) return

        if (points.size >= 2) {
            val last = points[points.size - 1]
            val prev = points[points.size - 2]
            val paint = createPaint(brush, last.pressure)
            canvas.drawLine(prev.x, prev.y, last.x, last.y, paint)
        } else {
            val point = points.first()
            val paint = createPaint(brush, point.pressure)
            paint.style = AndroidPaint.Style.FILL
            canvas.drawCircle(point.x, point.y, brush.size * point.pressure / 2f, paint)
        }

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

    private fun createPaint(brush: Brush, pressure: Float): AndroidPaint {
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

        when (brush.type) {
            BrushType.ERASER -> {
                paint.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            else -> {
                val argb = brush.color.copy(alpha = brush.opacity * pressure).let {
                    android.graphics.Color.argb(
                        (it.alpha * 255).toInt(),
                        (it.red * 255).toInt(),
                        (it.green * 255).toInt(),
                        (it.blue * 255).toInt(),
                    )
                }
                paint.color = argb
            }
        }

        val strokeWidth = brush.size * pressure.coerceAtLeast(0.3f)
        paint.strokeWidth = strokeWidth
        paint.style = AndroidPaint.Style.STROKE
        paint.strokeCap = AndroidPaint.Cap.ROUND
        paint.strokeJoin = AndroidPaint.Join.ROUND

        val blurRadius = strokeWidth * (1f - brush.hardness) * 0.5f
        if (blurRadius > 0.5f) {
            paint.maskFilter = android.graphics.BlurMaskFilter(
                blurRadius,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }

        return paint
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        previewBitmap?.recycle()
        previewBitmap = null
        previewCanvas = null
    }
}
