package com.artisthaven.engine.io

import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels

/**
 * Writes a layered Photoshop file (8-bit RGB, PackBits compressed) that opens in Photoshop,
 * Procreate, Fresco, Krita and GIMP with layer names, visibility, opacity, blend modes and
 * clipping masks intact. The paper becomes a "Background" layer at the bottom.
 */
object PsdWriter {

    /** The whole file. */
    fun encode(doc: Document): ByteArray {
        val o = ByteSink(1 shl 20)
        // File header.
        o.ascii("8BPS")
        o.short(1)
        o.zeros(6)
        o.short(4) // channels in the merged image: RGBA
        o.int(doc.height)
        o.int(doc.width)
        o.short(8)
        o.short(3) // RGB
        o.int(0) // colour mode data
        o.int(0) // image resources

        // Layer and mask information.
        val layerInfo = layerInfo(doc)
        o.int(layerInfo.size + 4 + 4)
        o.int(layerInfo.size)
        o.bytes(layerInfo)
        o.int(0) // global layer mask info

        // Merged image, planar, PackBits.
        val merged = Compositor.flatten(doc)
        writeImageRle(o, merged, doc.width, doc.height, channels = intArrayOf(16, 8, 0, 24))
        return o.toByteArray()
    }

    private class Entry(val name: String, val rect: IRect, val pixels: IntArray, val opacity: Int,
                        val blendKey: String, val clipping: Boolean, val visible: Boolean)

    private fun layerInfo(doc: Document): ByteArray {
        val entries = ArrayList<Entry>()
        if (doc.paperVisible) {
            val paper = doc.paperColor or (0xFF shl 24)
            entries += Entry("Background", doc.bounds, IntArray(doc.width * doc.height) { paper }, 255, "norm", false, true)
        }
        for (layer in doc.layers) {
            val rect = layer.surface.contentBounds() ?: IRect(0, 0, 0, 0)
            val px = IntArray(rect.width * rect.height)
            if (!rect.isEmpty) {
                // Masks are baked in, so the PSD looks the same in any app.
                var i = 0
                for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) px[i++] = Pixels.unpremultiply(layer.maskedPixel(x, y))
            }
            entries += Entry(
                layer.name, rect, px, (doc.effectiveOpacity(layer) * 255f + 0.5f).toInt(),
                layer.blendMode.psdKey, layer.clipping, doc.effectiveVisible(layer),
            )
        }

        val r = ByteSink(4096)
        val im = ByteSink(1 shl 20)
        r.short(entries.size)
        for (e in entries) {
            r.int(e.rect.top)
            r.int(e.rect.left)
            r.int(e.rect.bottom)
            r.int(e.rect.right)
            r.short(4)
            val shifts = intArrayOf(24, 16, 8, 0)
            val ids = intArrayOf(-1, 0, 1, 2)
            val channelData = shifts.map { shift -> channelRle(e.pixels, e.rect.width, e.rect.height, shift) }
            for (c in 0 until 4) {
                r.short(ids[c])
                r.int(channelData[c].size)
            }
            r.ascii("8BIM")
            r.ascii(e.blendKey)
            r.byte(e.opacity.coerceIn(0, 255))
            r.byte(if (e.clipping) 1 else 0)
            r.byte(if (e.visible) 0 else 2) // bit 1 = hidden
            r.byte(0)
            val x = ByteSink(256)
            x.int(0) // layer mask
            x.int(0) // blending ranges
            val nameBytes = ByteArray(minOf(255, e.name.length)) { i -> e.name[i].code.let { c -> if (c < 256) c else '?'.code }.toByte() }
            x.byte(nameBytes.size)
            x.bytes(nameBytes)
            val padded = (1 + nameBytes.size + 3) / 4 * 4
            repeat(padded - 1 - nameBytes.size) { x.byte(0) }
            // Unicode name, so non-Latin names survive.
            val u = ByteSink(64)
            u.int(e.name.length)
            for (ch in e.name) u.short(ch.code)
            if (u.size % 2 != 0) u.byte(0)
            x.ascii("8BIM")
            x.ascii("luni")
            x.int(u.size)
            x.bytes(u.toByteArray())
            r.int(x.size)
            r.bytes(x.toByteArray())
            for (c in channelData) im.bytes(c)
        }
        val total = ByteSink(r.size + im.size + 1)
        total.bytes(r.toByteArray())
        total.bytes(im.toByteArray())
        if (total.size % 2 != 0) total.byte(0)
        return total.toByteArray()
    }

    /** One channel of a layer: compression = 1, row byte counts, then PackBits rows. */
    private fun channelRle(px: IntArray, w: Int, h: Int, shift: Int): ByteArray {
        val d = ByteSink(w * h / 2 + 16)
        d.short(1)
        if (w == 0 || h == 0) return d.toByteArray()
        val rows = Array(h) { y -> packBits(ByteArray(w) { x -> (px[y * w + x] ushr shift).toByte() }) }
        for (row in rows) d.short(row.size)
        for (row in rows) d.bytes(row)
        return d.toByteArray()
    }

    private fun writeImageRle(o: ByteSink, px: IntArray, w: Int, h: Int, channels: IntArray) {
        o.short(1)
        val rows = channels.map { shift -> Array(h) { y -> packBits(ByteArray(w) { x -> (px[y * w + x] ushr shift).toByte() }) } }
        for (c in rows) for (row in c) o.short(row.size)
        for (c in rows) for (row in c) o.bytes(row)
    }

    /** Apple PackBits run-length encoding. */
    fun packBits(src: ByteArray): ByteArray {
        val out = ByteSink(src.size / 2 + 8)
        var i = 0
        val n = src.size
        while (i < n) {
            var run = 1
            while (i + run < n && run < 128 && src[i + run] == src[i]) run++
            if (run >= 3) {
                out.byte(-(run - 1) and 0xFF)
                out.byte(src[i].toInt())
                i += run
            } else {
                val start = i
                var len = 0
                while (i < n && len < 128) {
                    if (i + 2 < n && src[i] == src[i + 1] && src[i] == src[i + 2]) break
                    i++
                    len++
                }
                out.byte(len - 1)
                out.bytes(src, start, start + len)
            }
        }
        return out.toByteArray()
    }
}
