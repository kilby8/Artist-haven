package com.artisthaven.engine.document

import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.TiledSurface
import com.artisthaven.engine.tools.Affine
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Which pixels edits may touch, as soft 0..255 coverage so lasso edges are anti-aliased. Stored in
 * 64x64 tiles like layers: a null tile is unselected, [FULL] is fully selected. Immutable once
 * built, so undo can keep references to old selections.
 */
class Selection private constructor(val width: Int, val height: Int, private val tiles: Array<ByteArray?>) {
    private val tilesX = (width + TILE - 1) / TILE

    /** Coverage at a pixel, 0..255. */
    fun at(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        val t = tiles[(y shr SHIFT) * tilesX + (x shr SHIFT)] ?: return 0
        if (t === FULL) return 255
        return t[((y and MASK) shl SHIFT) or (x and MASK)].toInt() and 0xFF
    }

    fun coverage(x: Int, y: Int): Float = at(x, y) / 255f

    /** True if a whole layer tile is outside the selection, so edits can skip it. */
    fun tileEmpty(index: Int): Boolean = tiles[index] == null

    val isEmpty: Boolean get() = tiles.all { it == null }

    /** Bounding box of selected pixels, or null if nothing is selected. */
    fun bounds(): IRect? {
        var r: IRect? = null
        for (i in tiles.indices) {
            val t = tiles[i] ?: continue
            val tx = i % tilesX
            val ty = i / tilesX
            val tr = IRect(tx * TILE, ty * TILE, min(width, (tx + 1) * TILE), min(height, (ty + 1) * TILE))
            if (t === FULL) {
                r = IRect.unionOf(r, tr)
                continue
            }
            var l = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var bottom = Int.MIN_VALUE
            for (y in 0 until tr.height) for (x in 0 until tr.width) {
                if (t[(y shl SHIFT) or x].toInt() != 0) {
                    l = min(l, x); right = max(right, x + 1); top = min(top, y); bottom = max(bottom, y + 1)
                }
            }
            if (l != Int.MAX_VALUE) r = IRect.unionOf(r, IRect(tr.left + l, tr.top + top, tr.left + right, tr.top + bottom))
        }
        return r
    }

    fun inverted(): Selection = Builder(width, height).also { b ->
        for (y in 0 until height) for (x in 0 until width) b.set(x, y, 255 - at(x, y))
    }.build()

    /** Combines with [other]: union when [add], otherwise removes [other] from this. */
    fun combine(other: Selection, add: Boolean): Selection = Builder(width, height).also { b ->
        for (i in tiles.indices) {
            if (tiles[i] == null && other.tiles[i] == null) continue
            val tx = (i % tilesX) * TILE
            val ty = (i / tilesX) * TILE
            for (y in ty until min(height, ty + TILE)) for (x in tx until min(width, tx + TILE)) {
                val a = at(x, y)
                val o = other.at(x, y)
                b.set(x, y, if (add) max(a, o) else a * (255 - o) / 255)
            }
        }
    }.build()

    /** The selection moved by [m], resampled bilinearly. */
    fun transformed(m: Affine): Selection {
        val b = Builder(width, height)
        val src = bounds() ?: return this
        val out = m.mapBounds(src).intersect(IRect(0, 0, width, height))
        val inv = m.inverse()
        for (y in out.top until out.bottom) for (x in out.left until out.right) {
            val sx = inv.mapX(x + 0.5f, y + 0.5f) - 0.5f
            val sy = inv.mapY(x + 0.5f, y + 0.5f) - 0.5f
            val x0 = floor(sx).toInt()
            val y0 = floor(sy).toInt()
            val fx = sx - x0
            val fy = sy - y0
            val v = at(x0, y0) * (1 - fx) * (1 - fy) + at(x0 + 1, y0) * fx * (1 - fy) +
                at(x0, y0 + 1) * (1 - fx) * fy + at(x0 + 1, y0 + 1) * fx * fy
            b.set(x, y, (v + 0.5f).toInt())
        }
        return b.build()
    }

    /** Row-major coverage of the whole canvas, for drawing the selection overlay. */
    fun toBytes(): ByteArray {
        val out = ByteArray(width * height)
        for (i in tiles.indices) {
            val t = tiles[i] ?: continue
            val tx = (i % tilesX) * TILE
            val ty = (i / tilesX) * TILE
            for (y in ty until min(height, ty + TILE)) {
                val row = y * width
                for (x in tx until min(width, tx + TILE)) {
                    out[row + x] = if (t === FULL) -1 else t[((y and MASK) shl SHIFT) or (x and MASK)]
                }
            }
        }
        return out
    }

    val byteSize: Long get() = tiles.count { it != null && it !== FULL } * (TILE * TILE).toLong() + 64

    /** Accumulates coverage, then packs it into tiles (dropping empty ones, sharing full ones). */
    class Builder(private val width: Int, private val height: Int) {
        private val tilesX = (width + TILE - 1) / TILE
        private val tiles = arrayOfNulls<ByteArray>(tilesX * ((height + TILE - 1) / TILE))

        fun get(x: Int, y: Int): Int {
            val t = tiles[(y shr SHIFT) * tilesX + (x shr SHIFT)] ?: return 0
            return t[((y and MASK) shl SHIFT) or (x and MASK)].toInt() and 0xFF
        }

        fun set(x: Int, y: Int, v: Int) {
            if (x < 0 || y < 0 || x >= width || y >= height) return
            val index = (y shr SHIFT) * tilesX + (x shr SHIFT)
            val t = tiles[index] ?: if (v <= 0) return else ByteArray(TILE * TILE).also { tiles[index] = it }
            t[((y and MASK) shl SHIFT) or (x and MASK)] = v.coerceIn(0, 255).toByte()
        }

        fun build(): Selection {
            for (i in tiles.indices) {
                val t = tiles[i] ?: continue
                val tx = (i % tilesX) * TILE
                val ty = (i / tilesX) * TILE
                val fullTile = tx + TILE <= width && ty + TILE <= height
                if (t.all { it.toInt() == 0 }) tiles[i] = null
                else if (fullTile && t.all { it.toInt() == -1 }) tiles[i] = FULL
            }
            return Selection(width, height, tiles)
        }
    }

    companion object {
        private const val TILE = TiledSurface.TILE
        private const val SHIFT = TiledSurface.SHIFT
        private const val MASK = TiledSurface.MASK
        private val FULL = ByteArray(0)

        fun all(width: Int, height: Int): Selection {
            val b = Builder(width, height)
            for (y in 0 until height) for (x in 0 until width) b.set(x, y, 255)
            return b.build()
        }

        /**
         * Fills a closed polygon ([xy] = x0, y0, x1, y1, ...) with anti-aliased edges: four
         * sub-rows per pixel, exact horizontal coverage. Self-crossing lassos use even-odd.
         */
        fun polygon(width: Int, height: Int, xy: FloatArray): Selection {
            val b = Builder(width, height)
            val n = xy.size / 2
            if (n < 3) return b.build()
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (i in 0 until n) { minY = min(minY, xy[i * 2 + 1]); maxY = max(maxY, xy[i * 2 + 1]) }
            val y0 = max(0, floor(minY).toInt())
            val y1 = min(height, ceil(maxY).toInt() + 1)
            val row = FloatArray(width + 1)
            val xs = FloatArray(n)
            for (y in y0 until y1) {
                row.fill(0f)
                var any = false
                for (sub in 0 until SUB) {
                    val sy = y + (sub + 0.5f) / SUB
                    var k = 0
                    for (i in 0 until n) {
                        val ax = xy[i * 2]; val ay = xy[i * 2 + 1]
                        val j = (i + 1) % n
                        val bx = xy[j * 2]; val by = xy[j * 2 + 1]
                        if ((ay <= sy) != (by <= sy)) xs[k++] = ax + (sy - ay) / (by - ay) * (bx - ax)
                    }
                    if (k < 2) continue
                    xs.sort(0, k)
                    var p = 0
                    while (p + 1 < k) {
                        val a = xs[p].coerceIn(0f, width.toFloat())
                        val e = xs[p + 1].coerceIn(0f, width.toFloat())
                        p += 2
                        if (e <= a) continue
                        any = true
                        val ia = floor(a).toInt()
                        val ie = min(width - 1, floor(e).toInt())
                        for (x in ia..ie) {
                            val overlap = min(e, x + 1f) - max(a, x.toFloat())
                            if (overlap > 0f) row[x] += overlap / SUB
                        }
                    }
                }
                if (!any) continue
                for (x in 0 until width) {
                    val v = (row[x] * 255f + 0.5f).toInt()
                    if (v > 0) b.set(x, y, v)
                }
            }
            return b.build()
        }

        /** Builds a selection from per-pixel coverage, row-major, 0..1. */
        fun fromCoverage(width: Int, height: Int, rect: IRect, cover: FloatArray): Selection {
            val b = Builder(width, height)
            for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) {
                val v = cover[(y - rect.top) * rect.width + (x - rect.left)]
                if (v > 0f) b.set(x, y, (v * 255f + 0.5f).toInt())
            }
            return b.build()
        }

        private const val SUB = 4
    }
}
