package com.artisthaven.engine.brush

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A square greyscale mask, values 0..1, sampled with bilinear filtering. */
class Mask(val size: Int, val data: FloatArray) {
    /** Samples at normalised coordinates u, v in 0..1 (clamped). */
    fun sample(u: Float, v: Float): Float {
        val fx = u * (size - 1)
        val fy = v * (size - 1)
        if (fx < 0f || fy < 0f || fx > size - 1 || fy > size - 1) return 0f
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, size - 1)
        val y1 = minOf(y0 + 1, size - 1)
        val tx = fx - x0
        val ty = fy - y0
        val a = data[y0 * size + x0]
        val b = data[y0 * size + x1]
        val c = data[y1 * size + x0]
        val d = data[y1 * size + x1]
        return (a + (b - a) * tx) * (1f - ty) + (c + (d - c) * tx) * ty
    }

    /** Samples with wrap-around, for tileable grain. */
    fun sampleWrapped(x: Float, y: Float): Float {
        val fx = x - floor(x / size) * size
        val fy = y - floor(y / size) * size
        val x0 = fx.toInt() % size
        val y0 = fy.toInt() % size
        val x1 = (x0 + 1) % size
        val y1 = (y0 + 1) % size
        val tx = fx - floor(fx)
        val ty = fy - floor(fy)
        val a = data[y0 * size + x0]
        val b = data[y0 * size + x1]
        val c = data[y1 * size + x0]
        val d = data[y1 * size + x1]
        return (a + (b - a) * tx) * (1f - ty) + (c + (d - c) * tx) * ty
    }
}

/** Brush tip shapes, generated procedurally so the app ships no image assets. */
enum class TipShape(val displayName: String) {
    ROUND("Round"),
    PENCIL("Pencil"),
    CHARCOAL("Charcoal"),
    BRISTLE("Bristle"),
    SPLATTER("Splatter"),
    WATERCOLOR("Watercolour"),
    GAUSSIAN("Soft"),
}

/** Paper and canvas textures used as brush grain. */
enum class GrainTexture(val displayName: String) {
    NONE("None"),
    PAPER("Cold press paper"),
    FINE("Fine grain"),
    CANVAS("Canvas weave"),
    CHALK("Chalk"),
}

object Textures {
    private const val TIP_SIZE = 128
    private const val GRAIN_SIZE = 256

    private val tipCache = HashMap<Pair<TipShape, Int>, Mask>()
    private val grainCache = HashMap<GrainTexture, Mask>()

    /** Tip mask for a shape; [hardness] (0..1) is quantised so masks can be cached. */
    @Synchronized
    fun tip(shape: TipShape, hardness: Float): Mask {
        val h = (hardness.coerceIn(0f, 1f) * 20f).toInt()
        return tipCache.getOrPut(shape to h) { buildTip(shape, h / 20f) }
    }

    @Synchronized
    fun grain(texture: GrainTexture): Mask? {
        if (texture == GrainTexture.NONE) return null
        return grainCache.getOrPut(texture) { buildGrain(texture) }
    }

    private fun buildTip(shape: TipShape, hardness: Float): Mask {
        val n = TIP_SIZE
        val d = FloatArray(n * n)
        val c = (n - 1) / 2f
        val rnd = Random(shape.ordinal * 7919 + 17)
        when (shape) {
            TipShape.ROUND, TipShape.PENCIL, TipShape.WATERCOLOR, TipShape.GAUSSIAN -> {
                for (y in 0 until n) for (x in 0 until n) {
                    val r = sqrt(((x - c) * (x - c) + (y - c) * (y - c))) / c
                    d[y * n + x] = when (shape) {
                        TipShape.GAUSSIAN -> if (r >= 1f) 0f else exp(-r * r * 4.5f) * (1f - r * r)
                        TipShape.WATERCOLOR -> {
                            // Pigment pools at the rim, like a drying wash.
                            val body = falloff(r, 0.55f)
                            val rim = exp(-((r - 0.86f) * (r - 0.86f)) / 0.006f) * 0.55f
                            (body * 0.55f + rim).coerceAtMost(1f)
                        }
                        else -> falloff(r, hardness)
                    }
                }
                if (shape == TipShape.PENCIL) {
                    // Graphite tooth: speckle the inside of the tip.
                    for (i in d.indices) d[i] *= 0.55f + 0.45f * rnd.nextFloat()
                }
            }
            TipShape.CHARCOAL -> {
                val lobes = FloatArray(16) { 0.75f + 0.25f * rnd.nextFloat() }
                for (y in 0 until n) for (x in 0 until n) {
                    val dx = x - c
                    val dy = y - c
                    val a = ((atan2(dy, dx) + PI) / (2 * PI) * lobes.size).toFloat()
                    val i0 = a.toInt() % lobes.size
                    val i1 = (i0 + 1) % lobes.size
                    val t = a - floor(a)
                    val edge = lobes[i0] * (1 - t) + lobes[i1] * t
                    val r = sqrt(dx * dx + dy * dy) / c / edge
                    d[y * n + x] = falloff(r, 0.5f) * (0.35f + 0.65f * rnd.nextFloat())
                }
            }
            TipShape.BRISTLE -> {
                repeat(42) {
                    val ang = rnd.nextFloat() * 2f * PI.toFloat()
                    val rad = sqrt(rnd.nextFloat()) * 0.8f
                    val bx = c + cos(ang) * rad * c
                    val by = c + sin(ang) * rad * c
                    val br = c * (0.08f + 0.1f * rnd.nextFloat())
                    val strength = 0.5f + 0.5f * rnd.nextFloat()
                    stampDisc(d, n, bx, by, br, strength)
                }
            }
            TipShape.SPLATTER -> {
                repeat(28) {
                    val ang = rnd.nextFloat() * 2f * PI.toFloat()
                    val rad = sqrt(rnd.nextFloat()) * 0.85f
                    val bx = c + cos(ang) * rad * c
                    val by = c + sin(ang) * rad * c
                    val br = c * (0.03f + 0.11f * rnd.nextFloat() * rnd.nextFloat())
                    stampDisc(d, n, bx, by, br, 1f)
                }
            }
        }
        return Mask(n, d)
    }

    /** 1 inside the hard core, smooth falloff to 0 at the rim. */
    private fun falloff(r: Float, hardness: Float): Float {
        if (r >= 1f) return 0f
        val core = hardness.coerceIn(0f, 0.98f)
        if (r <= core) return 1f
        val t = (r - core) / (1f - core)
        val s = 1f - t
        return s * s * (3f - 2f * s)
    }

    private fun stampDisc(d: FloatArray, n: Int, cx: Float, cy: Float, r: Float, strength: Float) {
        val x0 = (cx - r - 1).toInt().coerceAtLeast(0)
        val x1 = (cx + r + 1).toInt().coerceAtMost(n - 1)
        val y0 = (cy - r - 1).toInt().coerceAtLeast(0)
        val y1 = (cy + r + 1).toInt().coerceAtMost(n - 1)
        for (y in y0..y1) for (x in x0..x1) {
            val dist = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
            val v = ((r + 0.5f - dist).coerceIn(0f, 1f)) * strength
            val i = y * n + x
            d[i] = maxOf(d[i], v)
        }
    }

    private fun buildGrain(texture: GrainTexture): Mask {
        val n = GRAIN_SIZE
        val d = FloatArray(n * n)
        when (texture) {
            GrainTexture.PAPER -> fractal(d, n, seed = 11, octaves = 5, base = 8, persistence = 0.55f)
            GrainTexture.FINE -> fractal(d, n, seed = 23, octaves = 3, base = 64, persistence = 0.6f)
            GrainTexture.CHALK -> {
                fractal(d, n, seed = 37, octaves = 4, base = 16, persistence = 0.6f)
                for (i in d.indices) d[i] = ((d[i] - 0.35f) * 2.2f).coerceIn(0f, 1f)
            }
            GrainTexture.CANVAS -> {
                val noise = FloatArray(n * n)
                fractal(noise, n, seed = 5, octaves = 3, base = 32, persistence = 0.5f)
                val period = 8f
                for (y in 0 until n) for (x in 0 until n) {
                    val wx = 0.5f + 0.5f * sin(x / period * PI.toFloat())
                    val wy = 0.5f + 0.5f * sin(y / period * PI.toFloat())
                    val weave = if (((x / period).toInt() + (y / period).toInt()) % 2 == 0) wx else wy
                    d[y * n + x] = (0.35f + 0.5f * weave + 0.25f * (noise[y * n + x] - 0.5f)).coerceIn(0f, 1f)
                }
            }
            GrainTexture.NONE -> d.fill(1f)
        }
        return Mask(n, d)
    }

    /** Tileable fractal value noise, normalised to 0..1. */
    private fun fractal(out: FloatArray, n: Int, seed: Int, octaves: Int, base: Int, persistence: Float) {
        val rnd = Random(seed)
        var amp = 1f
        var cells = base
        repeat(octaves) {
            val lattice = FloatArray(cells * cells) { rnd.nextFloat() }
            for (y in 0 until n) for (x in 0 until n) {
                val fx = x.toFloat() * cells / n
                val fy = y.toFloat() * cells / n
                val x0 = fx.toInt()
                val y0 = fy.toInt()
                val tx = smooth(fx - x0)
                val ty = smooth(fy - y0)
                val x1 = (x0 + 1) % cells
                val y1 = (y0 + 1) % cells
                val a = lattice[y0 * cells + x0]
                val b = lattice[y0 * cells + x1]
                val c = lattice[y1 * cells + x0]
                val dd = lattice[y1 * cells + x1]
                out[y * n + x] += ((a + (b - a) * tx) * (1 - ty) + (c + (dd - c) * tx) * ty) * amp
            }
            amp *= persistence
            cells = minOf(cells * 2, n)
        }
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (v in out) { if (v < lo) lo = v; if (v > hi) hi = v }
        val span = (hi - lo).takeIf { abs(it) > 1e-6f } ?: 1f
        for (i in out.indices) out[i] = (out[i] - lo) / span
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
}
