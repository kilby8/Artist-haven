package com.artisthaven.engine.brush

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Repeating patterns for pattern brushes: 1 is paint, 0 is a gap. They tile seamlessly over
 * [n] pixels (a power of two), so every period here divides it.
 */
internal object Patterns {
    private val TAU = (2 * PI).toFloat()

    fun build(texture: GrainTexture, d: FloatArray, n: Int) {
        val rnd = Random(texture.ordinal * 31337 + 7)
        when (texture) {
            GrainTexture.HALFTONE -> cells(d, n, 8f) { x, y -> aa(0.36f - hypot(x - 0.5f, y - 0.5f), 8f) }
            GrainTexture.DOT_SCREEN -> cells(d, n, 8f) { x, y -> 1f - hypot(x - 0.5f, y - 0.5f) / 0.7071f }
            GrainTexture.POLKA -> cells(d, n, 32f) { x, y ->
                // Offset rows, like printed fabric.
                aa(0.22f - min(hypot(x - 0.25f, y - 0.25f), hypot(x - 0.75f, y - 0.75f)), 32f)
            }
            GrainTexture.STRIPES -> each(d, n) { x, y -> aa(0.25f - abs(frac((x + y) / 16f) - 0.5f), 16f) }
            GrainTexture.CHECKER -> each(d, n) { x, y ->
                if (((floor(x / 16f) + floor(y / 16f)).toInt() and 1) == 0) 1f else 0f
            }
            GrainTexture.CROSSHATCH -> each(d, n) { x, y ->
                val a = aa(0.12f - abs(frac((x + y) / 8f) - 0.5f), 8f)
                val b = aa(0.12f - abs(frac((x - y) / 8f) - 0.5f), 8f)
                maxOf(a, b)
            }
            GrainTexture.BRICK -> each(d, n) { x, y ->
                val row = floor(y / 16f)
                val bx = frac((x + if (row.toInt() and 1 == 1) 16f else 0f) / 32f)
                val by = frac(y / 16f)
                val mortar = min(min(bx, 1f - bx) * 32f, min(by, 1f - by) * 16f)
                ((mortar - 1f) / 1.2f).coerceIn(0f, 1f)
            }
            GrainTexture.HONEYCOMB -> each(d, n) { x, y ->
                // Walls between cells of a staggered grid, which are hexagons.
                var f1 = 1e9f; var f2 = 1e9f
                val j0 = floor(y / 32f + 0.5f).toInt()
                for (j in j0 - 1..j0 + 1) {
                    val off = if (j and 1 == 1) 16f else 0f
                    val i0 = floor((x - off) / 32f + 0.5f).toInt()
                    for (i in i0 - 1..i0 + 1) {
                        val dist = hypot(x - (i * 32f + off), (y - j * 32f) * 1.15f)
                        if (dist < f1) { f2 = f1; f1 = dist } else if (dist < f2) f2 = dist
                    }
                }
                1f - ((f2 - f1 - 2f) / 1.5f).coerceIn(0f, 1f)
            }
            GrainTexture.SCALES -> each(d, n) { x, y ->
                val row = floor(y / 16f)
                val ox = if (row.toInt() and 1 == 1) 16f else 0f
                val cx = frac((x + ox) / 32f) * 32f - 16f
                val cy = frac(y / 16f) * 16f
                val r = hypot(cx, cy)
                if (r < 15f && r > 12.5f) 1f else if (r < 12.5f) 0.15f else 0f
            }
            GrainTexture.WAVES -> each(d, n) { x, y ->
                val w = y + 5f * sin(x / n * TAU * 8f)
                aa(0.22f - abs(frac(w / 16f) - 0.5f), 16f)
            }
            GrainTexture.CHEVRON -> each(d, n) { x, y ->
                val w = y + abs(frac(x / 32f) - 0.5f) * 32f
                aa(0.22f - abs(frac(w / 16f) - 0.5f), 16f)
            }
            GrainTexture.PLAID -> each(d, n) { x, y ->
                val a = band(x, 64f, 0.18f) * 0.7f + band(x + 24f, 64f, 0.06f) * 0.3f
                val b = band(y, 64f, 0.18f) * 0.7f + band(y + 24f, 64f, 0.06f) * 0.3f
                val twill = if (((x + y).toInt() / 2) and 1 == 0) 1f else 0.75f
                ((a + b) * twill).coerceIn(0f, 1f) * 0.85f + 0.15f
            }
            GrainTexture.TWILL -> {
                val noise = FloatArray(n * n)
                Textures.fractal(noise, n, seed = 71, octaves = 3, base = 32, persistence = 0.5f)
                each(d, n) { x, y ->
                    val diag = aa(0.3f - abs(frac((x + y * 0.5f) / 4f) - 0.5f), 4f)
                    (0.35f + 0.55f * diag + 0.3f * (noise[y.toInt() * n + x.toInt()] - 0.5f)).coerceIn(0f, 1f)
                }
            }
            GrainTexture.WOOD -> {
                val noise = FloatArray(n * n)
                Textures.fractal(noise, n, seed = 73, octaves = 4, base = 4, persistence = 0.55f)
                each(d, n) { x, y ->
                    val t = x / n * 12f + noise[y.toInt() * n + x.toInt()] * 3f
                    val ring = 0.5f + 0.5f * sin(t * TAU)
                    (ring * ring * ring * 1.2f).coerceIn(0f, 1f)
                }
            }
            GrainTexture.MARBLE -> {
                val noise = FloatArray(n * n)
                Textures.fractal(noise, n, seed = 79, octaves = 5, base = 4, persistence = 0.6f)
                each(d, n) { x, y ->
                    val t = (x + y) / n * 3f + noise[y.toInt() * n + x.toInt()] * 4f
                    val vein = 1f - abs(sin(t * PI.toFloat()))
                    (vein * vein * vein * vein * 1.3f).coerceIn(0f, 1f)
                }
            }
            GrainTexture.CRACKLE, GrainTexture.LEOPARD -> {
                val cellsPerSide = if (texture == GrainTexture.CRACKLE) 10 else 8
                val pts = FloatArray(cellsPerSide * cellsPerSide * 2) { rnd.nextFloat() }
                val cell = n.toFloat() / cellsPerSide
                each(d, n) { x, y ->
                    val gx = (x / cell).toInt(); val gy = (y / cell).toInt()
                    var f1 = 1e9f; var f2 = 1e9f
                    for (oy in -1..1) for (ox in -1..1) {
                        val cx = gx + ox; val cy = gy + oy
                        val wx = (cx + cellsPerSide) % cellsPerSide; val wy = (cy + cellsPerSide) % cellsPerSide
                        val k = (wy * cellsPerSide + wx) * 2
                        val px = (cx + pts[k]) * cell; val py = (cy + pts[k + 1]) * cell
                        val dist = hypot(x - px, y - py)
                        if (dist < f1) { f2 = f1; f1 = dist } else if (dist < f2) f2 = dist
                    }
                    if (texture == GrainTexture.CRACKLE) {
                        // Thin dark cracks where two cells meet.
                        ((f2 - f1 - 1f) / 1.5f).coerceIn(0f, 1f).let { 1f - it }
                    } else {
                        // Rosettes: a ring around each spot with a lighter centre.
                        val r = f1 / cell
                        when {
                            r < 0.17f -> 0.35f
                            r < 0.3f -> 1f
                            else -> 0f
                        }
                    }
                }
            }
            GrainTexture.STARFIELD -> {
                d.fill(0f)
                repeat(170) {
                    val cx = rnd.nextFloat() * n; val cy = rnd.nextFloat() * n
                    val r = 2.5f + 5f * rnd.nextFloat() * rnd.nextFloat()
                    for (yy in (cy - r - 1).toInt()..(cy + r + 1).toInt()) for (xx in (cx - r - 1).toInt()..(cx + r + 1).toInt()) {
                        val dx = abs(xx + 0.5f - cx); val dy = abs(yy + 0.5f - cy)
                        // A little four-pointed star.
                        val v = (1f - (sqrt(dx) + sqrt(dy)) / sqrt(r) / 1.1f).coerceIn(0f, 1f)
                        val i = ((yy % n + n) % n) * n + ((xx % n + n) % n)
                        if (v > d[i]) d[i] = min(1f, v * 2.5f)
                    }
                }
            }
            GrainTexture.STATIC -> for (i in d.indices) d[i] = if (rnd.nextFloat() < 0.45f) 1f else 0f
            else -> d.fill(1f)
        }
    }

    private inline fun each(d: FloatArray, n: Int, f: (Float, Float) -> Float) {
        for (y in 0 until n) for (x in 0 until n) d[y * n + x] = f(x + 0.5f, y + 0.5f).coerceIn(0f, 1f)
    }

    /** Fills square cells of [size] pixels; [f] gets the position inside the cell, 0..1. */
    private inline fun cells(d: FloatArray, n: Int, size: Float, f: (Float, Float) -> Float) {
        each(d, n) { x, y -> f(frac(x / size), frac(y / size)) }
    }

    private fun frac(x: Float) = x - floor(x)

    /** Anti-aliased step: [dist] is in cell units, [cell] the cell size in pixels. */
    private fun aa(dist: Float, cell: Float) = (dist * cell + 0.5f).coerceIn(0f, 1f)

    private fun band(x: Float, period: Float, width: Float) =
        if (abs(frac(x / period) - 0.5f) < width) 1f else 0f

}
