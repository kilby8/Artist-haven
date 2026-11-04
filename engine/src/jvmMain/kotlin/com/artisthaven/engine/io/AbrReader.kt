package com.artisthaven.engine.io

import com.artisthaven.engine.brush.BrushCategory
import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.brush.TipAngle
import com.artisthaven.engine.brush.TipImage
import com.artisthaven.engine.brush.TipShape

/**
 * Reads Photoshop brush files (.abr) into brushes: the legacy v1/v2 layout and the sectioned
 * v6+ layout written by Photoshop 7 onwards.
 *
 * Ported from PhotoCraft's clean-room reader (crates/psd/src/abr.rs and crates/io/src/abr_map.rs),
 * Copyright (c) 2026 ArtCraft Team and the PhotoCraft contributors, MIT licence.
 *
 * Tip shape, size, spacing, angle, roundness and hardness come across, plus pen pressure on size,
 * opacity and flow, size and angle jitter, and scatter. Photoshop features we don't have (dual
 * brush, texture patterns, colour dynamics) are skipped and listed in [Result.warnings].
 */
object AbrReader {
    /** Largest tip edge kept; bigger tips are scaled down. */
    const val MAX_TIP = 256
    private const val MAX_EDGE = 8192
    private const val MAX_BRUSHES = 2000

    class Result(val brushes: List<BrushPreset>, val warnings: List<String>)

    private class Sample(val id: String, val width: Int, val height: Int, val depth: Int, val data: ByteArray)

    fun read(bytes: ByteArray, fileName: String = "Brushes"): Result {
        val r = BinaryReader(bytes)
        val warnings = ArrayList<String>()
        val group = fileName.substringBeforeLast('.').ifBlank { "Brushes" }
        val tag = Integer.toHexString(bytes.contentHashCode())
        val brushes = when (val version = r.u16()) {
            1, 2 -> readLegacy(r, version, group, tag, warnings)
            in 6..10 -> readSectioned(r, group, tag, warnings)
            else -> throw FormatException("unsupported brush file version $version")
        }
        if (brushes.isEmpty()) throw FormatException("the file holds no brushes we can read")
        return Result(brushes, warnings.distinct())
    }

    // ---- v1 / v2 --------------------------------------------------------------------------------

    private fun readLegacy(r: BinaryReader, version: Int, group: String, tag: String, warnings: MutableList<String>): List<BrushPreset> {
        val out = ArrayList<BrushPreset>()
        val count = r.u16()
        for (i in 0 until count) {
            if (r.left < 6 || out.size >= MAX_BRUSHES) break
            val type = r.u16()
            val len = r.len32()
            val b = r.sub(len)
            runCatching {
                when (type) {
                    1 -> {
                        b.i32()
                        val spacing = b.u16()
                        val diameter = b.u16()
                        val roundness = b.u16()
                        val angle = b.i16()
                        val hardness = b.u16()
                        out += preset(
                            "$group ${out.size + 1}", group, tag, out.size, tip = null, diameter = diameter.toFloat(),
                            spacing = spacing / 100f, angle = angle.toFloat(), roundness = roundness / 100f, hardness = hardness / 100f,
                        )
                    }
                    2 -> {
                        b.i32()
                        val spacing = b.u16()
                        val name = if (version == 2) b.unicode() else ""
                        b.u8() // anti-alias
                        b.skip(8) // short bounds
                        val s = sampleBody(b, "")
                        out += preset(
                            name.ifBlank { "$group ${out.size + 1}" }, group, tag, out.size, tip = tipOf(s),
                            diameter = maxOf(s.width, s.height).toFloat(), spacing = spacing / 100f,
                        )
                    }
                    else -> warnings += "an unknown kind of brush was skipped"
                }
            }.onFailure { warnings += "a damaged brush was skipped" }
        }
        return out
    }

    // ---- v6+ ------------------------------------------------------------------------------------

    private fun readSectioned(r: BinaryReader, group: String, tag: String, warnings: MutableList<String>): List<BrushPreset> {
        val subversion = r.u16()
        if (subversion != 1 && subversion != 2) throw FormatException("unsupported brush file subversion $subversion")
        val samples = ArrayList<Sample>()
        var presets: List<Descriptor> = emptyList()
        while (r.left >= 12) {
            if (r.ascii(4) != "8BIM") break
            val key = r.ascii(4)
            val len = minOf(r.i32().toLong() and 0xFFFFFFFFL, r.left.toLong()).toInt()
            val data = r.sub(len)
            // Sections are padded to 4 bytes, but not every writer pads.
            val pad = (4 - len % 4) % 4
            if (pad > 0 && r.left >= pad + 4 && !startsWith8BIM(r, 0) && startsWith8BIM(r, pad)) r.pos += pad
            when (key) {
                "samp" -> readSamples(data, subversion, samples, warnings)
                "desc" -> presets = runCatching {
                    val d = Descriptor.readVersioned(data)
                    (d["Brsh"] as? List<*>)?.filterIsInstance<Descriptor>().orEmpty()
                }.getOrElse {
                    warnings += "brush settings couldn't be read; the tips were imported with default settings"
                    emptyList()
                }
                "patt" -> warnings += "texture patterns aren't supported yet and were skipped"
            }
        }

        val out = ArrayList<BrushPreset>()
        if (presets.isEmpty()) {
            for (s in samples.take(MAX_BRUSHES)) {
                out += preset("$group ${out.size + 1}", group, tag, out.size, tipOf(s), maxOf(s.width, s.height).toFloat(), 0.25f)
            }
            return out
        }
        for (d in presets.take(MAX_BRUSHES)) {
            val name = d.text("Nm  ")?.trim()?.takeIf { it.isNotEmpty() } ?: "$group ${out.size + 1}"
            val b = mapPreset(d, name, group, tag, out.size, samples, warnings)
            if (b == null) warnings += "\"$name\" was skipped: its tip isn't in the file" else out += b
        }
        return out
    }

    private fun startsWith8BIM(r: BinaryReader, offset: Int): Boolean {
        val p = r.pos + offset
        return p + 4 <= r.end && r.bytes[p] == '8'.code.toByte() && r.bytes[p + 1] == 'B'.code.toByte() &&
            r.bytes[p + 2] == 'I'.code.toByte() && r.bytes[p + 3] == 'M'.code.toByte()
    }

    private fun readSamples(r: BinaryReader, subversion: Int, out: MutableList<Sample>, warnings: MutableList<String>) {
        while (r.left >= 4 && out.size < MAX_BRUSHES) {
            val len = r.i32()
            if (len <= 0 || len > r.left) break
            val padded = minOf((len + 3) and 3.inv(), r.left)
            val b = r.sub(padded)
            runCatching {
                val idLen = b.u8()
                val id = b.ascii(idLen)
                b.skip(if (subversion == 1) 10 else 264)
                out += sampleBody(b, id)
            }.onFailure { warnings += "a damaged brush tip was skipped" }
        }
    }

    private fun sampleBody(b: BinaryReader, id: String): Sample {
        val top = b.i32()
        val left = b.i32()
        val bottom = b.i32()
        val right = b.i32()
        val w = right.toLong() - left
        val h = bottom.toLong() - top
        if (w <= 0 || h <= 0 || w > MAX_EDGE || h > MAX_EDGE) throw FormatException("bad tip size ${w}x$h")
        val depth = b.u16()
        if (depth != 8 && depth != 16) throw FormatException("bad tip depth $depth")
        val bpr = w.toInt() * depth / 8
        val data = ByteArray(bpr * h.toInt())
        when (b.u8()) {
            0 -> System.arraycopy(b.take(data.size), 0, data, 0, data.size)
            1 -> {
                val counts = IntArray(h.toInt()) { b.u16() }
                PackBits.decodeRows(b, counts, bpr, data, 0)
            }
            else -> throw FormatException("unknown tip compression")
        }
        return Sample(id, w.toInt(), h.toInt(), depth, data)
    }

    /** Centres a tip in a square and scales it to at most [MAX_TIP] pixels across. */
    private fun tipOf(s: Sample): TipImage {
        val edge = maxOf(s.width, s.height)
        val n = minOf(edge, MAX_TIP)
        val ox = (edge - s.width) / 2
        val oy = (edge - s.height) / 2
        val sum = FloatArray(n * n)
        val weight = FloatArray(n * n)
        val scale = n.toFloat() / edge
        for (y in 0 until s.height) {
            val ty = minOf(n - 1, ((y + oy) * scale).toInt())
            for (x in 0 until s.width) {
                val tx = minOf(n - 1, ((x + ox) * scale).toInt())
                val v = if (s.depth == 16) s.data[(y * s.width + x) * 2].toInt() and 0xFF else s.data[y * s.width + x].toInt() and 0xFF
                sum[ty * n + tx] += v.toFloat()
                weight[ty * n + tx] += 1f
            }
        }
        // Cells that only cover padding are empty; the rest average the pixels that land in them.
        val cell = 1f / (scale * scale)
        val out = ByteArray(n * n) { i -> (sum[i] / maxOf(weight[i], cell)).toInt().coerceIn(0, 255).toByte() }
        return TipImage(n, out)
    }

    private fun mapPreset(
        d: Descriptor,
        name: String,
        group: String,
        tag: String,
        index: Int,
        samples: List<Sample>,
        warnings: MutableList<String>,
    ): BrushPreset? {
        val t = d.obj("Brsh") ?: return preset(name, group, tag, index, null, 30f, 0.25f)
        val sampled = t.classId == "sampledBrush" || t["sampledData"] != null
        var tip: TipImage? = null
        var diameter = t.num("Dmtr")?.toFloat()
        if (sampled) {
            val id = t.text("sampledData").orEmpty()
            val s = samples.firstOrNull { it.id == id } ?: samples.singleOrNull() ?: return null
            tip = tipOf(s)
            if (diameter == null) diameter = maxOf(s.width, s.height).toFloat()
        }
        var b = preset(
            name, group, tag, index, tip, diameter ?: 30f,
            spacing = t.fraction("Spcn")?.toFloat() ?: 0.25f,
            angle = t.num("Angl")?.toFloat() ?: 0f,
            roundness = t.fraction("Rndn")?.toFloat() ?: 1f,
            hardness = if (sampled) 1f else t.fraction("Hrdn")?.toFloat() ?: 1f,
        )

        if (d.bool("useTipDynamics") == true) {
            val size = d.obj("szVr")
            val minSize = d.fraction("minimumDiameter")?.toFloat() ?: size?.fraction("Mnm ")?.toFloat() ?: 0f
            val angle = d.obj("angleDynamics")
            b = b.copy(
                pressureSize = if (control(size) == PEN_PRESSURE) (1f - minSize).coerceIn(0f, 1f) else 0f,
                tiltSize = if (control(size) == PEN_TILT) (1f - minSize).coerceIn(0f, 1f) * 2f else 0f,
                sizeJitter = (size?.fraction("jitter")?.toFloat() ?: 0f).coerceIn(0f, 1f),
                rotationJitter = ((angle?.fraction("jitter")?.toFloat() ?: 0f) * 180f).coerceIn(0f, 180f),
                tipAngle = when (control(angle)) {
                    DIRECTION, INITIAL_DIRECTION -> TipAngle.FOLLOW_STROKE
                    PEN_TILT, ROTATION -> TipAngle.STYLUS
                    else -> b.tipAngle
                },
            )
        }
        if (d.bool("useScatter") == true) {
            val scatter = d.obj("scatterDynamics")?.fraction("jitter")?.toFloat() ?: 0f
            b = b.copy(lateralJitter = (scatter / 4f).coerceIn(0f, 1f))
        }
        if (d.bool("usePaintDynamics") == true) {
            val opacity = d.obj("opVr")
            val flow = d.obj("prVr")
            b = b.copy(
                pressureOpacity = if (control(opacity) == PEN_PRESSURE) 1f - (opacity?.fraction("Mnm ")?.toFloat() ?: 0f) else 0f,
                pressureFlow = if (control(flow) == PEN_PRESSURE) 1f - (flow?.fraction("Mnm ")?.toFloat() ?: 0f) else 0f,
                opacityJitter = (opacity?.fraction("jitter")?.toFloat() ?: 0f).coerceIn(0f, 1f),
            )
        }
        if (d.bool("useTexture") == true) warnings += "texture settings aren't supported yet"
        if (d.bool("useDualBrush") == true) warnings += "dual brush settings aren't supported yet"
        if (d.bool("useColorDynamics") == true) warnings += "colour dynamics aren't supported yet"
        return b
    }

    private const val PEN_PRESSURE = 2
    private const val PEN_TILT = 3
    private const val INITIAL_DIRECTION = 5
    private const val DIRECTION = 6
    private const val ROTATION = 8

    private fun control(d: Descriptor?): Int = d?.num("bVTy")?.toInt() ?: 0

    private fun preset(
        name: String,
        group: String,
        tag: String,
        index: Int,
        tip: TipImage?,
        diameter: Float,
        spacing: Float,
        angle: Float = 0f,
        roundness: Float = 1f,
        hardness: Float = 1f,
    ): BrushPreset {
        val size = diameter.coerceIn(1f, 1000f)
        return BrushPreset(
            id = "abr_${tag}_$index",
            name = name.take(60),
            category = BrushCategory.MINE,
            defaultSize = size.coerceAtMost(400f),
            minSize = 1f,
            maxSize = maxOf(400f, size * 2f).coerceAtMost(1000f),
            tip = TipShape.ROUND,
            tipImage = tip?.encode(),
            hardness = hardness.coerceIn(0f, 1f),
            roundness = roundness.coerceIn(0.05f, 1f),
            // Photoshop measures angles anticlockwise with y up; the canvas has y down.
            tipAngle = TipAngle.FIXED,
            angleDegrees = -angle,
            spacing = spacing.coerceIn(0.01f, 2f),
            streamline = 0.2f,
            pressureSize = 0f,
            importedFrom = group,
        )
    }
}
