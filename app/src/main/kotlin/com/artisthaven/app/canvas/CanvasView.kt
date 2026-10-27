package com.artisthaven.app.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.tools.Affine
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What a touch on the canvas does, decided by the current tool. */
enum class TouchAction { PAINT, FILL, EYEDROPPER, TRANSFORM }

/**
 * The drawing surface. It shows the engine's composite through a zoom/rotate/pan matrix and turns
 * touches into strokes and gestures, Procreate style:
 *
 * - pen or one finger paints; a pen's eraser end erases
 * - two-finger tap undoes, three-finger tap redoes
 * - pinch to zoom and rotate, two-finger drag to pan (rotation snaps to straight near 0 degrees)
 * - touch and hold with a finger picks a colour; hold the pen still at the end of a line to snap it
 *   into a straight line, ellipse or rectangle (QuickShape)
 * - once a pen has been used, fingers no longer paint (palm rejection), only navigate
 */
@SuppressLint("ViewConstructor")
class CanvasView(context: Context, private val callbacks: Callbacks) : View(context) {

    interface Callbacks {
        /** Current behaviour for a canvas touch. */
        fun touchAction(): TouchAction
        fun beginStroke(first: InputPoint, isStylus: Boolean, isEraserTip: Boolean)
        fun strokeTo(points: List<InputPoint>)
        fun endStroke()
        fun cancelStroke()
        fun quickShape(done: (Boolean) -> Unit)
        fun fill(x: Int, y: Int)
        fun pickColor(x: Int, y: Int, final: Boolean)
        fun undo()
        fun redo()
        /** Diameter of the current brush in document pixels, for the hover outline. */
        fun brushDiameter(): Float
        fun fingerPaintingAllowed(): Boolean
        fun onStylusDetected()
        /** Moves the layer being transformed to [m] (document space). */
        fun transformTo(m: Affine)
    }

    /** The move/transform in progress, drawn as a box with handles; null when not transforming. */
    var transform: TransformUi? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var host: EngineHost? = null
        set(value) {
            field = value
            bitmap?.recycle()
            bitmap = value?.let { Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888) }
            value?.onFrame = { postInvalidateOnAnimation() }
            fitPending = true
            invalidate()
        }

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val inverse = Matrix()
    private var fitPending = true

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 90
        setShadowLayer(18f, 0f, 4f, Color.argb(120, 0, 0, 0))
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(200, 255, 255, 255)
    }
    private val outlineShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(110, 0, 0, 0)
    }
    private val loupePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val loupeRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val tmp = FloatArray(2)

    // Gesture state.
    private enum class Mode { IDLE, PAINTING, NAVIGATING, PICKING, WAITING }

    private var mode = Mode.IDLE
    private var stylusSeen = false
    private var strokeStart = 0L
    private var downX = 0f
    private var downY = 0f
    private var maxPointers = 0
    private var gestureStart = 0L
    private var gestureMoved = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastSpan = 0f
    private var lastAngle = 0f
    private var rotation = 0f
    private var activePointerId = -1

    // QuickShape: pen held still at the end of a stroke.
    private var stillX = 0f
    private var stillY = 0f
    private var shapeSnapped = false
    private val quickShapeCheck = Runnable {
        if (mode == Mode.PAINTING && !shapeSnapped) {
            callbacks.quickShape { snapped ->
                if (snapped) {
                    shapeSnapped = true
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }
    }
    private val holdToPick = Runnable {
        if (mode == Mode.PAINTING && !hasMovedFar && !isStylusStroke) {
            callbacks.cancelStroke()
            mode = Mode.PICKING
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            pick(downX, downY, final = false)
        }
    }
    private var hasMovedFar = false
    private var isStylusStroke = false

    // Hover outline and colour loupe.
    private var hoverX = Float.NaN
    private var hoverY = Float.NaN
    private var loupeX = Float.NaN
    private var loupeY = Float.NaN
    var loupeColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }
    var previousColor: Int = Color.WHITE

    init {
        isFocusable = true
    }

    // ---- View transform ---------------------------------------------------------------------------

    /** Space on the left covered by the sidebar, kept clear when fitting the canvas. */
    var leftInset: Float = 0f

    fun fitToScreen() {
        val h = host ?: return
        if (width == 0 || height == 0) return
        val margin = 16f * resources.displayMetrics.density
        val usableW = width - leftInset - 2 * margin
        val scale = min(usableW / h.width, (height - 2 * margin) / h.height)
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(leftInset + (width - leftInset - h.width * scale) / 2f, (height - h.height * scale) / 2f)
        rotation = 0f
        matrix.invert(inverse)
        invalidate()
    }

    private fun currentScale(): Float {
        val v = FloatArray(9)
        matrix.getValues(v)
        return hypot(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y])
    }

    private fun toDoc(x: Float, y: Float): Pair<Float, Float> {
        tmp[0] = x
        tmp[1] = y
        inverse.mapPoints(tmp)
        return tmp[0] to tmp[1]
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (fitPending || oldw == 0) {
            fitPending = false
            fitToScreen()
        } else {
            // Keep the picture centred when the window changes shape (rotation, split screen).
            matrix.postTranslate((w - oldw) / 2f, (h - oldh) / 2f)
            matrix.invert(inverse)
        }
    }

    // ---- Drawing ----------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BACKDROP)
        val h = host ?: return
        val bmp = bitmap ?: return
        if (fitPending && width > 0) {
            fitPending = false
            fitToScreen()
        }
        h.takeDirty()?.let { r ->
            synchronized(h.displayLock) {
                bmp.setPixels(h.display, r.top * h.width + r.left, h.width, r.left, r.top, r.width, r.height)
            }
        }
        canvas.save()
        canvas.concat(matrix)
        canvas.drawRect(0f, 0f, h.width.toFloat(), h.height.toFloat(), shadowPaint)
        // Smooth (bilinear) at normal zoom so strokes don't show pixel stairs; only show hard
        // pixels when zoomed right in for detail work, as Procreate does.
        bitmapPaint.isFilterBitmap = currentScale() < PIXEL_ZOOM
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        canvas.restore()

        transform?.let { drawTransformBox(canvas, it) }

        if (!hoverX.isNaN()) {
            val r = max(2f, callbacks.brushDiameter() * currentScale() / 2f)
            canvas.drawCircle(hoverX, hoverY, r, outlineShadow)
            canvas.drawCircle(hoverX, hoverY, r, outlinePaint)
        }
        if (mode == Mode.PICKING && !loupeX.isNaN()) drawLoupe(canvas)
    }

    private fun drawLoupe(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val r = 46f * d
        val cx = loupeX
        val cy = loupeY - 90f * d
        loupeRing.strokeWidth = 12f * d
        loupeRing.color = previousColor
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(oval, 0f, 180f, false, loupeRing)
        loupeRing.color = loupeColor
        canvas.drawArc(oval, 180f, 180f, false, loupeRing)
        loupePaint.color = Color.argb(160, 0, 0, 0)
        loupePaint.style = Paint.Style.STROKE
        loupePaint.strokeWidth = 1.5f * d
        canvas.drawCircle(cx, cy, r + 6f * d, loupePaint)
        canvas.drawCircle(cx, cy, r - 6f * d, loupePaint)
    }

    // ---- Input ------------------------------------------------------------------------------------

    private fun isStylus(e: MotionEvent, i: Int): Boolean {
        val t = e.getToolType(i)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun pointFrom(e: MotionEvent, index: Int, historyPos: Int = -1): InputPoint {
        val sx = if (historyPos < 0) e.getX(index) else e.getHistoricalX(index, historyPos)
        val sy = if (historyPos < 0) e.getY(index) else e.getHistoricalY(index, historyPos)
        val (x, y) = toDoc(sx, sy)
        val stylus = isStylus(e, index)
        val pressure = if (!stylus) 1f else if (historyPos < 0) e.getPressure(index) else e.getHistoricalPressure(index, historyPos)
        val tilt = if (!stylus) 0f else if (historyPos < 0) e.getAxisValue(MotionEvent.AXIS_TILT, index)
            else e.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, historyPos)
        val orientation = if (!stylus) 0f else if (historyPos < 0) e.getOrientation(index)
            else e.getHistoricalOrientation(index, historyPos)
        val time = if (historyPos < 0) e.eventTime else e.getHistoricalEventTime(historyPos)
        // Orientation is measured from "up"; turn it into a screen angle, then undo the canvas rotation.
        val azimuth = orientation - Math.toRadians(90.0).toFloat() - Math.toRadians(rotation.toDouble()).toFloat()
        return InputPoint(x, y, pressure.coerceIn(0f, 1f), tilt, azimuth, time)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (isStylus(event, 0)) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    hoverX = event.x
                    hoverY = event.y
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    hoverX = Float.NaN
                    hoverY = Float.NaN
                }
            }
            invalidate()
            return true
        }
        return super.onHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (host == null) return false
        hoverX = Float.NaN
        if (e.actionMasked == MotionEvent.ACTION_DOWN) transformGesture = transform != null
        if (transformGesture) {
            onTransformTouch(e)
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> onFirstDown(e)
            MotionEvent.ACTION_POINTER_DOWN -> onExtraDown(e)
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(e)
            MotionEvent.ACTION_UP -> onLastUp(e)
            MotionEvent.ACTION_CANCEL -> {
                if (mode == Mode.PAINTING) callbacks.cancelStroke()
                reset()
            }
        }
        return true
    }

    private fun onFirstDown(e: MotionEvent) {
        val stylus = isStylus(e, 0)
        if (stylus && !stylusSeen) {
            stylusSeen = true
            callbacks.onStylusDetected()
        }
        downX = e.x
        downY = e.y
        maxPointers = 1
        gestureStart = e.eventTime
        gestureMoved = false
        hasMovedFar = false
        shapeSnapped = false
        isStylusStroke = stylus
        activePointerId = e.getPointerId(0)
        val fingerBlocked = !stylus && stylusSeen && !callbacks.fingerPaintingAllowed()
        if (fingerBlocked) {
            mode = Mode.WAITING
            return
        }
        when (callbacks.touchAction()) {
            TouchAction.FILL, TouchAction.TRANSFORM -> {
                mode = Mode.WAITING
            }
            TouchAction.EYEDROPPER -> {
                mode = Mode.PICKING
                pick(e.x, e.y, final = false)
            }
            TouchAction.PAINT -> {
                mode = Mode.PAINTING
                strokeStart = e.eventTime
                stillX = e.x
                stillY = e.y
                callbacks.beginStroke(pointFrom(e, 0), stylus, e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)
                if (!stylus) postDelayed(holdToPick, HOLD_TO_PICK_MS)
            }
        }
    }

    private fun onExtraDown(e: MotionEvent) {
        maxPointers = max(maxPointers, e.pointerCount)
        if (mode == Mode.PAINTING) {
            // Fingers landing while the pen draws are a resting hand: ignore them.
            if (isStylusStroke) return
            // A second finger soon after the first means a gesture, so drop the stroke.
            if (hasMovedFar && e.eventTime - strokeStart > 250) return
            callbacks.cancelStroke()
        }
        removeCallbacks(holdToPick)
        removeCallbacks(quickShapeCheck)
        if (mode == Mode.PICKING) return
        mode = Mode.NAVIGATING
        gestureStart = if (maxPointers == 2) e.eventTime else gestureStart
        startTransform(e)
    }

    private fun startTransform(e: MotionEvent) {
        lastFocusX = focusX(e)
        lastFocusY = focusY(e)
        lastSpan = span(e)
        lastAngle = angle(e)
    }

    private fun onMove(e: MotionEvent) {
        when (mode) {
            Mode.PAINTING -> {
                val i = e.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: return
                if (!hasMovedFar && hypot(e.getX(i) - downX, e.getY(i) - downY) > slop) {
                    hasMovedFar = true
                    removeCallbacks(holdToPick)
                }
                if (shapeSnapped) return
                val points = ArrayList<InputPoint>(e.historySize + 1)
                for (h in 0 until e.historySize) points += pointFrom(e, i, h)
                points += pointFrom(e, i)
                callbacks.strokeTo(points)
                // Restart the QuickShape timer whenever the pen moves more than a few pixels.
                if (hypot(e.getX(i) - stillX, e.getY(i) - stillY) > QUICKSHAPE_STILL_PX) {
                    stillX = e.getX(i)
                    stillY = e.getY(i)
                    removeCallbacks(quickShapeCheck)
                    if (hasMovedFar) postDelayed(quickShapeCheck, QUICKSHAPE_HOLD_MS)
                }
            }
            Mode.NAVIGATING -> {
                if (e.pointerCount < 2) return
                val fx = focusX(e)
                val fy = focusY(e)
                val sp = span(e)
                val an = angle(e)
                if (!gestureMoved && (hypot(fx - lastFocusX, fy - lastFocusY) > slop || abs(sp - lastSpan) > slop)) {
                    gestureMoved = true
                }
                if (gestureMoved) {
                    val scaleBy = if (lastSpan > 0f) sp / lastSpan else 1f
                    val newScale = (currentScale() * scaleBy).coerceIn(MIN_SCALE, MAX_SCALE)
                    val applied = newScale / currentScale()
                    val rotateBy = Math.toDegrees((an - lastAngle).toDouble()).toFloat()
                    matrix.postTranslate(fx - lastFocusX, fy - lastFocusY)
                    matrix.postScale(applied, applied, fx, fy)
                    matrix.postRotate(rotateBy, fx, fy)
                    rotation += rotateBy
                    matrix.invert(inverse)
                    invalidate()
                }
                lastFocusX = fx
                lastFocusY = fy
                lastSpan = sp
                lastAngle = an
            }
            Mode.PICKING -> {
                val i = e.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: 0
                pick(e.getX(i), e.getY(i), final = false)
            }
            Mode.WAITING -> {
                if (hypot(e.x - downX, e.y - downY) > slop) gestureMoved = true
                if (e.pointerCount >= 2) {
                    mode = Mode.NAVIGATING
                    startTransform(e)
                }
            }
            Mode.IDLE -> Unit
        }
    }

    private fun onPointerUp(e: MotionEvent) {
        if (mode == Mode.NAVIGATING) {
            // Re-anchor on the remaining fingers so the picture doesn't jump.
            val remaining = (0 until e.pointerCount).filter { it != e.actionIndex }
            if (remaining.size >= 2) {
                var sx = 0f
                var sy = 0f
                for (i in remaining) { sx += e.getX(i); sy += e.getY(i) }
                lastFocusX = sx / remaining.size
                lastFocusY = sy / remaining.size
                val a = remaining[0]
                val b = remaining[1]
                lastSpan = hypot(e.getX(a) - e.getX(b), e.getY(a) - e.getY(b))
                lastAngle = atan2(e.getY(b) - e.getY(a), e.getX(b) - e.getX(a))
            }
        } else if (mode == Mode.PAINTING && e.getPointerId(e.actionIndex) == activePointerId) {
            finishStroke()
            mode = Mode.WAITING
        }
    }

    private fun onLastUp(e: MotionEvent) {
        removeCallbacks(holdToPick)
        removeCallbacks(quickShapeCheck)
        when (mode) {
            Mode.PAINTING -> finishStroke()
            Mode.PICKING -> pick(e.x, e.y, final = true)
            Mode.NAVIGATING -> {
                val quick = e.eventTime - gestureStart < TAP_MS && !gestureMoved
                if (quick && maxPointers == 2) callbacks.undo()
                if (quick && maxPointers >= 3) callbacks.redo()
                snapRotation()
            }
            Mode.WAITING -> {
                val quick = e.eventTime - gestureStart < TAP_MS && !gestureMoved
                if (callbacks.touchAction() == TouchAction.FILL && !gestureMoved && maxPointers == 1) {
                    val (x, y) = toDoc(e.x, e.y)
                    callbacks.fill(x.toInt(), y.toInt())
                } else if (quick && maxPointers == 2) {
                    callbacks.undo()
                } else if (quick && maxPointers >= 3) {
                    callbacks.redo()
                }
            }
            Mode.IDLE -> Unit
        }
        reset()
    }

    private fun finishStroke() {
        removeCallbacks(quickShapeCheck)
        removeCallbacks(holdToPick)
        callbacks.endStroke()
    }

    private fun pick(x: Float, y: Float, final: Boolean) {
        loupeX = x
        loupeY = y
        val (dx, dy) = toDoc(x, y)
        callbacks.pickColor(dx.toInt(), dy.toInt(), final)
        invalidate()
    }

    /** Straightens the canvas if it was left within a few degrees of upright. */
    private fun snapRotation() {
        val r = ((rotation % 360f) + 360f) % 360f
        val nearest = Math.round(r / 90f) * 90f
        val delta = nearest - r
        if (abs(delta) in 0.01f..ROTATION_SNAP_DEG) {
            matrix.postRotate(delta, width / 2f, height / 2f)
            rotation += delta
            matrix.invert(inverse)
            invalidate()
        }
    }

    private fun reset() {
        mode = Mode.IDLE
        maxPointers = 0
        loupeX = Float.NaN
        activePointerId = -1
        invalidate()
    }

    private fun focusX(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getX(i)
        return s / e.pointerCount
    }

    private fun focusY(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getY(i)
        return s / e.pointerCount
    }

    private fun span(e: MotionEvent): Float =
        if (e.pointerCount < 2) 0f else hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun angle(e: MotionEvent): Float =
        if (e.pointerCount < 2) 0f else atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0))

    // ---- Move and transform -------------------------------------------------------------------------

    private enum class Grab { NONE, MOVE, CORNER, EDGE, ROTATE, PINCH }

    private var transformGesture = false
    private var grab = Grab.NONE
    private var grabIndex = 0
    private var grabStart = Affine.IDENTITY
    private var grabX = 0f
    private var grabY = 0f

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(77, 163, 255)
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    /** Corners then edge midpoints of the source box, in its own (pre-transform) space. */
    private fun localHandles(t: TransformUi): FloatArray {
        val r = t.source
        val l = r.left.toFloat()
        val tp = r.top.toFloat()
        val rt = r.right.toFloat()
        val b = r.bottom.toFloat()
        val mx = (l + rt) / 2f
        val my = (tp + b) / 2f
        // 0 TL, 1 TR, 2 BR, 3 BL, then edges 4 top, 5 right, 6 bottom, 7 left.
        return floatArrayOf(l, tp, rt, tp, rt, b, l, b, mx, tp, rt, my, mx, b, l, my)
    }

    /** The handles in screen space, plus the rotate handle and the box centre at the end. */
    private fun screenHandles(t: TransformUi): FloatArray {
        val local = localHandles(t)
        val pts = FloatArray(local.size + 4)
        for (i in 0 until local.size / 2) {
            pts[i * 2] = t.matrix.mapX(local[i * 2], local[i * 2 + 1])
            pts[i * 2 + 1] = t.matrix.mapY(local[i * 2], local[i * 2 + 1])
        }
        pts[local.size + 2] = t.matrix.mapX(t.centerX, t.centerY)
        pts[local.size + 3] = t.matrix.mapY(t.centerX, t.centerY)
        matrix.mapPoints(pts)
        // Rotate handle: beyond the top edge, pointing away from the centre.
        val cx = pts[local.size + 2]
        val cy = pts[local.size + 3]
        val tx = pts[8]
        val ty = pts[9]
        val len = hypot(tx - cx, ty - cy).coerceAtLeast(1f)
        val out = ROTATE_HANDLE_DP * resources.displayMetrics.density
        pts[local.size] = tx + (tx - cx) / len * out
        pts[local.size + 1] = ty + (ty - cy) / len * out
        return pts
    }

    private fun drawTransformBox(canvas: Canvas, t: TransformUi) {
        val d = resources.displayMetrics.density
        val p = screenHandles(t)
        boxPaint.strokeWidth = 1.5f * d
        outlineShadow.strokeWidth = 3.5f * d
        for (paint in listOf(outlineShadow, boxPaint)) {
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                canvas.drawLine(p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1], paint)
            }
            canvas.drawLine(p[8], p[9], p[16], p[17], paint)
        }
        for (i in 0 until 9) {
            val r = (if (i < 4) 8f else if (i < 8) 6f else 9f) * d
            canvas.drawCircle(p[i * 2], p[i * 2 + 1], r + 1.5f * d, outlineShadow)
            canvas.drawCircle(p[i * 2], p[i * 2 + 1], r, handleFill)
            canvas.drawCircle(p[i * 2], p[i * 2 + 1], r, boxPaint)
        }
        // A small arrow-ish mark on the rotate handle so it reads as "turn".
        canvas.drawCircle(p[16], p[17], 3.5f * d, boxPaint)
    }

    private fun onTransformTouch(e: MotionEvent) {
        val t = transform
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (t == null) return
                grabStart = t.matrix
                val (x, y) = toDoc(e.x, e.y)
                grabX = x
                grabY = y
                val p = screenHandles(t)
                val hit = HANDLE_HIT_DP * resources.displayMetrics.density
                fun near(i: Int) = hypot(p[i * 2] - e.x, p[i * 2 + 1] - e.y) < hit
                grab = when {
                    near(8) -> Grab.ROTATE
                    (0 until 4).any { near(it) } -> Grab.CORNER.also { grabIndex = (0 until 4).minBy { hypot(p[it * 2] - e.x, p[it * 2 + 1] - e.y) } }
                    (4 until 8).any { near(it) } -> Grab.EDGE.also { grabIndex = (4 until 8).minBy { hypot(p[it * 2] - e.x, p[it * 2 + 1] - e.y) } }
                    else -> Grab.MOVE
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (t == null) return
                grab = Grab.PINCH
                startTransform(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (t == null) return
                if (grab == Grab.PINCH) {
                    if (e.pointerCount < 2) return
                    pinchTransform(e, t)
                    return
                }
                val (x, y) = toDoc(e.x, e.y)
                callbacks.transformTo(dragTransform(t, x, y))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (grab == Grab.PINCH) grab = Grab.NONE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                grab = Grab.NONE
                transformGesture = false
            }
        }
    }

    private fun dragTransform(t: TransformUi, x: Float, y: Float): Affine {
        val m0 = grabStart
        val r = t.source
        when (grab) {
            Grab.MOVE -> return Affine.translate(x - grabX, y - grabY) * m0
            Grab.ROTATE -> {
                val cx = m0.mapX(t.centerX, t.centerY)
                val cy = m0.mapY(t.centerX, t.centerY)
                var delta = Math.toDegrees((atan2(y - cy, x - cx) - atan2(grabY - cy, grabX - cx)).toDouble()).toFloat()
                // Snap to 45 degree steps when close, so straight is easy to hit.
                val total = m0.rotationDegrees + delta
                val nearest = Math.round(total / 45f) * 45f
                if (abs(nearest - total) < ROTATE_SNAP_DEG) delta += nearest - total
                return Affine.about(cx, cy, Affine.rotate(delta)) * m0
            }
            Grab.CORNER -> {
                val inv = m0.inverse()
                val lx = inv.mapX(x, y)
                val ly = inv.mapY(x, y)
                val local = localHandles(t)
                val ax = local[((grabIndex + 2) % 4) * 2]
                val ay = local[((grabIndex + 2) % 4) * 2 + 1]
                val cx = local[grabIndex * 2] - ax
                val cy = local[grabIndex * 2 + 1] - ay
                val s = (((lx - ax) * cx + (ly - ay) * cy) / (cx * cx + cy * cy)).coerceAtLeast(MIN_TRANSFORM_SCALE)
                return m0 * Affine.about(ax, ay, Affine.scale(s))
            }
            Grab.EDGE -> {
                val inv = m0.inverse()
                val lx = inv.mapX(x, y)
                val ly = inv.mapY(x, y)
                val w = r.width.toFloat().coerceAtLeast(1f)
                val h = r.height.toFloat().coerceAtLeast(1f)
                val stretch = when (grabIndex) {
                    4 -> Affine.about(0f, r.bottom.toFloat(), Affine.scale(1f, ((r.bottom - ly) / h).coerceAtLeast(MIN_TRANSFORM_SCALE)))
                    5 -> Affine.about(r.left.toFloat(), 0f, Affine.scale(((lx - r.left) / w).coerceAtLeast(MIN_TRANSFORM_SCALE), 1f))
                    6 -> Affine.about(0f, r.top.toFloat(), Affine.scale(1f, ((ly - r.top) / h).coerceAtLeast(MIN_TRANSFORM_SCALE)))
                    else -> Affine.about(r.right.toFloat(), 0f, Affine.scale(((r.right - lx) / w).coerceAtLeast(MIN_TRANSFORM_SCALE), 1f))
                }
                return m0 * stretch
            }
            else -> return t.matrix
        }
    }

    /** Two fingers move, scale and turn the object together, like holding it. */
    private fun pinchTransform(e: MotionEvent, t: TransformUi) {
        val fx = focusX(e)
        val fy = focusY(e)
        val sp = span(e)
        val an = angle(e)
        val (dx0, dy0) = toDoc(lastFocusX, lastFocusY)
        val (dx1, dy1) = toDoc(fx, fy)
        val k = if (lastSpan > 0f && sp > 0f) sp / lastSpan else 1f
        val rot = Math.toDegrees((an - lastAngle).toDouble()).toFloat()
        val step = Affine.translate(dx1, dy1) * Affine.rotate(rot) * Affine.scale(k) * Affine.translate(-dx0, -dy0)
        callbacks.transformTo(step * t.matrix)
        // Track the new matrix locally too, since the state round trip lags a frame.
        transform = t.copy(matrix = step * t.matrix)
        lastFocusX = fx
        lastFocusY = fy
        lastSpan = sp
        lastAngle = an
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(holdToPick)
        removeCallbacks(quickShapeCheck)
    }

    companion object {
        /** Zoom at which the canvas switches to showing individual pixels. */
        private const val PIXEL_ZOOM = 8f

        private val BACKDROP = Color.rgb(40, 40, 46)
        private const val MIN_SCALE = 0.05f
        private const val MAX_SCALE = 32f
        private const val TAP_MS = 280L
        private const val HOLD_TO_PICK_MS = 500L
        private const val QUICKSHAPE_HOLD_MS = 550L
        private const val QUICKSHAPE_STILL_PX = 6f
        private const val ROTATION_SNAP_DEG = 6f
        private const val HANDLE_HIT_DP = 28f
        private const val ROTATE_HANDLE_DP = 34f
        private const val ROTATE_SNAP_DEG = 4f
        private const val MIN_TRANSFORM_SCALE = 0.02f
    }
}
