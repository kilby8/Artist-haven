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
import com.artisthaven.app.domain.model.Brush as DrawingBrush
import com.artisthaven.app.domain.model.BrushStyle
import kotlin.math.hypot
import kotlin.math.max

/**
 * Compose DrawScope implementation for the pro brush engine profile.
 * Useful when rendering in Canvas { } composables.
 */
fun DrawScope.drawProBrushStroke(
    points: List<Offset>,
    brush: DrawingBrush,
) {
    if (points.isEmpty()) return

    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val mid = Offset((prev.x + curr.x) * 0.5f, (prev.y + curr.y) * 0.5f)
            quadraticBezierTo(prev.x, prev.y, mid.x, mid.y)
        }
    }

    when (brush.style) {
        BrushStyle.STANDARD,
        BrushStyle.CALLIGRAPHY,
        BrushStyle.TEXTURED_CHARCOAL,
        BrushStyle.NEON_GLOW,
        BrushStyle.PATTERN_STAMP,
        -> {
            val width = brush.size
            val blend = when (brush.profile.blend) {
                com.artisthaven.app.domain.model.BlendBehavior.MULTIPLY -> BlendMode.Multiply
                com.artisthaven.app.domain.model.BlendBehavior.DARKEN -> BlendMode.Darken
                com.artisthaven.app.domain.model.BlendBehavior.SRC_ATOP -> BlendMode.SrcAtop
                com.artisthaven.app.domain.model.BlendBehavior.CLEAR -> BlendMode.Clear
                else -> BlendMode.SrcOver
            }

            // Neon draws a soft outer pass first.
            if (brush.style == BrushStyle.NEON_GLOW) {
                drawPath(
                    path = path,
                    color = brush.color.copy(alpha = (brush.opacity * 0.35f).coerceIn(0f, 1f)),
                    style = Stroke(
                        width = width * 1.9f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                    blendMode = blend,
                )
            }

            // Textured charcoal uses a simple gradient fallback in DrawScope.
            if (brush.style == BrushStyle.TEXTURED_CHARCOAL) {
                val tint = brush.color.copy(alpha = brush.opacity)
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        listOf(
                            tint.copy(alpha = tint.alpha * 0.75f),
                            tint,
                            tint.copy(alpha = tint.alpha * 0.65f),
                        )
                    ),
                    style = Stroke(
                        width = width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.cornerPathEffect(max(2f, brush.profile.edge.cornerSmoothingPx)),
                    ),
                    alpha = brush.opacity,
                    blendMode = blend,
                )
                return
            }

            drawPath(
                path = path,
                color = brush.color,
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.cornerPathEffect(max(2f, brush.profile.edge.cornerSmoothingPx)),
                ),
                alpha = brush.opacity,
                blendMode = blend,
            )

            // Pattern brush draws repeated stamped circles along trajectory.
            if (brush.style == BrushStyle.PATTERN_STAMP) {
                var traveled = 0f
                val spacing = max(1f, brush.size * brush.profile.tip.spacing)
                for (i in 1 until points.size) {
                    val a = points[i - 1]
                    val b = points[i]
                    val d = hypot(b.x - a.x, b.y - a.y)
                    traveled += d
                    if (traveled >= spacing) {
                        traveled = 0f
                        drawCircle(
                            color = Color.White.copy(alpha = brush.opacity * 0.25f),
                            radius = brush.size * 0.28f,
                            center = b,
                            blendMode = blend,
                        )
                    }
                }
            }
        }
    }
}

