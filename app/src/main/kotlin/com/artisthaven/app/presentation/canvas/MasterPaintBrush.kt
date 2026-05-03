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
import kotlin.random.Random

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
class MasterPaintBrush(
    private val context: Context,
) {
    private val bristleBitmap: Bitmap by lazy { createBristleTipBitmap(80) }
    private val paperBitmap: Bitmap by lazy { createColdPressPaperBitmap(128) }
    private val random = Random(1337)

    private val tipShaderMatrix = Matrix()
    private val grainShaderMatrix = Matrix()

    fun renderStroke(
        canvas: Canvas,
        points: List<StrokePoint>,
        brush: Brush,
        isPreview: Boolean = false,
    ) {
        if (points.isEmpty()) return

        val samples = points.map { MasterSample(it.x, it.y, it.pressure.coerceIn(0f, 1f), it.timestamp) }
        val spline = catmullRomSpline(samples)
        if (spline.size < 2) {
            drawSingleStamp(canvas, samples.first(), brush, isPreview)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated) {
            val node = RenderNode("MasterPaintBrush").apply {
                setPosition(0, 0, max(1, canvas.width), max(1, canvas.height))
            }
            val rc = node.beginRecording(max(1, canvas.width), max(1, canvas.height))
            drawStampLoop(rc, spline, brush, isPreview)
            node.endRecording()
            canvas.drawRenderNode(node)
        } else {
            drawStampLoop(canvas, spline, brush, isPreview)
        }
    }

    private fun drawStampLoop(canvas: Canvas, spline: List<MasterSample>, brush: Brush, isPreview: Boolean) {
        val overlapAlias = (0.35f + brush.profile.tip.fluidVelocitySpacingTightening.coerceIn(0f, 0.8f) * 0.65f)
            .coerceIn(0f, 1f)
        val overlapFactor = max(brush.profile.tip.overlapFactor.coerceIn(0f, 1f), overlapAlias)
        val minGap = brush.profile.tip.minGapClamping.coerceAtLeast(0.25f)
        val baseSpacing = (brush.size * (1f - overlapFactor)).coerceAtLeast(minGap)
        val spacingTightening = brush.profile.tip.fluidVelocitySpacingTightening.coerceIn(0f, 0.8f)
        val fluidJitterPercent = brush.profile.tip.fluidJitterPercent.coerceIn(0f, 0.12f)
        val microDabEnabled = brush.profile.tip.enableMicroDab || brush.profile.tip.useMicroDabs

        val tipTile = if (brush.profile.grain.enabled) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
        val tipShader = BitmapShader(bristleBitmap, tipTile, tipTile)
        val grainShader = BitmapShader(paperBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val dualShader = ComposeShader(tipShader, grainShader, PorterDuff.Mode.MULTIPLY)

        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            isFilterBitmap = true
            // Fluid accumulation: always SRC_OVER with low per-dab alpha.
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            colorFilter = PorterDuffColorFilter(brush.color.toArgb(), PorterDuff.Mode.SRC_ATOP)
            shader = dualShader
        }

        var carry = 0f
        var alphaEma = -1f
        for (i in 1 until spline.size) {
            val a = spline[i - 1]
            val b = spline[i]
            val segLen = hypot(b.x - a.x, b.y - a.y)
            if (segLen <= 0.01f) continue

            val dt = max(1f, (b.t - a.t).toFloat())
            val velocity = segLen / dt
            val dynamics = dynamics(brush, pressure = b.pressure, velocity = velocity)
            val velocityFactor = (velocity / 1.2f).coerceIn(0f, 1f)
            // Faster movement => tighter spacing so no visible stamp gaps.
            val adaptiveSpacing = max(minGap * 0.5f, baseSpacing * lerp(1f, 1f - spacingTightening, velocityFactor))

            // Wet/soft edges
            stampPaint.maskFilter = if (brush.profile.edge.softness > 0.01f) {
                BlurMaskFilter(dynamics.width * brush.profile.edge.softness, BlurMaskFilter.Blur.NORMAL)
            } else {
                null
            }

            var consumed = carry
            while (consumed + adaptiveSpacing <= segLen) {
                consumed += adaptiveSpacing
                val t = (consumed / segLen).coerceIn(0f, 1f)
                // LERP fills gaps between sparse touch points.
                val x = lerp(a.x, b.x, t)
                val y = lerp(a.y, b.y, t)
                val tangentX = b.x - a.x
                val tangentY = b.y - a.y
                val angleDeg = Math.toDegrees(kotlin.math.atan2(tangentY.toDouble(), tangentX.toDouble())).toFloat()

                val jitterRange = if (microDabEnabled) max(0.1f, dynamics.width * fluidJitterPercent) else 0f
                val jitterX = (random.nextFloat() * 2f - 1f) * jitterRange
                val jitterY = (random.nextFloat() * 2f - 1f) * jitterRange

                val alphaAlias = brush.profile.tip.fluidAccumulationAlpha.coerceIn(0.05f, 0.35f)
                val baseAlpha = (brush.opacity * max(brush.profile.tip.alphaSmoothing, alphaAlias))
                    .coerceIn(0.05f, 0.30f) * 255f
                alphaEma = if (alphaEma < 0f) baseAlpha else lerp(alphaEma, baseAlpha, 0.35f)

                val primaryX = x + jitterX
                val primaryY = y + jitterY

                drawStampAt(
                    canvas = canvas,
                    paint = stampPaint,
                    tipShader = tipShader,
                    grainShader = grainShader,
                    brush = brush,
                    x = primaryX,
                    y = primaryY,
                    width = dynamics.width,
                    alpha = alphaEma.toInt(),
                    angleDeg = angleDeg,
                    isPreview = isPreview,
                )

                drawStampAt(
                    canvas = canvas,
                    paint = stampPaint,
                    tipShader = tipShader,
                    grainShader = grainShader,
                    brush = brush,
                    x = primaryX + jitterX * 0.45f,
                    y = primaryY + jitterY * 0.45f,
                    width = dynamics.width,
                    alpha = (alphaEma * 0.58f).toInt(),
                    angleDeg = angleDeg,
                    isPreview = isPreview,
                )
            }
            carry = max(0f, consumed - segLen)
        }
    }

    private fun drawSingleStamp(canvas: Canvas, sample: MasterSample, brush: Brush, isPreview: Boolean) {
        val tipShader = BitmapShader(bristleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val grainShader = BitmapShader(paperBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = ComposeShader(tipShader, grainShader, PorterDuff.Mode.MULTIPLY)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            colorFilter = PorterDuffColorFilter(brush.color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }
        val dyn = dynamics(brush, sample.pressure, velocity = 0f)
        drawStampAt(canvas, paint, tipShader, grainShader, brush, sample.x, sample.y, dyn.width, dyn.alpha, 0f, isPreview)
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
        isPreview: Boolean,
    ) {
        // Keep each dab translucent so overlap builds up naturally.
        val accumulationStrength = brush.profile.tip.fluidAccumulationAlpha.coerceIn(0.05f, 0.35f)
        val accumulationAlpha = (alpha * accumulationStrength).toInt().coerceIn(8, 90)
        paint.alpha = accumulationAlpha

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
        if (brush.profile.tip.enableMicroDab && !isPreview) {
            val microJitter = brush.size * 0.05f
            val jx = (random.nextFloat() * 2f - 1f) * microJitter
            val jy = (random.nextFloat() * 2f - 1f) * microJitter
            val dst = android.graphics.RectF(x - r + jx, y - r + jy, x + r + jx, y + r + jy)
            canvas.drawBitmap(bristleBitmap, null, dst, paint)
        } else {
            canvas.drawCircle(x, y, r, paint)
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
        return catmullRomSplinePublic(points, stepsPerSegment)
    }

    // Public version for use by StrokeComputeThread during off-main-thread calculations
    fun catmullRomSplinePublic(points: List<MasterSample>, stepsPerSegment: Int = 8): List<MasterSample> {
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
        val center = size * 0.5f
        val radius = size * 0.5f

        // Soft alpha mask: dense center, transparent edge for stamp melt.
        val softGradient = android.graphics.RadialGradient(
            center,
            center,
            radius,
            intArrayOf(
                android.graphics.Color.argb(255, 255, 255, 255),
                android.graphics.Color.argb(180, 255, 255, 255),
                android.graphics.Color.argb(70, 255, 255, 255),
                android.graphics.Color.argb(0, 255, 255, 255),
            ),
            floatArrayOf(0f, 0.45f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = softGradient
        }
        c.drawCircle(center, center, radius, p)

        // Bristle breakup on top to avoid perfectly mathematical dabs.
        val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        repeat(96) { idx ->
            val angle = (idx * 137.5f) % 360f
            val rr = radius * (0.1f + random.nextFloat() * 0.8f)
            val x = center + (kotlin.math.cos(Math.toRadians(angle.toDouble())) * rr).toFloat()
            val y = center + (kotlin.math.sin(Math.toRadians(angle.toDouble())) * rr).toFloat()
            val dot = max(0.8f, size * (0.005f + random.nextFloat() * 0.012f))
            noisePaint.alpha = 30 + random.nextInt(70)
            c.drawCircle(x, y, dot, noisePaint)
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

    data class MasterSample(
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

