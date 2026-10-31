package com.artisthaven.engine.tools

import com.artisthaven.engine.stroke.InputPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * QuickShape: hold the pen still at the end of a stroke and a wobbly line becomes a straight one,
 * a rough loop becomes a clean ellipse (or circle), and a four-cornered loop becomes a rectangle.
 */
object QuickShape {
    enum class Kind { LINE, ELLIPSE, RECTANGLE }

    data class Shape(val kind: Kind, val points: List<InputPoint>)

    fun recognise(raw: List<InputPoint>): Shape? {
        if (raw.size < 4) return null
        val first = raw.first()
        val last = raw.last()
        val chord = hypot(last.x - first.x, last.y - first.y)
        var pathLen = 0f
        for (i in 1 until raw.size) pathLen += hypot(raw[i].x - raw[i - 1].x, raw[i].y - raw[i - 1].y)
        if (pathLen < 12f) return null
        val pressure = raw.map { it.pressure }.average().toFloat()

        // Straight line: everything stays close to the chord.
        if (chord > 0.85f * pathLen) {
            var maxDev = 0f
            for (p in raw) maxDev = max(maxDev, distanceToLine(p, first, last))
            if (maxDev <= max(4f, chord * 0.07f)) {
                return Shape(Kind.LINE, resample(listOf(first.x to first.y, last.x to last.y), pressure, first.time))
            }
        }

        // Closed loop: ends meet relative to the size of the shape.
        val minX = raw.minOf { it.x }
        val maxX = raw.maxOf { it.x }
        val minY = raw.minOf { it.y }
        val maxY = raw.maxOf { it.y }
        val diag = hypot(maxX - minX, maxY - minY)
        if (diag < 10f || chord > diag * 0.3f) return null

        rectangle(raw, pressure)?.let { return it }

        // Ellipse from the points' principal axes.
        val cx = raw.map { it.x }.average().toFloat()
        val cy = raw.map { it.y }.average().toFloat()
        var sxx = 0f
        var syy = 0f
        var sxy = 0f
        for (p in raw) {
            val dx = p.x - cx
            val dy = p.y - cy
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        sxx /= raw.size; syy /= raw.size; sxy /= raw.size
        val tr = sxx + syy
        val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(0f, tr * tr / 4f - det))
        val l1 = tr / 2f + disc
        val l2 = max(0f, tr / 2f - disc)
        val theta = 0.5f * atan2(2f * sxy, sxx - syy)
        var a = sqrt(2f * l1)
        var b = sqrt(2f * l2)
        // Nearly round loops snap to a circle.
        if (b > 0f && a / b < 1.12f) {
            val r = (a + b) / 2f
            a = r; b = r
        }
        // Reject shapes whose points stray far from the fitted ellipse.
        var err = 0f
        for (p in raw) {
            val dx = p.x - cx
            val dy = p.y - cy
            val u = (dx * cos(theta) + dy * sin(theta)) / a
            val v = (-dx * sin(theta) + dy * cos(theta)) / max(b, 1f)
            err += abs(sqrt(u * u + v * v) - 1f)
        }
        if (err / raw.size > 0.22f) return null

        val start = atan2(first.y - cy, first.x - cx) - theta
        val n = max(48, (PI * (a + b) / 3f).toInt())
        val pts = (0..n).map { i ->
            val t = start + 2f * PI.toFloat() * i / n
            val ex = a * cos(t)
            val ey = b * sin(t)
            (cx + ex * cos(theta) - ey * sin(theta)) to (cy + ex * sin(theta) + ey * cos(theta))
        }
        return Shape(Kind.ELLIPSE, resample(pts, pressure, first.time))
    }

    /** A loop with four sharp corners near the corners of its bounding box. */
    private fun rectangle(raw: List<InputPoint>, pressure: Float): Shape? {
        val minX = raw.minOf { it.x }
        val maxX = raw.maxOf { it.x }
        val minY = raw.minOf { it.y }
        val maxY = raw.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        if (w < 16f || h < 16f) return null
        val tol = 0.14f * max(w, h)
        val corners = listOf(minX to minY, maxX to minY, maxX to maxY, minX to maxY)
        for ((x, y) in corners) {
            if (raw.none { hypot(it.x - x, it.y - y) < tol }) return null
        }
        // Most points should hug an edge of the box.
        val onEdge = raw.count { p ->
            minOf(abs(p.x - minX), abs(p.x - maxX), abs(p.y - minY), abs(p.y - maxY)) < tol * 0.6f
        }
        if (onEdge < raw.size * 0.85f) return null
        val first = raw.first()
        val startCorner = corners.indices.minByOrNull { hypot(corners[it].first - first.x, corners[it].second - first.y) } ?: 0
        val ordered = (0..4).map { corners[(startCorner + it) % 4] }
        return Shape(Kind.RECTANGLE, resample(ordered, pressure, first.time))
    }

    private fun distanceToLine(p: InputPoint, a: InputPoint, b: InputPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < 1e-3f) return hypot(p.x - a.x, p.y - a.y)
        return abs((p.x - a.x) * dy - (p.y - a.y) * dx) / len
    }

    /** Evenly spaced points along a polyline, every 2 px, with constant pressure. */
    private fun resample(vertices: List<Pair<Float, Float>>, pressure: Float, t0: Long): List<InputPoint> {
        val out = ArrayList<InputPoint>()
        var time = t0
        for (i in 0 until vertices.size - 1) {
            val (x0, y0) = vertices[i]
            val (x1, y1) = vertices[i + 1]
            val len = hypot(x1 - x0, y1 - y0)
            val steps = max(1, (len / 2f).toInt())
            for (s in 0 until steps) {
                val t = s / steps.toFloat()
                out += InputPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, pressure, time = time)
                time += 2
            }
        }
        val (lx, ly) = vertices.last()
        out += InputPoint(lx, ly, pressure, time = time)
        return out
    }
}
