package com.artisthaven.app.presentation.canvas

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.StrokePoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Dedicated background thread for heavy stroke computation.
 *
 * Offloads Catmull-Rom spline interpolation, stamp loop calculations, and
 * velocity dynamics from the main thread, reducing jank during high-frequency
 * touch events. Results are passed back to the main thread via callback.
 *
 * Architecture:
 * - Main thread: Collects touch points, requests computation
 * - ComputeThread: Spline interpolation, stamp placement, velocity/pressure dynamics
 * - Main thread: Renders precomputed stamp positions with GPU
 *
 * This decouples input from rendering, enabling 120 Hz input with ~16ms latency
 * instead of full stroke rendering on the critical path.
 */
class StrokeComputeThread(
    private val masterBrush: MasterPaintBrush,
) {
    private val handlerThread = HandlerThread("StrokeCompute").apply { start() }
    private val computeHandler = Handler(handlerThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class PrecomputedStroke(
        val stampPositions: List<StampPosition>,
        val predictedPath: List<Pair<Float, Float>>,
    )

    data class StampPosition(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val width: Float,
        val alpha: Float,
        val angleDeg: Float,
    )

    /**
     * Asynchronously compute stroke stamps and spline on background thread.
     * Calls onComplete on the main thread when done.
     */
    fun computeStrokeAsync(
        points: List<StrokePoint>,
        brush: Brush,
        onComplete: (PrecomputedStroke) -> Unit,
    ) {
        computeHandler.post {
            try {
                val samples = points.map { MasterPaintBrush.MasterSample(it.x, it.y, it.pressure.coerceIn(0f, 1f), it.timestamp) }
                val spline = masterBrush.catmullRomSplinePublic(samples)

                // Compute all stamp positions on this background thread
                val stampPositions = computeStampPositions(spline, brush, samples)
                val predictedPath = spline.map { it.x to it.y }

                val result = PrecomputedStroke(stampPositions, predictedPath)

                // Post result back to main thread
                mainHandler.post {
                    onComplete(result)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Synchronously compute stroke stamps (for blocking operations).
     */
    fun computeStrokeSync(
        points: List<StrokePoint>,
        brush: Brush,
    ): PrecomputedStroke {
        val samples = points.map { MasterPaintBrush.MasterSample(it.x, it.y, it.pressure.coerceIn(0f, 1f), it.timestamp) }
        val spline = masterBrush.catmullRomSplinePublic(samples)

        val stampPositions = computeStampPositions(spline, brush, samples)
        val predictedPath = spline.map { it.x to it.y }

        return PrecomputedStroke(stampPositions, predictedPath)
    }

    private fun computeStampPositions(
        spline: List<MasterPaintBrush.MasterSample>,
        brush: Brush,
        originalSamples: List<MasterPaintBrush.MasterSample>,
    ): List<StampPosition> {
        val positions = mutableListOf<StampPosition>()
        if (spline.size < 2) return positions

        val overlapFactor = brush.profile.tip.overlapFactor.coerceIn(0f, 1f)
        val minGap = brush.profile.tip.minGapClamping.coerceAtLeast(0.25f)
        val baseSpacing = (brush.size * (1f - overlapFactor)).coerceAtLeast(minGap)
        val spacingTightening = brush.profile.tip.fluidVelocitySpacingTightening.coerceIn(0f, 0.8f)

        var carry = 0f
        for (i in 1 until spline.size) {
            val a = spline[i - 1]
            val b = spline[i]
            val segLen = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            if (segLen <= 0.01f) continue

            val dt = kotlin.math.max(1f, (b.t - a.t).toFloat())
            val velocity = segLen / dt
            val velocityFactor = (velocity / 1.2f).coerceIn(0f, 1f)
            val adaptiveSpacing = kotlin.math.max(minGap * 0.5f, baseSpacing * lerp(1f, 1f - spacingTightening, velocityFactor))

            val width = brush.size * (1f - velocityFactor * 0.2f)
            val alpha = (brush.opacity * 0.2f).coerceIn(0.05f, 0.3f)

            var consumed = carry
            while (consumed + adaptiveSpacing <= segLen) {
                consumed += adaptiveSpacing
                val t = (consumed / segLen).coerceIn(0f, 1f)
                val x = lerp(a.x, b.x, t)
                val y = lerp(a.y, b.y, t)
                val tangentX = b.x - a.x
                val tangentY = b.y - a.y
                val angleDeg = Math.toDegrees(kotlin.math.atan2(tangentY.toDouble(), tangentX.toDouble())).toFloat()

                positions.add(
                    StampPosition(
                        x = x,
                        y = y,
                        pressure = b.pressure,
                        width = width,
                        alpha = alpha,
                        angleDeg = angleDeg,
                    )
                )
            }
            carry = consumed - segLen
        }

        return positions
    }

    fun release() {
        handlerThread.quitSafely()
    }

    companion object {
        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    }
}

