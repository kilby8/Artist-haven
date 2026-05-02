package com.artisthaven.app.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.artisthaven.app.domain.model.BlendBehavior
import com.artisthaven.app.domain.model.Brush as DrawingBrush
import com.artisthaven.app.domain.model.BrushStyle
import com.artisthaven.app.domain.model.DynamicsSettings
import com.artisthaven.app.domain.model.TipShape
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**
 * Point sample used for DrawScope stroke rendering with dynamics.
 */
data class BrushSample(
    val position: Offset,
    val pressure: Float = 1f,
    val timestampMs: Long = 0L,
)

/**
 * Compatibility overload: if only offsets are available, synthesize pressure/time.
 */
fun DrawScope.drawProBrushStroke(points: List<Offset>, brush: DrawingBrush) {
    if (points.isEmpty()) return
    val start = System.currentTimeMillis()
    val samples = points.mapIndexed { index, p ->
        BrushSample(position = p, pressure = 1f, timestampMs = start + index * 16L)
    }
    drawProBrushStrokeSamples(samples, brush)
}

/**
 * Core DrawScope brush logic for real-time rendering.
 *
 * Includes:
 * - Quad-bezier smoothing
 * - Pressure/velocity power-curve dynamics
 * - Style-specific rendering (textured, calligraphy, neon, pattern)
 * - Stamp spacing/jitter trail
 * - Blend-mode mapping from brush profile
 */
private fun DrawScope.drawProBrushStrokeSamples(samples: List<BrushSample>, brush: DrawingBrush) {
    if (samples.isEmpty()) return

    val blend = toComposeBlend(brush.profile.blend)
    val smoothedPath = buildQuadBezierPath(samples)
    val expanded = interpolateSamples(samples)

    when (brush.style) {
        BrushStyle.STANDARD -> drawDynamicSegments(expanded, brush, blend, StrokeCap.Round)
        BrushStyle.CALLIGRAPHY -> drawDynamicSegments(expanded, brush, blend, StrokeCap.Butt)
        BrushStyle.NEON_GLOW -> {
            // Soft outer pass approximates glow in pure DrawScope.
            drawPath(
                path = smoothedPath,
                color = brush.color.copy(alpha = (brush.opacity * 0.32f).coerceIn(0f, 1f)),
                style = Stroke(
                    width = brush.size * 2.1f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.cornerPathEffect(max(2f, brush.profile.edge.cornerSmoothingPx)),
                ),
                blendMode = blend,
            )
            drawDynamicSegments(expanded, brush, blend, StrokeCap.Round)
        }

        BrushStyle.TEXTURED_CHARCOAL -> {
            // Compose fallback for BitmapShader: layered gradient + grain stamp speckle.
            val tint = brush.color.copy(alpha = brush.opacity)
            drawPath(
                path = smoothedPath,
                brush = Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = tint.alpha * 0.6f),
                        tint,
                        tint.copy(alpha = tint.alpha * 0.8f),
                    )
                ),
                style = Stroke(
                    width = brush.size,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.cornerPathEffect(max(2f, brush.profile.edge.cornerSmoothingPx)),
                ),
                alpha = brush.opacity,
                blendMode = blend,
            )
            drawGrainSpeckle(expanded, brush, blend)
        }

        BrushStyle.PATTERN_STAMP -> {
            drawPath(
                path = smoothedPath,
                color = brush.color,
                style = Stroke(
                    width = (brush.size * 0.45f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.cornerPathEffect(max(2f, brush.profile.edge.cornerSmoothingPx)),
                ),
                alpha = (brush.opacity * 0.7f).coerceIn(0f, 1f),
                blendMode = blend,
            )
            drawStampTrail(expanded, brush, blend)
        }
    }
}

private fun DrawScope.drawDynamicSegments(
    samples: List<BrushSample>,
    brush: DrawingBrush,
    blend: BlendMode,
    cap: StrokeCap,
) {
    if (samples.size < 2) return

    for (i in 1 until samples.size) {
        val prev = samples[i - 1]
        val curr = samples[i]
        val dyn = dynamicsFor(brush.profile.dynamics, brush.size, brush.opacity, prev, curr)

        // Soft edge treatment approximation (pre-pass) for brushes that need feathering.
        if (brush.profile.edge.softness > 0.01f) {
            drawLine(
                color = brush.color.copy(alpha = dyn.alpha * 0.38f),
                start = prev.position,
                end = curr.position,
                strokeWidth = dyn.width * (1f + brush.profile.edge.softness * 1.6f),
                cap = cap,
                blendMode = blend,
            )
        }

        drawLine(
            color = brush.color.copy(alpha = dyn.alpha),
            start = prev.position,
            end = curr.position,
            strokeWidth = dyn.width,
            cap = cap,
            blendMode = blend,
        )
    }
}

private fun DrawScope.drawGrainSpeckle(
    samples: List<BrushSample>,
    brush: DrawingBrush,
    blend: BlendMode,
) {
    if (!brush.profile.grain.enabled || samples.isEmpty()) return
    val random = Random(brush.hashCode())
    val every = max(1, (8f / brush.profile.grain.scale.coerceAtLeast(0.2f)).toInt())

    for (i in samples.indices step every) {
        val s = samples[i]
        val radius = (brush.size * 0.06f * brush.profile.grain.scale).coerceAtLeast(0.4f)
        val jitter = brush.size * 0.16f
        val jx = (random.nextFloat() * 2f - 1f) * jitter
        val jy = (random.nextFloat() * 2f - 1f) * jitter
        val alpha = (brush.opacity * brush.profile.grain.strength * 0.35f).coerceIn(0.03f, 0.35f)
        drawCircle(
            color = brush.color.copy(alpha = alpha),
            radius = radius,
            center = Offset(s.position.x + jx, s.position.y + jy),
            blendMode = blend,
        )
    }
}

private fun DrawScope.drawStampTrail(
    samples: List<BrushSample>,
    brush: DrawingBrush,
    blend: BlendMode,
) {
    if (samples.size < 2) return

    val random = Random(brush.hashCode() xor 0x4B1D)
    var traveled = 0f
    val spacing = max(1f, brush.size * brush.profile.tip.spacing)
    val jitter = brush.size * brush.profile.tip.jitter

    for (i in 1 until samples.size) {
        val a = samples[i - 1].position
        val b = samples[i].position
        val d = hypot(b.x - a.x, b.y - a.y)
        traveled += d
        if (traveled < spacing) continue
        traveled = 0f

        val cx = b.x + (random.nextFloat() * 2f - 1f) * jitter
        val cy = b.y + (random.nextFloat() * 2f - 1f) * jitter
        val center = Offset(cx, cy)
        val alpha = (brush.opacity * 0.8f).coerceIn(0f, 1f)
        val size = brush.size * brush.profile.tip.stampScale

        when (brush.profile.tip.shape) {
            TipShape.CIRCLE -> drawCircle(
                color = brush.color.copy(alpha = alpha),
                radius = size * 0.23f,
                center = center,
                blendMode = blend,
            )
            TipShape.SQUARE -> drawRect(
                color = brush.color.copy(alpha = alpha),
                topLeft = Offset(center.x - size * 0.22f, center.y - size * 0.22f),
                size = androidx.compose.ui.geometry.Size(size * 0.44f, size * 0.44f),
                blendMode = blend,
            )
            TipShape.DIAMOND -> {
                val h = size * 0.24f
                val p = Path().apply {
                    moveTo(center.x, center.y - h)
                    lineTo(center.x + h, center.y)
                    lineTo(center.x, center.y + h)
                    lineTo(center.x - h, center.y)
                    close()
                }
                drawPath(path = p, color = brush.color.copy(alpha = alpha), blendMode = blend)
            }
            TipShape.SLASH -> {
                drawLine(
                    color = brush.color.copy(alpha = alpha),
                    start = Offset(center.x - size * 0.25f, center.y + size * 0.2f),
                    end = Offset(center.x + size * 0.25f, center.y - size * 0.2f),
                    strokeWidth = (size * 0.12f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                    blendMode = blend,
                )
            }
        }
    }
}

private fun buildQuadBezierPath(samples: List<BrushSample>): Path {
    val path = Path()
    if (samples.isEmpty()) return path
    path.moveTo(samples.first().position.x, samples.first().position.y)
    if (samples.size == 1) return path

    for (i in 1 until samples.size) {
        val prev = samples[i - 1].position
        val curr = samples[i].position
        val mid = Offset((prev.x + curr.x) * 0.5f, (prev.y + curr.y) * 0.5f)
        path.quadraticBezierTo(prev.x, prev.y, mid.x, mid.y)
    }
    path.lineTo(samples.last().position.x, samples.last().position.y)
    return path
}

private fun interpolateSamples(samples: List<BrushSample>): List<BrushSample> {
    if (samples.size < 2) return samples
    val out = ArrayList<BrushSample>(samples.size * 3)
    out += samples.first()

    for (i in 1 until samples.size) {
        val a = samples[i - 1]
        val b = samples[i]
        val d = hypot(b.position.x - a.position.x, b.position.y - a.position.y)
        val steps = max(1, (d / 6f).toInt())
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            out += BrushSample(
                position = Offset(
                    x = lerp(a.position.x, b.position.x, t),
                    y = lerp(a.position.y, b.position.y, t),
                ),
                pressure = lerp(a.pressure, b.pressure, t),
                timestampMs = lerp(a.timestampMs.toFloat(), b.timestampMs.toFloat(), t).toLong(),
            )
        }
    }
    return out
}

private data class DynamicsResult(val width: Float, val alpha: Float)

private fun dynamicsFor(
    cfg: DynamicsSettings,
    baseSize: Float,
    baseOpacity: Float,
    prev: BrushSample,
    curr: BrushSample,
): DynamicsResult {
    val pressure = curr.pressure.coerceIn(0f, 1f)
    val pressureCurve = pressure.pow(cfg.powerCurveExponent)

    val dt = max(1f, (curr.timestampMs - prev.timestampMs).toFloat())
    val velocity = hypot(curr.position.x - prev.position.x, curr.position.y - prev.position.y) / dt
    val velocityNorm = (velocity / cfg.velocityNormalization).coerceIn(0f, 1f)
    val velocityCurve = (1f - velocityNorm).pow(cfg.powerCurveExponent)

    val widthT = (cfg.pressureToWidth * pressureCurve + cfg.velocityToWidth * velocityCurve).coerceIn(0f, 1f)
    val alphaT = (cfg.pressureToAlpha * pressureCurve + cfg.velocityToAlpha * velocityCurve).coerceIn(0f, 1f)

    val widthMul = lerp(cfg.minWidthMultiplier, cfg.maxWidthMultiplier, widthT)
    val alphaMul = lerp(cfg.minAlphaMultiplier, cfg.maxAlphaMultiplier, alphaT)

    return DynamicsResult(
        width = (baseSize * widthMul).coerceAtLeast(0.5f),
        alpha = (baseOpacity * alphaMul).coerceIn(0f, 1f),
    )
}

private fun toComposeBlend(mode: BlendBehavior): BlendMode = when (mode) {
    BlendBehavior.MULTIPLY -> BlendMode.Multiply
    BlendBehavior.DARKEN -> BlendMode.Darken
    BlendBehavior.SRC_ATOP -> BlendMode.SrcAtop
    BlendBehavior.CLEAR -> BlendMode.Clear
    BlendBehavior.NORMAL -> BlendMode.SrcOver
}

private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t

