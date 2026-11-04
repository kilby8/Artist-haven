package com.artisthaven.engine.document

import com.artisthaven.engine.raster.Pixels
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** Layer and brush blend modes, matching the names artists know from Procreate and Photoshop. */
enum class BlendMode(val displayName: String, val psdKey: String) {
    NORMAL("Normal", "norm"),
    MULTIPLY("Multiply", "mul "),
    SCREEN("Screen", "scrn"),
    OVERLAY("Overlay", "over"),
    DARKEN("Darken", "dark"),
    LIGHTEN("Lighten", "lite"),
    COLOR_DODGE("Colour Dodge", "div "),
    COLOR_BURN("Colour Burn", "idiv"),
    ADD("Add", "lddg"),
    HARD_LIGHT("Hard Light", "hLit"),
    SOFT_LIGHT("Soft Light", "sLit"),
    DIFFERENCE("Difference", "diff"),
    EXCLUSION("Exclusion", "smud"),
    ;

    /** Blends one straight colour channel: [d] is the backdrop, [s] the source, both 0..1. */
    fun channel(d: Float, s: Float): Float = when (this) {
        NORMAL -> s
        MULTIPLY -> d * s
        SCREEN -> d + s - d * s
        OVERLAY -> if (d <= 0.5f) 2f * d * s else 1f - 2f * (1f - d) * (1f - s)
        DARKEN -> min(d, s)
        LIGHTEN -> maxOf(d, s)
        COLOR_DODGE -> if (d == 0f) 0f else if (s >= 1f) 1f else min(1f, d / (1f - s))
        COLOR_BURN -> if (d >= 1f) 1f else if (s <= 0f) 0f else 1f - min(1f, (1f - d) / s)
        ADD -> min(1f, d + s)
        HARD_LIGHT -> if (s <= 0.5f) 2f * d * s else 1f - 2f * (1f - d) * (1f - s)
        SOFT_LIGHT -> if (s <= 0.5f) {
            d - (1f - 2f * s) * d * (1f - d)
        } else {
            val g = if (d <= 0.25f) ((16f * d - 12f) * d + 4f) * d else sqrt(d)
            d + (2f * s - 1f) * (g - d)
        }
        DIFFERENCE -> abs(d - s)
        EXCLUSION -> d + s - 2f * d * s
    }

    /**
     * Composites premultiplied [src] over premultiplied [dst] using this mode, with the W3C
     * separable blending formula. [opacity] scales the source first.
     */
    fun blend(dst: Int, src: Int, opacity: Float = 1f): Int {
        val s = if (opacity < 1f) Pixels.scale(src, opacity) else src
        val sa255 = Pixels.alpha(s)
        if (sa255 == 0) return dst
        if (this == NORMAL) return Pixels.srcOver(dst, s)
        val da255 = Pixels.alpha(dst)
        if (da255 == 0) return s
        val sa = sa255 / 255f
        val da = da255 / 255f
        val outA = sa + da - sa * da
        // Straight colours for the blend function.
        val sr = Pixels.red(s) / 255f / sa
        val sg = Pixels.green(s) / 255f / sa
        val sb = Pixels.blue(s) / 255f / sa
        val dr = Pixels.red(dst) / 255f / da
        val dg = Pixels.green(dst) / 255f / da
        val db = Pixels.blue(dst) / 255f / da
        val both = sa * da
        val r = (1f - sa) * dr * da + (1f - da) * sr * sa + both * channel(dr, sr)
        val g = (1f - sa) * dg * da + (1f - da) * sg * sa + both * channel(dg, sg)
        val b = (1f - sa) * db * da + (1f - da) * sb * sa + both * channel(db, sb)
        return Pixels.pack(
            (outA * 255f + 0.5f).toInt(),
            (r * 255f + 0.5f).toInt(),
            (g * 255f + 0.5f).toInt(),
            (b * 255f + 0.5f).toInt(),
        )
    }

    companion object {
        fun fromName(name: String?): BlendMode = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
