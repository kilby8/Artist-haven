package com.artisthaven.engine.document

import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.raster.TiledSurface

/**
 * Flattens a document into pixels: paper, then each visible layer bottom to top with its blend
 * mode, opacity and clipping mask. Output is always straight (non-premultiplied) ARGB.
 */
object Compositor {

    /**
     * Composites [rect] of the document into [out]. Pixel (x, y) is written to
     * `out[(y - originY) * stride + (x - originX)]`; the defaults address a full-document buffer.
     * With [withPaper] false the background stays transparent.
     */
    fun composite(
        doc: Document,
        rect: IRect,
        out: IntArray,
        originX: Int = 0,
        originY: Int = 0,
        stride: Int = doc.width,
        withPaper: Boolean = doc.paperVisible,
    ) {
        val r = rect.intersect(doc.bounds)
        if (r.isEmpty) return
        val visible = doc.layers.filter { it.visible && it.opacity > 0f }
        val paper = if (withPaper) Pixels.premultiply(doc.paperColor or (0xFF shl 24)) else 0
        val count = visible.size
        val tileData = arrayOfNulls<IntArray>(count)
        val clipBase = IntArray(count) { -1 }
        var base = -1
        for (i in 0 until count) {
            if (visible[i].clipping && base >= 0) clipBase[i] = base else base = i
        }

        val shift = TiledSurface.SHIFT
        val tilesX = (doc.width + TiledSurface.TILE - 1) / TiledSurface.TILE
        for (ty in (r.top shr shift)..((r.bottom - 1) shr shift)) {
            for (tx in (r.left shr shift)..((r.right - 1) shr shift)) {
                val index = ty * tilesX + tx
                for (i in 0 until count) tileData[i] = visible[i].surface.tile(index)
                val x0 = maxOf(r.left, tx shl shift)
                val x1 = minOf(r.right, (tx + 1) shl shift)
                val y0 = maxOf(r.top, ty shl shift)
                val y1 = minOf(r.bottom, (ty + 1) shl shift)
                for (y in y0 until y1) {
                    val tileRow = (y and TiledSurface.MASK) shl shift
                    val outRow = (y - originY) * stride - originX
                    for (x in x0 until x1) {
                        val ti = tileRow or (x and TiledSurface.MASK)
                        var acc = paper
                        for (i in 0 until count) {
                            val data = tileData[i] ?: continue
                            var src = data[ti]
                            if (src == 0) continue
                            val cb = clipBase[i]
                            if (cb >= 0) {
                                val ba = (tileData[cb]?.get(ti) ?: 0) ushr 24
                                if (ba == 0) continue
                                if (ba < 255) src = Pixels.scale(src, ba / 255f)
                            }
                            val layer = visible[i]
                            acc = layer.blendMode.blend(acc, src, layer.opacity)
                        }
                        out[outRow + x] = Pixels.unpremultiply(acc)
                    }
                }
            }
        }
    }

    /** Full straight-ARGB image of the document. */
    fun flatten(doc: Document, withPaper: Boolean = doc.paperVisible): IntArray {
        val out = IntArray(doc.width * doc.height)
        composite(doc, doc.bounds, out, withPaper = withPaper)
        return out
    }

    /** Straight colour of the flattened document at one point (for the eyedropper). */
    fun sample(doc: Document, x: Int, y: Int): Int {
        val cx = x.coerceIn(0, doc.width - 1)
        val cy = y.coerceIn(0, doc.height - 1)
        val one = IntArray(1)
        composite(doc, IRect(cx, cy, cx + 1, cy + 1), one, originX = cx, originY = cy, stride = 1, withPaper = true)
        return one[0]
    }
}
