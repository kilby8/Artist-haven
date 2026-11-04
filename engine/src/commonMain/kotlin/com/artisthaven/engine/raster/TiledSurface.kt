package com.artisthaven.engine.raster

/**
 * A layer's pixels, stored as 64x64 tiles of premultiplied ARGB. Tiles that were never painted
 * stay null, so a blank layer costs almost no memory.
 */
class TiledSurface(val width: Int, val height: Int) {
    val tilesX: Int = (width + TILE - 1) / TILE
    val tilesY: Int = (height + TILE - 1) / TILE
    val tileCount: Int get() = tilesX * tilesY

    private val tiles = arrayOfNulls<IntArray>(tilesX * tilesY)

    val bounds: IRect get() = IRect(0, 0, width, height)

    fun tileIndex(tx: Int, ty: Int): Int = ty * tilesX + tx

    fun tile(index: Int): IntArray? = tiles[index]

    fun tileOrCreate(index: Int): IntArray =
        tiles[index] ?: IntArray(TILE * TILE).also { tiles[index] = it }

    fun setTile(index: Int, data: IntArray?) {
        tiles[index] = data
    }

    fun copyTile(index: Int): IntArray? = tiles[index]?.copyOf()

    fun tileRect(index: Int): IRect {
        val tx = index % tilesX
        val ty = index / tilesX
        return IRect(tx * TILE, ty * TILE, minOf(width, (tx + 1) * TILE), minOf(height, (ty + 1) * TILE))
    }

    fun getPixel(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        val t = tiles[(y shr SHIFT) * tilesX + (x shr SHIFT)] ?: return 0
        return t[((y and MASK) shl SHIFT) or (x and MASK)]
    }

    fun setPixel(x: Int, y: Int, c: Int) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        val index = (y shr SHIFT) * tilesX + (x shr SHIFT)
        val t = if (c == 0) tiles[index] ?: return else tileOrCreate(index)
        t[((y and MASK) shl SHIFT) or (x and MASK)] = c
    }

    /** Tile indices overlapping [rect], clipped to the surface. */
    fun tilesIn(rect: IRect): IntArray {
        val r = rect.intersect(bounds)
        if (r.isEmpty) return IntArray(0)
        val tx0 = r.left shr SHIFT
        val ty0 = r.top shr SHIFT
        val tx1 = (r.right - 1) shr SHIFT
        val ty1 = (r.bottom - 1) shr SHIFT
        val out = IntArray((tx1 - tx0 + 1) * (ty1 - ty0 + 1))
        var i = 0
        for (ty in ty0..ty1) for (tx in tx0..tx1) out[i++] = tileIndex(tx, ty)
        return out
    }

    /** Drops tiles that became fully transparent, to keep memory and files small. */
    fun compact(indices: IntArray? = null) {
        val range = indices ?: IntArray(tiles.size) { it }
        for (i in range) {
            val t = tiles[i] ?: continue
            if (t.all { it == 0 }) tiles[i] = null
        }
    }

    fun clear() {
        tiles.fill(null)
    }

    fun isBlank(): Boolean = tiles.all { it == null }

    fun nonEmptyTileIndices(): IntArray = tiles.indices.filter { tiles[it] != null }.toIntArray()

    fun copy(): TiledSurface {
        val s = TiledSurface(width, height)
        for (i in tiles.indices) s.tiles[i] = tiles[i]?.copyOf()
        return s
    }

    /** Bounding box of painted pixels, or null if the surface is blank. */
    fun contentBounds(): IRect? {
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = Int.MIN_VALUE
        var b = Int.MIN_VALUE
        for (i in tiles.indices) {
            val data = tiles[i] ?: continue
            val tr = tileRect(i)
            for (y in 0 until tr.height) {
                val row = y shl SHIFT
                for (x in 0 until tr.width) {
                    if (data[row or x] ushr 24 != 0) {
                        val px = tr.left + x
                        val py = tr.top + y
                        if (px < l) l = px
                        if (px >= r) r = px + 1
                        if (py < t) t = py
                        if (py >= b) b = py + 1
                    }
                }
            }
        }
        return if (l == Int.MAX_VALUE) null else IRect(l, t, r, b)
    }

    /** Reads a region into [out] (row stride = rect width), premultiplied. */
    fun read(rect: IRect, out: IntArray) {
        val w = rect.width
        for (y in rect.top until rect.bottom) {
            val o = (y - rect.top) * w
            for (x in rect.left until rect.right) out[o + x - rect.left] = getPixel(x, y)
        }
    }

    companion object {
        const val TILE = 64
        const val SHIFT = 6
        const val MASK = TILE - 1
        const val TILE_BYTES = TILE * TILE * 4L
    }
}
