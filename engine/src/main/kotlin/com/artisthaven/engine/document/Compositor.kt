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
        val maskData = arrayOfNulls<IntArray>(count)
        val hasMask = visible.any { it.mask != null }
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
                if (hasMask) for (i in 0 until count) maskData[i] = visible[i].mask?.tile(index)
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
                            val md = maskData[i]
                            if (md != null && md[ti] != 0) {
                                src = Pixels.scale(src, Layer.maskVisibility(md[ti]))
                                if (src == 0) continue
                            }
                            val cb = clipBase[i]
                            if (cb >= 0) {
                                var ba = (tileData[cb]?.get(ti) ?: 0) ushr 24
                                val bm = maskData[cb]
                                if (bm != null && bm[ti] != 0) ba = (ba * Layer.maskVisibility(bm[ti])).toInt()
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

    /**
     * Small straight-ARGB preview that fits in [maxSize] x [maxSize], averaging a 2x2 grid of
     * samples per output pixel. Returns the image and its width and height.
     */
    fun thumbnail(doc: Document, maxSize: Int, withPaper: Boolean = true): Triple<IntArray, Int, Int> {
        val scale = minOf(maxSize.toFloat() / doc.width, maxSize.toFloat() / doc.height, 1f)
        val tw = maxOf(1, (doc.width * scale).toInt())
        val th = maxOf(1, (doc.height * scale).toInt())
        val out = IntArray(tw * th)
        val visible = doc.layers.filter { it.visible && it.opacity > 0f }
        val paper = if (withPaper && doc.paperVisible) Pixels.premultiply(doc.paperColor or (0xFF shl 24)) else 0
        val fx = doc.width.toFloat() / tw
        val fy = doc.height.toFloat() / th
        for (y in 0 until th) for (x in 0 until tw) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (sy in 0..1) for (sx in 0..1) {
                val px = ((x + 0.25f + sx * 0.5f) * fx).toInt().coerceIn(0, doc.width - 1)
                val py = ((y + 0.25f + sy * 0.5f) * fy).toInt().coerceIn(0, doc.height - 1)
                var acc = paper
                var base = 0
                for (layer in visible) {
                    var src = layer.maskedPixel(px, py)
                    if (!layer.clipping) base = src ushr 24
                    if (src == 0) continue
                    if (layer.clipping) {
                        if (base == 0) continue
                        if (base < 255) src = Pixels.scale(src, base / 255f)
                    }
                    acc = layer.blendMode.blend(acc, src, layer.opacity)
                }
                a += Pixels.alpha(acc); r += Pixels.red(acc); g += Pixels.green(acc); b += Pixels.blue(acc)
            }
            out[y * tw + x] = Pixels.unpremultiply(Pixels.pack(a / 4, r / 4, g / 4, b / 4))
        }
        return Triple(out, tw, th)
    }

    /** Thumbnail of a single layer's pixels on a transparent background. */
    fun layerThumbnail(doc: Document, layerId: String, maxSize: Int): Triple<IntArray, Int, Int> {
        val layer = doc.layer(layerId)
        val scale = minOf(maxSize.toFloat() / doc.width, maxSize.toFloat() / doc.height, 1f)
        val tw = maxOf(1, (doc.width * scale).toInt())
        val th = maxOf(1, (doc.height * scale).toInt())
        val out = IntArray(tw * th)
        if (layer != null) {
            val fx = doc.width.toFloat() / tw
            val fy = doc.height.toFloat() / th
            for (y in 0 until th) for (x in 0 until tw) {
                val c = layer.maskedPixel(((x + 0.5f) * fx).toInt(), ((y + 0.5f) * fy).toInt())
                out[y * tw + x] = Pixels.unpremultiply(c)
            }
        }
        return Triple(out, tw, th)
    }

    /** A layer's mask as a black-and-white thumbnail (white shows, black hides), or null if none. */
    fun maskThumbnail(doc: Document, layerId: String, maxSize: Int): Triple<IntArray, Int, Int>? {
        val mask = doc.layer(layerId)?.mask ?: return null
        val scale = minOf(maxSize.toFloat() / doc.width, maxSize.toFloat() / doc.height, 1f)
        val tw = maxOf(1, (doc.width * scale).toInt())
        val th = maxOf(1, (doc.height * scale).toInt())
        val out = IntArray(tw * th)
        val fx = doc.width.toFloat() / tw
        val fy = doc.height.toFloat() / th
        for (y in 0 until th) for (x in 0 until tw) {
            val v = (Layer.maskVisibility(mask.getPixel(((x + 0.5f) * fx).toInt(), ((y + 0.5f) * fy).toInt())) * 255f).toInt()
            out[y * tw + x] = Pixels.pack(255, v, v, v)
        }
        return Triple(out, tw, th)
    }
}
