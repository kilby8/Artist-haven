package com.artisthaven.app.presentation.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathDashPathEffect
import android.graphics.PathEffect
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.artisthaven.app.domain.model.BlendBehavior
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.BrushProfile
import com.artisthaven.app.domain.model.BrushStyle
import com.artisthaven.app.domain.model.BrushType
import com.artisthaven.app.domain.model.StrokePoint
import com.artisthaven.app.domain.model.TextureTiling
import com.artisthaven.app.domain.model.TipShape
import androidx.compose.ui.graphics.toArgb
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**
 * Core stroke renderer for all advanced brush styles.
 *
 * Features:
 * - Grain/texture via BitmapShader
 * - Pressure + velocity dynamics with power-curve interpolator
 * - Stamp tip rendering with spacing and jitter
 * - Edge treatment (BlurMaskFilter + CornerPathEffect)
 * - Blend modes for watercolor/overlay effects
 * - Quad-bezier smoothing to reduce jagged robotic lines
 */
class BrushEngine(
    private val context: Context,
) {
    private val masterPaintBrush = MasterPaintBrush(context)
    private val random = Random(1977)

    private val grainBitmap: Bitmap by lazy { createNoiseBitmap(size = 96) }
    private val bitmapStamp: Bitmap by lazy { createBitmapStamp(size = 36) }

    fun renderStroke(
        canvas: Canvas,
        points: List<StrokePoint>,
        brush: Brush,
        isPreview: Boolean,
    ) {
        if (points.isEmpty()) return

        // Master stamp engine path for pro brushes. Eraser keeps direct clear behavior.
        if (brush.type != BrushType.ERASER) {
            masterPaintBrush.renderStroke(canvas, points, brush, isPreview)
            return
        }

        val profile = if (brush.profile.style == brush.style) {
            brush.profile
        } else {
            BrushProfile.preset(brush.style)
        }

        val smoothedPath = buildQuadBezierPath(points)
        val samples = interpolateStroke(points)

        when (brush.style) {
            BrushStyle.STANDARD -> drawDynamicStroke(canvas, samples, brush, profile)
            BrushStyle.TEXTURED_CHARCOAL -> drawTexturedStroke(canvas, smoothedPath, samples, brush, profile)
            BrushStyle.CALLIGRAPHY -> drawCalligraphyStroke(canvas, samples, brush, profile)
            BrushStyle.NEON_GLOW -> drawNeonStroke(canvas, smoothedPath, samples, brush, profile)
            BrushStyle.PATTERN_STAMP -> drawPatternStampStroke(canvas, smoothedPath, samples, brush, profile)
        }

        if (profile.tip.useStamp && brush.style != BrushStyle.PATTERN_STAMP) {
            drawStampTrail(canvas, smoothedPath, brush, profile)
        }
    }

    private fun drawDynamicStroke(
        canvas: Canvas,
        samples: List<StrokePoint>,
        brush: Brush,
        profile: BrushProfile,
    ) {
        if (samples.isEmpty()) return

        val paint = basePaint(brush, profile)
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]
            val dynamics = dynamicsFor(brush, profile, prev, curr)
            paint.strokeWidth = dynamics.width
            paint.alpha = dynamics.alpha
            canvas.drawLine(prev.x, prev.y, curr.x, curr.y, paint)
        }
    }

    private fun drawCalligraphyStroke(
        canvas: Canvas,
        samples: List<StrokePoint>,
        brush: Brush,
        profile: BrushProfile,
    ) {
        if (samples.isEmpty()) return

        val paint = basePaint(brush, profile).apply {
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.ROUND
            pathEffect = CornerPathEffect(max(8f, profile.edge.cornerSmoothingPx))
        }

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]
            val dynamics = dynamicsFor(brush, profile, prev, curr)
            paint.strokeWidth = dynamics.width
            paint.alpha = dynamics.alpha
            canvas.drawLine(prev.x, prev.y, curr.x, curr.y, paint)
        }
    }

    private fun drawTexturedStroke(
        canvas: Canvas,
        path: Path,
        samples: List<StrokePoint>,
        brush: Brush,
        profile: BrushProfile,
    ) {
        val dynamics = averageDynamics(brush, profile, samples)
        val paint = basePaint(brush, profile).apply {
            strokeWidth = dynamics.width
            alpha = dynamics.alpha
        }

        if (profile.grain.enabled) {
            val tile = when (profile.grain.tiling) {
                TextureTiling.REPEAT -> Shader.TileMode.REPEAT
                TextureTiling.MIRROR -> Shader.TileMode.MIRROR
                TextureTiling.CLAMP -> Shader.TileMode.CLAMP
            }
            val shader = BitmapShader(grainBitmap, tile, tile)
            val matrix = android.graphics.Matrix().apply {
                val scale = profile.grain.scale.coerceAtLeast(0.1f)
                setScale(scale, scale)
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            paint.colorFilter = PorterDuffColorFilter(brush.color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }

        canvas.drawPath(path, paint)
    }

    private fun drawNeonStroke(
        canvas: Canvas,
        path: Path,
        samples: List<StrokePoint>,
        brush: Brush,
        profile: BrushProfile,
    ) {
        val dynamics = averageDynamics(brush, profile, samples)

        val glowPaint = basePaint(brush, profile).apply {
            strokeWidth = dynamics.width * 1.8f
            alpha = (dynamics.alpha * 0.5f).toInt().coerceIn(20, 200)
            maskFilter = BlurMaskFilter(dynamics.width * 0.8f, BlurMaskFilter.Blur.NORMAL)
        }

        val corePaint = basePaint(brush, profile).apply {
            strokeWidth = dynamics.width
            alpha = dynamics.alpha
            maskFilter = null
        }

        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, corePaint)
    }

    private fun drawPatternStampStroke(
        canvas: Canvas,
        path: Path,
        samples: List<StrokePoint>,
        brush: Brush,
        profile: BrushProfile,
    ) {
        val dynamics = averageDynamics(brush, profile, samples)
        val spacingPx = max(1f, brush.size * profile.tip.spacing)

        // PathDashPathEffect gives us repeated custom path stamps along the trajectory.
        val shape = stampPath(shape = profile.tip.shape, size = brush.size * profile.tip.stampScale)
        val paint = basePaint(brush, profile).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(0.5f, brush.size * 0.25f)
            alpha = dynamics.alpha
            pathEffect = PathDashPathEffect(shape, spacingPx, 0f, PathDashPathEffect.Style.ROTATE)
        }

        canvas.drawPath(path, paint)

        // Add jittered bitmap stamps for a more organic pro-brush feel.
        drawStampTrail(canvas, path, brush, profile)
    }

    private fun drawStampTrail(
        canvas: Canvas,
        path: Path,
        brush: Brush,
        profile: BrushProfile,
    ) {
        val spacingPx = max(1f, brush.size * profile.tip.spacing)
        val jitterPx = brush.size * profile.tip.jitter
        val measure = PathMeasure(path, false)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val paint = basePaint(brush, profile).apply {
            style = Paint.Style.FILL
            strokeWidth = brush.size
            alpha = (brush.opacity * 255).toInt().coerceIn(0, 255)
            pathEffect = null
        }

        var d = 0f
        while (d <= measure.length) {
            measure.getPosTan(d, pos, tan)
            val jitterX = (random.nextFloat() * 2f - 1f) * jitterPx
            val jitterY = (random.nextFloat() * 2f - 1f) * jitterPx
            val x = pos[0] + jitterX
            val y = pos[1] + jitterY

            if (profile.tip.useBitmapStamp) {
                val stampSize = max(4f, brush.size * profile.tip.stampScale)
                val half = stampSize / 2f
                val dst = android.graphics.RectF(x - half, y - half, x + half, y + half)
                canvas.drawBitmap(bitmapStamp, null, dst, paint)
            } else {
                drawShapeStamp(canvas, x, y, brush.size * profile.tip.stampScale, profile.tip.shape, paint)
            }

            d += spacingPx
        }
    }

    private fun drawShapeStamp(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        shape: TipShape,
        paint: Paint,
    ) {
        when (shape) {
            TipShape.CIRCLE -> canvas.drawCircle(x, y, size * 0.45f, paint)
            TipShape.SQUARE -> canvas.drawRect(x - size * 0.4f, y - size * 0.4f, x + size * 0.4f, y + size * 0.4f, paint)
            TipShape.DIAMOND -> {
                val p = Path()
                val h = size * 0.45f
                p.moveTo(x, y - h)
                p.lineTo(x + h, y)
                p.lineTo(x, y + h)
                p.lineTo(x - h, y)
                p.close()
                canvas.drawPath(p, paint)
            }
            TipShape.SLASH -> {
                val h = size * 0.45f
                val oldWidth = paint.strokeWidth
                paint.strokeWidth = max(1f, size * 0.18f)
                canvas.drawLine(x - h, y + h, x + h, y - h, paint)
                paint.strokeWidth = oldWidth
            }
        }
    }

    private fun averageDynamics(
        brush: Brush,
        profile: BrushProfile,
        samples: List<StrokePoint>,
    ): DynamicsResult {
        if (samples.size < 2) {
            val alpha = (brush.opacity * 255).toInt().coerceIn(0, 255)
            return DynamicsResult(width = brush.size, alpha = alpha)
        }

        var widthAccum = 0f
        var alphaAccum = 0f
        var count = 0
        for (i in 1 until samples.size) {
            val d = dynamicsFor(brush, profile, samples[i - 1], samples[i])
            widthAccum += d.width
            alphaAccum += d.alpha
            count++
        }
        return DynamicsResult(
            width = widthAccum / max(1, count),
            alpha = (alphaAccum / max(1, count)).toInt(),
        )
    }

    private fun dynamicsFor(
        brush: Brush,
        profile: BrushProfile,
        prev: StrokePoint,
        curr: StrokePoint,
    ): DynamicsResult {
        val cfg = profile.dynamics
        val pressure = curr.pressure.coerceIn(0f, 1f)
        val pressureCurve = pressure.pow(cfg.powerCurveExponent)

        val dt = max(1f, (curr.timestamp - prev.timestamp).toFloat())
        val distance = hypot(curr.x - prev.x, curr.y - prev.y)
        val velocity = distance / dt
        val velocityNorm = (velocity / cfg.velocityNormalization).coerceIn(0f, 1f)
        val velocityCurve = (1f - velocityNorm).pow(cfg.powerCurveExponent)

        val widthT = (cfg.pressureToWidth * pressureCurve + cfg.velocityToWidth * velocityCurve)
            .coerceIn(0f, 1f)
        val alphaT = (cfg.pressureToAlpha * pressureCurve + cfg.velocityToAlpha * velocityCurve)
            .coerceIn(0f, 1f)

        val widthMul = lerp(cfg.minWidthMultiplier, cfg.maxWidthMultiplier, widthT)
        val alphaMul = lerp(cfg.minAlphaMultiplier, cfg.maxAlphaMultiplier, alphaT)

        return DynamicsResult(
            width = max(0.5f, brush.size * widthMul),
            alpha = (brush.opacity * alphaMul * 255f).toInt().coerceIn(0, 255),
        )
    }

    private fun basePaint(brush: Brush, profile: BrushProfile): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brush.color.toArgb()
            alpha = (brush.opacity * 255).toInt().coerceIn(0, 255)
            strokeWidth = brush.size
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (profile.edge.softness > 0f) {
            paint.maskFilter = BlurMaskFilter(brush.size * profile.edge.softness, BlurMaskFilter.Blur.NORMAL)
        }

        val edgeEffect = mutableListOf<PathEffect>()
        if (profile.edge.cornerSmoothingPx > 0f) {
            edgeEffect += CornerPathEffect(profile.edge.cornerSmoothingPx)
        }
        paint.pathEffect = if (edgeEffect.isNotEmpty()) edgeEffect.first() else null

        if (brush.type == BrushType.ERASER) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            return paint
        }

        when (profile.blend) {
            BlendBehavior.NORMAL -> Unit
            BlendBehavior.MULTIPLY -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            BlendBehavior.DARKEN -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            BlendBehavior.SRC_ATOP -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            BlendBehavior.CLEAR -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        return paint
    }

    private fun buildQuadBezierPath(points: List<StrokePoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path

        path.moveTo(points.first().x, points.first().y)
        if (points.size == 1) return path

        var prev = points.first()
        for (i in 1 until points.size) {
            val curr = points[i]
            val midX = (prev.x + curr.x) * 0.5f
            val midY = (prev.y + curr.y) * 0.5f
            path.quadTo(prev.x, prev.y, midX, midY)
            prev = curr
        }
        path.lineTo(points.last().x, points.last().y)
        return path
    }

    private fun interpolateStroke(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 2) return points
        val out = ArrayList<StrokePoint>(points.size * 3)
        out += points.first()

        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val distance = hypot(b.x - a.x, b.y - a.y)
            val steps = max(1, (distance / 6f).toInt())

            for (s in 1..steps) {
                val t = s.toFloat() / steps
                out += StrokePoint(
                    x = lerp(a.x, b.x, t),
                    y = lerp(a.y, b.y, t),
                    pressure = lerp(a.pressure, b.pressure, t),
                    tiltX = lerp(a.tiltX, b.tiltX, t),
                    tiltY = lerp(a.tiltY, b.tiltY, t),
                    timestamp = lerp(a.timestamp.toFloat(), b.timestamp.toFloat(), t).toLong(),
                )
            }
        }
        return out
    }

    private fun stampPath(shape: TipShape, size: Float): Path {
        val s = max(3f, size)
        val h = s * 0.5f
        return Path().apply {
            when (shape) {
                TipShape.CIRCLE -> addCircle(0f, 0f, h * 0.55f, Path.Direction.CW)
                TipShape.SQUARE -> addRect(-h * 0.5f, -h * 0.5f, h * 0.5f, h * 0.5f, Path.Direction.CW)
                TipShape.DIAMOND -> {
                    moveTo(0f, -h * 0.6f)
                    lineTo(h * 0.6f, 0f)
                    lineTo(0f, h * 0.6f)
                    lineTo(-h * 0.6f, 0f)
                    close()
                }
                TipShape.SLASH -> {
                    moveTo(-h * 0.6f, h * 0.6f)
                    lineTo(-h * 0.15f, h * 0.6f)
                    lineTo(h * 0.6f, -h * 0.6f)
                    lineTo(h * 0.15f, -h * 0.6f)
                    close()
                }
            }
        }
    }

    private fun createNoiseBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val g = 120 + random.nextInt(136)
                val argb = android.graphics.Color.argb(255, g, g, g)
                bmp.setPixel(x, y, argb)
            }
        }
        return bmp
    }

    private fun createBitmapStamp(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }

        val path = Path().apply {
            moveTo(size * 0.5f, size * 0.08f)
            lineTo(size * 0.88f, size * 0.5f)
            lineTo(size * 0.5f, size * 0.92f)
            lineTo(size * 0.12f, size * 0.5f)
            close()
        }
        c.drawPath(path, p)
        return bmp
    }

    private data class DynamicsResult(
        val width: Float,
        val alpha: Int,
    )

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}

