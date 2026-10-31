package com.artisthaven.engine.raster

/**
 * Helpers for 32-bit ARGB pixels.
 *
 * Layer pixels are stored premultiplied (colour channels already scaled by alpha), which keeps
 * compositing and smudging correct at soft edges. Colours handed in from the UI are straight
 * (non-premultiplied) ARGB.
 */
object Pixels {
    const val TRANSPARENT = 0

    fun alpha(c: Int): Int = c ushr 24
    fun red(c: Int): Int = (c shr 16) and 0xFF
    fun green(c: Int): Int = (c shr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF

    fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Straight ARGB to premultiplied ARGB. */
    fun premultiply(c: Int): Int {
        val a = alpha(c)
        if (a == 255) return c
        if (a == 0) return 0
        return pack(a, mulDiv255(red(c), a), mulDiv255(green(c), a), mulDiv255(blue(c), a))
    }

    /** Premultiplied ARGB to straight ARGB. */
    fun unpremultiply(c: Int): Int {
        val a = alpha(c)
        if (a == 255) return c
        if (a == 0) return 0
        return pack(
            a,
            (red(c) * 255 + a / 2) / a,
            (green(c) * 255 + a / 2) / a,
            (blue(c) * 255 + a / 2) / a,
        )
    }

    /** Porter-Duff source-over for premultiplied pixels. */
    fun srcOver(dst: Int, src: Int): Int {
        val sa = alpha(src)
        if (sa == 255) return src
        if (sa == 0) return dst
        val inv = 255 - sa
        return pack(
            sa + mulDiv255(alpha(dst), inv),
            red(src) + mulDiv255(red(dst), inv),
            green(src) + mulDiv255(green(dst), inv),
            blue(src) + mulDiv255(blue(dst), inv),
        )
    }

    /** Scales every channel of a premultiplied pixel by [f] in 0..1. */
    fun scale(c: Int, f: Float): Int {
        if (f >= 1f) return c
        if (f <= 0f) return 0
        val k = (f * 256f).toInt()
        return pack(
            (alpha(c) * k) shr 8,
            (red(c) * k) shr 8,
            (green(c) * k) shr 8,
            (blue(c) * k) shr 8,
        )
    }

    /** Linear interpolation between two premultiplied pixels, t in 0..1. */
    fun lerp(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val k = (t * 256f).toInt()
        val ik = 256 - k
        return pack(
            (alpha(a) * ik + alpha(b) * k) shr 8,
            (red(a) * ik + red(b) * k) shr 8,
            (green(a) * ik + green(b) * k) shr 8,
            (blue(a) * ik + blue(b) * k) shr 8,
        )
    }

    fun mulDiv255(a: Int, b: Int): Int {
        val t = a * b + 128
        return (t + (t shr 8)) shr 8
    }

    /** Distance between two straight colours, 0 (same) to 1 (opposite), alpha included. */
    fun distance(a: Int, b: Int): Float {
        val dr = (red(a) - red(b)) / 255f
        val dg = (green(a) - green(b)) / 255f
        val db = (blue(a) - blue(b)) / 255f
        val da = (alpha(a) - alpha(b)) / 255f
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db + da * da) / 4f)
    }
}
