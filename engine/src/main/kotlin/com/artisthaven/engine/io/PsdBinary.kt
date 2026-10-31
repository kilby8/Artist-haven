package com.artisthaven.engine.io

/*
 * Shared pieces for reading Adobe binary formats (.psd and .abr): a bounds-checked big-endian
 * reader, PackBits decoding and Photoshop's ActionDescriptor structure.
 *
 * The descriptor layout follows PhotoCraft's reader (crates/psd/src/descriptor.rs),
 * Copyright (c) 2026 ArtCraft Team and the PhotoCraft contributors, MIT licence.
 */

/** Thrown when a file is damaged or isn't the format we expected. */
class FormatException(message: String) : Exception(message)

/** Big-endian reader over a byte array. Every read is bounds-checked. */
class BinaryReader(val bytes: ByteArray, start: Int = 0, end: Int = bytes.size) {
    var pos: Int = start
    val end: Int = end.coerceAtMost(bytes.size)
    val left: Int get() = end - pos

    private fun need(n: Int) {
        if (n < 0 || n > left) throw FormatException("file ends early (needed $n bytes at $pos)")
    }

    fun u8(): Int { need(1); return bytes[pos++].toInt() and 0xFF }
    fun u16(): Int { need(2); val v = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF); pos += 2; return v }
    fun i16(): Int = u16().toShort().toInt()
    fun i32(): Int {
        need(4)
        val v = ((bytes[pos].toInt() and 0xFF) shl 24) or ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 8) or (bytes[pos + 3].toInt() and 0xFF)
        pos += 4
        return v
    }
    /** An unsigned 32-bit length, refused if it can't fit in what is left. */
    fun len32(): Int {
        val v = i32().toLong() and 0xFFFFFFFFL
        if (v > left) throw FormatException("length $v runs past the end of the file")
        return v.toInt()
    }
    fun i64(): Long = (i32().toLong() shl 32) or (i32().toLong() and 0xFFFFFFFFL)
    fun f64(): Double = java.lang.Double.longBitsToDouble(i64())
    fun take(n: Int): ByteArray { need(n); val out = bytes.copyOfRange(pos, pos + n); pos += n; return out }
    fun skip(n: Int) { need(n); pos += n }
    fun ascii(n: Int): String = String(take(n), Charsets.ISO_8859_1)
    /** A sub-reader over the next [n] bytes; this reader moves past them. */
    fun sub(n: Int): BinaryReader { need(n); val r = BinaryReader(bytes, pos, pos + n); pos += n; return r }

    /** A u32 count of UTF-16 units, then the units (Photoshop's "Unicode string"). */
    fun unicode(): String {
        val n = i32()
        if (n < 0 || n.toLong() * 2 > left) throw FormatException("bad text length $n")
        val chars = CharArray(n) { u16().toChar() }
        return String(chars).trimEnd('\u0000')
    }
}

object PackBits {
    /**
     * Decodes [rows] PackBits rows of [width] bytes each, whose packed sizes are in [counts], from
     * [r] into [out] starting at [offset].
     */
    fun decodeRows(r: BinaryReader, counts: IntArray, width: Int, out: ByteArray, offset: Int) {
        var o = offset
        for (count in counts) {
            val row = r.sub(count)
            decodeRow(row, out, o, width)
            o += width
        }
    }

    /** Decodes one packed row; a damaged row is left part-filled rather than failing. */
    fun decodeRow(r: BinaryReader, out: ByteArray, offset: Int, width: Int) {
        var x = 0
        while (x < width && r.left > 0) {
            val n = r.u8().toByte().toInt()
            when {
                n >= 0 -> {
                    val len = minOf(n + 1, width - x, r.left)
                    System.arraycopy(r.bytes, r.pos, out, offset + x, len)
                    r.pos += minOf(n + 1, r.left)
                    x += len
                }
                n != -128 -> {
                    if (r.left == 0) return
                    val v = r.u8().toByte()
                    val len = minOf(1 - n, width - x)
                    java.util.Arrays.fill(out, offset + x, offset + x + len, v)
                    x += len
                }
            }
        }
    }
}

/** A Photoshop ActionDescriptor: a class id and a list of keyed values. */
class Descriptor(val classId: String, val items: List<Pair<String, Any?>>) {
    operator fun get(key: String): Any? = items.firstOrNull { it.first == key }?.second

    fun obj(key: String): Descriptor? = get(key) as? Descriptor
    fun text(key: String): String? = get(key) as? String
    fun bool(key: String): Boolean? = get(key) as? Boolean

    /** A plain number, or a unit value in its own unit (pixels, degrees, percent). */
    fun num(key: String): Double? = when (val v = get(key)) {
        is UnitValue -> v.value
        is Double -> v
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        else -> null
    }

    /** A percentage as 0..1. */
    fun fraction(key: String): Double? = when (val v = get(key)) {
        is UnitValue -> if (v.unit == "#Prc") v.value / 100.0 else v.value
        is Double -> v
        is Int -> v / 100.0
        else -> null
    }

    data class UnitValue(val unit: String, val value: Double)
    data class Enum(val type: String, val value: String)

    companion object {
        private const val MAX_DEPTH = 64

        /** Reads a version-16 descriptor. */
        fun readVersioned(r: BinaryReader): Descriptor {
            val v = r.i32()
            if (v != 16) throw FormatException("descriptor version $v")
            return read(r, 0)
        }

        fun read(r: BinaryReader, depth: Int): Descriptor {
            if (depth > MAX_DEPTH) throw FormatException("descriptor nested too deep")
            r.unicode() // display name
            val classId = id(r)
            val n = r.i32()
            if (n < 0 || n > r.left / 8 + 1) throw FormatException("bad descriptor item count $n")
            val items = ArrayList<Pair<String, Any?>>(minOf(n, 256))
            repeat(n) {
                val key = id(r)
                items += key to value(r, r.ascii(4), depth + 1)
            }
            return Descriptor(classId, items)
        }

        private fun id(r: BinaryReader): String {
            val n = r.i32()
            return if (n == 0) r.ascii(4) else {
                if (n < 0 || n > r.left) throw FormatException("bad id length $n")
                r.ascii(n)
            }
        }

        private fun value(r: BinaryReader, type: String, depth: Int): Any? = when (type) {
            "Objc", "GlbO" -> read(r, depth + 1)
            "VlLs" -> {
                val n = r.i32()
                if (n < 0 || n > r.left / 4 + 1) throw FormatException("bad list length $n")
                List(n) { value(r, r.ascii(4), depth + 1) }
            }
            "doub" -> r.f64()
            "UntF" -> UnitValue(r.ascii(4), r.f64())
            "UnFl" -> {
                val unit = r.ascii(4)
                val n = r.i32()
                if (n < 0 || n.toLong() * 8 > r.left) throw FormatException("bad unit list length $n")
                List(n) { UnitValue(unit, r.f64()) }
            }
            "TEXT" -> r.unicode()
            "enum" -> Enum(id(r), id(r))
            "long" -> r.i32()
            "comp" -> r.i64()
            "bool" -> r.u8() != 0
            "type", "GlbC" -> { r.unicode(); id(r) }
            "alis", "Pth ", "tdta" -> r.take(r.len32())
            "ObAr" -> { r.i32(); read(r, depth + 1) }
            "obj " -> {
                val n = r.i32()
                if (n < 0 || n > r.left / 8 + 1) throw FormatException("bad reference length $n")
                repeat(n) { reference(r) }
                null
            }
            else -> throw FormatException("unknown descriptor value type '$type'")
        }

        private fun reference(r: BinaryReader) {
            when (val t = r.ascii(4)) {
                "prop" -> { r.unicode(); id(r); id(r) }
                "Clss" -> { r.unicode(); id(r) }
                "Enmr" -> { r.unicode(); id(r); id(r); id(r) }
                "rele", "Idnt", "indx" -> { r.unicode(); id(r); r.i32() }
                "name" -> { r.unicode(); id(r); r.unicode() }
                else -> throw FormatException("unknown reference type '$t'")
            }
        }
    }
}
