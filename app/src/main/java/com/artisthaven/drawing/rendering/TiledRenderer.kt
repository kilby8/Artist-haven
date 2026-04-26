package com.artisthaven.drawing.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.artisthaven.drawing.layer.LayerManager
import kotlin.math.ceil

/**
 * Splits a large canvas into a grid of fixed-size tiles and renders each tile
 * independently to keep per-frame heap allocations bounded.
 *
 * Clean Architecture — Infrastructure layer.
 *
 * **Why tiled rendering?**
 * A 4K canvas (3840 × 2160) at ARGB_8888 requires ~32 MB as a single bitmap.
 * Allocating and blending multiple such buffers simultaneously on the render
 * thread can trigger OOM errors on devices with constrained Java heap limits.
 * By processing [TILE_SIZE] × [TILE_SIZE] pixel blocks one at a time, the
 * maximum additional allocation per frame is bounded to `TILE_SIZE² × 4` bytes
 * (≈ 4 MB for a 1024-px tile), regardless of canvas resolution.
 *
 * **Thread safety:**
 * [renderToTarget] is designed to be called from a single background render
 * thread.  The [LayerManager] must not mutate its bitmaps concurrently.
 *
 * @param layerManager The [LayerManager] whose layers this renderer will composite.
 * @param canvasWidth  Total canvas width in pixels.
 * @param canvasHeight Total canvas height in pixels.
 */
class TiledRenderer(
    private val layerManager: LayerManager,
    private val canvasWidth: Int,
    private val canvasHeight: Int
) {
    companion object {
        /**
         * Side length of each square tile in pixels.
         * 512 px ≈ 1 MB per tile at ARGB_8888 — a safe working set on most devices.
         * Increase to 1024 for higher-end devices; decrease if OOM errors persist.
         */
        const val TILE_SIZE = 512
    }

    /** Number of tile columns. */
    val tileColumns: Int = ceil(canvasWidth.toDouble() / TILE_SIZE).toInt()

    /** Number of tile rows. */
    val tileRows: Int = ceil(canvasHeight.toDouble() / TILE_SIZE).toInt()

    /** Total tile count. */
    val tileCount: Int = tileColumns * tileRows

    /**
     * Reusable scratch tile bitmap — allocated once at construction and reused
     * for every tile to eliminate per-tile GC allocation pressure.
     * Size is capped at [TILE_SIZE] on both axes.
     */
    private val tileBitmap: Bitmap =
        Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
    private val tileCanvas: Canvas = Canvas(tileBitmap)
    private val blitPaint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)

    // Reusable Rect instances — pre-allocated to avoid per-tile GC pressure.
    private val tileSrcRect: Rect = Rect()
    private val tileDstRect: Rect = Rect()
    private val tileDrawRect: Rect = Rect()

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Composites all layers tile-by-tile and draws the result onto [targetCanvas].
     *
     * The function:
     *  1. Iterates over every tile in row-major order.
     *  2. For each tile, calls [LayerManager.flatten] to obtain the full-resolution
     *     composite bitmap (LayerManager maintains its own reusable buffer).
     *  3. Copies only the relevant tile region from the composite into the reusable
     *     [tileBitmap] scratch buffer.
     *  4. Draws [tileBitmap] at the correct offset on [targetCanvas].
     *
     * Memory high-water mark per frame:
     *   [LayerManager] composite bitmap (canvasWidth × canvasHeight × 4 bytes)
     *   + [tileBitmap] ([TILE_SIZE]² × 4 bytes)
     *
     * @param targetCanvas The canvas to render the composited image onto
     *                     (typically bound to a [android.view.SurfaceView]).
     */
    fun renderToTarget(targetCanvas: Canvas) {
        // Obtain the fully-blended composite. LayerManager reuses its internal
        // buffer — we must not recycle it.
        val composite: Bitmap = layerManager.flatten()

        for (row in 0 until tileRows) {
            for (col in 0 until tileColumns) {
                // Source region in the composite bitmap.
                val left   = col * TILE_SIZE
                val top    = row * TILE_SIZE
                val right  = minOf(left + TILE_SIZE, canvasWidth)
                val bottom = minOf(top + TILE_SIZE, canvasHeight)

                tileSrcRect.set(left, top, right, bottom)

                // Tile dimensions may be smaller than TILE_SIZE at the edges.
                val tileW = right - left
                val tileH = bottom - top

                // Copy the tile region from the composite into the scratch bitmap.
                // Using Canvas.drawBitmap with a src rect avoids creating a sub-bitmap.
                tileDrawRect.set(0, 0, tileW, tileH)
                tileCanvas.drawBitmap(composite, tileSrcRect, tileDrawRect, blitPaint)

                // Blit the scratch tile onto the target canvas at the correct offset.
                tileDstRect.set(left, top, right, bottom)
                targetCanvas.drawBitmap(tileBitmap, tileDrawRect, tileDstRect, blitPaint)
            }
        }
    }

    /**
     * Renders only the tiles that intersect with [dirtyRegion], skipping
     * tiles that are fully outside the changed area.
     *
     * Use this for incremental redraw after a single stroke dab to avoid
     * re-compositing the entire canvas on every [android.view.MotionEvent].
     *
     * @param targetCanvas   The canvas to render onto.
     * @param dirtyRegion    Bounding box of the changed area in canvas pixels.
     */
    fun renderDirty(targetCanvas: Canvas, dirtyRegion: Rect) {
        val composite: Bitmap = layerManager.flatten()

        // Determine which tile columns and rows intersect the dirty region.
        val colStart = (dirtyRegion.left / TILE_SIZE).coerceAtLeast(0)
        val colEnd   = ceil(dirtyRegion.right.toDouble() / TILE_SIZE).toInt()
                           .coerceAtMost(tileColumns)
        val rowStart = (dirtyRegion.top / TILE_SIZE).coerceAtLeast(0)
        val rowEnd   = ceil(dirtyRegion.bottom.toDouble() / TILE_SIZE).toInt()
                           .coerceAtMost(tileRows)

        val srcRect = tileSrcRect
        val dstRect = tileDstRect

        for (row in rowStart until rowEnd) {
            for (col in colStart until colEnd) {
                val left   = col * TILE_SIZE
                val top    = row * TILE_SIZE
                val right  = minOf(left + TILE_SIZE, canvasWidth)
                val bottom = minOf(top + TILE_SIZE, canvasHeight)

                val tileW = right - left
                val tileH = bottom - top

                srcRect.set(left, top, right, bottom)
                tileDrawRect.set(0, 0, tileW, tileH)
                tileCanvas.drawBitmap(composite, srcRect, tileDrawRect, blitPaint)

                dstRect.set(left, top, right, bottom)
                targetCanvas.drawBitmap(tileBitmap, tileDrawRect, dstRect, blitPaint)
            }
        }
    }

    /**
     * Releases the scratch tile bitmap.
     * LayerManager's composite bitmap is managed by [LayerManager.recycle].
     */
    fun recycle() {
        if (!tileBitmap.isRecycled) tileBitmap.recycle()
    }
}
