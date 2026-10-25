package com.artisthaven.engine.stroke

import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.brush.Mask
import com.artisthaven.engine.brush.RenderMode
import com.artisthaven.engine.brush.Textures
import com.artisthaven.engine.brush.TipAngle
import com.artisthaven.engine.document.BlendMode
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.document.PixelCommand
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.raster.TiledSurface
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Result of finishing a stroke: the area to redraw and the undo step (null if nothing changed). */
data class StrokeResult(val dirty: IRect?, val command: PixelCommand?)

/**
 * Paints one stroke onto a layer as input arrives.
 *
 * What you see while drawing is exactly what gets committed: every dab is written into the layer,
 * and the tiles the stroke touches are snapshotted first, so the stroke can be undone, cancelled,
 * or replayed (to apply the end taper, or to snap to a QuickShape).
 */
class StrokeSession(
    private val doc: Document,
    private val layer: Layer,
    val params: StrokeParams,
) {
    private val brush: BrushPreset = params.brush
    private val surface: TiledSurface = layer.surface
    private val tip: Mask = Textures.tip(brush.tip, brush.hardness)
    private val grain: Mask? = Textures.grain(brush.grain)
    private val glaze: Boolean =
        params.tool == Tool.ERASER || (params.tool == Tool.BRUSH && brush.renderMode == RenderMode.GLAZE)
    private val blendMode: BlendMode = if (params.tool == Tool.BRUSH) brush.blendMode else BlendMode.NORMAL

    private val originals = HashMap<Int, IntArray?>()
    private val coverage = HashMap<Int, FloatArray>()

    private val raw = ArrayList<InputPoint>()
    val rawPoints: List<InputPoint> get() = raw

    private var paths: List<PathState> = emptyList()
    private var totalDirty: IRect? = null
    private var finished = false

    // Straight brush colour, 0..1.
    private val cr = Pixels.red(params.color) / 255f
    private val cg = Pixels.green(params.color) / 255f
    private val cb = Pixels.blue(params.color) / 255f

    init {
        resetPaths()
    }

    /** Adds an input point; returns the document area that changed. */
    fun add(point: InputPoint): IRect? {
        check(!finished) { "Stroke already finished" }
        raw += point
        return feed(point, endLength = null)
    }

    /** Ends the stroke, applies the end taper, and returns the undo step. */
    fun finish(): StrokeResult {
        if (finished) return StrokeResult(totalDirty, null)
        var dirty = flushEnds(endLength = null)
        if (brush.taperEnd > 0f && raw.size > 1) {
            dirty = IRect.unionOf(dirty, replay(raw, taperAware = true))
        }
        finished = true
        val command = PixelCommand.capture(label(), layer, originals)
        return StrokeResult(IRect.unionOf(dirty, totalDirty), command)
    }

    /** Replaces the stroke's path (QuickShape) and repaints it. */
    fun replacePath(points: List<InputPoint>): IRect? {
        bypassSmoothing = true
        raw.clear()
        raw.addAll(points)
        return replay(points, taperAware = true)
    }

    /** Puts the layer back the way it was before the stroke. */
    fun cancel(): IRect? {
        restoreOriginals()
        finished = true
        return totalDirty
    }

    private fun label() = when (params.tool) {
        Tool.BRUSH -> brush.name
        Tool.ERASER -> "Erase"
        Tool.SMUDGE -> "Smudge"
    }

    // ---------------------------------------------------------------------------------------------
    // Paths, smoothing and dab placement

    /** Per-mirror state: smoothing, curve points, distance and the smudge colour. */
    private inner class PathState(val mirrorX: Boolean, val mirrorY: Boolean, seed: Int) {
        val rnd = Random(seed)
        var smoothed: InputPoint? = null
        var p0: InputPoint? = null
        var p1: InputPoint? = null
        var distance = 0f
        var carry = 0f
        var lastDabX = Float.NaN
        var lastDabY = Float.NaN
        var directionAngle = 0f
        // Smudge colour, premultiplied 0..1.
        var sa = -1f
        var sr = 0f
        var sg = 0f
        var sb = 0f
        val grainOffsetX = rnd.nextFloat() * 256f
        val grainOffsetY = rnd.nextFloat() * 256f
    }

    private fun resetPaths() {
        val list = ArrayList<PathState>()
        list += PathState(false, false, params.seed)
        when (params.symmetry) {
            Symmetry.NONE -> Unit
            Symmetry.VERTICAL -> list += PathState(true, false, params.seed + 1)
            Symmetry.HORIZONTAL -> list += PathState(false, true, params.seed + 2)
            Symmetry.QUADRANT -> {
                list += PathState(true, false, params.seed + 1)
                list += PathState(false, true, params.seed + 2)
                list += PathState(true, true, params.seed + 3)
            }
        }
        paths = list
    }

    private fun mirror(p: InputPoint, s: PathState): InputPoint {
        if (!s.mirrorX && !s.mirrorY) return p
        var x = p.x
        var y = p.y
        var az = p.azimuth
        if (s.mirrorX) {
            x = doc.width - x
            az = (PI.toFloat() - az)
        }
        if (s.mirrorY) {
            y = doc.height - y
            az = -az
        }
        return p.copy(x = x, y = y, azimuth = az)
    }

    /** Synthetic paths (QuickShape) are already clean, so they skip StreamLine. */
    private var bypassSmoothing = false

    private val streamline: Float
        get() = if (bypassSmoothing) 0f else
            (brush.streamline + params.extraStreamline * (1f - brush.streamline)).coerceIn(0f, 0.97f)

    private fun feed(point: InputPoint, endLength: Float?): IRect? {
        var dirty: IRect? = null
        for (s in paths) {
            val p = mirror(point, s)
            val prev = s.smoothed
            val sm = if (prev == null || streamline <= 0f) {
                p
            } else {
                // Time-normalised exponential smoothing ("pulled string").
                val dt = (p.time - prev.time).coerceIn(1L, 50L).toFloat()
                val keep = (streamline * 0.92f).pow(dt / 8f)
                val k = 1f - keep
                InputPoint(
                    prev.x + (p.x - prev.x) * k,
                    prev.y + (p.y - prev.y) * k,
                    prev.pressure + (p.pressure - prev.pressure) * min(1f, k * 1.5f),
                    p.tilt,
                    p.azimuth,
                    p.time,
                )
            }
            s.smoothed = sm
            dirty = IRect.unionOf(dirty, advance(s, sm, endLength))
        }
        totalDirty = IRect.unionOf(totalDirty, dirty)
        return dirty
    }

    /** Adds a smoothed point to a path and paints the curve segment it completes. */
    private fun advance(s: PathState, p: InputPoint, endLength: Float?): IRect? {
        val p0 = s.p0
        val p1 = s.p1
        if (p0 == null) {
            s.p0 = p
            return dab(s, p, p, 0f, endLength, first = true)
        }
        if (p1 == null) {
            if (hypot(p.x - p0.x, p.y - p0.y) < 0.5f) return null
            s.p1 = p
            return line(s, p0, mid(p0, p), endLength)
        }
        if (hypot(p.x - p1.x, p.y - p1.y) < 0.5f) return null
        val dirty = quad(s, mid(p0, p1), p1, mid(p1, p), endLength)
        s.p0 = p1
        s.p1 = p
        return dirty
    }

    /** Catches every path up to the pen-up point and draws the last half segment. */
    private fun flushEnds(endLength: Float?): IRect? {
        var dirty: IRect? = null
        val last = raw.lastOrNull() ?: return null
        for (s in paths) {
            val target = mirror(last, s)
            var cur = s.smoothed ?: continue
            // Catch the smoothed point up to where the pen actually lifted.
            var guard = 0
            while (hypot(target.x - cur.x, target.y - cur.y) > 1f && guard++ < 12) {
                cur = InputPoint(
                    cur.x + (target.x - cur.x) * 0.6f,
                    cur.y + (target.y - cur.y) * 0.6f,
                    cur.pressure + (target.pressure - cur.pressure) * 0.6f,
                    target.tilt, target.azimuth, cur.time + 4,
                )
                dirty = IRect.unionOf(dirty, advance(s, cur, endLength))
            }
            dirty = IRect.unionOf(dirty, advance(s, target, endLength))
            val p0 = s.p0
            val p1 = s.p1
            if (p0 != null && p1 != null) dirty = IRect.unionOf(dirty, line(s, mid(p0, p1), p1, endLength))
            s.smoothed = null
        }
        totalDirty = IRect.unionOf(totalDirty, dirty)
        return dirty
    }

    private fun mid(a: InputPoint, b: InputPoint) = InputPoint(
        (a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.pressure + b.pressure) / 2f,
        (a.tilt + b.tilt) / 2f, b.azimuth, (a.time + b.time) / 2,
    )

    private fun line(s: PathState, a: InputPoint, b: InputPoint, endLength: Float?): IRect? =
        walk(s, a, endLength) { t -> lerpPoint(a, b, t) }

    private fun quad(s: PathState, a: InputPoint, c: InputPoint, b: InputPoint, endLength: Float?): IRect? =
        walk(s, a, endLength) { t ->
            val u = 1f - t
            val base = lerpPoint(a, b, t)
            base.copy(
                x = u * u * a.x + 2 * u * t * c.x + t * t * b.x,
                y = u * u * a.y + 2 * u * t * c.y + t * t * b.y,
            )
        }

    private fun lerpPoint(a: InputPoint, b: InputPoint, t: Float) = InputPoint(
        a.x + (b.x - a.x) * t,
        a.y + (b.y - a.y) * t,
        a.pressure + (b.pressure - a.pressure) * t,
        a.tilt + (b.tilt - a.tilt) * t,
        b.azimuth,
        a.time + ((b.time - a.time) * t).toLong(),
    )

    /** Walks a curve in small steps and places dabs at the brush spacing. */
    private inline fun walk(
        s: PathState,
        start: InputPoint,
        endLength: Float?,
        at: (Float) -> InputPoint,
    ): IRect? {
        var dirty: IRect? = null
        var prev = start
        val approx = run {
            val e = at(1f)
            val m = at(0.5f)
            hypot(m.x - start.x, m.y - start.y) + hypot(e.x - m.x, e.y - m.y)
        }
        val steps = max(1, (approx / 1.5f).toInt())
        for (i in 1..steps) {
            val p = at(i / steps.toFloat())
            val segLen = hypot(p.x - prev.x, p.y - prev.y)
            if (segLen <= 0f) continue
            s.directionAngle = atan2(p.y - prev.y, p.x - prev.x)
            var consumed = 0f
            while (true) {
                val spacing = spacingAt(prev, s)
                val need = spacing - s.carry
                if (consumed + need > segLen) {
                    s.carry += segLen - consumed
                    break
                }
                consumed += need
                s.carry = 0f
                val t = consumed / segLen
                val q = lerpPoint(prev, p, t)
                s.distance += need
                dirty = IRect.unionOf(dirty, dab(s, q, prev, s.distance, endLength, first = false))
            }
            prev = p
        }
        return dirty
    }

    private fun spacingAt(p: InputPoint, s: PathState): Float {
        val size = params.size * sizeFactor(p, s, distance = s.distance, endLength = null, speed = 0f)
        return max(0.4f, brush.spacing * size)
    }

    private fun pressureCurve(p: Float): Float =
        if (params.usePressure) p.coerceIn(0f, 1f).pow(brush.pressureGamma) else 1f

    private fun tiltNorm(p: InputPoint): Float =
        if (params.usePressure) (p.tilt / (PI.toFloat() / 2f)).coerceIn(0f, 1f) else 0f

    private fun taper(distance: Float, endLength: Float?): Float {
        val base = max(params.size, 2f)
        var f = 1f
        if (brush.taperStart > 0f) {
            val len = brush.taperStart * base
            if (distance < len) f = min(f, ease(distance / len))
        }
        if (brush.taperEnd > 0f && endLength != null) {
            val len = brush.taperEnd * base
            val remaining = endLength - distance
            if (remaining < len) f = min(f, ease(max(0f, remaining) / len))
        }
        return brush.taperMinSize + (1f - brush.taperMinSize) * f
    }

    private fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 1f - (1f - c) * (1f - c)
    }

    private fun sizeFactor(p: InputPoint, s: PathState, distance: Float, endLength: Float?, speed: Float): Float {
        val pr = pressureCurve(p.pressure)
        var f = 1f - brush.pressureSize * (1f - pr)
        val tn = tiltNorm(p)
        f *= 1f + brush.tiltSize * tn * tn
        f *= 1f - brush.speedSize * speed
        f *= taper(distance, endLength)
        return f.coerceIn(0.02f, 4f)
    }

    // ---------------------------------------------------------------------------------------------
    // Dab rendering

    private fun dab(
        s: PathState,
        p: InputPoint,
        prev: InputPoint,
        distance: Float,
        endLength: Float?,
        first: Boolean,
    ): IRect? {
        val dt = max(1L, p.time - prev.time).toFloat()
        val speed = if (first) 0f else (hypot(p.x - prev.x, p.y - prev.y) / dt / 3f).coerceIn(0f, 1f)
        val pr = pressureCurve(p.pressure)
        val tn = tiltNorm(p)

        var diameter = params.size * sizeFactor(p, s, distance, endLength, speed)
        if (brush.sizeJitter > 0f) diameter *= 1f + brush.sizeJitter * (s.rnd.nextFloat() * 2f - 1f)

        var opacity = 1f - brush.pressureOpacity * (1f - pr)
        opacity *= 1f - brush.tiltOpacity * tn
        opacity *= 1f - brush.speedOpacity * speed
        if (brush.opacityJitter > 0f) opacity *= 1f - brush.opacityJitter * s.rnd.nextFloat()
        var flow = brush.flow * (1f - brush.pressureFlow * (1f - pr))
        if (params.tool == Tool.SMUDGE) flow = max(flow, 0.35f)

        var radius = diameter / 2f
        var areaScale = 1f
        if (radius < 0.7f) {
            areaScale = (radius / 0.7f) * (radius / 0.7f)
            radius = 0.7f
        }

        var cx = p.x
        var cy = p.y
        if (brush.lateralJitter > 0f) {
            val off = (s.rnd.nextFloat() * 2f - 1f) * brush.lateralJitter * diameter * 2f
            cx += -sin(s.directionAngle) * off
            cy += cos(s.directionAngle) * off
        }

        var angle = when (brush.tipAngle) {
            TipAngle.FIXED -> brush.angleDegrees * (PI.toFloat() / 180f)
            TipAngle.FOLLOW_STROKE -> s.directionAngle
            TipAngle.STYLUS -> if (params.usePressure) p.azimuth else s.directionAngle
        }
        if (s.mirrorX && brush.tipAngle == TipAngle.FIXED) angle = PI.toFloat() - angle
        if (s.mirrorY && brush.tipAngle == TipAngle.FIXED) angle = -angle
        if (brush.rotationJitter > 0f) {
            angle += (s.rnd.nextFloat() * 2f - 1f) * brush.rotationJitter * (PI.toFloat() / 180f)
        }

        val alpha = (opacity * areaScale).coerceIn(0f, 1f)
        val dabFlow = (flow * areaScale).coerceIn(0f, 1f)
        val grainDepth = (brush.grainDepth * (1.2f - 0.35f * pr)).coerceIn(0f, 1f)
        s.lastDabX = cx
        s.lastDabY = cy
        return if (glaze) {
            glazeDab(s, cx, cy, radius, angle, alpha, dabFlow, grainDepth)
        } else {
            wetDab(s, cx, cy, radius, angle, dabFlow * opacity, grainDepth, distance)
        }
    }

    /** Computes the tip and grain mask value for a pixel centre, or -1 outside the tip. */
    private fun maskAt(
        s: PathState,
        px: Float,
        py: Float,
        cx: Float,
        cy: Float,
        radius: Float,
        cosA: Float,
        sinA: Float,
        invRound: Float,
        grainDepth: Float,
    ): Float {
        val dx = px - cx
        val dy = py - cy
        val u = (dx * cosA + dy * sinA) / radius
        val v = (-dx * sinA + dy * cosA) / radius * invRound
        if (u * u + v * v > 1.02f) return -1f
        var m = tip.sample((u + 1f) * 0.5f, (v + 1f) * 0.5f)
        if (m <= 0f) return 0f
        val g = grain
        if (g != null && grainDepth > 0f) {
            val scale = brush.grainScale.coerceAtLeast(0.1f)
            val gv = if (brush.grainMoving) {
                g.sampleWrapped(dx / scale + s.grainOffsetX, dy / scale + s.grainOffsetY)
            } else {
                g.sampleWrapped(px / scale, py / scale)
            }
            m *= 1f - grainDepth + grainDepth * gv
        }
        return m
    }

    /**
     * What a wet brush picks up: mostly the paint that was there before this stroke, plus a little
     * of its own fresh paint, so it mixes with the colours underneath instead of only itself.
     */
    private fun wetSample(x: Int, y: Int): Int {
        val current = surface.getPixel(x, y)
        val index = (y shr TiledSurface.SHIFT) * surface.tilesX + (x shr TiledSurface.SHIFT)
        if (!originals.containsKey(index)) return current
        val before = originals[index]?.get(((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)) ?: 0
        return Pixels.lerp(before, current, 0.3f)
    }

    private fun originalTile(index: Int): IntArray? {
        if (!originals.containsKey(index)) originals[index] = surface.copyTile(index)
        return originals[index]
    }

    private fun glazeDab(
        s: PathState,
        cx: Float,
        cy: Float,
        radius: Float,
        angle: Float,
        target: Float,
        flow: Float,
        grainDepth: Float,
    ): IRect? {
        val rect = IRect.of(cx - radius - 1, cy - radius - 1, cx + radius + 1, cy + radius + 1)
            .intersect(doc.bounds)
        if (rect.isEmpty) return null
        val cosA = cos(angle)
        val sinA = sin(angle)
        val invRound = 1f / brush.roundness.coerceIn(0.05f, 1f)
        val strokeOpacity = params.opacity.coerceIn(0f, 1f)
        val erase = params.tool == Tool.ERASER
        val alphaLock = layer.alphaLock && !erase
        val shift = TiledSurface.SHIFT

        for (index in surface.tilesIn(rect)) {
            val orig = originalTile(index)
            if (alphaLock && orig == null) continue
            if (erase && orig == null) continue
            val cov = coverage.getOrPut(index) { FloatArray(TiledSurface.TILE * TiledSurface.TILE) }
            val tr = surface.tileRect(index)
            val r = rect.intersect(tr)
            var dest: IntArray? = surface.tile(index)
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    val m = maskAt(s, x + 0.5f, y + 0.5f, cx, cy, radius, cosA, sinA, invRound, grainDepth)
                    if (m <= 0f) continue
                    val i = row or (x and TiledSurface.MASK)
                    val c0 = cov[i]
                    if (c0 >= target) continue
                    val c1 = c0 + (target - c0) * min(1f, m * flow)
                    cov[i] = c1
                    val o = orig?.get(i) ?: 0
                    val a = c1 * strokeOpacity
                    val out = when {
                        erase -> Pixels.scale(o, 1f - a)
                        alphaLock -> lockedPaint(o, a)
                        else -> blendMode.blend(o, srcPixel(a))
                    }
                    if (dest == null) {
                        if (out == 0) continue
                        dest = surface.tileOrCreate(index)
                    }
                    dest[i] = out
                }
            }
        }
        return rect
    }

    private fun srcPixel(a: Float): Int {
        val a255 = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
        return Pixels.pack(a255, (cr * a255 + 0.5f).toInt(), (cg * a255 + 0.5f).toInt(), (cb * a255 + 0.5f).toInt())
    }

    /** Paints colour into existing pixels without changing their alpha (Alpha Lock). */
    private fun lockedPaint(orig: Int, a: Float): Int {
        val oa = Pixels.alpha(orig)
        if (oa == 0) return orig
        val target = Pixels.pack(oa, (cr * oa + 0.5f).toInt(), (cg * oa + 0.5f).toInt(), (cb * oa + 0.5f).toInt())
        val blended = if (blendMode == BlendMode.NORMAL) target else blendMode.blend(orig, target).let { b ->
            val ba = Pixels.alpha(b)
            if (ba == 0 || ba == oa) b else Pixels.scale(b, oa / ba.toFloat())
        }
        return Pixels.lerp(orig, blended, a)
    }

    /** MyPaint-style wet dab: mixes the paint already on the layer into the brush colour. */
    private fun wetDab(
        s: PathState,
        cx: Float,
        cy: Float,
        radius: Float,
        angle: Float,
        amount: Float,
        grainDepth: Float,
        distance: Float,
    ): IRect? {
        val rect = IRect.of(cx - radius - 1, cy - radius - 1, cx + radius + 1, cy + radius + 1)
            .intersect(doc.bounds)
        if (rect.isEmpty) return null
        val cosA = cos(angle)
        val sinA = sin(angle)
        val invRound = 1f / brush.roundness.coerceIn(0.05f, 1f)
        val smudge = params.tool == Tool.SMUDGE

        // Average the paint under the dab (on a coarse grid; it only needs to be roughly right).
        var wa = 0f
        var wr = 0f
        var wg = 0f
        var wb = 0f
        var wsum = 0f
        val step = max(1, (radius / 4f).toInt())
        var sy = rect.top
        while (sy < rect.bottom) {
            var sx = rect.left
            while (sx < rect.right) {
                val m = maskAt(s, sx + 0.5f, sy + 0.5f, cx, cy, radius, cosA, sinA, invRound, 0f)
                if (m > 0f) {
                    val c = if (smudge) surface.getPixel(sx, sy) else wetSample(sx, sy)
                    wa += Pixels.alpha(c) / 255f * m
                    wr += Pixels.red(c) / 255f * m
                    wg += Pixels.green(c) / 255f * m
                    wb += Pixels.blue(c) / 255f * m
                    wsum += m
                }
                sx += step
            }
            sy += step
        }
        if (wsum > 0f) {
            wa /= wsum; wr /= wsum; wg /= wsum; wb /= wsum
        }
        if (s.sa < 0f) {
            s.sa = wa; s.sr = wr; s.sg = wg; s.sb = wb
        }

        // Paint colour: the brush's own colour mixed with what it has picked up.
        val pa: Float
        val pr: Float
        val pg: Float
        val pb: Float
        if (smudge) {
            pa = s.sa; pr = s.sr; pg = s.sg; pb = s.sb
        } else {
            val load = if (brush.charge >= 1f) 1f else max(0f, 1f - distance / (brush.charge * 900f + 1f))
            val mix = 1f - (1f - brush.pull) * load
            val alphaC = 1f - brush.dilution
            // The reservoir only tints the paint as much as it actually holds paint, so passing
            // over bare canvas never thins the brush out.
            val held = s.sa.coerceIn(0f, 1f)
            val m = mix * held
            val inv = if (held > 0.004f) 1f / held else 0f
            val rr = (s.sr * inv).coerceIn(0f, 1f)
            val rg = (s.sg * inv).coerceIn(0f, 1f)
            val rb = (s.sb * inv).coerceIn(0f, 1f)
            pa = alphaC + max(0f, held - alphaC) * mix
            pr = (cr + (rr - cr) * m) * pa
            pg = (cg + (rg - cg) * m) * pa
            pb = (cb + (rb - cb) * m) * pa
        }
        val paint = Pixels.pack(
            (pa * 255f + 0.5f).toInt(), (pr * 255f + 0.5f).toInt(),
            (pg * 255f + 0.5f).toInt(), (pb * 255f + 0.5f).toInt(),
        )

        // The brush picks up some of the paint it just passed over.
        if (smudge) {
            val pickup = 1f - params.brush.smudgeStrength.coerceIn(0f, 0.98f)
            s.sa += (wa - s.sa) * pickup
            s.sr += (wr - s.sr) * pickup
            s.sg += (wg - s.sg) * pickup
            s.sb += (wb - s.sb) * pickup
        } else {
            // A loaded brush takes on colour in proportion to the paint it touches, and only slowly
            // runs dry over bare canvas, so colours drag on past the edge of what it crossed.
            val pickup = 0.3f
            val heldBefore = s.sa.coerceIn(0f, 1f)
            val invH = if (heldBefore > 0.004f) 1f / heldBefore else 0f
            val invW = if (wa > 0.004f) 1f / wa else 0f
            var r = s.sr * invH
            var g = s.sg * invH
            var b = s.sb * invH
            val k = pickup * wa.coerceIn(0f, 1f)
            r += (wr * invW - r) * k
            g += (wg * invW - g) * k
            b += (wb * invW - b) * k
            val held = heldBefore + (wa - heldBefore) * (if (wa > heldBefore) pickup else pickup * 0.12f)
            s.sa = held
            s.sr = r.coerceIn(0f, 1f) * held
            s.sg = g.coerceIn(0f, 1f) * held
            s.sb = b.coerceIn(0f, 1f) * held
        }

        val strength = (amount * (if (smudge) params.opacity else 1f)).coerceIn(0f, 1f)
        val shift = TiledSurface.SHIFT
        val alphaLock = layer.alphaLock
        val useBlend = !smudge && blendMode != BlendMode.NORMAL
        for (index in surface.tilesIn(rect)) {
            originalTile(index)
            val tr = surface.tileRect(index)
            val r = rect.intersect(tr)
            var dest: IntArray? = surface.tile(index)
            if (alphaLock && dest == null) continue
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    val m = maskAt(s, x + 0.5f, y + 0.5f, cx, cy, radius, cosA, sinA, invRound, grainDepth)
                    if (m <= 0f) continue
                    val i = row or (x and TiledSurface.MASK)
                    val d = dest?.get(i) ?: 0
                    val t = m * strength * (if (smudge) 1f else params.opacity)
                    var out = if (useBlend) {
                        Pixels.lerp(d, blendMode.blend(d, paint), t)
                    } else {
                        Pixels.lerp(d, paint, t)
                    }
                    if (alphaLock) {
                        val da = Pixels.alpha(d)
                        val oa = Pixels.alpha(out)
                        out = if (oa == 0) d else Pixels.scale(out, da / oa.toFloat()).let {
                            Pixels.pack(da, Pixels.red(it), Pixels.green(it), Pixels.blue(it))
                        }
                    }
                    if (dest == null) {
                        if (out == 0) continue
                        dest = surface.tileOrCreate(index)
                    }
                    dest[i] = out
                }
            }
        }
        return rect
    }

    // ---------------------------------------------------------------------------------------------
    // Replay

    private fun restoreOriginals() {
        for ((index, tile) in originals) surface.setTile(index, tile?.copyOf())
    }

    /** Repaints the stroke from scratch along [points], knowing its full length for the end taper. */
    private fun replay(points: List<InputPoint>, taperAware: Boolean): IRect? {
        val before = totalDirty
        restoreOriginals()
        coverage.clear()
        totalDirty = null

        var length: Float? = null
        if (taperAware && brush.taperEnd > 0f) {
            // Dry run to measure the stroke's painted length.
            resetPaths()
            val measured = measure(points)
            length = measured
        }
        resetPaths()
        var dirty: IRect? = null
        for (p in points) dirty = IRect.unionOf(dirty, feed(p, length))
        dirty = IRect.unionOf(dirty, flushEnds(length))
        return IRect.unionOf(before, dirty)
    }

    private fun measure(points: List<InputPoint>): Float {
        // Walk the same smoothing and curves without painting by sampling the first path only.
        var len = 0f
        var prev: InputPoint? = null
        var smoothed: InputPoint? = null
        for (p in points) {
            val sm = if (smoothed == null || streamline <= 0f) p else {
                val dt = (p.time - smoothed.time).coerceIn(1L, 50L).toFloat()
                val k = 1f - (streamline * 0.92f).pow(dt / 8f)
                p.copy(x = smoothed.x + (p.x - smoothed.x) * k, y = smoothed.y + (p.y - smoothed.y) * k)
            }
            smoothed = sm
            if (prev != null) len += hypot(sm.x - prev.x, sm.y - prev.y)
            prev = sm
        }
        val last = points.lastOrNull()
        if (last != null && prev != null) len += hypot(last.x - prev.x, last.y - prev.y)
        return len
    }

    companion object {
        /** Length of a straight polyline, used by tools that synthesise points. */
        fun polylineLength(points: List<InputPoint>): Float {
            var l = 0f
            for (i in 1 until points.size) l += sqrt(
                (points[i].x - points[i - 1].x).let { it * it } + (points[i].y - points[i - 1].y).let { it * it },
            )
            return l
        }
    }
}
