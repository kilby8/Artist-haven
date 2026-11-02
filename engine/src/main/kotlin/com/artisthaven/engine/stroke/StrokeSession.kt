package com.artisthaven.engine.stroke

import com.artisthaven.engine.brush.Bristles
import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.brush.GrainTexture
import com.artisthaven.engine.brush.Mask
import com.artisthaven.engine.brush.RenderMode
import com.artisthaven.engine.brush.Textures
import com.artisthaven.engine.brush.TipAngle
import com.artisthaven.engine.brush.TipShape
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
    /** Paint the Mixer Brush carries from stroke to stroke; a fresh one is used if null. */
    mixerState: MixerState? = null,
) {
    private val brush: BrushPreset = params.brush
    private val onMask: Boolean = doc.paintsMask(layer)
    private val surface: TiledSurface = doc.paintSurface(layer)
    private val tip: Mask = Textures.tipFor(brush)
    private val grain: Mask? = Textures.grain(brush.grain)
    private val glaze: Boolean =
        params.tool == Tool.ERASER || (params.tool == Tool.BRUSH && brush.renderMode == RenderMode.GLAZE)
    private val wash: Boolean = params.tool == Tool.BRUSH && brush.renderMode == RenderMode.WASH
    private val mixer: Boolean = params.tool == Tool.BRUSH && brush.renderMode == RenderMode.MIXER
    private val mixerState: MixerState = mixerState ?: MixerState()
    /** The mixer's paint as the stroke began, so a replay starts from the same load. */
    private val mixerStart: MixerState
    private val bristly: Boolean = brush.tip == TipShape.BRISTLE && brush.tipImage == null
    /** Picture tips are square, so their corners reach past the radius when rotated. */
    private val pictureTip: Boolean = brush.tipImage != null
    private val reachScale: Float = if (pictureTip) 1.415f else 1f

    /** How far into the paper tooth the current dab reaches (pressure and tilt). */
    private var reach = 1f
    private val blendMode: BlendMode = if (params.tool == Tool.BRUSH) brush.blendMode else BlendMode.NORMAL

    /** Paint only lands inside the selection, if there is one. */
    private val selection = doc.selection

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
        if (mixer) this.mixerState.beginStroke(params.color)
        mixerStart = this.mixerState.copy()
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
        if (mixer) mixerState.endStroke(params.color, brush.mixerClean)
        val command = PixelCommand.capture(label(), layer, originals, onMask)
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
        if (mixer) mixerState.restore(mixerStart)
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
        val bristles: Bristles? = if (bristly) Bristles(seed, brush) else null
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
        if (wash) dirty = IRect.unionOf(dirty, shadeWash())
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
        if (wash) dirty = IRect.unionOf(dirty, shadeWash())
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
        s.bristles?.prepare(distance, params.size)
        // Light pressure only catches the tops of the paper grain; pressing harder fills the
        // valleys. Laying the pencil on its side spreads it out and lightens it. A loaded brush
        // pushes paint well into the weave whatever the pressure.
        reach = if (bristly) 0.85f + 0.3f * pr else (0.4f + 0.7f * pr) * (1f - 0.45f * tn * brush.tiltOpacity)
        return when {
            wash -> washDab(cx, cy, radius, angle, alpha, dabFlow)
            mixer -> mixerDab(s, cx, cy, radius, angle, dabFlow * opacity, grainDepth)
            glaze -> {
                var dirty = glazeDab(s, cx, cy, radius, angle, alpha, dabFlow, grainDepth)
                if (brush.dust > 0f && radius > 2f && params.tool == Tool.BRUSH) {
                    dirty = IRect.unionOf(dirty, dust(s, cx, cy, radius, alpha, grainDepth))
                }
                dirty
            }
            else -> wetDab(s, cx, cy, radius, angle, dabFlow * opacity, grainDepth, distance)
        }
    }

    /** Scatters a few loose specks just beyond the edge of the dab, like charcoal dust. */
    private fun dust(s: PathState, cx: Float, cy: Float, radius: Float, alpha: Float, grainDepth: Float): IRect? {
        var dirty: IRect? = null
        val n = (brush.dust * radius * 0.2f * s.rnd.nextFloat() + s.rnd.nextFloat()).toInt()
        val keep = reach
        reach = 1.1f
        repeat(n) {
            val a = s.rnd.nextFloat() * 2f * PI.toFloat()
            val d = radius * (0.8f + 0.9f * s.rnd.nextFloat() * s.rnd.nextFloat())
            val r = 0.55f + 0.8f * s.rnd.nextFloat() * s.rnd.nextFloat()
            val speck = alpha * (0.25f + 0.5f * s.rnd.nextFloat())
            dirty = IRect.unionOf(dirty, glazeDab(s, cx + cos(a) * d, cy + sin(a) * d, r, a, speck, 1f, grainDepth, round = true))
        }
        reach = keep
        return dirty
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
        val b = s.bristles
        var m: Float
        if (b != null && !roundDab) {
            // A flat brush: short along the stroke, one track per bristle across it.
            if (u < -0.45f || u > 0.45f || v < -1f || v > 1f) return -1f
            val t = 1f - (if (u < 0f) -u else u) / 0.45f
            m = t * (2f - t) * b.coverAt(v)
        } else {
            if (pictureTip && !roundDab) {
                if (u < -1f || u > 1f || v < -1f || v > 1f) return -1f
            } else if (u * u + v * v > 1.02f) return -1f
            m = if (roundDab) roundTip.sample((u + 1f) * 0.5f, (v + 1f) * 0.5f)
            else tip.sample((u + 1f) * 0.5f, (v + 1f) * 0.5f)
        }
        if (m <= 0f) return 0f
        val g = grain
        if (g != null && (grainDepth > 0f || brush.tooth > 0f)) {
            val scale = brush.grainScale.coerceAtLeast(0.1f)
            val gv = if (brush.grainMoving) {
                g.sampleWrapped(dx / scale + s.grainOffsetX, dy / scale + s.grainOffsetY)
            } else {
                g.sampleWrapped(px / scale, py / scale)
            }
            if (brush.tooth > 0f) {
                // Dry media sit on the peaks of the paper: paint lands where the tip reaches
                // deeper than the hollow at this spot.
                // Softer leads (lower depth) reach further into the hollows at the same pressure.
                // Even pressed hard a few of the deepest hollows stay white, and light pressure
                // leaves a paler deposit on the peaks it does reach.
                // A loaded bristle fills the weave completely; only a drying one skips.
                val deep = if (b != null && !roundDab) m * reach * 1.4f
                else min(0.94f, m * reach * (1.6f - 0.8f * brush.grainDepth))
                val dep = ((deep - (1f - gv)) / TOOTH_SOFTNESS + 0.5f).coerceIn(0f, 1f) * (0.4f + 0.6f * deep)
                m += (dep - m) * brush.tooth
                // Thick paint is either there or not: no see-through fringes.
                if (b != null && !roundDab) m = ((m - 0.12f) * 2f).coerceIn(0f, 1f)
            } else {
                m *= 1f - grainDepth + grainDepth * gv
            }
        }
        return m
    }

    /** True while drawing dust specks, which use a plain round tip. */
    private var roundDab = false
    private val roundTip: Mask by lazy { Textures.tip(TipShape.ROUND, 0.3f) }

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

    /** The layer's pixel at (x, y) as it was before this stroke (premultiplied). */
    private fun originalPixel(x: Int, y: Int): Int {
        val index = (y shr TiledSurface.SHIFT) * surface.tilesX + (x shr TiledSurface.SHIFT)
        if (!originals.containsKey(index)) return surface.getPixel(x, y)
        return originals[index]?.get(((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)) ?: 0
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
        round: Boolean = false,
    ): IRect? {
        val rect = IRect.of(cx - radius * reachScale - 1, cy - radius * reachScale - 1, cx + radius * reachScale + 1, cy + radius * reachScale + 1)
            .intersect(doc.bounds)
        if (rect.isEmpty) return null
        roundDab = round
        val cosA = cos(angle)
        val sinA = sin(angle)
        val invRound = 1f / brush.roundness.coerceIn(0.05f, 1f)
        val strokeOpacity = params.opacity.coerceIn(0f, 1f)
        val erase = params.tool == Tool.ERASER
        val alphaLock = layer.alphaLock && !erase && !onMask
        val shift = TiledSurface.SHIFT

        for (index in surface.tilesIn(rect)) {
            val orig = originalTile(index)
            if (alphaLock && orig == null) continue
            if (erase && orig == null) continue
            if (selection != null && selection.tileEmpty(index)) continue
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
                    var out = when {
                        erase -> Pixels.scale(o, 1f - a)
                        alphaLock -> lockedPaint(o, a)
                        else -> blendMode.blend(o, srcPixel(a))
                    }
                    if (selection != null) out = Pixels.lerp(o, out, selection.coverage(x, y))
                    if (dest == null) {
                        if (out == 0) continue
                        dest = surface.tileOrCreate(index)
                    }
                    dest[i] = out
                }
            }
        }
        roundDab = false
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
        val rect = IRect.of(cx - radius * reachScale - 1, cy - radius * reachScale - 1, cx + radius * reachScale + 1, cy + radius * reachScale + 1)
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
        // Bristle streaks: each lane of the brush gets its own shade of the paint, plus light and
        // shadow on the ridges the bristles plough (light from the top left).
        val b = if (smudge) null else s.bristles
        val light = (sinA * 0.6f - cosA * 0.8f) * brush.impasto * 1.2f
        val alphaLock = layer.alphaLock && !onMask
        val useBlend = !smudge && blendMode != BlendMode.NORMAL
        for (index in surface.tilesIn(rect)) {
            originalTile(index)
            val tr = surface.tileRect(index)
            val r = rect.intersect(tr)
            var dest: IntArray? = surface.tile(index)
            if (alphaLock && dest == null) continue
            if (selection != null && selection.tileEmpty(index)) continue
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    val m = maskAt(s, x + 0.5f, y + 0.5f, cx, cy, radius, cosA, sinA, invRound, grainDepth)
                    if (m <= 0f) continue
                    val i = row or (x and TiledSurface.MASK)
                    val d = dest?.get(i) ?: 0
                    val t = m * strength * (if (smudge) 1f else params.opacity)
                    var src = paint
                    if (b != null) {
                        val v = (-(x + 0.5f - cx) * sinA + (y + 0.5f - cy) * cosA) / radius * invRound
                        val lane = b.laneOf(v)
                        src = shadePaint(paint, 1f + b.tint[lane] + b.slope[lane] * light)
                    }
                    var out = if (useBlend) {
                        Pixels.lerp(d, blendMode.blend(d, src), t)
                    } else {
                        Pixels.lerp(d, src, t)
                    }
                    if (alphaLock) {
                        val da = Pixels.alpha(d)
                        val oa = Pixels.alpha(out)
                        out = if (oa == 0) d else Pixels.scale(out, da / oa.toFloat()).let {
                            Pixels.pack(da, Pixels.red(it), Pixels.green(it), Pixels.blue(it))
                        }
                    }
                    if (selection != null) out = Pixels.lerp(d, out, selection.coverage(x, y))
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

    private var mixerSampleBuf = IntArray(0)

    /**
     * Mixer Brush dab: picks up the colour under the brush into its pickup well, mixes that with
     * what is left in the reservoir, and lays the mix down. The reservoir drains a little with
     * every dab unless Load is full, so low-Load strokes fade out as the brush runs dry.
     */
    private fun mixerDab(
        s: PathState,
        cx: Float,
        cy: Float,
        radius: Float,
        angle: Float,
        amount: Float,
        grainDepth: Float,
    ): IRect? {
        val rr = radius * reachScale
        val rect = IRect.of(cx - rr - 1, cy - rr - 1, cx + rr + 1, cy + rr + 1).intersect(doc.bounds)
        if (rect.isEmpty) return null
        val cosA = cos(angle)
        val sinA = sin(angle)
        val invRound = 1f / brush.roundness.coerceIn(0.05f, 1f)
        val m = mixerState
        val wet = brush.mixerWet.coerceIn(0f, 1f)
        val mix = brush.mixerMix.coerceIn(0f, 1f)
        // Rates are tuned for dabs a quarter of the brush apart; closer dabs each do less.
        val perDab = (brush.spacing / 0.25f).coerceIn(0.05f, 1f)

        if (wet > 0f) {
            // The brush picks up the paint that was there before this stroke, so a smear can't
            // keep feeding on its own paint.
            val sampleAll = brush.mixerSampleAll
            if (sampleAll) {
                val n = rect.width * rect.height
                if (mixerSampleBuf.size < n) mixerSampleBuf = IntArray(n)
                val swapped = ArrayList<Pair<Int, IntArray?>>()
                for (index in surface.tilesIn(rect)) {
                    if (originals.containsKey(index)) {
                        swapped += index to surface.tile(index)
                        surface.setTile(index, originals[index])
                    }
                }
                try {
                    com.artisthaven.engine.document.Compositor.composite(
                        doc, rect, mixerSampleBuf, originX = rect.left, originY = rect.top, stride = rect.width, withPaper = false,
                    )
                } finally {
                    for ((index, tile) in swapped) surface.setTile(index, tile)
                }
            }
            var ar = 0f
            var ag = 0f
            var ab = 0f
            var asum = 0f
            var wsum = 0f
            val step = max(1, (radius / 4f).toInt())
            var sy = rect.top
            while (sy < rect.bottom) {
                var sx = rect.left
                while (sx < rect.right) {
                    val w = maskAt(s, sx + 0.5f, sy + 0.5f, cx, cy, radius, cosA, sinA, invRound, 0f)
                    if (w > 0f) {
                        // Straight colour and its alpha.
                        val c = if (sampleAll) mixerSampleBuf[(sy - rect.top) * rect.width + (sx - rect.left)]
                        else Pixels.unpremultiply(originalPixel(sx, sy))
                        val al = (c ushr 24) / 255f
                        ar += ((c shr 16) and 0xFF) / 255f * w * al
                        ag += ((c shr 8) and 0xFF) / 255f * w * al
                        ab += (c and 0xFF) / 255f * w * al
                        asum += w * al
                        wsum += w
                    }
                    sx += step
                }
                sy += step
            }
            val cover = if (wsum > 0f) asum / wsum else 0f
            if (asum > 1e-4f) {
                val canvas = floatArrayOf(ar / asum, ag / asum, ab / asum)
                val p = m.pickup
                if (p == null) {
                    m.pickup = canvas
                    m.pickupAmount = cover
                } else {
                    val k = MixerState.PICKUP_RATE * wet * cover * perDab
                    for (i in 0..2) p[i] += (canvas[i] - p[i]) * k
                }
            }
            // Dragging over bare canvas thins out what the brush picked up; paint tops it up.
            // Some is always used up, so a smear can't keep feeding on itself forever.
            if (m.pickup != null) {
                m.pickupAmount += (cover - m.pickupAmount) * MixerState.PICKUP_RATE * wet * perDab
                m.pickupAmount *= 1f - 0.06f * perDab
            }
        }

        val res = m.reservoir
        val pickup = m.pickup
        val wr = if (res != null) m.amount.coerceIn(0f, 1f) * (1f - mix * wet) else 0f
        val wp = if (pickup != null) mix * wet * m.pickupAmount.coerceIn(0f, 1f) else 0f
        val total = wr + wp
        m.amount *= 1f - 0.08f * (1f - brush.mixerLoad.coerceIn(0f, 1f)) * perDab
        if (total <= 1e-5f) return rect

        val pr = ((res?.get(0) ?: 0f) * wr + (pickup?.get(0) ?: 0f) * wp) / total
        val pg = ((res?.get(1) ?: 0f) * wr + (pickup?.get(1) ?: 0f) * wp) / total
        val pb = ((res?.get(2) ?: 0f) * wr + (pickup?.get(2) ?: 0f) * wp) / total
        val paint = Pixels.pack(255, (pr * 255f + 0.5f).toInt().coerceIn(0, 255), (pg * 255f + 0.5f).toInt().coerceIn(0, 255), (pb * 255f + 0.5f).toInt().coerceIn(0, 255))
        val deposit = (min(total, 1f) * amount * params.opacity).coerceIn(0f, 1f)

        val shift = TiledSurface.SHIFT
        val b = s.bristles
        val light = (sinA * 0.6f - cosA * 0.8f) * brush.impasto * 1.2f
        val alphaLock = layer.alphaLock && !onMask
        for (index in surface.tilesIn(rect)) {
            originalTile(index)
            var dest: IntArray? = surface.tile(index)
            if (alphaLock && dest == null) continue
            if (selection != null && selection.tileEmpty(index)) continue
            val r = rect.intersect(surface.tileRect(index))
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    val w = maskAt(s, x + 0.5f, y + 0.5f, cx, cy, radius, cosA, sinA, invRound, grainDepth)
                    if (w <= 0f) continue
                    val i = row or (x and TiledSurface.MASK)
                    val d = dest?.get(i) ?: 0
                    var src = paint
                    if (b != null) {
                        val v = (-(x + 0.5f - cx) * sinA + (y + 0.5f - cy) * cosA) / radius * invRound
                        val lane = b.laneOf(v)
                        src = shadePaint(paint, 1f + b.tint[lane] + b.slope[lane] * light)
                    }
                    var out = Pixels.lerp(d, src, w * deposit)
                    if (alphaLock) {
                        val da = Pixels.alpha(d)
                        val oa = Pixels.alpha(out)
                        out = if (oa == 0) d else Pixels.scale(out, da / oa.toFloat()).let {
                            Pixels.pack(da, Pixels.red(it), Pixels.green(it), Pixels.blue(it))
                        }
                    }
                    if (selection != null) out = Pixels.lerp(d, out, selection.coverage(x, y))
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

    /** Lightens or darkens a premultiplied colour by [k], keeping its alpha. */
    private fun shadePaint(c: Int, k: Float): Int {
        if (k == 1f) return c
        val a = Pixels.alpha(c)
        val f = k.coerceIn(0.6f, 1.4f)
        return Pixels.pack(
            a,
            (Pixels.red(c) * f + 0.5f).toInt().coerceAtMost(a),
            (Pixels.green(c) * f + 0.5f).toInt().coerceAtMost(a),
            (Pixels.blue(c) * f + 0.5f).toInt().coerceAtMost(a),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Watercolour
    //
    // A watercolour stroke is a pool of water carrying pigment. Dabs only add water and pigment to
    // two per-stroke maps; after each batch of input the touched area is re-shaded from those maps,
    // because how a wash dries depends on its surroundings: pigment migrates to the edge of the
    // pool and dries there as a dark rim, the rim follows the paper rather than the brush, and
    // pigment settles into the hollows of the paper (granulation).

    private val washWater = arrayOfNulls<FloatArray>(surface.tilesX * surface.tilesY)
    private val washPigment = arrayOfNulls<FloatArray>(surface.tilesX * surface.tilesY)
    private var washPending: IRect? = null
    private val flowNoise: Mask by lazy { Textures.grain(GrainTexture.PAPER)!! }
    private val washPaper: Mask? by lazy { grain ?: Textures.grain(GrainTexture.COLD_PRESS) }
    private val granules: Mask by lazy { Textures.grain(GrainTexture.DRAWING)!! }

    /** How far, in pixels, pigment travels to the edge of the wash. */
    private val rimWidth: Float = (params.size * 0.1f).coerceIn(2f, 10f)

    private fun washDab(cx: Float, cy: Float, radius: Float, angle: Float, pigment: Float, flow: Float): IRect? {
        val rect = IRect.of(cx - radius * reachScale - 1, cy - radius * reachScale - 1, cx + radius * reachScale + 1, cy + radius * reachScale + 1)
            .intersect(doc.bounds)
        if (rect.isEmpty) return null
        val cosA = cos(angle)
        val sinA = sin(angle)
        val invRound = 1f / brush.roundness.coerceIn(0.05f, 1f)
        val shift = TiledSurface.SHIFT
        for (index in surface.tilesIn(rect)) {
            if (selection != null && selection.tileEmpty(index)) continue
            if (originalTile(index) == null && layer.alphaLock && !onMask) continue
            val water = washWater[index] ?: FloatArray(TiledSurface.TILE * TiledSurface.TILE).also { washWater[index] = it }
            val pig = washPigment[index] ?: FloatArray(TiledSurface.TILE * TiledSurface.TILE).also { washPigment[index] = it }
            val r = rect.intersect(surface.tileRect(index))
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    val dx = x + 0.5f - cx
                    val dy = y + 0.5f - cy
                    val u = (dx * cosA + dy * sinA) / radius
                    val v = (-dx * sinA + dy * cosA) / radius * invRound
                    if (u * u + v * v > 1.02f) continue
                    val m = tip.sample((u + 1f) * 0.5f, (v + 1f) * 0.5f)
                    if (m <= 0f) continue
                    val i = row or (x and TiledSurface.MASK)
                    water[i] += (1f - water[i]) * min(1f, m * flow)
                    val p = pigment * min(1f, m * 1.6f)
                    if (p > pig[i]) pig[i] = p
                }
            }
        }
        washPending = IRect.unionOf(washPending, rect)
        return null
    }

    private fun waterAt(x: Int, y: Int): Float {
        if (x < 0 || y < 0 || x >= doc.width || y >= doc.height) return 0f
        val t = washWater[(y shr TiledSurface.SHIFT) * surface.tilesX + (x shr TiledSurface.SHIFT)] ?: return 0f
        return t[((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)]
    }

    /** Where the wash actually wets the paper: its outline wanders with the paper and the flow. */
    private fun wetAt(x: Int, y: Int): Float {
        val w = waterAt(x, y)
        if (w <= 0f) return 0f
        val wobble = brush.edgeWobble
        val n = flowNoise.sampleWrapped(x * 0.9f + 97f, y * 0.9f + 31f) - 0.5f
        val paper = washPaper?.sampleWrapped(x.toFloat(), y.toFloat())?.minus(0.5f) ?: 0f
        val level = w + (n * 0.5f + paper * 0.18f) * wobble
        // Paint laid on dry paper dries with a crisp edge; a wetter wash feathers out.
        val soft = 0.16f + (1f - brush.edgeDarken) * 0.4f
        val t = ((level - 0.32f) / soft).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private val rimDirs: FloatArray = FloatArray(16).also { d ->
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f + 0.39f
            d[k * 2] = cos(a)
            d[k * 2 + 1] = sin(a)
        }
    }

    /** Re-shades the wash wherever it changed since the last call. */
    private fun shadeWash(): IRect? {
        val pending = washPending ?: return null
        washPending = null
        val reachOut = rimWidth.toInt() + 2
        val rect = IRect(pending.left - reachOut, pending.top - reachOut, pending.right + reachOut, pending.bottom + reachOut)
            .intersect(doc.bounds)
        if (rect.isEmpty) return null
        val strokeOpacity = params.opacity.coerceIn(0f, 1f)
        val alphaLock = layer.alphaLock && !onMask
        val shift = TiledSurface.SHIFT
        val r1 = rimWidth
        val r2 = rimWidth * 0.5f
        for (index in surface.tilesIn(rect)) {
            val water = washWater[index] ?: continue
            val pig = washPigment[index] ?: continue
            val orig = originalTile(index)
            if (alphaLock && orig == null) continue
            val tr = surface.tileRect(index)
            val r = rect.intersect(tr)
            var dest: IntArray? = surface.tile(index)
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    val i = row or (x and TiledSurface.MASK)
                    if (water[i] <= 0f) continue
                    val wet = wetAt(x, y)
                    var density = 0f
                    if (wet > 0f) {
                        // Edge darkening: compare the wetness here with its surroundings. Inside the
                        // rim the surroundings are drier, and that is where pigment piles up.
                        var around = 0f
                        for (k in 0 until 8) {
                            val ddx = rimDirs[k * 2]
                            val ddy = rimDirs[k * 2 + 1]
                            around += wetAt((x + ddx * r1).toInt(), (y + ddy * r1).toInt())
                            around += wetAt((x + ddx * r2).toInt(), (y + ddy * r2).toInt())
                        }
                        around /= 16f
                        val rim = ((wet - around) * 2.5f).coerceIn(0f, 1f)
                        density = pig[i] * wet * (1f + brush.edgeDarken * rim * 2f)
                        if (brush.granulation > 0f) {
                            // Pigment grains settle in the fine hollows of the paper.
                            val h = granules.sampleWrapped(x / 1.3f, y / 1.3f)
                            density *= 1f + brush.granulation * (0.5f - h) * 1.1f
                        }
                        if (brush.pigmentFlow > 0f) {
                            val f = flowNoise.sampleWrapped(x * 0.18f + 13f, y * 0.18f + 71f)
                            density *= 1f + brush.pigmentFlow * (f - 0.5f) * 0.9f
                        }
                    }
                    val a = (density * strokeOpacity).coerceIn(0f, 1f)
                    val o = orig?.get(i) ?: 0
                    var out = when {
                        a <= 0f -> o
                        alphaLock -> lockedPaint(o, a)
                        else -> blendMode.blend(o, srcPixel(a))
                    }
                    if (selection != null) out = Pixels.lerp(o, out, selection.coverage(x, y))
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
        washWater.fill(null)
        washPigment.fill(null)
        washPending = null
        totalDirty = null
        if (mixer) mixerState.restore(mixerStart)

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
        /** How gradually paint fades in over a tooth height difference; smaller is grittier. */
        private const val TOOTH_SOFTNESS = 0.3f

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
