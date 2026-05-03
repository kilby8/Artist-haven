package com.artisthaven.app.presentation.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.artisthaven.app.domain.model.BlendBehavior
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.StrokePoint
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * God-tier high-frequency stamp brush.
 *
 * Features:
 * - Catmull-Rom spline interpolation for smooth curves
 * - Stamp loop (bristle bitmap at 2% brush-size spacing)
 * - Velocity-based thinning (width/alpha dry-out)
 * - Dual texture system: bristle-tip + canvas grain compose shader
 * - Hardware render path via RenderNode when available
 * - Wet blending via DARKEN / SRC_ATOP / MULTIPLY / etc.
 */
class PaintBrush(
    private val context: Context,
) {
    private val bristleBitmap: Bitmap by lazy { createBristleTipBitmap(80) }
    private val paperBitmap: Bitmap by lazy { createColdPressPaperBitmap(128) }

    private val tipShaderMatrix = Matrix()
    private val grainShaderMatrix = Matrix()

    fun renderStroke(
        canvas: Canvas,
        points: List<StrokePoint>,
        brush: Brush,
    ) {
        if (points.isEmpty()) return

        val samples = points.map { MasterSample(it.x, it.y, it.pressure.coerceIn(0f, 1f), it.timestamp) }
        val spline = catmullRomSpline(samples)
        if (spline.size < 2) {
            drawSingleStamp(canvas, samples.first(), brush)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated) {
            val node = RenderNode("PaintBrush").apply {
                setPosition(0, 0, max(1, canvas.width), max(1, canvas.height))
            }
            val rc = node.beginRecording(max(1, canvas.width), max(1, canvas.height))
            drawStampLoop(rc, spline, brush)
            node.endRecording()
            canvas.drawRenderNode(node)
        } else {
            drawStampLoop(canvas, spline, brush)
        }
    }

    private fun drawStampLoop(canvas: Canvas, spline: List<MasterSample>, brush: Brush) {
        val spacingPx = max(0.8f, brush.size * 0.02f) // 2% of brush size (well below 10%)
        val wetBlend = toPorterDuffMode(brush.profile.blend)

        val tipTile = if (brush.profile.grain.enabled) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
        val tipShader = BitmapShader(bristleBitmap, tipTile, tipTile)
        val grainShader = BitmapShader(paperBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val dualShader = ComposeShader(tipShader, grainShader, PorterDuff.Mode.MULTIPLY)

        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            isFilterBitmap = true
            // Use SRC_OVER for real paint-like opacity build-up between dabs.
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            colorFilter = PorterDuffColorFilter(brush.color.toArgb(), PorterDuff.Mode.SRC_ATOP)
            shader = dualShader
        }

        var carry = 0f
        for (i in 1 until spline.size) {
            val a = spline[i - 1]
            val b = spline[i]
            val segLen = hypot(b.x - a.x, b.y - a.y)
            if (segLen <= 0.01f) continue

            val dt = max(1f, (b.t - a.t).toFloat())
            val velocity = segLen / dt
            val dynamics = dynamics(brush, pressure = b.pressure, velocity = velocity)

            // Wet/soft edges
            stampPaint.maskFilter = if (brush.profile.edge.softness > 0.01f) {
                BlurMaskFilter(dynamics.width * brush.profile.edge.softness, BlurMaskFilter.Blur.NORMAL)
            } else {
                null
            }

            var consumed = carry
            while (consumed + spacingPx <= segLen) {
                consumed += spacingPx
                val t = (consumed / segLen).coerceIn(0f, 1f)
                val x = lerp(a.x, b.x, t)
                val y = lerp(a.y, b.y, t)
                val tangentX = b.x - a.x
                val tangentY = b.y - a.y
                val angleDeg = Math.toDegrees(kotlin.math.atan2(tangentY.toDouble(), tangentX.toDouble())).toFloat()

                drawStampAt(
                    canvas = canvas,
                    paint = stampPaint,
                    tipShader = tipShader,
                    grainShader = grainShader,
                    brush = brush,
                    x = x,
                    y = y,
                    width = dynamics.width,
                    alpha = dynamics.alpha,
                    angleDeg = angleDeg,
                    wetBlend = wetBlend,
                )
            }
            carry = max(0f, consumed - segLen)
        }
    }

    private fun drawSingleStamp(canvas: Canvas, sample: MasterSample, brush: Brush) {
        val tipShader = BitmapShader(bristleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val grainShader = BitmapShader(paperBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = ComposeShader(tipShader, grainShader, PorterDuff.Mode.MULTIPLY)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            colorFilter = PorterDuffColorFilter(brush.color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }
        val dyn = dynamics(brush, sample.pressure, velocity = 0f)
        drawStampAt(
            canvas,
            paint,
            tipShader,
            grainShader,
            brush,
            sample.x,
            sample.y,
            dyn.width,
            dyn.alpha,
            0f,
            toPorterDuffMode(brush.profile.blend),
        )
    }

    private fun drawStampAt(
        canvas: Canvas,
        paint: Paint,
        tipShader: BitmapShader,
        grainShader: BitmapShader,
        brush: Brush,
        x: Float,
        y: Float,
        width: Float,
        alpha: Int,
        angleDeg: Float,
        wetBlend: PorterDuff.Mode,
    ) {
        paint.alpha = alpha

        val tipScale = max(0.08f, width / bristleBitmap.width)
        tipShaderMatrix.reset()
        tipShaderMatrix.postScale(tipScale, tipScale)
        tipShaderMatrix.postRotate(angleDeg, bristleBitmap.width * tipScale * 0.5f, bristleBitmap.height * tipScale * 0.5f)
        tipShaderMatrix.postTranslate(x - bristleBitmap.width * tipScale * 0.5f, y - bristleBitmap.height * tipScale * 0.5f)
        tipShader.setLocalMatrix(tipShaderMatrix)

        val grainScale = brush.profile.grain.scale.coerceIn(0.2f, 4f)
        grainShaderMatrix.reset()
        grainShaderMatrix.postScale(grainScale, grainScale)
        grainShader.setLocalMatrix(grainShaderMatrix)

        val r = max(0.35f, width * 0.5f)

        // Wet edge pass: blur only edge layer for liquid feathering.
        if (brush.profile.edge.softness > 0.01f) {
            val edgePaint = Paint(paint).apply {
                this.alpha = (alpha * 0.35f).toInt().coerceIn(0, 255)
                maskFilter = BlurMaskFilter(width * brush.profile.edge.softness, BlurMaskFilter.Blur.NORMAL)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            }
            canvas.drawCircle(x, y, r * 1.08f, edgePaint)
        }

        // Core dab: either soft-edged circle or explicit bristle bitmap stamp.
        if (brush.profile.tip.useBitmapStamp) {
            val half = r
            val dst = android.graphics.RectF(x - half, y - half, x + half, y + half)
            paint.maskFilter = null
            canvas.drawBitmap(bristleBitmap, null, dst, paint)
        } else {
            paint.maskFilter = null
            canvas.drawCircle(x, y, r, paint)
        }

        // Optional wet-mix glaze pass for DARKEN/SRC_ATOP/MULTIPLY styles.
        if (wetBlend != PorterDuff.Mode.SRC_OVER) {
            val glazePaint = Paint(paint).apply {

                this.alpha = (alpha * 0.45f).toInt().coerceIn(0, 255)
                xfermode = PorterDuffXfermode(wetBlend)
                maskFilter = null
            }
            canvas.drawCircle(x, y, r, glazePaint)
        }
    }

    /**
     * Render a stroke with canvas tooth interaction enabled.
     *
     * Uses PorterDuff.Mode.DST_IN to mask strokes through canvas texture.
     * Light pressure fills only the peaks of the paper texture, leaving valleys white
     * (or transparent, depending on the canvas type).
     * This creates the dry-brush effect seen in Adobe Fresco.
     *
     * @param canvas Target canvas
     * @param points Stroke points
     * @param brush Brush configuration
     * @param canvasTexture Bitmap to use as tooth mask (optional)
     * @param toothIntensity How strongly the texture affects brush strokes (0f - 1f)
     */
    fun renderStrokeWithToothInteraction(
        canvas: Canvas,
        points: List<StrokePoint>,
        brush: Brush,
        canvasTexture: Bitmap? = null,
        toothIntensity: Float = 0.35f,
    ) {
        if (points.isEmpty()) return

        val samples = points.map { MasterSample(it.x, it.y, it.pressure.coerceIn(0f, 1f), it.timestamp) }
        val spline = catmullRomSpline(samples)
        if (spline.size < 2) {
            drawSingleStampWithTooth(canvas, samples.first(), brush, canvasTexture, toothIntensity)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated) {
            val node = RenderNode("PaintBrushWithTooth").apply {
                setPosition(0, 0, max(1, canvas.width), max(1, canvas.height))
            }
            val rc = node.beginRecording(max(1, canvas.width), max(1, canvas.height))
            drawStampLoopWithTooth(rc, spline, brush, canvasTexture, toothIntensity)
            node.endRecording()
            canvas.drawRenderNode(node)
        } else {
            drawStampLoopWithTooth(canvas, spline, brush, canvasTexture, toothIntensity)
        }
    }

    private fun drawStampLoopWithTooth(
        canvas: Canvas,
        spline: List<MasterSample>,
        brush: Brush,
        canvasTexture: Bitmap?,
        toothIntensity: Float,
    ) {
        val spacingPx = max(0.8f, brush.size * 0.02f)
        val wetBlend = toPorterDuffMode(brush.profile.blend)

        val tipTile = if (brush.profile.grain.enabled) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
        val tipShader = BitmapShader(bristleBitmap, tipTile, tipTile)
        val grainShader = BitmapShader(paperBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val dualShader = ComposeShader(tipShader, grainShader, PorterDuff.Mode.MULTIPLY)

        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            colorFilter = PorterDuffColorFilter(brush.color.toArgb(), PorterDuff.Mode.SRC_ATOP)
            shader = dualShader
        }

        // Tooth interaction shader
        val toothShader = if (canvasTexture != null) {
            BitmapShader(canvasTexture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        } else {
            null
        }

        var carry = 0f
        for (i in 1 until spline.size) {
            val a = spline[i - 1]
            val b = spline[i]
            val segLen = hypot(b.x - a.x, b.y - a.y)
            if (segLen <= 0.01f) continue

            val dt = max(1f, (b.t - a.t).toFloat())
            val velocity = segLen / dt
            val dynamics = dynamics(brush, pressure = b.pressure, velocity = velocity)

            stampPaint.maskFilter = if (brush.profile.edge.softness > 0.01f) {
                BlurMaskFilter(dynamics.width * brush.profile.edge.softness, BlurMaskFilter.Blur.NORMAL)
            } else {
                null
            }

            var consumed = carry
            while (consumed + spacingPx <= segLen) {
                consumed += spacingPx
                val t = (consumed / segLen).coerceIn(0f, 1f)
                val x = lerp(a.x, b.x, t)
                val y = lerp(a.y, b.y, t)
                val tangentX = b.x - a.x
                val tangentY = b.y - a.y
                val angleDeg = Math.toDegrees(kotlin.math.atan2(tangentY.toDouble(), tangentX.toDouble())).toFloat()

                drawStampAtWithTooth(
                    canvas = canvas,
                    paint = stampPaint,
                    tipShader = tipShader,
                    grainShader = grainShader,
                    toothShader = toothShader,
                    brush = brush,
                    x = x,
                    y = y,
                    width = dynamics.width,
                    alpha = dynamics.alpha,
                    angleDeg = angleDeg,
                    wetBlend = wetBlend,
                    toothIntensity = toothIntensity,
                )
            }
            carry = max(0f, consumed - segLen)
        }
    }

    private fun drawSingleStampWithTooth(
        canvas: Canvas,
        sample: MasterSample,
        brush: Brush,
        canvasTexture: Bitmap?,
        toothIntensity: Float,
    ) {
        val tipShader = BitmapShader(bristleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val grainShader = BitmapShader(paperBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = ComposeShader(tipShader, grainShader, PorterDuff.Mode.MULTIPLY)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            colorFilter = PorterDuffColorFilter(brush.color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }
        val dyn = dynamics(brush, sample.pressure, velocity = 0f)

        val toothShader = if (canvasTexture != null) {
            BitmapShader(canvasTexture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        } else {
            null
        }

        drawStampAtWithTooth(
            canvas,
            paint,
            tipShader,
            grainShader,
            toothShader,
            brush,
            sample.x,
            sample.y,
            dyn.width,
            dyn.alpha,
            0f,
            toPorterDuffMode(brush.profile.blend),
            toothIntensity,
        )
    }

    private fun drawStampAtWithTooth(
        canvas: Canvas,
        paint: Paint,
        tipShader: BitmapShader,
        grainShader: BitmapShader,
        toothShader: BitmapShader?,
        brush: Brush,
        x: Float,
        y: Float,
        width: Float,
        alpha: Int,
        angleDeg: Float,
        wetBlend: PorterDuff.Mode,
        toothIntensity: Float,
    ) {
        paint.alpha = alpha

        val tipScale = max(0.08f, width / bristleBitmap.width)
        tipShaderMatrix.reset()
        tipShaderMatrix.postScale(tipScale, tipScale)
        tipShaderMatrix.postRotate(angleDeg, bristleBitmap.width * tipScale * 0.5f, bristleBitmap.height * tipScale * 0.5f)
        tipShaderMatrix.postTranslate(x - bristleBitmap.width * tipScale * 0.5f, y - bristleBitmap.height * tipScale * 0.5f)
        tipShader.setLocalMatrix(tipShaderMatrix)

        val grainScale = brush.profile.grain.scale.coerceIn(0.2f, 4f)
        grainShaderMatrix.reset()
        grainShaderMatrix.postScale(grainScale, grainScale)
        grainShader.setLocalMatrix(grainShaderMatrix)

        val r = max(0.35f, width * 0.5f)

        // Soft edge pass
        if (brush.profile.edge.softness > 0.01f) {
            val edgePaint = Paint(paint).apply {
                this.alpha = (alpha * 0.35f).toInt().coerceIn(0, 255)
                maskFilter = BlurMaskFilter(width * brush.profile.edge.softness, BlurMaskFilter.Blur.NORMAL)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            }
            canvas.drawCircle(x, y, r * 1.08f, edgePaint)
        }

        // Core dab with optional tooth masking
        if (brush.profile.tip.useBitmapStamp) {
            val half = r
            val dst = android.graphics.RectF(x - half, y - half, x + half, y + half)
            paint.maskFilter = null
            canvas.drawBitmap(bristleBitmap, null, dst, paint)
        } else {
            paint.maskFilter = null
            canvas.drawCircle(x, y, r, paint)
        }

        // Tooth interaction: apply canvas texture mask for dry brush effect
        if (toothShader != null && toothIntensity > 0.01f) {
            val toothPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = toothShader
                this.alpha = (alpha * toothIntensity).toInt().coerceIn(0, 255)
                // DST_IN: keep only where mask is opaque
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                isFilterBitmap = true
            }
            canvas.drawCircle(x, y, r, toothPaint)
        }

        // Wet-mix glaze pass
        if (wetBlend != PorterDuff.Mode.SRC_OVER) {
            val glazePaint = Paint(paint).apply {
                this.alpha = (alpha * 0.45f).toInt().coerceIn(0, 255)
                xfermode = PorterDuffXfermode(wetBlend)
                maskFilter = null
            }
            canvas.drawCircle(x, y, r, glazePaint)
        }
    }

    private fun dynamics(brush: Brush, pressure: Float, velocity: Float): DynamicsResult {
        val cfg = brush.profile.dynamics
        val p = pressure.coerceIn(0f, 1f)
        val pCurve = p.pow(cfg.powerCurveExponent)
        val vNorm = (velocity / cfg.velocityNormalization).coerceIn(0f, 1f)
        val vCurve = (1f - vNorm).pow(cfg.powerCurveExponent)

        val widthT = (cfg.pressureToWidth * pCurve + cfg.velocityToWidth * vCurve).coerceIn(0f, 1f)
        val alphaT = (cfg.pressureToAlpha * pCurve + cfg.velocityToAlpha * vCurve).coerceIn(0f, 1f)

        // Thinning / running dry at high velocity.
        val dryDown = (1f - min(1f, velocity * 0.9f)).coerceIn(0.2f, 1f)

        val widthMul = lerp(cfg.minWidthMultiplier, cfg.maxWidthMultiplier, widthT) * dryDown
        val alphaMul = lerp(cfg.minAlphaMultiplier, cfg.maxAlphaMultiplier, alphaT) * dryDown

        return DynamicsResult(
            width = max(0.5f, brush.size * widthMul),
            alpha = (brush.opacity * alphaMul * 255f).toInt().coerceIn(20, 255),
        )
    }

    private fun catmullRomSpline(points: List<MasterSample>, stepsPerSegment: Int = 8): List<MasterSample> {
        if (points.size < 2) return points
        val out = ArrayList<MasterSample>(points.size * stepsPerSegment)

        for (i in points.indices) {
            val p0 = points[max(0, i - 1)]
            val p1 = points[i]
            val p2 = points[min(points.lastIndex, i + 1)]
            val p3 = points[min(points.lastIndex, i + 2)]

            for (s in 0 until stepsPerSegment) {
                val t = s.toFloat() / stepsPerSegment
                val tt = t * t
                val ttt = tt * t

                val x = 0.5f * (
                    2f * p1.x +
                        (-p0.x + p2.x) * t +
                        (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * tt +
                        (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * ttt
                    )

                val y = 0.5f * (
                    2f * p1.y +
                        (-p0.y + p2.y) * t +
                        (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * tt +
                        (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * ttt
                    )

                out += MasterSample(
                    x = x,
                    y = y,
                    pressure = lerp(p1.pressure, p2.pressure, t),
                    t = lerp(p1.t.toFloat(), p2.t.toFloat(), t).toLong(),
                )
            }
        }

        out += points.last()
        return out
    }

    private fun toPorterDuffMode(behavior: BlendBehavior): PorterDuff.Mode = when (behavior) {
        BlendBehavior.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        BlendBehavior.DARKEN -> PorterDuff.Mode.DARKEN
        BlendBehavior.SRC_ATOP -> PorterDuff.Mode.SRC_ATOP
        BlendBehavior.CLEAR -> PorterDuff.Mode.CLEAR
        BlendBehavior.NORMAL -> PorterDuff.Mode.SRC_OVER
    }

    private fun createBristleTipBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }

        // Build clustered bristles in one tip bitmap.
        val center = size * 0.5f
        for (i in 0 until 72) {
            val angle = (i * 137.5f) % 360f
            val radius = (size * 0.12f) + (i % 7) * (size * 0.045f)
            val x = center + (kotlin.math.cos(Math.toRadians(angle.toDouble())) * radius).toFloat()
            val y = center + (kotlin.math.sin(Math.toRadians(angle.toDouble())) * radius).toFloat()
            val r = (size * 0.018f) + (i % 3) * (size * 0.008f)
            p.alpha = 95 + (i * 11 % 120)
            c.drawCircle(x, y, r, p)
        }
        return bmp
    }

    private fun createColdPressPaperBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = kotlin.random.Random(4242)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val base = 180 + random.nextInt(56)
                val warm = base + random.nextInt(14)
                val argb = android.graphics.Color.argb(255, warm.coerceAtMost(255), base, (base - 8).coerceAtLeast(0))
                bmp.setPixel(x, y, argb)
            }
        }
        return bmp
    }

    private data class MasterSample(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val t: Long,
    )

    private data class DynamicsResult(
        val width: Float,
        val alpha: Int,
    )

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}


