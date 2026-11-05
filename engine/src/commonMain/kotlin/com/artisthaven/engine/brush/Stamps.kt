package com.artisthaven.engine.brush

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Stamp tips: shapes drawn whole at each dab, like Photoshop's brush tips or Procreate's stamp
 * brushes. Every shape is generated here from geometry, so no artwork is shipped or copied.
 * u runs along the stroke (or to the right for a fixed angle), v across it (or down), both -1..1.
 */
internal object Stamps {
    fun build(shape: TipShape, n: Int, hardness: Float): Mask {
        val f = shapeFunction(shape)
        val d = FloatArray(n * n)
        val ss = 4
        for (y in 0 until n) for (x in 0 until n) {
            var acc = 0f
            for (sy in 0 until ss) for (sx in 0 until ss) {
                val u = ((x + (sx + 0.5f) / ss) / n) * 2f - 1f
                val v = ((y + (sy + 0.5f) / ss) / n) * 2f - 1f
                acc += f(u, v)
            }
            d[y * n + x] = (acc / (ss * ss)).coerceIn(0f, 1f)
        }
        val blur = ((1f - hardness.coerceIn(0f, 1f)) * 14f).toInt()
        if (blur > 0) {
            boxBlur(d, n, blur)
            boxBlur(d, n, blur)
        }
        // Keep the very edge clear so rotated stamps don't show a cut-off square.
        for (i in 0 until n) {
            d[i] = 0f; d[(n - 1) * n + i] = 0f; d[i * n] = 0f; d[i * n + n - 1] = 0f
        }
        return Mask(n, d)
    }

    private fun shapeFunction(shape: TipShape): (Float, Float) -> Float {
        val rnd = Random(shape.ordinal * 104729 + 3)
        return when (shape) {
            TipShape.SQUARE -> { u, v -> if (abs(u) < 0.86f && abs(v) < 0.86f) 1f else 0f }
            TipShape.TRIANGLE -> {
                val poly = regularPolygon(3, 0.95f, -PI.toFloat() / 2f, cy = 0.15f)
                return { u, v -> if (inPolygon(poly, u, v)) 1f else 0f }
            }
            TipShape.STAR -> {
                val poly = starPolygon(5, 0.95f, 0.4f, -PI.toFloat() / 2f, cy = 0.06f)
                return { u, v -> if (inPolygon(poly, u, v)) 1f else 0f }
            }
            TipShape.SPARKLE -> { u, v ->
                // A four-pointed glint with a soft glow in the middle.
                val au = abs(u); val av = abs(v)
                val arms = if (sqrt(au) + sqrt(av) < 0.95f) 1f else 0f
                val glow = (1f - hypot(u, v) / 0.45f).coerceIn(0f, 1f)
                max(arms, glow * glow)
            }
            TipShape.HEART -> { u, v ->
                val x = u * 1.22f
                val y = -v * 1.22f + 0.18f
                val a = x * x + y * y - 1f
                if (a * a * a - x * x * y * y * y <= 0f) 1f else 0f
            }
            TipShape.LEAF -> { u, v -> leaf(u, v, 0.48f) }
            TipShape.FLOWER -> { u, v ->
                val r = hypot(u, v)
                val a = atan2(v, u)
                val petal = 0.5f + 0.42f * abs(cos(2.5f * a))
                when {
                    r < 0.2f -> 0.75f
                    r < 0.24f -> 0.35f
                    r < petal -> 1f
                    else -> 0f
                }
            }
            TipShape.FOLIAGE -> {
                // A clump of small leaves pointing every way, for trees and bushes.
                val leaves = List(18) {
                    val a = rnd.nextFloat() * 2f * PI.toFloat()
                    val dist = sqrt(rnd.nextFloat()) * 0.5f
                    // Leaves point roughly away from the middle of the clump.
                    floatArrayOf(cos(a) * dist, sin(a) * dist, a + (rnd.nextFloat() - 0.5f) * 1.2f, 0.42f + 0.18f * rnd.nextFloat(), 0.65f + 0.35f * rnd.nextFloat())
                }
                return { u, v ->
                    var m = 0f
                    for (l in leaves) {
                        val dx = u - l[0]; val dy = v - l[1]
                        val c = cos(l[2]); val s = sin(l[2])
                        val lu = (dx * c + dy * s) / l[3]
                        val lv = (-dx * s + dy * c) / l[3]
                        val k = leaf(lv, lu, 0.45f) * l[4]
                        if (k > m) m = k
                    }
                    m
                }
            }
            TipShape.GRASS -> {
                val blades = List(9) {
                    floatArrayOf(
                        -0.6f + 1.2f * rnd.nextFloat(), // base x
                        (rnd.nextFloat() - 0.5f) * 0.9f, // lean
                        0.9f + 0.9f * rnd.nextFloat(), // height
                        0.05f + 0.04f * rnd.nextFloat(), // width at the root
                    )
                }
                return { u, v ->
                    var m = 0f
                    for (b in blades) {
                        val t = (0.95f - v) / b[2]
                        if (t < 0f || t > 1f) continue
                        val cx = b[0] + b[1] * t * t
                        if (abs(u - cx) < b[3] * (1f - t)) m = 1f
                    }
                    m
                }
            }
            TipShape.HAIR -> {
                // Fine strands side by side across the stroke; dragged, they leave parallel hairs.
                val strands = List(22) { floatArrayOf(-0.9f + 1.8f * rnd.nextFloat(), 0.012f + 0.02f * rnd.nextFloat(), 0.45f + 0.55f * rnd.nextFloat()) }
                return { u, v ->
                    var m = 0f
                    if (abs(u) < 0.06f) for (s in strands) if (abs(v - s[0]) < s[1] && s[2] > m) m = s[2]
                    m
                }
            }
            TipShape.RAKE -> { u, v ->
                var m = 0f
                for (k in 0 until 6) {
                    val cy = -0.8f + k * 0.32f
                    if (hypot(u, v - cy) < 0.11f) m = 1f
                }
                m
            }
            TipShape.CLOUD -> {
                val puffs = listOf(
                    floatArrayOf(-0.45f, 0.2f, 0.36f), floatArrayOf(0f, -0.05f, 0.5f), floatArrayOf(0.48f, 0.18f, 0.36f),
                    floatArrayOf(-0.2f, 0.35f, 0.38f), floatArrayOf(0.25f, 0.38f, 0.36f), floatArrayOf(-0.62f, 0.42f, 0.22f),
                    floatArrayOf(0.66f, 0.42f, 0.22f),
                )
                return { u, v ->
                    var m = 0f
                    for (p in puffs) {
                        val r = hypot(u - p[0], v - p[1]) / p[2]
                        if (r < 1f) m = max(m, 0.75f + 0.25f * (1f - r * r))
                    }
                    if (v > 0.62f) m *= ((0.8f - v) / 0.18f).coerceIn(0f, 1f)
                    m
                }
            }
            TipShape.SNOWFLAKE -> { u, v ->
                val r = hypot(u, v)
                if (r > 0.95f) 0f else {
                    var a = atan2(v, u)
                    val sector = PI.toFloat() / 3f
                    a = abs(a - floor(a / sector + 0.5f) * sector)
                    val x = r * cos(a)
                    val y = r * sin(a)
                    val arm = y < 0.045f
                    val branches = listOf(0.4f, 0.65f).any { bx -> x > bx && abs(y - (x - bx) * 0.9f) < 0.04f && x < bx + 0.25f }
                    if (arm || branches || r < 0.12f) 1f else 0f
                }
            }
            TipShape.DOTS -> { u, v ->
                // A halftone screen fading out towards the rim.
                val r = hypot(u, v)
                val g = 0.25f
                val gx = u - floor(u / g + 0.5f) * g
                val gy = v - floor(v / g + 0.5f) * g
                val size = 0.11f * (1f - r * r).coerceAtLeast(0f).pow(0.6f)
                if (hypot(gx, gy) < size) 1f else 0f
            }
            TipShape.STIPPLE -> {
                val dots = List(40) {
                    val a = rnd.nextFloat() * 2f * PI.toFloat()
                    val dist = sqrt(rnd.nextFloat()) * 0.85f
                    floatArrayOf(cos(a) * dist, sin(a) * dist, 0.025f + 0.06f * rnd.nextFloat() * rnd.nextFloat())
                }
                return { u, v -> if (dots.any { hypot(u - it[0], v - it[1]) < it[2] }) 1f else 0f }
            }
            TipShape.RING -> { u, v -> val r = hypot(u, v); if (r > 0.66f && r < 0.9f) 1f else 0f }
            TipShape.BUBBLE -> { u, v ->
                val r = hypot(u, v)
                val hl = hypot(u + 0.36f, v + 0.36f)
                when {
                    r > 0.92f -> 0f
                    r > 0.8f -> 0.9f
                    hl < 0.16f -> 1f
                    else -> 0.12f + 0.25f * r * r
                }
            }
            TipShape.HATCH -> { u, v ->
                val k = (u + v) * 3.2f
                if (hypot(u, v) < 0.95f && abs(k - floor(k + 0.5f)) < 0.13f) 1f else 0f
            }
            TipShape.CROSS -> { u, v ->
                if ((abs(u) < 0.2f && abs(v) < 0.85f) || (abs(v) < 0.2f && abs(u) < 0.85f)) 1f else 0f
            }
            TipShape.MOON -> { u, v ->
                if (hypot(u, v) < 0.88f && hypot(u - 0.38f, v - 0.18f) > 0.68f) 1f else 0f
            }
            else -> { u, v -> if (hypot(u, v) < 0.9f) 1f else 0f }
        }
    }

    /** A pointed leaf along v with a pale midrib; 0 outside. */
    private fun leaf(u: Float, v: Float, width: Float): Float {
        if (v < -0.92f || v > 0.92f) return 0f
        val t = (v + 0.92f) / 1.84f
        val w = width * sin(t * PI.toFloat()).pow(0.85f) * (1f - 0.25f * t)
        if (abs(u) > w) return 0f
        return if (abs(u) < 0.03f && t > 0.08f) 0.55f else 1f
    }

    private fun regularPolygon(sides: Int, r: Float, start: Float, cy: Float = 0f): FloatArray {
        val out = FloatArray(sides * 2)
        for (i in 0 until sides) {
            val a = start + i * 2f * PI.toFloat() / sides
            out[i * 2] = cos(a) * r
            out[i * 2 + 1] = sin(a) * r + cy
        }
        return out
    }

    private fun starPolygon(points: Int, outer: Float, inner: Float, start: Float, cy: Float = 0f): FloatArray {
        val out = FloatArray(points * 4)
        for (i in 0 until points * 2) {
            val a = start + i * PI.toFloat() / points
            val r = if (i % 2 == 0) outer else inner
            out[i * 2] = cos(a) * r
            out[i * 2 + 1] = sin(a) * r + cy
        }
        return out
    }

    private fun inPolygon(p: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        val n = p.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = p[i * 2]; val yi = p[i * 2 + 1]
            val xj = p[j * 2]; val yj = p[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun boxBlur(d: FloatArray, n: Int, r: Int) {
        val tmp = FloatArray(n * n)
        val w = 2 * r + 1
        for (y in 0 until n) {
            var acc = 0f
            for (k in -r..r) acc += d[y * n + k.coerceIn(0, n - 1)]
            for (x in 0 until n) {
                tmp[y * n + x] = acc / w
                acc += d[y * n + min(n - 1, x + r + 1)] - d[y * n + max(0, x - r)]
            }
        }
        for (x in 0 until n) {
            var acc = 0f
            for (k in -r..r) acc += tmp[k.coerceIn(0, n - 1) * n + x]
            for (y in 0 until n) {
                d[y * n + x] = acc / w
                acc += tmp[min(n - 1, y + r + 1) * n + x] - tmp[max(0, y - r) * n + x]
            }
        }
    }
}
