package com.artisthaven.engine.stroke

import com.artisthaven.engine.brush.GrainTexture
import com.artisthaven.engine.brush.Mask
import com.artisthaven.engine.brush.Textures
import com.artisthaven.engine.document.Selection
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.raster.TiledSurface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Watercolour water on one layer: a small fluid simulation on a grid of [CELL]-pixel cells.
 *
 * Brush dabs add water and pigment to the grid. Every [step] the water soaks into the paper, flows
 * downhill (water level plus paper height) into neighbouring cells that are already wet, stays
 * pinned at the edge of dry paper, and carries suspended pigment along. Pigment settles out as the
 * water thins. Water evaporates fastest at the edge of a wash, so water keeps flowing outwards and
 * leaves a dark rim (the coffee-ring effect). Paint keeps moving after the pen lifts, until the
 * water has gone.
 *
 * The layer's pixels in the wet area are re-rendered from the pixels that were there before the
 * water arrived ("base") and the pigment in the grid, so pigment can move freely while wet. Once
 * every cell is dry the result is final: the grid's pigment is cleared and the pixels stay ([bake]).
 * The paper stays damp a little longer, so a new wash can still run into it.
 *
 * Based on Curtis et al. 1997 ("Computer-Generated Watercolor"), simplified to run on the CPU:
 * no velocity field, just height-driven flow. Not part of the document: only the state of the paper.
 */
class WaterSim(val width: Int, val height: Int) {
    val gw = (width + CELL - 1) / CELL
    val gh = (height + CELL - 1) / CELL
    private val n = gw * gh

    // Surface water, paper saturation, paper height, ever-wet footprint.
    private val water = FloatArray(n)
    private val sat = FloatArray(n)
    private val paperH = FloatArray(n)
    private val foot = FloatArray(n)
    /** Cells the brush itself painted (their outline is kept at full resolution in [footPx]). */
    private val brushed = ByteArray(n)
    /** Where cells hold paint, for interpolating pigment only between painted cells. */
    private val occ = FloatArray(n)
    /** How much each cell differs from what was last drawn is not enough near a new brush mark. */
    private var brushRect: IRect? = null
    /** How much each cell dries faster than usual at an edge: rims are strong in places, faint in others. */
    private val rimVar = FloatArray(n)
    // Suspended and deposited pigment, as absorbance per channel.
    private val gr = FloatArray(n)
    private val gg = FloatArray(n)
    private val gb = FloatArray(n)
    private val dr = FloatArray(n)
    private val dg = FloatArray(n)
    private val db = FloatArray(n)
    // Flow deltas for one step.
    private val fw = FloatArray(n)
    private val fr = FloatArray(n)
    private val fg = FloatArray(n)
    private val fb = FloatArray(n)

    /** Cells the current selection lets water into (1), or null with no selection. */
    private var allow: ByteArray? = null

    // Bounds, in cells, inclusive. Empty when x1 < x0.
    private var wx0 = gw; private var wy0 = gh; private var wx1 = -1; private var wy1 = -1 // wet
    private var px0 = gw; private var py0 = gh; private var px1 = -1; private var py1 = -1 // painted
    private var sx0 = gw; private var sy0 = gh; private var sx1 = -1; private var sy1 = -1 // damp
    private var qx0 = gw; private var qy0 = gh; private var qx1 = -1; private var qy1 = -1 // flowed, to render
    private var bx0 = gw; private var by0 = gh; private var bx1 = -1; private var by1 = -1 // brushed, to render

    // How the latest stroke wants its water to behave.
    private var leak = 0.02f
    private var wBreak = 0.55f
    /** How far past the brush very wet brushes dampen the paper, as a multiple of the radius. */
    private var halo = 0f
    private var evap = 0.0022f
    private var edgeEvap = 3f
    private var edgeDep = 2f
    private var granulation = 0.3f
    /** How strongly pigment is carried out to the drying edge (the coffee-ring effect). */
    private var drift = 0.05f
    /** How sharp the outline is: high for paint on dry paper, low for paint into wet paper. */
    private var edgeSharpness = 8f

    // Pixels under the wash before it arrived, per tile, while there is pigment in the grid.
    private val tilesX = (width + TiledSurface.TILE - 1) / TiledSurface.TILE
    private val tilesY = (height + TiledSurface.TILE - 1) / TiledSurface.TILE
    private val base = arrayOfNulls<IntArray>(tilesX * tilesY)
    private val baseTaken = BooleanArray(tilesX * tilesY)
    /** The brush's outline at full resolution, 0..255 per pixel, per tile. */
    private val footPx = arrayOfNulls<ByteArray>(tilesX * tilesY)

    private val paper: Mask = Textures.grain(GrainTexture.COLD_PRESS)!!
    /** Fine speckle where heavy pigment settles (granulation). */
    private val granules: Mask = Textures.grain(GrainTexture.DRAWING)!!

    init {
        // The paper's hills and hollows, which the water runs around.
        for (y in 0 until gh) for (x in 0 until gw) {
            val rv = StrokeSession.valueNoise(x * 0.045f + 71f, y * 0.045f + 23f)
            rimVar[y * gw + x] = 0.15f + 2.4f * rv * rv * rv
            paperH[y * gw + x] = paper.sampleWrapped(x * 1.7f + 31f, y * 1.7f + 11f) * 0.55f +
                StrokeSession.valueNoise(x * 0.07f + 5f, y * 0.07f + 9f) * 0.45f
        }
    }

    /** True while any cell holds surface water (paint can still move). */
    val isWet: Boolean get() = wx1 >= wx0

    /** True while the paper is damp anywhere, so new water will run into it. */
    val isDamp: Boolean get() = sx1 >= sx0

    val isActive: Boolean get() = isWet || isDamp || px1 >= px0

    /** Sets how the next stroke's water behaves, from its brush. */
    fun configure(wetness: Float, edgeDarken: Float, granulation: Float, selection: Selection?) {
        val wt = wetness.coerceIn(0f, 1f)
        leak = 0.01f + 0.09f * wt * wt
        // Wet brushes push their water out over dry paper; drier ones stay where they are put.
        wBreak = 0.6f - 0.45f * wt * wt
        evap = 0.0016f - 0.0009f * wt
        halo = if (wt > 0.6f) (wt - 0.6f) / 0.3f * 1.2f else 0f
        edgeEvap = 5.5f * edgeDarken
        edgeDep = 0.5f + 2f * edgeDarken
        drift = 0f
        val soft = (1f - edgeDarken) * wt
        edgeSharpness = 8f - 6.5f * soft
        this.granulation = granulation
        allow = selection?.let { sel ->
            ByteArray(n).also { a ->
                for (y in 0 until gh) for (x in 0 until gw) {
                    val px = min(width - 1, x * CELL + CELL / 2)
                    val py = min(height - 1, y * CELL + CELL / 2)
                    a[y * gw + x] = if (sel.coverage(px, py) > 0.3f) 1 else 0
                }
            }
        }
    }

    /**
     * A brush dab: brings the water under the brush up to [waterLevel] and its pigment towards
     * [pigment] times the colour's absorbance ([ar], [ag], [ab]).
     */
    fun dab(
        cx: Float, cy: Float, radius: Float, angle: Float, roundness: Float, tip: Mask,
        waterLevel: Float, pigment: Float, ar: Float, ag: Float, ab: Float,
    ) {
        val outer = radius * (1f + halo)
        val reach = outer + CELL
        val x0 = max(0, floor((cx - reach) / CELL).toInt())
        val y0 = max(0, floor((cy - reach) / CELL).toInt())
        val x1 = min(gw - 1, floor((cx + reach) / CELL).toInt())
        val y1 = min(gh - 1, floor((cy + reach) / CELL).toInt())
        if (x1 < x0 || y1 < y0) return
        val cosA = cos(angle)
        val sinA = sin(angle)
        val invRound = 1f / roundness.coerceIn(0.05f, 1f)
        val sel = allow
        for (y in y0..y1) {
            val py = y * CELL + CELL * 0.5f
            for (x in x0..x1) {
                val px = x * CELL + CELL * 0.5f
                val dx = px - cx
                val dy = py - cy
                val u = (dx * cosA + dy * sinA) / radius
                val v = (-dx * sinA + dy * cosA) / radius * invRound
                val d2 = u * u + v * v
                val i = y * gw + x
                if (sel != null && sel[i].toInt() == 0) continue
                if (d2 > 1.02f) {
                    // Painting into wet paper: a very wet brush dampens the paper around it, and
                    // its paint will run out into that.
                    if (halo <= 0f) continue
                    val t = (kotlin.math.sqrt(d2) - 1f) / halo
                    if (t >= 1f) continue
                    val damp = 0.45f * (1f - t) * (0.6f + 0.8f * StrokeSession.valueNoise(px * 0.05f, py * 0.05f))
                    if (damp > sat[i]) {
                        sat[i] = damp
                        growDamp(x, y)
                    }
                    continue
                }
                val m = tip.sample((u + 1f) * 0.5f, (v + 1f) * 0.5f)
                if (m <= 0.02f) continue
                val w0 = water[i]
                val target = waterLevel * (0.4f + 0.6f * m)
                if (target > w0) water[i] = w0 + (target - w0) * min(1f, m * 1.5f)
                val k = min(1f, m * 1.2f)
                val tr = pigment * ar
                val tg = pigment * ag
                val tb = pigment * ab
                if (w0 > 0.08f) {
                    // Into wet paint: the brush lets out more water, which pushes outwards, and its
                    // pigment mixes with what is there instead of replacing it.
                    water[i] += 0.02f * m * waterLevel
                    val ka = k * 0.12f
                    gr[i] = min(gr[i] + tr * ka, max(gr[i], tr * 1.6f))
                    gg[i] = min(gg[i] + tg * ka, max(gg[i], tg * 1.6f))
                    gb[i] = min(gb[i] + tb * ka, max(gb[i], tb * 1.6f))
                } else {
                    // Pigment: the brush tints the water towards its own strength, and a weaker
                    // brush dilutes stronger paint only a little.
                    val kd = k * 0.25f
                    gr[i] += (tr - gr[i]) * (if (tr > gr[i]) k else kd)
                    gg[i] += (tg - gg[i]) * (if (tg > gg[i]) k else kd)
                    gb[i] += (tb - gb[i]) * (if (tb > gb[i]) k else kd)
                }
                brushed[i] = 1
                if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y
                growWet(x, y)
                growPaint(x, y)
                growDamp(x, y)
            }
        }
        outline(cx, cy, radius, cosA, sinA, invRound, tip)
    }

    /** Records the brush's exact outline at full resolution. */
    private fun outline(cx: Float, cy: Float, radius: Float, cosA: Float, sinA: Float, invRound: Float, tip: Mask) {
        val x0 = max(0, (cx - radius - 1f).toInt())
        val y0 = max(0, (cy - radius - 1f).toInt())
        val x1 = min(width - 1, (cx + radius + 1f).toInt())
        val y1 = min(height - 1, (cy + radius + 1f).toInt())
        val sel = allow
        val invR = 1f / radius
        for (y in y0..y1) {
            val ty = y shr TiledSurface.SHIFT
            val dy = y + 0.5f - cy
            val rowT = (y and TiledSurface.MASK) shl TiledSurface.SHIFT
            val cRow = (y / CELL) * gw
            for (x in x0..x1) {
                val dx = x + 0.5f - cx
                val u = (dx * cosA + dy * sinA) * invR
                val v = (-dx * sinA + dy * cosA) * invR * invRound
                if (u * u + v * v > 1.02f) continue
                if (sel != null && sel[cRow + x / CELL].toInt() == 0) continue
                val m = tip.sample((u + 1f) * 0.5f, (v + 1f) * 0.5f)
                if (m <= 0.02f) continue
                val t = ty * tilesX + (x shr TiledSurface.SHIFT)
                val px = footPx[t] ?: ByteArray(TiledSurface.TILE * TiledSurface.TILE).also { footPx[t] = it }
                val k = rowT or (x and TiledSurface.MASK)
                val q = (min(1f, m * 5f) * 255f).toInt()
                if (q > (px[k].toInt() and 0xFF)) px[k] = q.toByte()
            }
        }
    }

    /**
     * Where water spread beyond the brush. Cells the brush painted count only where water ran out
     * of them, so a stroke that stays put keeps the brush's own smooth outline.
     */
    private fun spreadFoot(i: Int): Float {
        if (brushed[i].toInt() == 0) return foot[i]
        val x = i % gw
        if (x > 0 && brushed[i - 1].toInt() == 0 && foot[i - 1] > 0f) return 1f
        if (x < gw - 1 && brushed[i + 1].toInt() == 0 && foot[i + 1] > 0f) return 1f
        if (i >= gw && brushed[i - gw].toInt() == 0 && foot[i - gw] > 0f) return 1f
        if (i + gw < n && brushed[i + gw].toInt() == 0 && foot[i + gw] > 0f) return 1f
        return 0f
    }

    private fun growPending(x: Int, y: Int) {
        if (x < qx0) qx0 = x; if (x > qx1) qx1 = x; if (y < qy0) qy0 = y; if (y > qy1) qy1 = y
    }

    /**
     * The pixel area changed since the last call, to re-render; null if none. Where the brush
     * went is always included; where the water flowed only if [withFlow].
     */
    fun takePending(withFlow: Boolean = true): IRect? {
        var r: IRect? = null
        if (bx1 >= bx0) {
            r = pixelRect(intArrayOf(bx0, by0, bx1, by1))
            brushRect = IRect.unionOf(brushRect, r)
            bx0 = gw; by0 = gh; bx1 = -1; by1 = -1
        }
        if (withFlow && qx1 >= qx0) {
            r = IRect.unionOf(r, pixelRect(intArrayOf(qx0, qy0, qx1, qy1)))
            qx0 = gw; qy0 = gh; qx1 = -1; qy1 = -1
        }
        return r
    }

    private fun growWet(x: Int, y: Int) {
        if (x < wx0) wx0 = x; if (x > wx1) wx1 = x; if (y < wy0) wy0 = y; if (y > wy1) wy1 = y
    }

    private fun growPaint(x: Int, y: Int) {
        if (x < px0) px0 = x; if (x > px1) px1 = x; if (y < py0) py0 = y; if (y > py1) py1 = y
    }

    private fun growDamp(x: Int, y: Int) {
        if (x < sx0) sx0 = x; if (x > sx1) sx1 = x; if (y < sy0) sy0 = y; if (y > sy1) sy1 = y
    }

    /**
     * Advances the water one tick (about 1/60 s). Returns the cell box (x0, y0, x1, y1) whose
     * pigment may have changed, or null if nothing moved.
     */
    fun step(): IntArray? {
        if (!isWet) {
            stepDamp()
            return null
        }
        // Water can only reach one cell beyond the wet box per step.
        val x0 = max(0, wx0 - 1)
        val y0 = max(0, wy0 - 1)
        val x1 = min(gw - 1, wx1 + 1)
        val y1 = min(gh - 1, wy1 + 1)
        for (y in y0..y1) {
            val row = y * gw
            for (x in x0..x1) {
                val i = row + x
                fw[i] = 0f; fr[i] = 0f; fg[i] = 0f; fb[i] = 0f
            }
        }
        val sel = allow
        // 1. Flow: downhill on water level plus paper height, into wet or damp paper only.
        for (y in max(wy0, 0)..min(wy1, gh - 1)) {
            val row = y * gw
            for (x in max(wx0, 0)..min(wx1, gw - 1)) {
                val i = row + x
                val w = water[i]
                if (w <= EPS) continue
                val head = w + K_HEIGHT * paperH[i]
                val canBreak = w > wBreak
                var f0 = 0f; var f1 = 0f; var f2 = 0f; var f3 = 0f
                if (x > 0) f0 = flowTo(head, i - 1, canBreak, sel)
                if (x < gw - 1) f1 = flowTo(head, i + 1, canBreak, sel)
                if (y > 0) f2 = flowTo(head, i - gw, canBreak, sel)
                if (y < gh - 1) f3 = flowTo(head, i + gw, canBreak, sel)
                // Evaporation at the edge draws a steady current outwards that carries pigment
                // to the edge, where it dries as a dark line.
                if (drift > 0f) {
                    val ei = exposure(x, y, i)
                    if (x > 0) edgeDrift(i, i - 1, ei, exposure(x - 1, y, i - 1))
                    if (x < gw - 1) edgeDrift(i, i + 1, ei, exposure(x + 1, y, i + 1))
                    if (y > 0) edgeDrift(i, i - gw, ei, exposure(x, y - 1, i - gw))
                    if (y < gh - 1) edgeDrift(i, i + gw, ei, exposure(x, y + 1, i + gw))
                }
                val total = f0 + f1 + f2 + f3
                if (total <= 0f) continue
                val scale = if (total > w * 0.45f) w * 0.45f / total else 1f
                val inv = scale / w
                val pr = gr[i]; val pg = gg[i]; val pb = gb[i]
                fw[i] -= total * scale
                if (f0 > 0f) move(i, i - 1, f0, scale, inv, pr, pg, pb)
                if (f1 > 0f) move(i, i + 1, f1, scale, inv, pr, pg, pb)
                if (f2 > 0f) move(i, i - gw, f2, scale, inv, pr, pg, pb)
                if (f3 > 0f) move(i, i + gw, f3, scale, inv, pr, pg, pb)
            }
        }
        // 2. Per cell: absorb, settle pigment, evaporate (fastest at the edge), dry.
        var nx0 = gw; var ny0 = gh; var nx1 = -1; var ny1 = -1
        for (y in y0..y1) {
            val row = y * gw
            for (x in x0..x1) {
                val i = row + x
                var w = water[i] + fw[i]
                gr[i] += fr[i]; gg[i] += fg[i]; gb[i] += fb[i]
                if (w <= EPS) {
                    if (water[i] > EPS || w > 0f) {
                        // Just dried: everything left in the water settles here.
                        water[i] = 0f
                        dr[i] += gr[i]; dg[i] += gg[i]; db[i] += gb[i]
                        gr[i] = 0f; gg[i] = 0f; gb[i] = 0f
                    }
                    continue
                }
                val fw2 = min(1f, w * 25f)
                if (fw2 > foot[i] && brushed[i].toInt() == 0) foot[i] = fw2
                // Soak into the paper.
                val h = paperH[i]
                val room = 0.15f + 0.25f * h - sat[i]
                if (room > 0f) {
                    val a = min(K_ABSORB, min(w, room))
                    w -= a
                    sat[i] += a
                }
                // Water soaked into the paper creeps on through its fibres, dampening the paper
                // just past the edge so the wash can follow slowly (soft edges on damp paper).
                val si = sat[i]
                if (si > 0.05f) {
                    if (x > 0) capillary(i, i - 1, si)
                    if (x < gw - 1) capillary(i, i + 1, si)
                    if (y > 0) capillary(i, i - gw, si)
                    if (y < gh - 1) capillary(i, i + gw, si)
                }
                // How exposed this cell is: dry neighbours evaporate it faster and drop pigment.
                var dry = 0
                if (x == 0 || water[i - 1] + fw[i - 1] <= EPS) dry++
                if (x == gw - 1 || water[i + 1] + fw[i + 1] <= EPS) dry++
                if (y == 0 || water[i - gw] + fw[i - gw] <= EPS) dry++
                if (y == gh - 1 || water[i + gw] + fw[i + gw] <= EPS) dry++
                val e = dry * 0.25f
                val dep = min(0.5f, K_DEPOSIT * (1f + edgeDep * e) * (1f + granulation * (0.5f - h) * 0.8f) / (w + 0.05f))
                if (dep > 0f) {
                    val a = gr[i] * dep; gr[i] -= a; dr[i] += a
                    val b = gg[i] * dep; gg[i] -= b; dg[i] += b
                    val c = gb[i] * dep; gb[i] -= c; db[i] += c
                }
                w -= evap * (1f + edgeEvap * e * rimVar[i])
                if (w <= EPS) {
                    water[i] = 0f
                    dr[i] += gr[i]; dg[i] += gg[i]; db[i] += gb[i]
                    gr[i] = 0f; gg[i] = 0f; gb[i] = 0f
                } else {
                    water[i] = w
                    if (x < nx0) nx0 = x; if (x > nx1) nx1 = x; if (y < ny0) ny0 = y; if (y > ny1) ny1 = y
                    if (x < px0) px0 = x; if (x > px1) px1 = x; if (y < py0) py0 = y; if (y > py1) py1 = y
                    if (x < sx0) sx0 = x; if (x > sx1) sx1 = x; if (y < sy0) sy0 = y; if (y > sy1) sy1 = y
                }
            }
        }
        wx0 = nx0; wy0 = ny0; wx1 = nx1; wy1 = ny1
        growPending(x0, y0)
        growPending(x1, y1)
        stepDamp()
        return intArrayOf(x0, y0, x1, y1)
    }

    private fun flowTo(head: Float, j: Int, canBreak: Boolean, sel: ByteArray?): Float {
        if (sel != null && sel[j].toInt() == 0) return 0f
        val wj = water[j]
        val d = head - (wj + K_HEIGHT * paperH[j])
        if (d <= 0f) return 0f
        val gate = when {
            wj > EPS || sat[j] > S_WET -> 1f
            canBreak -> leak
            else -> return 0f
        }
        return K_FLOW * d * gate
    }

    /** How many of a cell's neighbours are dry, 0..4 (0 for a dry cell itself). */
    private fun exposure(x: Int, y: Int, i: Int): Int {
        if (water[i] <= EPS) return 0
        var d = 0
        if (x == 0 || water[i - 1] <= EPS) d++
        if (x == gw - 1 || water[i + 1] <= EPS) d++
        if (y == 0 || water[i - gw] <= EPS) d++
        if (y == gh - 1 || water[i + gw] <= EPS) d++
        return d
    }

    private fun edgeDrift(i: Int, j: Int, ei: Int, ej: Int) {
        if (ej <= ei) return
        val k = drift * (ej - ei) * 0.25f
        val a = gr[i] * k; fr[i] -= a; fr[j] += a
        val b = gg[i] * k; fg[i] -= b; fg[j] += b
        val c = gb[i] * k; fb[i] -= c; fb[j] += c
    }

    private fun capillary(i: Int, j: Int, si: Float) {
        val sj = sat[j]
        if (sj >= si) return
        val t = K_CAP * (si - sj)
        sat[i] -= t
        sat[j] = sj + t
        if (sat[j] > 0f) growDamp(j % gw, j / gw)
    }

    private fun move(i: Int, j: Int, f: Float, scale: Float, inv: Float, pr: Float, pg: Float, pb: Float) {
        fw[j] += f * scale
        // Water creeping into fresh paper runs ahead of its pigment, which the fibres hold back,
        // so a spreading edge fades out softly.
        val k = if (water[j] <= EPS) f * inv * FRONT_PIGMENT else f * inv
        fr[j] += pr * k; fr[i] -= pr * k
        fg[j] += pg * k; fg[i] -= pg * k
        fb[j] += pb * k; fb[i] -= pb * k
    }

    /** Damp paper dries slowly once the surface water has gone. */
    private fun stepDamp() {
        if (!isDamp) return
        var nx0 = gw; var ny0 = gh; var nx1 = -1; var ny1 = -1
        for (y in sy0..sy1) {
            val row = y * gw
            for (x in sx0..sx1) {
                val i = row + x
                var s = sat[i]
                if (s <= 0f) continue
                if (water[i] <= EPS) {
                    s -= K_DRY
                    if (s < 0f) s = 0f
                    sat[i] = s
                }
                if (s > 0f) {
                    if (x < nx0) nx0 = x; if (x > nx1) nx1 = x; if (y < ny0) ny0 = y; if (y > ny1) ny1 = y
                }
            }
        }
        sx0 = nx0; sy0 = ny0; sx1 = nx1; sy1 = ny1
    }

    /** The pixel area covered by a cell box from [step], with a margin for smooth upsampling. */
    fun pixelRect(box: IntArray): IRect =
        IRect((box[0] - 1) * CELL, (box[1] - 1) * CELL, (box[2] + 2) * CELL, (box[3] + 2) * CELL)
            .intersect(IRect(0, 0, width, height))

    /** The pixel area that has pigment in the grid (painted since the last [bake]). */
    fun paintedRect(): IRect? = if (px1 < px0) null else pixelRect(intArrayOf(px0, py0, px1, py1))

    /**
     * Re-renders the layer's pixels in [rect] from the pixels underneath and the pigment in the
     * grid. [touch] is called with each tile index before its pixels change (for undo).
     */
    fun render(
        surface: TiledSurface, rect: IRect, opacity: Float, alphaLock: Boolean,
        force: Boolean = false, touch: (Int) -> Unit,
    ): IRect? {
        if (rect.isEmpty) return null
        val shift = TiledSurface.SHIFT
        // Total pigment per cell in the area, summed once instead of per pixel.
        val cx0 = max(0, (rect.left - CELL) / CELL)
        val cy0 = max(0, (rect.top - CELL) / CELL)
        val cx1 = min(gw - 1, rect.right / CELL + 1)
        val cy1 = min(gh - 1, rect.bottom / CELL + 1)
        for (y in cy0..cy1) {
            val row = y * gw
            for (x in cx0..cx1) {
                val i = row + x
                sumR[i] = gr[i] + dr[i]; sumG[i] = gg[i] + dg[i]; sumB[i] = gb[i] + db[i]
                // The outline, smoothed so the grid's steps don't show.
                val l = if (x > 0) spreadFoot(i - 1) else 0f
                val rr = if (x < gw - 1) spreadFoot(i + 1) else 0f
                val u = if (y > 0) spreadFoot(i - gw) else 0f
                val dd = if (y < gh - 1) spreadFoot(i + gw) else 0f
                footS[i] = spreadFoot(i) * 0.5f + (l + rr + u + dd) * 0.125f
                occ[i] = if (foot[i] > 0f || brushed[i].toInt() != 0) 1f else 0f
            }
        }
        // Where each pixel column falls between cell centres.
        val w = rect.width
        if (colIx.size < w) { colIx = IntArray(w); colIx1 = IntArray(w); colTx = FloatArray(w) }
        for (k in 0 until w) {
            val gx = (rect.left + k - 1.5f) / CELL
            var ix = floor(gx).toInt()
            var tx = gx - ix
            if (ix < 0) { ix = 0; tx = 0f }
            if (ix >= gw - 1) { ix = gw - 1; tx = 0f }
            colIx[k] = ix
            colIx1[k] = if (ix + 1 < gw) ix + 1 else ix
            colTx[k] = tx
        }
        val pSize = paper.size
        val pMask = pSize - 1
        val pData = paper.data
        val gran = granulation * 1.3f
        val gSize = granules.size
        val gMask = gSize - 1
        val gData = granules.data
        // Only tiles whose paint visibly changed since they were last drawn are redrawn.
        val tiles = surface.tilesIn(rect)
        val redraw = BooleanArray(tiles.size)
        for (t in tiles.indices) {
            val tr = surface.tileRect(tiles[t])
            val ax0 = max(cx0, tr.left / CELL - 1); val ax1 = min(cx1, (tr.right - 1) / CELL + 1)
            val ay0 = max(cy0, tr.top / CELL - 1); val ay1 = min(cy1, (tr.bottom - 1) / CELL + 1)
            val br = brushRect
            var changed = force || !baseTaken[tiles[t]] || (br != null && !br.intersect(tr).isEmpty)
            var y = ay0
            while (!changed && y <= ay1) {
                val row = y * gw
                for (x in ax0..ax1) {
                    val i = row + x
                    if (foot[i] != lastF[i] || abs(sumR[i] - lastR[i]) > REDRAW || abs(sumG[i] - lastG[i]) > REDRAW ||
                        abs(sumB[i] - lastB[i]) > REDRAW) { changed = true; break }
                }
                y++
            }
            redraw[t] = changed
        }
        for (t in tiles.indices) {
            if (!redraw[t]) continue
            val tr = surface.tileRect(tiles[t])
            val ax0 = max(cx0, tr.left / CELL - 1); val ax1 = min(cx1, (tr.right - 1) / CELL + 1)
            val ay0 = max(cy0, tr.top / CELL - 1); val ay1 = min(cy1, (tr.bottom - 1) / CELL + 1)
            for (y in ay0..ay1) {
                val row = y * gw
                for (x in ax0..ax1) {
                    val i = row + x
                    lastR[i] = sumR[i]; lastG[i] = sumG[i]; lastB[i] = sumB[i]; lastF[i] = foot[i]
                }
            }
        }
        brushRect = null
        var dirty: IRect? = null
        for ((t, index) in tiles.withIndex()) {
            if (!redraw[t]) continue
            dirty = IRect.unionOf(dirty, rect.intersect(surface.tileRect(index)))
            if (!baseTaken[index]) {
                baseTaken[index] = true
                base[index] = surface.tile(index)?.copyOf()
            }
            val under = base[index]
            val outlinePx = footPx[index]
            touch(index)
            val r = rect.intersect(surface.tileRect(index))
            if (r.isEmpty) continue
            var tile: IntArray? = surface.tile(index)
            for (y in r.top until r.bottom) {
                val gy = (y - 1.5f) / CELL
                var iy = floor(gy).toInt()
                var ty = gy - iy
                if (iy < 0) { iy = 0; ty = 0f }
                if (iy >= gh - 1) { iy = gh - 1; ty = 0f }
                val iy1 = if (iy + 1 < gh) iy + 1 else iy
                val row0 = iy * gw
                val row1 = iy1 * gw
                val rowT = (y and TiledSurface.MASK) shl shift
                val pRow = (y and pMask) * pSize
                val gRow = (y and gMask) * gSize
                for (x in r.left until r.right) {
                    val k = x - rect.left
                    val ix = colIx[k]
                    val ix1 = colIx1[k]
                    val tx = colTx[k]
                    val a = row0 + ix; val b = row0 + ix1; val c = row1 + ix; val d = row1 + ix1
                    val ti = rowT or (x and TiledSurface.MASK)
                    val bp = if (under == null) 0 else under[ti]
                    val oa = occ[a]; val ob = occ[b]; val oc = occ[c]; val od = occ[d]
                    var out = bp
                    if (oa + ob + oc + od > 0f) {
                        val wa = (1f - tx) * (1f - ty) * oa; val wb = tx * (1f - ty) * ob
                        val wc = (1f - tx) * ty * oc; val wd = tx * ty * od
                        val grain = pData[pRow + (x and pMask)]
                        // The outline: exactly where the brush went (full resolution), or where the
                        // water spread to (smoothed from the grid), with a little paper wobble.
                        val fpx = if (outlinePx == null) 0f else (outlinePx[ti].toInt() and 0xFF) * INV255
                        val fs = (footS[a] * (1f - tx) + footS[b] * tx) * (1f - ty) + (footS[c] * (1f - tx) + footS[d] * tx) * ty
                        val soft = (8f - edgeSharpness) * 0.05f
                        val c1 = (fpx + (grain - 0.5f) * 0.2f - 0.25f) * edgeSharpness * 1.5f + soft
                        val c2 = (fs + (grain - 0.5f) * 0.35f - 0.42f) * edgeSharpness + soft
                        // Where the brush's edge meets water that ran past it, the two together cover.
                        val c3 = (1f - (1f - fpx) * (1f - fs) + (grain - 0.5f) * 0.35f - 0.42f) * edgeSharpness + soft
                        val cover = if (c1 > c2) (if (c1 > c3) c1 else c3) else (if (c2 > c3) c2 else c3)
                        val wsum = wa + wb + wc + wd
                        if (cover > 0f && wsum > 0f) {
                            val speck = gData[gRow + (x and gMask)] * 0.7f + grain * 0.3f
                            var gm = (if (cover > 1f) 1f else cover) * opacity * (1f + gran * (0.5f - speck)) / wsum
                            if (gm < 0f) gm = 0f
                            val ar = (sumR[a] * wa + sumR[b] * wb + sumR[c] * wc + sumR[d] * wd) * gm
                            val ag = (sumG[a] * wa + sumG[b] * wb + sumG[c] * wc + sumG[d] * wd) * gm
                            val ab = (sumB[a] * wa + sumB[b] * wb + sumB[c] * wc + sumB[d] * wd) * gm
                            if (ar > 0.003f || ag > 0.003f || ab > 0.003f) out = glaze(bp, ar, ag, ab, alphaLock)
                        }
                    }
                    if (tile == null) {
                        if (out == 0) continue
                        tile = surface.tileOrCreate(index)
                    }
                    tile[ti] = out
                }
            }
        }
        return dirty
    }

    // What each cell looked like when it was last drawn.
    private val lastR = FloatArray(n)
    private val lastG = FloatArray(n)
    private val lastB = FloatArray(n)
    private val lastF = FloatArray(n)
    private val sumR = FloatArray(n)
    private val footS = FloatArray(n)
    private val sumG = FloatArray(n)
    private val sumB = FloatArray(n)
    private var colIx = IntArray(0)
    private var colIx1 = IntArray(0)
    private var colTx = FloatArray(0)

    /** Lays pigment with absorbance [ar], [ag], [ab] over premultiplied [b], multiplying like glazes. */
    private fun glaze(b: Int, ar: Float, ag: Float, ab: Float, alphaLock: Boolean): Int {
        // Integer maths throughout: float-to-int conversion is slow in the web build.
        val tr = transmit(ar)
        val tg = transmit(ag)
        val tb = transmit(ab)
        val ba = b ushr 24
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        if (alphaLock) return (ba shl 24) or (((br * tr) shr 8) shl 16) or (((bg * tg) shr 8) shl 8) or ((bb * tb) shr 8)
        // The pigment on its own, as premultiplied colour over nothing: over white it shows T.
        val tMin = min(tr, min(tg, tb))
        val pa = ((256 - tMin) * 255) shr 8
        val pr = ((tr - tMin) * 255) shr 8
        val pg = ((tg - tMin) * 255) shr 8
        val pb = ((tb - tMin) * 255) shr 8
        // Multiply blend: over opaque paint this is exactly base times T.
        val ia = 255 - ba
        val ip = 255 - pa
        val oa = pa + ba - Pixels.mulDiv255(pa, ba)
        var or = Pixels.mulDiv255(pr, ia) + Pixels.mulDiv255(br, ip) + Pixels.mulDiv255(pr, br)
        var og = Pixels.mulDiv255(pg, ia) + Pixels.mulDiv255(bg, ip) + Pixels.mulDiv255(pg, bg)
        var ob = Pixels.mulDiv255(pb, ia) + Pixels.mulDiv255(bb, ip) + Pixels.mulDiv255(pb, bb)
        if (or > oa) or = oa
        if (og > oa) og = oa
        if (ob > oa) ob = oa
        return (oa shl 24) or (or shl 16) or (og shl 8) or ob
    }

    /**
     * Once no water is left the pixels are final: forget the pigment and the pixels underneath, so
     * the next wash glazes over this one. The paper stays damp.
     */
    fun bake() {
        if (px1 >= px0) {
            for (y in py0..py1) {
                val row = y * gw
                for (x in px0..px1) {
                    val i = row + x
                    gr[i] = 0f; gg[i] = 0f; gb[i] = 0f
                    dr[i] = 0f; dg[i] = 0f; db[i] = 0f
                    foot[i] = 0f
                    brushed[i] = 0
                    water[i] = 0f
                }
            }
        }
        px0 = gw; py0 = gh; px1 = -1; py1 = -1
        wx0 = gw; wy0 = gh; wx1 = -1; wy1 = -1
        base.fill(null)
        baseTaken.fill(false)
        footPx.fill(null)
    }

    /** Dries everything at once where it is (Dry layer, or another tool on this layer). */
    fun freeze(surface: TiledSurface, opacity: Float, alphaLock: Boolean, touch: (Int) -> Unit): IRect? {
        val rect = paintedRect()
        if (px1 >= px0) {
            for (y in py0..py1) for (x in px0..px1) {
                val i = y * gw + x
                if (water[i] > 0f) {
                    water[i] = 0f
                    dr[i] += gr[i]; dg[i] += gg[i]; db[i] += gb[i]
                    gr[i] = 0f; gg[i] = 0f; gb[i] = 0f
                }
            }
        }
        val dirty = if (rect != null) render(surface, rect, opacity, alphaLock, force = true, touch = touch) else null
        bake()
        clearDamp()
        return dirty
    }

    /** Forgets all water without touching the pixels (undo and redo). */
    fun clear() {
        bake()
        clearDamp()
    }

    private fun clearDamp() {
        if (sx1 >= sx0) for (y in sy0..sy1) for (x in sx0..sx1) sat[y * gw + x] = 0f
        sx0 = gw; sy0 = gh; sx1 = -1; sy1 = -1
    }

    /** The grid's state where anything is happening, to put back if a stroke is cancelled. */
    inner class Snapshot internal constructor() {
        private val box = intArrayOf(
            min(px0, sx0), min(py0, sy0), max(px1, sx1), max(py1, sy1),
        )
        private val bounds = intArrayOf(wx0, wy0, wx1, wy1, px0, py0, px1, py1, sx0, sy0, sx1, sy1)
        private val data: Array<FloatArray> = if (box[2] < box[0]) emptyArray() else {
            val fields = fields()
            Array(fields.size) { k ->
                val f = fields[k]
                val bw = box[2] - box[0] + 1
                FloatArray(bw * (box[3] - box[1] + 1)).also { out ->
                    for (y in box[1]..box[3]) for (x in box[0]..box[2]) out[(y - box[1]) * bw + x - box[0]] = f[y * gw + x]
                }
            }
        }

        /** Puts the grid back; returns the pixel area to re-render. */
        internal fun restore(): IRect? {
            val dirty = paintedRect()
            val ux0 = min(min(px0, sx0), min(wx0, box[0]))
            val uy0 = min(min(py0, sy0), min(wy0, box[1]))
            val ux1 = max(max(px1, sx1), max(wx1, box[2]))
            val uy1 = max(max(py1, sy1), max(wy1, box[3]))
            val fields = fields()
            if (ux1 >= ux0) for (f in fields) for (y in uy0..uy1) for (x in ux0..ux1) f[y * gw + x] = 0f
            if (data.isNotEmpty()) {
                val bw = box[2] - box[0] + 1
                for (k in fields.indices) {
                    val f = fields[k]
                    val src = data[k]
                    for (y in box[1]..box[3]) for (x in box[0]..box[2]) f[y * gw + x] = src[(y - box[1]) * bw + x - box[0]]
                }
            }
            wx0 = bounds[0]; wy0 = bounds[1]; wx1 = bounds[2]; wy1 = bounds[3]
            px0 = bounds[4]; py0 = bounds[5]; px1 = bounds[6]; py1 = bounds[7]
            sx0 = bounds[8]; sy0 = bounds[9]; sx1 = bounds[10]; sy1 = bounds[11]
            return IRect.unionOf(dirty, paintedRect())
        }
    }

    private fun fields() = arrayOf(water, sat, foot, gr, gg, gb, dr, dg, db)

    fun snapshot(): Snapshot = Snapshot()

    fun restore(s: Snapshot): IRect? = s.restore()

    // ---- For tests ----------------------------------------------------------------------------

    /** Surface water at a pixel. */
    fun waterAt(x: Int, y: Int): Float = water[(y / CELL).coerceIn(0, gh - 1) * gw + (x / CELL).coerceIn(0, gw - 1)]

    /** Debug summary: wet cells, total water, max water, painted cells, damp cells. */
    fun stats(): String {
        var wet = 0; var tot = 0f; var mx = 0f; var damp = 0
        for (i in 0 until n) { if (water[i] > EPS) wet++; tot += water[i]; if (water[i] > mx) mx = water[i]; if (sat[i] > S_WET) damp++ }
        return "wet=$wet water=$tot max=$mx painted=${paintedCells()} damp=$damp box=$wx0,$wy0..$wx1,$wy1"
    }

    /** Number of cells that hold pigment or water (the wash's area, in cells). */
    fun paintedCells(): Int {
        var c = 0
        for (i in 0 until n) if (foot[i] > 0f) c++
        return c
    }

    /** Light let through by pigment of absorbance [a], 0..256. */
    private fun transmit(a: Float): Int {
        if (a <= 0f) return 256
        val k = (a * EXP_SCALE).toInt()
        return if (k >= EXP_SIZE) 0 else EXP[k]
    }

    companion object {
        /** Pixels per grid cell. */
        const val CELL = 4
        private const val EPS = 0.002f
        private const val K_FLOW = 0.2f
        private const val K_HEIGHT = 0.12f
        private const val S_WET = 0.15f
                private const val K_ABSORB = 0.0015f
        private const val K_DEPOSIT = 0.0008f
        private const val K_DRY = 0.0015f
        private const val K_CAP = 0.03f
        private const val FRONT_PIGMENT = 0.3f

        /** Change in pigment (absorbance) that is worth redrawing. */
        private const val REDRAW = 0.012f
        private const val INV255 = 1f / 255f
        private const val EXP_SCALE = 256f
        private const val EXP_SIZE = 256 * 10
        private val EXP = IntArray(EXP_SIZE) { (exp(-(it + 0.5f) / EXP_SCALE) * 256f + 0.5f).toInt() }
    }
}
