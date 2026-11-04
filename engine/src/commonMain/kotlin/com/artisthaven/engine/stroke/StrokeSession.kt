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
    /** Where earlier watercolour on this layer is still wet, for wet-in-wet. */
    private val wetCanvas: WetCanvas? = null,
) {
    private val brush: BrushPreset = params.brush
    /** Blending brushes work like the Smudge tool even when painted with the brush. */
    private val tool: Tool = if (params.tool == Tool.BRUSH && brush.blendOnly) Tool.SMUDGE else params.tool
    private val onMask: Boolean = doc.paintsMask(layer)
    private val surface: TiledSurface = doc.paintSurface(layer)
    private val tip: Mask = Textures.tipFor(brush)
    private val grain: Mask? = Textures.grain(brush.grain)
    private val glaze: Boolean =
        tool == Tool.ERASER || (tool == Tool.BRUSH && brush.renderMode == RenderMode.GLAZE)
    private val wash: Boolean = tool == Tool.BRUSH && brush.renderMode == RenderMode.WASH
    private val mixer: Boolean = tool == Tool.BRUSH && brush.renderMode == RenderMode.MIXER
    /** Thick paint: painted into [reliefBase] and lit from a height map by [shadeRelief]. */
    private val relief: Boolean = tool == Tool.BRUSH && brush.renderMode == RenderMode.WET && brush.relief > 0f && !onMask
    private val mixerState: MixerState = mixerState ?: MixerState()
    /** The mixer's paint as the stroke began, so a replay starts from the same load. */
    private val mixerStart: MixerState
    private val bristly: Boolean = brush.tip == TipShape.BRISTLE && brush.tipImage == null
    /** Picture tips are square, so their corners reach past the radius when rotated. */
    private val pictureTip: Boolean = brush.tipImage != null
    private val reachScale: Float = if (pictureTip) 1.415f else 1f

    /** How far into the paper tooth the current dab reaches (pressure and tilt). */
    private var reach = 1f
    private val blendMode: BlendMode = if (tool == Tool.BRUSH) brush.blendMode else BlendMode.NORMAL

    /** Paint only lands inside the selection, if there is one. */
    private val selection = doc.selection

    private val originals = HashMap<Int, IntArray?>()
    private val coverage = HashMap<Int, FloatArray>()

    private val raw = ArrayList<InputPoint>()
    /** Time of the latest input point, for wet-in-wet drying. */
    private var lastTime = 0L
    /** Pressure (after the curve) of the dab being painted. */
    private var dabPressure = 1f
    /** A flat brush's first dab, held back until the stroke has a direction. */
    private var heldFirstDab: Pair<PathState, InputPoint>? = null
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
        lastTime = point.time
        return feed(point, endLength = null)
    }

    /** Ends the stroke, applies the end taper, and returns the undo step. */
    fun finish(): StrokeResult {
        if (finished) return StrokeResult(totalDirty, null)
        var dirty = flushEnds(endLength = null)
        // A tap with a flat brush: its only dab was held back (see dab), so paint it now.
        val held = heldFirstDab
        if (totalDirty == null && dirty == null && held != null) {
            heldFirstDab = null
            dirty = dab(held.first, held.second, held.second, 0f, null, first = false)
            if (wash) dirty = IRect.unionOf(dirty, shadeWash())
            if (relief) dirty = IRect.unionOf(dirty, shadeRelief())
            totalDirty = IRect.unionOf(totalDirty, dirty)
        }
        // The end taper needs the stroke's length, so the stroke is repainted once it is known.
        // With a pen, lifting off already tapers the end through pressure, and repainting a wet,
        // watercolour or mixing stroke is slow, so that is only done for mouse and finger strokes.
        if (brush.taperEnd > 0f && raw.size > 1 && (glaze || !params.usePressure)) {
            dirty = IRect.unionOf(dirty, replay(raw, taperAware = true))
        }
        finished = true
        if (wash && !onMask) wetCanvas?.deposit(washWater, lastTime)
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
        /** Smudging with bristles: the colour each strip of the brush carries, premultiplied argb. */
        var lanes: FloatArray? = null
        /** Per strip: how quickly it takes on new colour, and how firmly it lays its colour down. */
        val laneRate: FloatArray
        val laneHold: FloatArray
        init {
            val r = Random(seed * 13 + 5)
            laneRate = FloatArray(SMUDGE_LANES) { val k = r.nextFloat(); 0.1f + 1.8f * k * k }
            laneHold = FloatArray(SMUDGE_LANES) { 0.35f + 0.65f * r.nextFloat() }
        }
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
        if (relief) dirty = IRect.unionOf(dirty, shadeRelief())
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
        if (relief) dirty = IRect.unionOf(dirty, shadeRelief())
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
        // A flat brush's footprint depends on the stroke direction, which the first dab doesn't
        // know yet; drawn anyway, it leaves a pale square at the start of the stroke.
        if (first && (bristly || brush.roundness < 0.7f) && brush.tipAngle == TipAngle.FOLLOW_STROKE) {
            if (s === paths.first()) heldFirstDab = s to p
            return null
        }
        val dt = max(1L, p.time - prev.time).toFloat()
        val speed = if (first) 0f else (hypot(p.x - prev.x, p.y - prev.y) / dt / 3f).coerceIn(0f, 1f)
        val pr = pressureCurve(p.pressure)
        dabPressure = pr
        val tn = tiltNorm(p)

        var diameter = params.size * sizeFactor(p, s, distance, endLength, speed)
        if (brush.sizeJitter > 0f) diameter *= 1f + brush.sizeJitter * (s.rnd.nextFloat() * 2f - 1f)

        var opacity = 1f - brush.pressureOpacity * (1f - pr)
        opacity *= 1f - brush.tiltOpacity * tn
        opacity *= 1f - brush.speedOpacity * speed
        if (brush.opacityJitter > 0f) opacity *= 1f - brush.opacityJitter * s.rnd.nextFloat()
        var flow = brush.flow * (1f - brush.pressureFlow * (1f - pr))
        if (tool == Tool.SMUDGE) flow = max(flow, 0.35f)

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
            TipAngle.FOLLOW_STROKE -> s.directionAngle + brush.angleDegrees * (PI.toFloat() / 180f)
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
            wash -> washDab(cx, cy, radius, angle, alpha, dabFlow, speed, distance)
            mixer -> mixerDab(s, cx, cy, radius, angle, dabFlow * opacity, grainDepth)
            glaze -> {
                var dirty = glazeDab(s, cx, cy, radius, angle, alpha, dabFlow, grainDepth)
                if (brush.dust > 0f && radius > 2f && tool == Tool.BRUSH) {
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
    // Cached by hand: `by lazy` is slow in hot loops on Kotlin/JS (the web app).
    private var roundTipCache: Mask? = null
    private val roundTip: Mask get() = roundTipCache ?: Textures.tip(TipShape.ROUND, 0.3f).also { roundTipCache = it }

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
        val erase = tool == Tool.ERASER
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
        val smudge = tool == Tool.SMUDGE

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

        // A bristle brush smudges in streaks: each strip across the brush picks up and drags the
        // colour under it, so blending two colours leaves streaks of both instead of one average.
        val laneCols: FloatArray? = if (smudge && s.bristles != null) {
            val picked = FloatArray(SMUDGE_LANES * 4)
            val across = radius / invRound
            for (k in 0 until SMUDGE_LANES) {
                val v = -1f + (k + 0.5f) * 2f / SMUDGE_LANES
                var n = 0
                for (u in SMUDGE_U) {
                    val px = (cx + u * radius * cosA - v * across * sinA).toInt()
                    val py = (cy + u * radius * sinA + v * across * cosA).toInt()
                    if (px < 0 || py < 0 || px >= doc.width || py >= doc.height) continue
                    val c = surface.getPixel(px, py)
                    picked[k * 4] += Pixels.alpha(c) / 255f
                    picked[k * 4 + 1] += Pixels.red(c) / 255f
                    picked[k * 4 + 2] += Pixels.green(c) / 255f
                    picked[k * 4 + 3] += Pixels.blue(c) / 255f
                    n++
                }
                if (n > 0) for (j in 0..3) picked[k * 4 + j] /= n
            }
            val carried = s.lanes ?: picked.copyOf().also { s.lanes = it }
            val out = carried.copyOf()
            val pickup = 1f - brush.smudgeStrength.coerceIn(0f, 0.98f)
            for (j in carried.indices) carried[j] += (picked[j] - carried[j]) * min(1f, pickup * s.laneRate[j / 4])
            out
        } else null

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
        val light = if (relief) 0f else (sinA * 0.6f - cosA * 0.8f) * brush.impasto * 1.2f
        // Thick paint: how high it stands, thinner under light pressure, with a blob at the start.
        val reliefScale = brush.relief * radius * 0.16f
        // A loaded brush lays thick paint, and the paint thins out as the brush empties.
        val reliefLoad = 1f / (1f + distance / (brush.charge.coerceAtLeast(0.05f) * 25f * max(params.size, 4f)))
        val body = (0.35f + 0.65f * dabPressure) * (0.45f + 0.55f * reliefLoad)
        val startLen = max(params.size, 4f) * 0.7f
        val blob = if (distance < startLen) 0.7f * (1f - distance / startLen) else 0f
        val groove = if (bristly) 0.3f else 0.1f
        // Bristle grooves, softened so they read as paint and not as a comb.
        val smooth: FloatArray? = if (relief && b != null) {
            val n = b.lanes
            val out = if (reliefCover.size == n) reliefCover else FloatArray(n).also { reliefCover = it }
            val reach = (0.1f * (n - 1) / 2f).toInt().coerceAtLeast(1)
            for (j in 0 until n) {
                var sum = 0f
                var cnt = 0
                for (k in j - reach..j + reach step max(1, reach / 2)) {
                    if (k < 0 || k >= n) continue
                    sum += b.cover[k]; cnt++
                }
                out[j] = (sum / cnt).coerceAtMost(1f)
            }
            out
        } else null
        val sinD = sin(s.directionAngle)
        val cosD = cos(s.directionAngle)
        val alphaLock = layer.alphaLock && !onMask
        val useBlend = !smudge && blendMode != BlendMode.NORMAL
        for (index in surface.tilesIn(rect)) {
            originalTile(index)
            val tr = surface.tileRect(index)
            val r = rect.intersect(tr)
            var dest: IntArray? = if (relief) reliefBaseTile(index) else surface.tile(index)
            if (alphaLock && dest == null) continue
            if (selection != null && selection.tileEmpty(index)) continue
            val heights = if (relief) reliefHeightTile(index) else null
            val lumps = if (relief) reliefLumps(index) else EMPTY_F
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    var m = maskAt(s, x + 0.5f, y + 0.5f, cx, cy, radius, cosA, sinA, invRound, grainDepth)
                    if (m <= 0f) continue
                    if (heights != null) {
                        // Thick paint has a torn, uneven edge, not a clean cut.
                        val ac = (-(x + 0.5f - cx) * sinD + (y + 0.5f - cy) * cosD) / radius
                        val a2 = if (ac < 0f) -ac else ac
                        if (a2 > 0.7f) {
                            val n = flowNoise.sampleWrapped(x * 0.55f + 19f, y * 0.55f + 47f)
                            m *= ((0.74f + 0.34f * n - a2) / 0.07f).coerceIn(0f, 1f)
                            if (m <= 0f) continue
                        }
                    }
                    val i = row or (x and TiledSurface.MASK)
                    val d = dest?.get(i) ?: 0
                    var t = m * strength * (if (smudge) 1f else params.opacity)
                    if (heights != null) {
                        val ddx = x + 0.5f - cx
                        val ddy = y + 0.5f - cy
                        // Paint pushed aside by the brush piles up in a ridge along each edge.
                        val across = (-ddx * sinD + ddy * cosD) / radius
                        val lump0 = lumps[i]
                        // The ridges wander, swell and shrink along the stroke.
                        val q = ((if (across < 0f) -across else across) - 0.84f - 0.08f * lump0) / 0.16f
                        val ridge = if (q > -1f && q < 1f) (1f - q * q) * (0.85f + 1.5f * lump0).coerceAtLeast(0.1f) else 0f
                        // Bristles plough grooves; the paint itself is lumpy, not a ribbon.
                        var cov = 1f
                        if (smooth != null) {
                            val v = (-(x + 0.5f - cx) * sinA + (y + 0.5f - cy) * cosA) / radius * invRound
                            cov = smooth[b!!.laneOf(v)]
                        }
                        val lump = lumps[i]
                        val surfaceH = body * (1f - groove + groove * cov) * (1f + lump * 0.7f)
                        val target = reliefScale * (surfaceH + ridge * 0.65f + blob * m)
                        heights[i] += (target - heights[i]) * min(1f, t * 1.5f)
                    }
                    var src = paint
                    var laneT = 1f
                    if (laneCols != null) {
                        val v = (-(x + 0.5f - cx) * sinA + (y + 0.5f - cy) * cosA) / radius * invRound
                        val f = ((v + 1f) * 0.5f * SMUDGE_LANES - 0.5f).coerceIn(0f, SMUDGE_LANES - 1f)
                        val k0 = f.toInt().coerceAtMost(SMUDGE_LANES - 2)
                        val ft = f - k0
                        val o0 = k0 * 4
                        val o1 = o0 + 4
                        laneT = s.laneHold[k0] + (s.laneHold[k0 + 1] - s.laneHold[k0]) * ft
                        src = Pixels.pack(
                            ((laneCols[o0] + (laneCols[o1] - laneCols[o0]) * ft) * 255f + 0.5f).toInt().coerceIn(0, 255),
                            ((laneCols[o0 + 1] + (laneCols[o1 + 1] - laneCols[o0 + 1]) * ft) * 255f + 0.5f).toInt().coerceIn(0, 255),
                            ((laneCols[o0 + 2] + (laneCols[o1 + 2] - laneCols[o0 + 2]) * ft) * 255f + 0.5f).toInt().coerceIn(0, 255),
                            ((laneCols[o0 + 3] + (laneCols[o1 + 3] - laneCols[o0 + 3]) * ft) * 255f + 0.5f).toInt().coerceIn(0, 255),
                        )
                    }
                    t *= laneT
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
                        dest = if (relief) reliefBaseCreate(index) else surface.tileOrCreate(index)
                    }
                    dest[i] = out
                }
            }
        }
        if (relief) reliefPending = IRect.unionOf(reliefPending, rect)
        return rect
    }

    // ---------------------------------------------------------------------------------------------
    // Relief (thick paint)
    //
    // A relief brush paints its colour into a per-stroke copy of the layer (the base) and builds a
    // height map of the paint. After each batch of input the changed area is lit from the top left:
    // slopes facing the light get lighter, with a sheen on the steepest, and slopes facing away get
    // darker. The lit paint is what goes on the layer.

    private val reliefBase = HashMap<Int, IntArray?>()
    private val reliefHeights = arrayOfNulls<FloatArray>(surface.tilesX * surface.tilesY)
    private var reliefPending: IRect? = null

    private fun reliefBaseTile(index: Int): IntArray? {
        if (!reliefBase.containsKey(index)) {
            originalTile(index)
            reliefBase[index] = surface.copyTile(index)
        }
        return reliefBase[index]
    }

    private fun reliefBaseCreate(index: Int): IntArray =
        reliefBaseTile(index) ?: IntArray(TiledSurface.TILE * TiledSurface.TILE).also { reliefBase[index] = it }

    private fun reliefHeightTile(index: Int): FloatArray =
        reliefHeights[index] ?: FloatArray(TiledSurface.TILE * TiledSurface.TILE).also { reliefHeights[index] = it }

    private var reliefCover = FloatArray(0)
    private val reliefLumpTiles = arrayOfNulls<FloatArray>(surface.tilesX * surface.tilesY)

    /** Lumpiness of the paint surface, -0.5..0.5, fixed to the canvas. */
    private fun reliefLumps(index: Int): FloatArray {
        reliefLumpTiles[index]?.let { return it }
        val tr = surface.tileRect(index)
        val out = FloatArray(TiledSurface.TILE * TiledSurface.TILE)
        for (ty in 0 until TiledSurface.TILE) for (tx in 0 until TiledSurface.TILE) {
            out[(ty shl TiledSurface.SHIFT) or tx] = valueNoise((tr.left + tx) * 0.07f + 31f, (tr.top + ty) * 0.07f + 17f) - 0.5f
        }
        reliefLumpTiles[index] = out
        return out
    }

    private var heightBuf = FloatArray(0)

    private fun heightAt(x: Int, y: Int): Float {
        if (x < 0 || y < 0 || x >= doc.width || y >= doc.height) return 0f
        val t = reliefHeights[(y shr TiledSurface.SHIFT) * surface.tilesX + (x shr TiledSurface.SHIFT)] ?: return 0f
        return t[((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)]
    }

    private fun shadeRelief(): IRect? {
        val pending = reliefPending ?: return null
        reliefPending = null
        val rect = IRect(pending.left - 2, pending.top - 2, pending.right + 2, pending.bottom + 2).intersect(doc.bounds)
        if (rect.isEmpty) return null
        val shift = TiledSurface.SHIFT
        // Heights with a 2-pixel margin, gathered once so the slope needs no bounds checks.
        val ox = rect.left - 2
        val oy = rect.top - 2
        val ow = rect.width + 4
        val oh = rect.height + 4
        if (heightBuf.size < ow * oh) heightBuf = FloatArray(ow * oh)
        val hb = heightBuf
        for (yy in 0 until oh) for (xx in 0 until ow) hb[yy * ow + xx] = heightAt(ox + xx, oy + yy)
        for (index in surface.tilesIn(rect)) {
            if (!reliefBase.containsKey(index)) continue
            val base = reliefBase[index] ?: continue
            val heights = reliefHeights[index]
            val r = rect.intersect(surface.tileRect(index))
            var dest: IntArray? = surface.tile(index)
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    val i = row or (x and TiledSurface.MASK)
                    val c = base[i]
                    var out = c
                    if (heights != null && c != 0) {
                        // Height rising towards the bottom right means the paint faces the light.
                        // Slopes over a few pixels, so the small steps between dabs don't show.
                        val j = (y - oy) * ow + (x - ox)
                        val gx = (hb[j + 1] - hb[j - 1]) * 0.25f + (hb[j + 2] - hb[j - 2]) * 0.125f +
                            (hb[j - ow + 1] - hb[j - ow - 1] + hb[j + ow + 1] - hb[j + ow - 1]) * 0.125f
                        val gy = (hb[j + ow] - hb[j - ow]) * 0.25f + (hb[j + 2 * ow] - hb[j - 2 * ow]) * 0.125f +
                            (hb[j + ow - 1] - hb[j - ow - 1] + hb[j + ow + 1] - hb[j - ow + 1]) * 0.125f
                        val lit = (gx + gy) * 0.45f
                        if (lit != 0f) {
                            out = shadePaint(c, 1f + lit.coerceIn(-0.4f, 0.4f))
                            val sheen = ((lit - 0.34f) * 1.1f).coerceIn(0f, 0.3f)
                            if (sheen > 0f) {
                                val a = Pixels.alpha(out)
                                out = Pixels.pack(
                                    a,
                                    Pixels.red(out) + ((a - Pixels.red(out)) * sheen).toInt(),
                                    Pixels.green(out) + ((a - Pixels.green(out)) * sheen).toInt(),
                                    Pixels.blue(out) + ((a - Pixels.blue(out)) * sheen).toInt(),
                                )
                            }
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
    // Cached by hand: `by lazy` is slow in hot loops on Kotlin/JS (the web app).
    private var flowNoiseCache: Mask? = null
    private val flowNoise: Mask get() = flowNoiseCache ?: Textures.grain(GrainTexture.PAPER)!!.also { flowNoiseCache = it }
    private var washPaperCache: Mask? = null
    private var washPaperLoaded = false
    private val washPaper: Mask?
        get() {
            if (!washPaperLoaded) {
                washPaperCache = grain ?: Textures.grain(GrainTexture.COLD_PRESS)
                washPaperLoaded = true
            }
            return washPaperCache
        }
    private var granulesCache: Mask? = null
    private val granules: Mask get() = granulesCache ?: Textures.grain(GrainTexture.DRAWING)!!.also { granulesCache = it }

    /** How far, in pixels, pigment travels to the edge of the wash. */
    private val rimWidth: Float = (params.size * 0.1f).coerceIn(2f, 10f)

    /** Whether earlier washes are still wet, checked once when the stroke starts painting. */
    private var canvasWetChecked = false
    private var lastBleedX = Float.NaN
    private var lastBleedY = Float.NaN
    private var canvasWet = false

    private fun canvasWetNow(): Boolean {
        if (!canvasWetChecked) {
            canvasWetChecked = true
            canvasWet = wetCanvas != null && !onMask && wetCanvas.anyWet(lastTime)
            if (canvasWet) canvasDrying = wetCanvas!!.dryingAt(lastTime)
        }
        return canvasWet
    }

    private var canvasDrying = FloatArray(0)

    private fun canvasWetAt(x: Int, y: Int): Float = if (canvasWet) wetCanvas!!.at(x, y, canvasDrying) else 0f

    private fun washDab(
        cx: Float, cy: Float, radius: Float, angle: Float, pigment: Float, flow: Float,
        speed: Float, distance: Float,
    ): IRect? {
        // Water runs into paper that is still wet, well past the brush. The bloom only needs
        // working out every so often along the stroke, since each one reaches far.
        var bleeds = canvasWetNow()
        if (bleeds) {
            val moved = hypot(cx - lastBleedX, cy - lastBleedY)
            if (moved.isNaN() || moved >= radius * 0.4f) {
                lastBleedX = cx
                lastBleedY = cy
            } else {
                bleeds = false
            }
        }
        val reachOut = if (bleeds) 2.4f else 1f
        val ext = radius * reachScale * reachOut
        val rect = IRect.of(cx - ext - 1, cy - ext - 1, cx + ext + 1, cy + ext + 1).intersect(doc.bounds)
        if (rect.isEmpty) return null

        // Pigment along the stroke: a fresh brush puts down a bead of colour where it lands and
        // wherever it slows down, then thins out as it runs out of water.
        val base = max(params.size, 2f)
        val load = 1f / (1f + distance / (brush.charge.coerceAtLeast(0.05f) * 30f * base))
        val landing = if (distance < base) 0.45f * (1f - distance / base) else 0f
        val slow = (1f - speed).let { it * it } * 0.45f
        val wander = 1f + 0.6f * (valueNoise(distance / base * 0.6f + 41f, params.seed * 7.31f) - 0.5f)
        val pig = pigment * (0.5f + 0.5f * load) * (1f + landing + slow) * wander
        val water = flow * (0.55f + 0.45f * load)

        val cosA = cos(angle)
        val sinA = sin(angle)
        val invRound = 1f / brush.roundness.coerceIn(0.05f, 1f)
        val shift = TiledSurface.SHIFT
        val outer2 = reachOut * reachOut
        for (index in surface.tilesIn(rect)) {
            if (selection != null && selection.tileEmpty(index)) continue
            if (originalTile(index) == null && layer.alphaLock && !onMask) continue
            val r = rect.intersect(surface.tileRect(index))
            var waterTile = washWater[index]
            var pigTile = washPigment[index]
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                for (x in r.left until r.right) {
                    val dx = x + 0.5f - cx
                    val dy = y + 0.5f - cy
                    val u = (dx * cosA + dy * sinA) / radius
                    val v = (-dx * sinA + dy * cosA) / radius * invRound
                    val d2 = u * u + v * v
                    val i = row or (x and TiledSurface.MASK)
                    if (d2 <= 1.02f) {
                        val m = tip.sample((u + 1f) * 0.5f, (v + 1f) * 0.5f)
                        if (m <= 0f) continue
                        if (waterTile == null) {
                            waterTile = FloatArray(TiledSurface.TILE * TiledSurface.TILE).also { washWater[index] = it }
                            pigTile = FloatArray(TiledSurface.TILE * TiledSurface.TILE).also { washPigment[index] = it }
                        }
                        waterTile[i] += (1f - waterTile[i]) * min(1f, m * water)
                        // The newest dab sets the pigment where it lands, so changes along the
                        // stroke show smoothly instead of as overlapping circles.
                        val pt = pigTile!!
                        pt[i] += (pig - pt[i]) * min(1f, m * 1.6f)
                    } else {
                        // Water running into the wet paper around the brush: it blooms out in an
                        // irregular, feathery shape and carries a little pigment with it.
                        if (!bleeds || d2 > outer2) continue
                        val cw = canvasWetAt(x, y)
                        if (cw <= 0.05f) continue
                        val reach = 1f - (sqrt(d2) - 1f) / (reachOut - 1f)
                        val f = (reach * (0.35f + 1.1f * valueNoise(x * 0.09f + 7f, y * 0.09f + 3f)) - 0.1f).coerceIn(0f, 1f) * cw
                        if (f <= 0f) continue
                        if (waterTile == null) {
                            waterTile = FloatArray(TiledSurface.TILE * TiledSurface.TILE).also { washWater[index] = it }
                            pigTile = FloatArray(TiledSurface.TILE * TiledSurface.TILE).also { washPigment[index] = it }
                        }
                        val w = f * 0.9f
                        if (w > waterTile[i]) waterTile[i] = w
                        val pt = pigTile!!
                        val pb = pig * f * 0.9f
                        if (pb > pt[i]) pt[i] = pb
                    }
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

    /**
     * Where the wash actually wets the paper. Its outline wanders on two scales: broad swells from
     * the flow of the water, and fine raggedness from the paper. On paper that is still wet from an
     * earlier wash ([cw]) the water spreads further and the edge goes soft.
     */
    private fun wetAt(x: Int, y: Int, cw: Float): Float {
        if (x < 0 || y < 0 || x >= doc.width || y >= doc.height) return 0f
        val index = (y shr TiledSurface.SHIFT) * surface.tilesX + (x shr TiledSurface.SHIFT)
        val t0 = washWater[index] ?: return 0f
        val i = ((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)
        val w = t0[i]
        if (w <= 0f) return 0f
        // The wander moves the edge; deep inside a full wash it can't open holes.
        val level = w + washStatic(index)[i] * (1.1f - w).coerceAtLeast(0.35f)
        // Paint laid on dry paper dries with a crisp edge; a wetter wash feathers out.
        val soft = 0.16f + (1f - brush.edgeDarken) * 0.4f + cw * 0.5f
        val t = ((level - 0.32f + cw * 0.2f) / soft).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private val washStatics = arrayOfNulls<FloatArray>(surface.tilesX * surface.tilesY)

    /**
     * The parts of the wash's look that depend only on where a pixel is, worked out once per tile
     * per stroke rather than on every re-shade: how far the edge wanders there, how much pigment
     * the rim gathers, and how granulation and flow vary the density.
     */
    private fun washStatic(index: Int): FloatArray {
        washStatics[index]?.let { return it }
        val out = FloatArray(STATIC * 3)
        val tr = surface.tileRect(index)
        val wobble = brush.edgeWobble
        val paperTex = washPaper
        for (ty in 0 until TiledSurface.TILE) {
            val y = tr.top + ty
            for (tx in 0 until TiledSurface.TILE) {
                val x = tr.left + tx
                val i = (ty shl TiledSurface.SHIFT) or tx
                val broad = valueNoise(x * 0.045f + 211f, y * 0.045f + 157f) - 0.5f
                val n = flowNoise.sampleWrapped(x * 0.9f + 97f, y * 0.9f + 31f) - 0.5f
                val paper = paperTex?.sampleWrapped(x.toFloat(), y.toFloat())?.minus(0.5f) ?: 0f
                out[i] = (broad * 0.8f + n * 0.3f + paper * 0.18f) * wobble
                // How much pigment the rim gathers changes along the edge.
                val rv = valueNoise(x * 0.025f + 401f, y * 0.025f + 89f)
                // Some stretches get a strong dark edge, some almost none.
                val rq = ((rv - 0.4f) / 0.45f).coerceIn(0f, 1f)
                out[STATIC + i] = brush.edgeDarken * 2f * (0.05f + 2.6f * rq * rq)
                var d = 1f
                if (brush.granulation > 0f) {
                    // Pigment grains clump and settle in the hollows of the paper.
                    // Pigment settles into the dips of the paper itself, in clumps.
                    val clump = granules.sampleWrapped(x / 3.4f + 63f, y / 3.4f + 17f)
                    val dip = paperTex?.sampleWrapped(x.toFloat(), y.toFloat()) ?: 0.5f
                    val h = dip * 0.6f + clump * 0.4f
                    d *= 1f + brush.granulation * (0.5f - h) * 1.6f
                }
                if (brush.pigmentFlow > 0f) {
                    val f = flowNoise.sampleWrapped(x * 0.07f + 13f, y * 0.07f + 71f)
                    val g = valueNoise(x * 0.02f + 151f, y * 0.02f + 33f)
                    d *= 1f + brush.pigmentFlow * ((f - 0.5f) * 0.6f + (g - 0.5f) * 0.9f)
                }
                out[2 * STATIC + i] = d
            }
        }
        washStatics[index] = out
        return out
    }

    private val rimDirs: FloatArray = FloatArray(16).also { d ->
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f + 0.39f
            d[k * 2] = cos(a)
            d[k * 2 + 1] = sin(a)
        }
    }

    // Scratch buffers for shadeWash, reused between passes.
    private var wetBuf = FloatArray(0)
    private var canvasBuf = FloatArray(0)
    private var aroundBuf = FloatArray(0)

    /** Re-shades the wash wherever it changed since the last call. */
    private fun shadeWash(): IRect? {
        val pending = washPending ?: return null
        washPending = null
        val r1 = rimWidth * 0.75f
        val r2 = rimWidth * 0.35f
        val reachOut = r1.toInt() + 2
        val rect = IRect(pending.left - reachOut, pending.top - reachOut, pending.right + reachOut, pending.bottom + reachOut)
            .intersect(doc.bounds)
        if (rect.isEmpty) return null

        // Wetness of every pixel the rim test can look at, worked out once per pass. The buffer has
        // a margin all round (zero off the canvas), so the rim test needs no bounds checks.
        val outer = IRect(rect.left - reachOut, rect.top - reachOut, rect.right + reachOut, rect.bottom + reachOut)
        val ow = outer.right - outer.left
        val oh = outer.bottom - outer.top
        if (wetBuf.size < ow * oh) {
            wetBuf = FloatArray(ow * oh)
            canvasBuf = FloatArray(ow * oh)
            aroundBuf = FloatArray(ow * oh)
        }
        val wb = wetBuf
        val cb = canvasBuf
        val mb = aroundBuf
        val bleeds = canvasWet
        for (y in outer.top until outer.bottom) {
            val o = (y - outer.top) * ow - outer.left
            val inY = y >= 0 && y < doc.height
            for (x in outer.left until outer.right) {
                if (!inY || x < 0 || x >= doc.width) {
                    cb[o + x] = 0f; wb[o + x] = 0f; mb[o + x] = 0f
                    continue
                }
                val cw = if (bleeds) canvasWetAt(x, y) else 0f
                val w = wetAt(x, y, cw)
                cb[o + x] = cw
                wb[o + x] = w
                mb[o + x] = if (cw > w) cw else w
            }
        }
        val offsets = IntArray(16)
        for (k in 0 until 8) {
            val ddx = rimDirs[k * 2]
            val ddy = rimDirs[k * 2 + 1]
            offsets[k * 2] = kotlin.math.round(ddy * r1).toInt() * ow + kotlin.math.round(ddx * r1).toInt()
            offsets[k * 2 + 1] = kotlin.math.round(ddy * r2).toInt() * ow + kotlin.math.round(ddx * r2).toInt()
        }

        val strokeOpacity = params.opacity.coerceIn(0f, 1f)
        val alphaLock = layer.alphaLock && !onMask
        val shift = TiledSurface.SHIFT
        for (index in surface.tilesIn(rect)) {
            val water = washWater[index] ?: continue
            val pig = washPigment[index] ?: continue
            val orig = originalTile(index)
            if (alphaLock && orig == null) continue
            val tr = surface.tileRect(index)
            val r = rect.intersect(tr)
            var dest: IntArray? = surface.tile(index)
            val st = washStatic(index)
            for (y in r.top until r.bottom) {
                val row = (y and TiledSurface.MASK) shl shift
                val o = (y - outer.top) * ow - outer.left
                for (x in r.left until r.right) {
                    val i = row or (x and TiledSurface.MASK)
                    if (water[i] <= 0f) continue
                    val wet = wb[o + x]
                    var density = 0f
                    if (wet > 0f) {
                        // Edge darkening: compare the wetness here with its surroundings. Inside the
                        // rim the surroundings are drier, and that is where pigment piles up. Paper
                        // still wet from an earlier wash counts as wet, so no rim forms there.
                        val j = o + x
                        var around = 0f
                        for (k in 0 until 16) around += mb[j + offsets[k]]
                        around /= 16f
                        // How much pigment the rim gathers changes along the edge.
                        val rim = ((wet - around) * 3.5f).coerceIn(0f, 1f)
                        // Pigment dropped into wet paper spreads out and dries lighter.
                        density = pig[i] * wet * (1f + rim * st[STATIC + i]) * st[2 * STATIC + i] * (1f - 0.35f * cb[j])
                    }
                    val a = (density * strokeOpacity).coerceIn(0f, 1f)
                    val ov = orig?.get(i) ?: 0
                    var out = when {
                        a <= 0f -> ov
                        alphaLock -> lockedPaint(ov, a)
                        else -> blendMode.blend(ov, srcPixel(a))
                    }
                    if (selection != null) out = Pixels.lerp(ov, out, selection.coverage(x, y))
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
        reliefBase.clear()
        reliefHeights.fill(null)
        reliefPending = null
        lastBleedX = Float.NaN
        lastBleedY = Float.NaN
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
        private const val STATIC = TiledSurface.TILE * TiledSurface.TILE
        private const val SMUDGE_LANES = 24
        private val EMPTY_F = FloatArray(0)
        private val SMUDGE_U = floatArrayOf(-0.3f, 0f, 0.3f)

        /** Smooth value noise, 0..1, with features about one unit apart. */
        fun valueNoise(x: Float, y: Float): Float {
            val fx = kotlin.math.floor(x)
            val fy = kotlin.math.floor(y)
            val ix = fx.toInt()
            val iy = fy.toInt()
            var tx = x - fx
            var ty = y - fy
            tx = tx * tx * (3f - 2f * tx)
            ty = ty * ty * (3f - 2f * ty)
            val a = hash(ix, iy)
            val b = hash(ix + 1, iy)
            val c = hash(ix, iy + 1)
            val d = hash(ix + 1, iy + 1)
            return (a + (b - a) * tx) * (1f - ty) + (c + (d - c) * tx) * ty
        }

        private fun hash(x: Int, y: Int): Float {
            var h = x * 374761393 + y * 668265263
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            return (h and 0xFFFF) / 65535f
        }

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
