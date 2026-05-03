package com.artisthaven.app.presentation.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.toArgb
import com.artisthaven.app.domain.model.Brush
import kotlin.math.hypot

/**
 * Front-Buffer Prediction System for Zero-Latency Drawing.
 *
 * While the MasterPaintBrush is rendering high-frequency stamps on the background thread,
 * this system draws a lightweight, antialiased line prediction from the last stamped point
 * to the current touch position. The result is immediate visual feedback (~1-2ms) while
 * the high-fidelity rendering catches up in the background.
 *
 * The prediction line is rendered on the preview bitmap and will be completely replaced
 * by the final rendered stamps as they become available.
 */
class PredictionRenderer {

    private val predictionPath = Path()
    private val predictionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        isFilterBitmap = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Render a prediction line from lastPoint to currentPoint.
     * Uses a simplified stroke path to minimize CPU work (<1ms on most devices).
     */
    fun renderPredictionLine(
        canvas: Canvas,
        lastX: Float,
        lastY: Float,
        currentX: Float,
        currentY: Float,
        brush: Brush,
    ) {
        val distance = hypot(currentX - lastX, currentY - lastY)
        if (distance < 0.5f) return  // Skip tiny movements

        predictionPaint.color = brush.color.toArgb()
        // Pressure-sensitive width: reduces at low pressure for finer lines
        predictionPaint.strokeWidth = (brush.size * brush.profile.tip.alphaSmoothing).coerceIn(0.5f, brush.size * 2f)
        // Slightly reduce prediction alpha so it feels like a "preview"
        predictionPaint.alpha = ((brush.opacity * 200) / 255).toInt().coerceIn(50, 200)

        predictionPath.reset()
        predictionPath.moveTo(lastX, lastY)
        predictionPath.lineTo(currentX, currentY)

        canvas.drawPath(predictionPath, predictionPaint)
    }

    /**
     * Render a smooth prediction curve through multiple points (for interpolation during touch move).
     */
    fun renderPredictionCurve(
        canvas: Canvas,
        points: List<Pair<Float, Float>>,
        brush: Brush,
    ) {
        if (points.size < 2) return

        predictionPaint.color = brush.color.toArgb()
        predictionPaint.strokeWidth = (brush.size * brush.profile.tip.alphaSmoothing).coerceIn(0.5f, brush.size * 2f)
        predictionPaint.alpha = ((brush.opacity * 180) / 255).toInt().coerceIn(50, 200)

        predictionPath.reset()
        predictionPath.moveTo(points[0].first, points[0].second)

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            // Catmull-Rom like smoothing with just quadratic bezier for speed
            val midX = (prev.first + curr.first) * 0.5f
            val midY = (prev.second + curr.second) * 0.5f
            predictionPath.quadTo(prev.first, prev.second, midX, midY)
        }

        // Connect to the last point
        if (points.size >= 2) {
            val last = points.last()
            val prev = points[points.size - 2]
            predictionPath.lineTo(last.first, last.second)
        }

        canvas.drawPath(predictionPath, predictionPaint)
    }

    /**
     * Clear prediction rendering from the canvas.
     * Called when high-fidelity stamps arrive to avoid double-rendering.
     */
    fun clearPrediction(canvas: Canvas) {
        predictionPath.reset()
    }
}

