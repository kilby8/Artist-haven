package com.artisthaven.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.artisthaven.app.ui.theme.AccentBlue
import com.artisthaven.app.ui.theme.BorderGray
import com.artisthaven.app.ui.theme.GlassBorder

/**
 * A custom vertical slider that:
 * - Occupies minimal horizontal space (mimics Procreate left-hand controls).
 * - Draws its own track and thumb via [Canvas] (no intrinsic horizontal footprint).
 * - Reports values in [0, 1] through [onValueChange].
 *
 * @param value       Current slider value in [0, 1].
 * @param onValueChange Callback invoked on drag.
 * @param trackColor  Gradient end-color for the filled portion of the track.
 * @param height      Total height of the slider widget.
 * @param width       Width of the slider widget.
 */
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = AccentBlue,
    height: Dp = 160.dp,
    width: Dp = 28.dp
) {
    var dragValue by remember(value) { mutableFloatStateOf(value) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(width)
            .height(height)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newValue = (dragValue - dragAmount.y / size.height)
                        .coerceIn(0f, 1f)
                    dragValue = newValue
                    onValueChange(newValue)
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val trackWidth = 8.dp.toPx()
            val thumbRadius = 10.dp.toPx()
            val trackLeft = (size.width - trackWidth) / 2f
            val trackRight = trackLeft + trackWidth
            val trackTop = thumbRadius
            val trackBottom = size.height - thumbRadius
            val trackHeight = trackBottom - trackTop

            // ── Background track ─────────────────────────────────────────────
            drawRoundRect(
                color = BorderGray,
                topLeft = Offset(trackLeft, trackTop),
                size = Size(trackWidth, trackHeight),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )

            // ── Filled portion (bottom of track up to thumb position) ────────
            val fillStart = trackTop + trackHeight * (1f - dragValue)
            val fillHeight = trackHeight * dragValue
            if (fillHeight > 0f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(trackColor, trackColor.copy(alpha = 0.4f)),
                        startY = fillStart,
                        endY = trackBottom
                    ),
                    topLeft = Offset(trackLeft, fillStart),
                    size = Size(trackWidth, fillHeight),
                    cornerRadius = CornerRadius(trackWidth / 2f)
                )
            }

            // ── Thumb ────────────────────────────────────────────────────────
            val thumbY = trackTop + trackHeight * (1f - dragValue)
            // Outer ring (glass border)
            drawCircle(
                color = GlassBorder,
                radius = thumbRadius + 2.dp.toPx(),
                center = Offset(size.width / 2f, thumbY),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Thumb fill
            drawCircle(
                color = trackColor,
                radius = thumbRadius,
                center = Offset(size.width / 2f, thumbY)
            )
            // Thumb inner shine
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = thumbRadius * 0.4f,
                center = Offset(size.width / 2f - thumbRadius * 0.15f, thumbY - thumbRadius * 0.3f)
            )
        }
    }
}
