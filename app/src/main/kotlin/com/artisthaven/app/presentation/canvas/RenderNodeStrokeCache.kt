package com.artisthaven.app.presentation.canvas

import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * RenderNode-based Stroke Cache for Zero-Latency Rendering.
 *
 * Caches the final rendered portions of strokes using RenderNode recording.
 * Only the most recent ~100ms of line needs to be computed and rendered.
 * Older portions are cached as RenderNodes and replayed without recomputation.
 *
 * Benefits:
 * - Finished stroke segments never need to be touched again
 * - Only active stroke tail is being computed
 * - Massive reduction in per-frame work for long strokes
 *
 * Available on Android 10+ (API 29+), where the public RenderNode APIs used here exist.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class RenderNodeStrokeCache(
    private val canvasWidth: Int,
    private val canvasHeight: Int,
) {
    private val cachedNodes = mutableListOf<CachedNodeSegment>()

    data class CachedNodeSegment(
        val renderNode: RenderNode,
        val startPointIndex: Int,
        val endPointIndex: Int,
        val createdTimeMs: Long,
    )

    /**
     * Record a finished stroke segment to the cache.
     * Only call this for segments that are truly "final" and won't be re-rendered.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun cacheSegment(
        pointRange: IntRange,
        renderBlock: (Canvas) -> Unit,
    ) {
        val now = System.currentTimeMillis()

        // Create a new RenderNode for this segment
        val node = RenderNode("StrokeSegment_${pointRange.first}_${pointRange.last}").apply {
            setPosition(0, 0, canvasWidth, canvasHeight)
        }

        val recordingCanvas = node.beginRecording()
        renderBlock(recordingCanvas)
        node.endRecording()

        cachedNodes.add(
            CachedNodeSegment(
                renderNode = node,
                startPointIndex = pointRange.first,
                endPointIndex = pointRange.last,
                createdTimeMs = now,
            )
        )

    }

    /**
     * Replay all cached segments onto the target canvas.
     * This replaces the need to recompute those portions.
     */
    fun replayCachedSegments(canvas: Canvas) {
        for (segment in cachedNodes) {
            canvas.drawRenderNode(segment.renderNode)
        }
    }

    /**
     * Get the index of the oldest cached point.
     * The caller should only compute strokes after this point.
     */
    fun getOldestCachedPointIndex(): Int {
        return cachedNodes.minOfOrNull { it.startPointIndex } ?: 0
    }

    /**
     * Clear all cached segments immediately.
     * Call when starting a new stroke or clearing the canvas.
     */
    fun clearAllCaches() {
        cachedNodes.clear()
    }

    /**
     * Get the number of cached segments.
     */
    fun getCacheSize(): Int = cachedNodes.size
}

