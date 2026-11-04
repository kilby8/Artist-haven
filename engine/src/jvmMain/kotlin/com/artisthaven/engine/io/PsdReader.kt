package com.artisthaven.engine.io

import com.artisthaven.engine.document.BlendMode
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.document.LayerProps
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.raster.TiledSurface

/**
 * Opens Photoshop files (.psd and .psb) as layered documents: 8 or 16-bit RGB or greyscale, raw
 * or PackBits compressed, with layer names, visibility, opacity, blend modes, clipping masks and
 * layer masks. Layers inside groups come in flat, keeping the group's visibility and opacity.
 * Files without layer data (or with layers we can't read) open as their merged image.
 *
 * The layout follows PhotoCraft's PSD reader (crates/psd), Copyright (c) 2026 ArtCraft Team and
 * the PhotoCraft contributors, MIT licence.
 */
object PsdReader {
    const val MAX_FILE_SIDE = 30000

    class Result(val doc: Document, val warnings: List<String>)

    private class ChannelInfo(val id: Int, val length: Long)

    private class Record(
        val top: Int, val left: Int, val bottom: Int, val right: Int,
        val channels: List<ChannelInfo>,
        val blendKey: String,
        val opacity: Int,
        val clipping: Boolean,
        val hidden: Boolean,
        var name: String,
        val maskTop: Int, val maskLeft: Int, val maskBottom: Int, val maskRight: Int,
        val maskDefault: Int,
        val maskDisabled: Boolean,
        /** 0 normal layer, 1 or 2 group start (open or closed), 3 group end. */
        var section: Int,
        var sectionBlend: String? = null,
    ) {
        val width get() = right - left
        val height get() = bottom - top
        val hasMask get() = maskRight > maskLeft && maskBottom > maskTop
    }

    /**
     * Reads [bytes]. Documents bigger than [maxSide] on their long edge are scaled down to fit,
     * as the canvas can't be larger than that.
     */
    fun read(bytes: ByteArray, maxSide: Int = 4096): Result {
        val r = BinaryReader(bytes)
        if (r.ascii(4) != "8BPS") throw FormatException("not a Photoshop file")
        val version = r.u16()
        if (version != 1 && version != 2) throw FormatException("unsupported Photoshop version $version")
        val psb = version == 2
        r.skip(6)
        val channelCount = r.u16()
        val height = r.i32()
        val width = r.i32()
        val depth = r.u16()
        val mode = r.u16()
        if (width <= 0 || height <= 0 || width > MAX_FILE_SIDE || height > MAX_FILE_SIDE) throw FormatException("bad image size ${width}x$height")
        if (depth != 8 && depth != 16) throw FormatException("only 8 and 16-bit files can be opened (this one is $depth-bit)")
        val grey = when (mode) {
            1 -> true
            3 -> false
            4 -> throw FormatException("CMYK files can't be opened yet; save as RGB in Photoshop first")
            else -> throw FormatException("this colour mode can't be opened; save as RGB first")
        }
        r.skip(r.len32()) // colour mode data
        r.skip(r.len32()) // image resources

        val warnings = ArrayList<String>()
        val scale = minOf(1f, maxSide.toFloat() / maxOf(width, height))
        if (scale < 1f) warnings += "the picture was scaled down to fit $maxSide px"
        val dw = maxOf(1, (width * scale).toInt())
        val dh = maxOf(1, (height * scale).toInt())
        val geom = Geometry(width, height, dw, dh, depth, psb)

        val doc = Document(dw, dh)
        val layerSectionLength = if (psb) r.i64() else (r.i32().toLong() and 0xFFFFFFFFL)
        val layerSectionEnd = r.pos + minOf(layerSectionLength, r.left.toLong()).toInt()
        val layers = runCatching { readLayers(r, layerSectionEnd, geom, grey, doc, warnings) }.getOrElse {
            warnings += "the layers couldn't be read, so the flattened picture was opened instead"
            emptyList()
        }
        r.pos = layerSectionEnd

        if (layers.isEmpty()) {
            val layer = doc.createLayer("Background")
            readMerged(r, geom, channelCount, grey, layer.surface)
            doc.layers += layer
        } else {
            doc.layers += layers
        }
        doc.activeLayerId = doc.layers.last().id
        return Result(doc, warnings.distinct())
    }

    private class Geometry(val width: Int, val height: Int, val dw: Int, val dh: Int, val depth: Int, val psb: Boolean) {
        val scaled get() = dw != width || dh != height
    }

    // ---- Layers ---------------------------------------------------------------------------------

    private fun readLayers(r: BinaryReader, sectionEnd: Int, g: Geometry, grey: Boolean, doc: Document, warnings: MutableList<String>): List<Layer> {
        if (sectionEnd - r.pos < 4) return emptyList()
        val infoLength = if (g.psb) r.i64() else (r.i32().toLong() and 0xFFFFFFFFL)
        if (infoLength == 0L) return emptyList()
        val info = r.sub(minOf(infoLength, (sectionEnd - r.pos).toLong()).toInt())
        val count = Math.abs(info.i16())
        val records = ArrayList<Record>(count)
        repeat(count) { records += readRecord(info, g.psb, warnings) }

        // Channel data follows the records, layer by layer in the same order.
        val out = ArrayList<Layer>()
        // How many groups each layer in [out] is inside, and how many are open now.
        val depths = ArrayList<Int>()
        var open = 0
        for (rec in records) {
            val planes = HashMap<Int, ByteArray>()
            for (ch in rec.channels) {
                val start = info.pos
                val w = if (ch.id == -2 || ch.id == -3) rec.maskRight - rec.maskLeft else rec.width
                val h = if (ch.id == -2 || ch.id == -3) rec.maskBottom - rec.maskTop else rec.height
                val len = minOf(ch.length, info.left.toLong()).toInt()
                if (len >= 2 && w > 0 && h > 0) {
                    runCatching { planes[ch.id] = readPlane(info.sub(len), w, h, g) }
                        .onFailure { warnings += "part of layer \"${rec.name}\" couldn't be read" }
                } else if (len > 0) {
                    info.skip(len)
                }
                info.pos = start + len
            }
            // Groups are read bottom to top, so the end marker of a group comes first.
            when (rec.section) {
                3 -> {
                    open++
                    continue
                }
                1, 2 -> {
                    // The group's own record comes after its layers and carries its properties.
                    if (open > 0) {
                        applyGroup(out, depths, open, rec)
                        open--
                    }
                    continue
                }
            }
            val mode = BlendMode.entries.firstOrNull { it.psdKey == rec.blendKey }
            if (mode == null && rec.blendKey != "norm") warnings += "blend mode '${rec.blendKey.trim()}' isn't available and became Normal"
            val layer = Layer(
                doc.newLayerId(),
                LayerProps(
                    name = rec.name.ifBlank { "Layer ${out.size + 1}" },
                    visible = !rec.hidden,
                    opacity = rec.opacity / 255f,
                    blendMode = mode ?: BlendMode.NORMAL,
                    clipping = rec.clipping,
                ),
                TiledSurface(g.dw, g.dh),
            )
            depths += open
            paintLayer(layer.surface, rec, planes, g, grey)
            val maskPlane = planes[-2]
            if (maskPlane != null && rec.hasMask && !rec.maskDisabled) {
                layer.mask = buildMask(rec, maskPlane, g)
            }
            out += layer
        }
        return out
    }

    /**
     * Applies a closing group's visibility and opacity to the layers inside it: the ones at the
     * end of [layers] that are [depth] or more groups deep.
     */
    private fun applyGroup(layers: List<Layer>, depths: MutableList<Int>, depth: Int, group: Record) {
        for (i in layers.indices.reversed()) {
            if (depths[i] < depth) break
            depths[i] = depth - 1
            val l = layers[i]
            l.props = l.props.copy(
                visible = l.visible && !group.hidden,
                opacity = l.opacity * group.opacity / 255f,
                name = if (group.name.isNotBlank()) "${group.name} / ${l.name}" else l.name,
            )
        }
    }

    private fun readRecord(r: BinaryReader, psb: Boolean, warnings: MutableList<String>): Record {
        val top = r.i32()
        val left = r.i32()
        val bottom = r.i32()
        val right = r.i32()
        val n = r.u16()
        val channels = List(n) {
            val id = r.i16()
            val len = if (psb) r.i64() else (r.i32().toLong() and 0xFFFFFFFFL)
            ChannelInfo(id, len)
        }
        if (r.ascii(4) != "8BIM") throw FormatException("bad layer record")
        val blend = r.ascii(4)
        val opacity = r.u8()
        val clipping = r.u8() != 0
        val flags = r.u8()
        r.u8()
        val extra = r.sub(r.len32())

        var mTop = 0; var mLeft = 0; var mBottom = 0; var mRight = 0; var mDefault = 0; var mDisabled = false
        val maskLen = extra.len32()
        if (maskLen >= 18) {
            val m = extra.sub(maskLen)
            mTop = m.i32(); mLeft = m.i32(); mBottom = m.i32(); mRight = m.i32()
            mDefault = m.u8()
            val mFlags = m.u8()
            mDisabled = mFlags and 2 != 0
        } else {
            extra.skip(maskLen)
        }
        extra.skip(extra.len32()) // blending ranges
        val nameLen = extra.u8()
        var name = String(extra.take(nameLen), Charsets.ISO_8859_1)
        val pad = (4 - (1 + nameLen) % 4) % 4
        extra.skip(minOf(pad, extra.left))

        var section = 0
        var sectionBlend: String? = null
        while (extra.left >= 12) {
            val sig = extra.ascii(4)
            if (sig != "8BIM" && sig != "8B64") break
            val key = extra.ascii(4)
            val long = psb && key in PSB_LONG_KEYS
            val len = if (long) extra.i64().toInt() else extra.len32()
            val block = extra.sub(minOf(len, extra.left))
            when (key) {
                "luni" -> runCatching { name = block.unicode() }
                "lsct", "lsdk" -> runCatching {
                    section = block.i32()
                    if (block.left >= 8 && block.ascii(4) == "8BIM") sectionBlend = block.ascii(4)
                }
                "SoLd", "PlLd", "SoLE" -> warnings += "smart objects open as plain pixels"
                "TySh" -> warnings += "text layers open as plain pixels"
            }
            // Blocks are padded to an even length (to 4 in PSB).
            if (len % 2 == 1 && extra.left > 0 && extra.bytes[extra.pos] == 0.toByte()) extra.skip(1)
        }
        return Record(
            top, left, bottom, right, channels, blend, opacity, clipping, hidden = flags and 2 != 0,
            name = name, maskTop = mTop, maskLeft = mLeft, maskBottom = mBottom, maskRight = mRight,
            maskDefault = mDefault, maskDisabled = mDisabled, section = section, sectionBlend = sectionBlend,
        )
    }

    private val PSB_LONG_KEYS = setOf("LMsk", "Lr16", "Lr32", "Layr", "Mt16", "Mt32", "Mtrn", "Alph", "FMsk", "lnk2", "FEid", "FXid", "PxSD")

    /** One channel of a layer: a compression code, then the samples; returns w x h bytes. */
    private fun readPlane(r: BinaryReader, w: Int, h: Int, g: Geometry): ByteArray {
        val compression = r.u16()
        return decodePlanes(r, compression, 1, w, h, g)[0]
    }

    /** Decodes [planes] planes of w x h samples to 8 bits per sample. */
    private fun decodePlanes(r: BinaryReader, compression: Int, planes: Int, w: Int, h: Int, g: Geometry): List<ByteArray> {
        val bpp = g.depth / 8
        val rowBytes = w * bpp
        val raw = List(planes) { ByteArray(rowBytes * h) }
        when (compression) {
            0 -> for (p in raw) System.arraycopy(r.take(p.size), 0, p, 0, p.size)
            1 -> {
                val counts = IntArray(planes * h) { if (g.psb) r.i32() else r.u16() }
                for ((i, p) in raw.withIndex()) {
                    PackBits.decodeRows(r, counts.copyOfRange(i * h, (i + 1) * h), rowBytes, p, 0)
                }
            }
            2, 3 -> {
                val inflater = java.util.zip.Inflater()
                inflater.setInput(r.bytes, r.pos, r.left)
                for (p in raw) inflater.inflate(p)
                inflater.end()
                if (compression == 3) for (p in raw) unpredict(p, w, h, bpp)
            }
            else -> throw FormatException("unknown compression $compression")
        }
        if (bpp == 1) return raw
        // 16-bit: keep the high byte of each big-endian sample.
        return raw.map { p -> ByteArray(w * h) { p[it * 2] } }
    }

    /** Undoes ZIP-with-prediction's per-row delta encoding. */
    private fun unpredict(p: ByteArray, w: Int, h: Int, bpp: Int) {
        for (y in 0 until h) {
            val row = y * w * bpp
            if (bpp == 1) {
                for (x in 1 until w) p[row + x] = (p[row + x] + p[row + x - 1]).toByte()
            } else {
                var prev = 0
                for (x in 0 until w) {
                    val i = row + x * 2
                    val v = (((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)) + prev and 0xFFFF
                    p[i] = (v shr 8).toByte(); p[i + 1] = v.toByte()
                    prev = v
                }
            }
        }
    }

    private fun paintLayer(surface: TiledSurface, rec: Record, planes: Map<Int, ByteArray>, g: Geometry, grey: Boolean) {
        val w = rec.width
        val h = rec.height
        if (w <= 0 || h <= 0) return
        val red = planes[0] ?: return
        val green = if (grey) red else planes[1] ?: red
        val blue = if (grey) red else planes[2] ?: red
        val alpha = planes[-1]
        blit(surface, rec.left, rec.top, w, h, g) { i ->
            val a = alpha?.get(i)?.toInt()?.and(0xFF) ?: 255
            (a shl 24) or ((red[i].toInt() and 0xFF) shl 16) or ((green[i].toInt() and 0xFF) shl 8) or (blue[i].toInt() and 0xFF)
        }
    }

    /**
     * Writes a w x h block of straight ARGB pixels at (left, top) in file coordinates into
     * [surface], premultiplied, scaling it down with a box filter if the document was scaled.
     */
    private inline fun blit(surface: TiledSurface, left: Int, top: Int, w: Int, h: Int, g: Geometry, pixel: (Int) -> Int) {
        if (!g.scaled) {
            for (y in 0 until h) {
                val dy = top + y
                if (dy < 0 || dy >= g.dh) continue
                for (x in 0 until w) {
                    val dx = left + x
                    if (dx < 0 || dx >= g.dw) continue
                    val c = pixel(y * w + x)
                    if (c ushr 24 != 0) surface.setPixel(dx, dy, Pixels.premultiply(c))
                }
            }
            return
        }
        val sx = g.dw.toFloat() / g.width
        val sy = g.dh.toFloat() / g.height
        val x0 = maxOf(0, (left * sx).toInt())
        val x1 = minOf(g.dw, ((left + w) * sx).toInt() + 1)
        val y0 = maxOf(0, (top * sy).toInt())
        val y1 = minOf(g.dh, ((top + h) * sy).toInt() + 1)
        if (x1 <= x0 || y1 <= y0) return
        val bw = x1 - x0
        // One output row at a time: sums of premultiplied channels and a sample count.
        val acc = LongArray(bw * 5)
        var outY = y0
        for (y in 0 until h) {
            val fy = top + y
            val ty = (fy * sy).toInt()
            if (ty < y0 || fy < 0 || fy >= g.height) continue
            if (ty != outY) {
                flushRow(surface, acc, x0, outY, bw)
                outY = ty
            }
            if (ty >= y1) break
            for (x in 0 until w) {
                val fx = left + x
                if (fx < 0 || fx >= g.width) continue
                val tx = (fx * sx).toInt() - x0
                if (tx < 0 || tx >= bw) continue
                val c = Pixels.premultiply(pixel(y * w + x))
                val k = tx * 5
                acc[k] += (c ushr 24).toLong()
                acc[k + 1] += ((c shr 16) and 0xFF).toLong()
                acc[k + 2] += ((c shr 8) and 0xFF).toLong()
                acc[k + 3] += (c and 0xFF).toLong()
                acc[k + 4]++
            }
        }
        flushRow(surface, acc, x0, outY, bw)
    }

    private fun flushRow(surface: TiledSurface, acc: LongArray, x0: Int, y: Int, bw: Int) {
        for (tx in 0 until bw) {
            val k = tx * 5
            val n = acc[k + 4]
            if (n > 0 && acc[k] > 0) {
                surface.setPixel(
                    x0 + tx, y,
                    Pixels.pack((acc[k] / n).toInt(), (acc[k + 1] / n).toInt(), (acc[k + 2] / n).toInt(), (acc[k + 3] / n).toInt()),
                )
            }
        }
        java.util.Arrays.fill(acc, 0L)
    }

    /**
     * Photoshop masks are white where the layer shows. Ours are paint that hides where it is dark
     * and shows where it is empty or white, so hidden areas become black paint.
     */
    private fun buildMask(rec: Record, plane: ByteArray, g: Geometry): TiledSurface {
        val mask = TiledSurface(g.dw, g.dh)
        val black = 0xFF000000.toInt()
        if (rec.maskDefault < 128) {
            // Everything outside the mask's rectangle is hidden.
            for (y in 0 until g.dh) for (x in 0 until g.dw) mask.setPixel(x, y, black)
        }
        val w = rec.maskRight - rec.maskLeft
        val h = rec.maskBottom - rec.maskTop
        // Clear the rectangle first so the scaled blit can't leave the default showing through.
        if (rec.maskDefault < 128) {
            val sx = g.dw.toFloat() / g.width
            val sy = g.dh.toFloat() / g.height
            for (y in maxOf(0, (rec.maskTop * sy).toInt()) until minOf(g.dh, (rec.maskBottom * sy).toInt())) {
                for (x in maxOf(0, (rec.maskLeft * sx).toInt()) until minOf(g.dw, (rec.maskRight * sx).toInt())) mask.setPixel(x, y, 0)
            }
        }
        blit(mask, rec.maskLeft, rec.maskTop, w, h, g) { i ->
            val v = plane[i].toInt() and 0xFF
            if (v == 255) 0 else black or (v shl 16) or (v shl 8) or v
        }
        return mask
    }

    // ---- Merged image ---------------------------------------------------------------------------

    private fun readMerged(r: BinaryReader, g: Geometry, channels: Int, grey: Boolean, surface: TiledSurface) {
        if (r.left < 2) throw FormatException("the file has no picture data")
        val compression = r.u16()
        val planes = decodePlanes(r, compression, channels, g.width, g.height, g)
        val red = planes[0]
        val green = if (grey) red else planes.getOrElse(1) { red }
        val blue = if (grey) red else planes.getOrElse(2) { red }
        // Extra channels in a flat file are usually saved selections, not transparency.
        blit(surface, 0, 0, g.width, g.height, g) { i ->
            (0xFF shl 24) or ((red[i].toInt() and 0xFF) shl 16) or ((green[i].toInt() and 0xFF) shl 8) or (blue[i].toInt() and 0xFF)
        }
    }
}
