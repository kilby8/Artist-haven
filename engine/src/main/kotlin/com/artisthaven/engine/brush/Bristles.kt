package com.artisthaven.engine.brush

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A row of bristles across a flat brush. Each bristle leaves its own track, carries its own amount
 * of paint and runs dry at its own rate, so long strokes break up into dry-brush streaks the way a
 * real loaded brush does.
 *
 * Before each dab, [prepare] works out a profile across the brush width ([lanes] values for v from
 * -1 to 1): how much paint lands there, the colour shift of the bristles there ([tint]) and the
 * slope of the paint ridges for impasto shading ([slope]).
 */
class Bristles(seed: Int, private val brush: BrushPreset) {
    private val count = 30
    private val rnd = Random(seed * 31 + 7)
    private val pos = FloatArray(count) { i -> -0.95f + 1.9f * (i + 0.5f + (rnd.nextFloat() - 0.5f) * 0.9f) / count }
    private val width = FloatArray(count) { 0.035f + 0.04f * rnd.nextFloat() }
    // Bristles at the edges of the brush hold less paint than those in the middle.
    private val load = FloatArray(count) { i -> (0.85f + 0.35f * rnd.nextFloat()) * (1f - 0.3f * pos[i] * pos[i]) }
    private val rate = FloatArray(count) { 0.5f + 1.2f * rnd.nextFloat() * rnd.nextFloat() + 0.3f * rnd.nextFloat() }
    private val tone = FloatArray(count) { rnd.nextFloat() * 2f - 1f }
    private val phase = FloatArray(count) { rnd.nextFloat() * 6.28f }

    val lanes = 128
    val cover = FloatArray(lanes)
    /** Brightness shift of the paint in each lane, from bristle to bristle colour variation. */
    val tint = FloatArray(lanes)
    /** Slope of the paint surface across the brush, for impasto lighting. */
    val slope = FloatArray(lanes)

    /** Builds the profile for a dab [distance] pixels into a stroke of a brush [size] pixels wide. */
    fun prepare(distance: Float, size: Float) {
        cover.fill(0f)
        val tones = FloatArray(lanes)
        val weight = FloatArray(lanes)
        val widths = distance / max(size, 4f)
        for (i in 0 until count) {
            // Paint left on this bristle; a little above 1 still paints solid.
            val paint = (load[i] - widths * rate[i] * brush.dryOut * 0.075f).coerceIn(0f, 1.2f)
            if (paint <= 0f) continue
            // Bristles wander slightly as the brush flexes.
            val c = pos[i] + 0.012f * sin(widths * 1.7f + phase[i])
            val w = width[i]
            val j0 = ((c - w * 1.6f + 1f) * 0.5f * (lanes - 1)).toInt().coerceAtLeast(0)
            val j1 = ((c + w * 1.6f + 1f) * 0.5f * (lanes - 1)).toInt().coerceAtMost(lanes - 1)
            for (j in j0..j1) {
                val v = j / (lanes - 1f) * 2f - 1f
                val d = (v - c) / w
                val profile = max(0f, 1f - d * d * 0.45f)
                if (profile <= 0f) continue
                // Each spot takes paint from the fullest bristle touching it, so a bristle that runs
                // dry leaves a gap even where its neighbours overlap it.
                val amount = min(1f, paint * profile)
                if (amount > cover[j]) cover[j] = amount
                tones[j] += tone[i] * amount
                weight[j] += amount
            }
        }
        for (j in 0 until lanes) {
            val w = weight[j]
            tones[j] = if (w > 0f) tones[j] / w else 0f
        }
        // Ridge slope across the brush, for impasto light and shade.
        for (j in 0 until lanes) {
            val a = cover[max(0, j - 1)]
            val b = cover[min(lanes - 1, j + 1)]
            tint[j] = brush.streakColour * 0.2f * tones[j]
            slope[j] = (b - a) * 0.5f
        }
        // Soften the ridges a little so they read as paint, not lines.
        for (j in 1 until lanes - 1) {
            slope[j] = (slope[j - 1] + slope[j] * 2f + slope[j + 1]) * 0.25f
        }
    }

    /** Paint amount at position [v] across the brush, -1..1. */
    fun coverAt(v: Float): Float {
        val f = (v + 1f) * 0.5f * (lanes - 1)
        if (f < 0f || f > lanes - 1) return 0f
        val j = f.toInt().coerceAtMost(lanes - 2)
        val t = f - j
        return cover[j] + (cover[j + 1] - cover[j]) * t
    }

    fun laneOf(v: Float): Int = ((v + 1f) * 0.5f * (lanes - 1) + 0.5f).toInt().coerceIn(0, lanes - 1)
}
