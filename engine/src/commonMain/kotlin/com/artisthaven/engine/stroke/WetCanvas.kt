package com.artisthaven.engine.stroke

import com.artisthaven.engine.raster.TiledSurface
import kotlin.math.min

/**
 * Where recent watercolour washes are still wet. Each wash leaves its water here when it ends, and
 * the paper dries over [dryMillis]. A new wash that runs into a wet one bleeds into it instead of
 * forming a hard edge (wet-in-wet).
 *
 * Wetness is stored per pixel; the time of the last wash is kept per tile, so a whole tile dries at
 * the same rate. It is not part of the document or undo history: it is only the state of the paper.
 */
class WetCanvas(val width: Int, val height: Int, private val dryMillis: Long = 15_000L) {
    private val tilesX = (width + TiledSurface.TILE - 1) / TiledSurface.TILE
    private val tilesY = (height + TiledSurface.TILE - 1) / TiledSurface.TILE
    private val water = arrayOfNulls<FloatArray>(tilesX * tilesY)
    private val stamp = LongArray(tilesX * tilesY)

    /** True if any wash is still wet at [now]. */
    fun anyWet(now: Long): Boolean {
        for (i in water.indices) if (water[i] != null && now - stamp[i] < dryMillis) return true
        return false
    }

    private fun dryness(index: Int, now: Long): Float {
        val age = (now - stamp[index]).coerceAtLeast(0L)
        if (age >= dryMillis) return 0f
        val t = 1f - age / dryMillis.toFloat()
        // Stays wet for a while, then dries quickly.
        return t * (2f - t)
    }

    /** Wetness 0..1 at a pixel. */
    fun at(x: Int, y: Int, now: Long): Float {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0f
        val index = (y shr TiledSurface.SHIFT) * tilesX + (x shr TiledSurface.SHIFT)
        val t = water[index] ?: return 0f
        val k = dryness(index, now)
        if (k <= 0f) return 0f
        return t[((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)] * k
    }

    /** How wet each tile still is at [now], 0..1; pass it to [at] to look up many pixels quickly. */
    fun dryingAt(now: Long): FloatArray = FloatArray(water.size) { if (water[it] == null) 0f else dryness(it, now) }

    /** Wetness 0..1 at a pixel, with each tile's drying from [dryingAt]. */
    fun at(x: Int, y: Int, drying: FloatArray): Float {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0f
        val index = (y shr TiledSurface.SHIFT) * tilesX + (x shr TiledSurface.SHIFT)
        val k = drying[index]
        if (k <= 0f) return 0f
        val t = water[index] ?: return 0f
        return t[((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)] * k
    }

    /** Adds a finished wash's water (one 64 x 64 array per tile, null where it is dry) at [now]. */
    fun deposit(tiles: Array<FloatArray?>, now: Long) {
        for (index in 0 until min(tiles.size, water.size)) {
            val add = tiles[index] ?: continue
            val k = dryness(index, now)
            val old = water[index]
            val out = FloatArray(TiledSurface.TILE * TiledSurface.TILE)
            for (i in out.indices) {
                val prev = (old?.get(i) ?: 0f) * k
                out[i] = if (add[i] > prev) add[i] else prev
            }
            water[index] = out
            stamp[index] = now
        }
    }

    fun clear() {
        water.fill(null)
    }
}
