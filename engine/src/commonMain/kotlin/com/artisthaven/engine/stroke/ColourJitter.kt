package com.artisthaven.engine.stroke

import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/** Hue, saturation and brightness variation for colour dynamics. Colours are straight 0..1. */
internal object ColourJitter {
    fun toHsv(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d < 1e-6f -> 0f
            max == r -> ((g - b) / d).let { if (it < 0f) it + 6f else it }
            max == g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
        val s = if (max <= 0f) 0f else d / max
        return floatArrayOf(h, s, max)
    }

    fun toRgb(h: Float, s: Float, v: Float): FloatArray {
        val hh = (h - floor(h)) * 6f
        val c = v * s
        val x = c * (1f - abs(hh % 2f - 1f))
        val m = v - c
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return floatArrayOf(r + m, g + m, b + m)
    }

    /** The colour [hsv] varied at random by up to the given amounts (each 0..1). */
    fun jitter(hsv: FloatArray, hue: Float, sat: Float, bright: Float, rnd: Random): FloatArray {
        val h = hsv[0] + (rnd.nextFloat() * 2f - 1f) * hue * 0.5f
        // Greys have no hue to vary, so hue jitter gives them a little colour.
        val s0 = if (hue > 0f && hsv[1] < 0.05f) 0.35f * hue else hsv[1]
        val s = (s0 + (rnd.nextFloat() * 2f - 1f) * sat).coerceIn(0f, 1f)
        val v = (hsv[2] + (rnd.nextFloat() * 2f - 1f) * bright).coerceIn(0f, 1f)
        return toRgb(h, s, v)
    }
}
