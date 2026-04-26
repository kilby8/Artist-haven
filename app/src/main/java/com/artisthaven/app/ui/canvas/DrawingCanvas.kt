package com.artisthaven.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.artisthaven.app.model.DrawingState
import com.artisthaven.app.model.DrawingTool

/**
 * Represents a single recorded stroke on the canvas.
 */
data class Stroke(
    val points: List<Offset>,
    val color: Color,
    val size: Float,
    val opacity: Float,
    val tool: DrawingTool
)

/**
 * Full-screen drawing canvas with:
 *  - Two-finger pinch-to-zoom and rotation via [detectTransformGestures]
 *  - Stroke capture via [PointerEventPass.Initial] so UI never blocks stylus input
 *
 * The canvas is intentionally rendered independently of the UI overlay layer.
 * Pointer events for drawing use [PointerEventPass.Initial] to guarantee
 * priority over any overlaid UI elements.
 */
@Composable
fun DrawingCanvas(
    drawingState: DrawingState,
    modifier: Modifier = Modifier,
    onStrokeAdded: () -> Unit = {}
) {
    // ── Transform state (pinch-zoom + rotate) ────────────────────────────────
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // ── Stroke history ───────────────────────────────────────────────────────
    val committedStrokes = remember { mutableListOf<Stroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawing by remember { mutableStateOf(false) }

    // ── Canvas background (white drawing surface) ────────────────────────────
    val canvasBackground = Color.White

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Capture two-finger transform gestures (zoom / rotate / pan)
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, rotationDelta ->
                    scale = (scale * zoom).coerceIn(0.1f, 10f)
                    rotation += rotationDelta
                    offset += pan
                }
            }
            // Capture stylus / finger drawing strokes using PointerEventPass.Initial
            // so this handler always gets priority before any UI overlay consumes events
            .pointerInput(drawingState) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val pointers = event.changes.filter { it.pressed }
                        // Single-pointer contact = drawing gesture
                        if (pointers.size == 1) {
                            val pointer = pointers.first()
                            val position = pointer.position
                            when {
                                !isDrawing -> {
                                    isDrawing = true
                                    currentStrokePoints = listOf(position)
                                }
                                pointer.pressed -> {
                                    currentStrokePoints = currentStrokePoints + position
                                }
                                else -> {
                                    if (currentStrokePoints.isNotEmpty()) {
                                        committedStrokes.add(
                                            Stroke(
                                                points = currentStrokePoints,
                                                color = drawingState.currentColor,
                                                size = drawingState.brushSize * 60f + 4f,
                                                opacity = drawingState.opacity,
                                                tool = drawingState.currentTool
                                            )
                                        )
                                        onStrokeAdded()
                                    }
                                    currentStrokePoints = emptyList()
                                    isDrawing = false
                                }
                            }
                            pointer.consume()
                        } else if (pointers.isEmpty() && isDrawing) {
                            if (currentStrokePoints.isNotEmpty()) {
                                committedStrokes.add(
                                    Stroke(
                                        points = currentStrokePoints,
                                        color = drawingState.currentColor,
                                        size = drawingState.brushSize * 60f + 4f,
                                        opacity = drawingState.opacity,
                                        tool = drawingState.currentTool
                                    )
                                )
                                onStrokeAdded()
                            }
                            currentStrokePoints = emptyList()
                            isDrawing = false
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        // White drawing surface
        drawRect(color = canvasBackground)

        // Render all committed strokes
        for (stroke in committedStrokes) {
            drawStroke(stroke)
        }

        // Render the current in-progress stroke
        if (currentStrokePoints.size >= 2) {
            drawStroke(
                Stroke(
                    points = currentStrokePoints,
                    color = drawingState.currentColor,
                    size = drawingState.brushSize * 60f + 4f,
                    opacity = drawingState.opacity,
                    tool = drawingState.currentTool
                )
            )
        }
    }
}

/** Draws a [Stroke] onto the [DrawScope] using a smooth Bézier path. */
private fun DrawScope.drawStroke(stroke: Stroke) {
    if (stroke.points.size < 2) return

    val path = Path()
    path.moveTo(stroke.points.first().x, stroke.points.first().y)

    // Smooth quadratic Bézier through all recorded points
    for (i in 1 until stroke.points.lastIndex) {
        val mid = Offset(
            (stroke.points[i].x + stroke.points[i + 1].x) / 2f,
            (stroke.points[i].y + stroke.points[i + 1].y) / 2f
        )
        path.quadraticTo(
            stroke.points[i].x, stroke.points[i].y,
            mid.x, mid.y
        )
    }
    path.lineTo(stroke.points.last().x, stroke.points.last().y)

    val paintColor = when (stroke.tool) {
        DrawingTool.ERASE -> Color.White
        else -> stroke.color.copy(alpha = stroke.opacity)
    }

    drawPath(
        path = path,
        color = paintColor,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke.size,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}
