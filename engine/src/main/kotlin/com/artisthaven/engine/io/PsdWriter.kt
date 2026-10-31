package com.artisthaven.engine.io

import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream

/**
 * Writes a layered Photoshop file (8-bit RGB, PackBits compressed) that opens in Photoshop,
 * Procreate, Fresco, Krita and GIMP with layer names, visibility, opacity, blend modes and
 * clipping masks intact. The paper becomes a "Background" layer at the bottom.
 */
object PsdWriter {

    fun write(doc: Document, out: OutputStream) {
        val o = DataOutputStream(out)
        // File header.
        o.writeBytes("8BPS")
        o.writeShort(1)
        o.write(ByteArray(6))
        o.writeShort(4) // channels in the merged image: RGBA
        o.writeInt(doc.height)
        o.writeInt(doc.width)
        o.writeShort(8)
        o.writeShort(3) // RGB
        o.writeInt(0) // colour mode data
        o.writeInt(0) // image resources

        // Layer and mask information.
        val layerInfo = layerInfo(doc)
        o.writeInt(layerInfo.size + 4 + 4)
        o.writeInt(layerInfo.size)
        o.write(layerInfo)
        o.writeInt(0) // global layer mask info

        // Merged image, planar, PackBits.
        val merged = Compositor.flatten(doc)
        writeImageRle(o, merged, doc.width, doc.height, channels = intArrayOf(16, 8, 0, 24))
        o.flush()
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
                layer.name, rect, px, (layer.opacity * 255f + 0.5f).toInt(),
                layer.blendMode.psdKey, layer.clipping, layer.visible,
            )
        }

        val records = ByteArrayOutputStream()
        val r = DataOutputStream(records)
        val images = ByteArrayOutputStream()
        val im = DataOutputStream(images)
        r.writeShort(entries.size)
        for (e in entries) {
            r.writeInt(e.rect.top)
            r.writeInt(e.rect.left)
            r.writeInt(e.rect.bottom)
            r.writeInt(e.rect.right)
            r.writeShort(4)
            val shifts = intArrayOf(24, 16, 8, 0)
            val ids = intArrayOf(-1, 0, 1, 2)
            val channelData = shifts.map { shift -> channelRle(e.pixels, e.rect.width, e.rect.height, shift) }
            for (c in 0 until 4) {
                r.writeShort(ids[c])
                r.writeInt(channelData[c].size)
            }
            r.writeBytes("8BIM")
            r.writeBytes(e.blendKey)
            r.writeByte(e.opacity.coerceIn(0, 255))
            r.writeByte(if (e.clipping) 1 else 0)
            r.writeByte(if (e.visible) 0 else 2) // bit 1 = hidden
            r.writeByte(0)
            val extra = ByteArrayOutputStream()
            val x = DataOutputStream(extra)
            x.writeInt(0) // layer mask
            x.writeInt(0) // blending ranges
            val nameBytes = e.name.toByteArray(Charsets.ISO_8859_1).take(255).toByteArray()
            x.writeByte(nameBytes.size)
            x.write(nameBytes)
            val padded = (1 + nameBytes.size + 3) / 4 * 4
            repeat(padded - 1 - nameBytes.size) { x.writeByte(0) }
            // Unicode name, so non-Latin names survive.
            val uni = ByteArrayOutputStream()
            val u = DataOutputStream(uni)
            u.writeInt(e.name.length)
            for (ch in e.name) u.writeChar(ch.code)
            if (uni.size() % 2 != 0) u.writeByte(0)
            x.writeBytes("8BIM")
            x.writeBytes("luni")
            x.writeInt(uni.size())
            x.write(uni.toByteArray())
            r.writeInt(extra.size())
            r.write(extra.toByteArray())
            for (c in channelData) im.write(c)
        }
        val total = ByteArrayOutputStream()
        total.write(records.toByteArray())
        total.write(images.toByteArray())
        if (total.size() % 2 != 0) total.write(0)
        return total.toByteArray()
    }

    /** One channel of a layer: compression = 1, row byte counts, then PackBits rows. */
    private fun channelRle(px: IntArray, w: Int, h: Int, shift: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val d = DataOutputStream(out)
        d.writeShort(1)
        if (w == 0 || h == 0) return out.toByteArray()
        val rows = Array(h) { y -> packBits(ByteArray(w) { x -> (px[y * w + x] ushr shift).toByte() }) }
        for (row in rows) d.writeShort(row.size)
        for (row in rows) d.write(row)
        return out.toByteArray()
    }

    private fun writeImageRle(o: DataOutputStream, px: IntArray, w: Int, h: Int, channels: IntArray) {
        o.writeShort(1)
        val rows = channels.map { shift -> Array(h) { y -> packBits(ByteArray(w) { x -> (px[y * w + x] ushr shift).toByte() }) } }
        for (c in rows) for (row in c) o.writeShort(row.size)
        for (c in rows) for (row in c) o.write(row)
    }

    /** Apple PackBits run-length encoding. */
    fun packBits(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(src.size / 2 + 8)
        var i = 0
        val n = src.size
        while (i < n) {
            var run = 1
            while (i + run < n && run < 128 && src[i + run] == src[i]) run++
            if (run >= 3) {
                out.write(-(run - 1) and 0xFF)
                out.write(src[i].toInt())
                i += run
            } else {
                val start = i
                var len = 0
                while (i < n && len < 128) {
                    if (i + 2 < n && src[i] == src[i + 1] && src[i] == src[i + 2]) break
                    i++
                    len++
                }
                out.write(len - 1)
                out.write(src, start, len)
            }
        }
        return out.toByteArray()
    }
}
