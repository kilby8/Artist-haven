package com.artisthaven.engine.tools

import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.document.PixelCommand
import com.artisthaven.engine.document.Selection
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.raster.TiledSurface
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** What a liquify stroke does to the pixels under the brush. */
enum class LiquifyMode(val displayName: String) {
    PUSH("Push"),
    TWIRL_RIGHT("Twirl right"),
    TWIRL_LEFT("Twirl left"),
    PINCH("Pinch"),
    EXPAND("Expand"),
    /** Paints the pixels back to how they were before liquify started. */
    RESTORE("Reconstruct"),
}

/**
 * One liquify stroke: warps a layer's pixels under a soft round brush as it moves. Each dab
 * resamples the current pixels, so warps build up like Procreate's and Photoshop's Liquify.
 * Tiles are copied before their first change, so the originals are the undo "before" state.
 */
class LiquifySession(
    private val layer: Layer,
    private val mode: LiquifyMode,
    /** Brush diameter in document pixels. */
    private val size: Float,
    /** 0..1. */
    private val strength: Float,
    private val selection: Selection?,
    private val onMask: Boolean = false,
) {
    private val surface = if (onMask) layer.mask!! else layer.surface
    private val original: Array<IntArray?> = Array(surface.tileCount) { surface.tile(it) }
    private val owned = HashSet<Int>()
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var dirty: IRect? = null

    /** Adds a point of the stroke. Returns the area to recomposite. */
    fun add(x: Float, y: Float, pressure: Float): IRect? {
        val radius = max(2f, size / 2f * (0.35f + 0.65f * pressure.coerceIn(0f, 1f)))
        if (lastX.isNaN()) {
            lastX = x; lastY = y
            if (mode != LiquifyMode.PUSH) return dab(x, y, 0f, 0f, radius)
            return null
        }
        val dist = hypot(x - lastX, y - lastY)
        val step = max(1f, radius * 0.2f)
        if (dist < step && mode == LiquifyMode.PUSH) return null
        var out: IRect? = null
        val n = max(1, ceil(dist / step).toInt())
        var px = lastX
        var py = lastY
        for (i in 1..n) {
            val t = i.toFloat() / n
            val nx = lastX + (x - lastX) * t
            val ny = lastY + (y - lastY) * t
            out = IRect.unionOf(out, dab(nx, ny, nx - px, ny - py, radius))
            px = nx; py = ny
        }
        lastX = x; lastY = y
        return out
    }

    private fun dab(cx: Float, cy: Float, dx: Float, dy: Float, radius: Float): IRect? {
        val rect = IRect.of(cx - radius - 1, cy - radius - 1, cx + radius + 1, cy + radius + 1).intersect(surface.bounds)
        if (rect.isEmpty) return null
        // Sources can come from up to one radius away (a push moves pixels along the stroke).
        val read = rect.inset(ceil(radius).toInt() + 2).intersect(surface.bounds)
        val rw = read.width
        val buf = IntArray(rw * read.height)
        for (y in read.top until read.bottom) for (x in read.left until read.right) buf[(y - read.top) * rw + (x - read.left)] = surface.getPixel(x, y)
        fun sample(sx: Float, sy: Float): Int {
            val x0 = floor(sx).toInt()
            val y0 = floor(sy).toInt()
            val fx = sx - x0
            val fy = sy - y0
            fun at(x: Int, y: Int): Int {
                if (x < read.left || y < read.top || x >= read.right || y >= read.bottom) return 0
                return buf[(y - read.top) * rw + (x - read.left)]
            }
            val p00 = at(x0, y0); val p10 = at(x0 + 1, y0); val p01 = at(x0, y0 + 1); val p11 = at(x0 + 1, y0 + 1)
            if (p00 == p10 && p00 == p01 && p00 == p11) return p00
            fun ch(s: Int): Int = (((p00 ushr s) and 0xFF) * (1 - fx) * (1 - fy) + ((p10 ushr s) and 0xFF) * fx * (1 - fy) +
                ((p01 ushr s) and 0xFF) * (1 - fx) * fy + ((p11 ushr s) and 0xFF) * fx * fy + 0.5f).toInt()
            return Pixels.pack(ch(24), ch(16), ch(8), ch(0))
        }
        val s = strength.coerceIn(0.02f, 1f)
        var changed = false
        for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) {
            val px = x + 0.5f - cx
            val py = y + 0.5f - cy
            val d = hypot(px, py) / radius
            if (d >= 1f) continue
            val q = 1f - d * d
            var f = q * q * s
            val cov = selection?.coverage(x, y) ?: 1f
            if (cov <= 0f) continue
            f *= cov
            val cur = buf[(y - read.top) * rw + (x - read.left)]
            val next = when (mode) {
                LiquifyMode.PUSH -> sample(x + 0.5f - dx * f - 0.5f, y + 0.5f - dy * f - 0.5f)
                LiquifyMode.TWIRL_RIGHT, LiquifyMode.TWIRL_LEFT -> {
                    val ang = f * 0.12f * if (mode == LiquifyMode.TWIRL_RIGHT) -1f else 1f
                    val cs = cos(ang); val sn = sin(ang)
                    sample(cx + px * cs - py * sn - 0.5f, cy + px * sn + py * cs - 0.5f)
                }
                LiquifyMode.PINCH -> {
                    val k = 1f + f * 0.08f
                    sample(cx + px * k - 0.5f, cy + py * k - 0.5f)
                }
                LiquifyMode.EXPAND -> {
                    val k = 1f - f * 0.08f
                    sample(cx + px * k - 0.5f, cy + py * k - 0.5f)
                }
                LiquifyMode.RESTORE -> {
                    val o = original[(y shr TiledSurface.SHIFT) * surface.tilesX + (x shr TiledSurface.SHIFT)]
                    val op = o?.get(((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)) ?: 0
                    Pixels.lerp(cur, op, f * 0.5f)
                }
            }
            if (next == cur) continue
            write(x, y, next)
            changed = true
        }
        if (!changed) return null
        dirty = IRect.unionOf(dirty, rect)
        return rect
    }

    private fun write(x: Int, y: Int, c: Int) {
        val index = (y shr TiledSurface.SHIFT) * surface.tilesX + (x shr TiledSurface.SHIFT)
        if (index !in owned) {
            owned += index
            surface.setTile(index, original[index]?.copyOf() ?: IntArray(TiledSurface.TILE * TiledSurface.TILE))
        }
        surface.tile(index)!![((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)] = c
    }

    /** The undo step for the whole stroke, or null if nothing changed. */
    fun finish(): PixelCommand? {
        val before = HashMap<Int, IntArray?>()
        for (i in owned) before[i] = original[i]
        return PixelCommand.capture("Liquify", layer, before, onMask)
    }

    fun cancel(): IRect? {
        for (i in owned) surface.setTile(i, original[i])
        owned.clear()
        return dirty
    }
}
