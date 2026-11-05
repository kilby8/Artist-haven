package com.artisthaven.engine.tools

import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.document.PixelCommand
import com.artisthaven.engine.document.Selection
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.raster.TiledSurface
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Picture adjustments, like Procreate's Adjustments menu and Photoshop's Image > Adjustments. */
enum class Adjustment(val displayName: String) {
    /** a: hue shift -1..1 (= -180..180 degrees), b: saturation -1..1, c: lightness -1..1. */
    HUE_SATURATION("Hue, saturation, lightness"),

    /** a: brightness -1..1, b: contrast -1..1. */
    BRIGHTNESS_CONTRAST("Brightness and contrast"),

    /** a: black point 0..1, b: white point 0..1, c: midtones -1..1 (gamma). */
    LEVELS("Levels"),

    /** a: warm/cool -1..1 (red-cyan), b: magenta/green -1..1, c: yellow/blue -1..1. */
    COLOR_BALANCE("Colour balance"),

    /** a: radius 0..1 (= 0..120 px). */
    BLUR("Gaussian blur"),

    /** a: amount 0..1, b: radius 0..1 (= 0.5..10 px). */
    SHARPEN("Sharpen"),

    /** a: amount 0..1. */
    NOISE("Noise"),

    /** No settings. */
    INVERT("Invert colours"),
    ;

    /** Spreads pixels into their neighbours, so it works on a whole area, not pixel by pixel. */
    val spatial: Boolean get() = this == BLUR || this == SHARPEN

    companion object {
        fun blurRadius(a: Float): Float = (a.coerceIn(0f, 1f).pow(1.6f) * 120f)
        fun sharpenRadius(b: Float): Float = 0.5f + b.coerceIn(0f, 1f) * 9.5f
    }
}

/**
 * A live adjustment of one layer (or its mask, or only the selected part of it). The original
 * tiles stay untouched, so every [render] starts again from them and they double as the "before"
 * state for undo, like [TransformSession].
 */
class AdjustSession(
    private val layer: Layer,
    val kind: Adjustment,
    private val selection: Selection?,
    private val onMask: Boolean = false,
) {
    private val surface = if (onMask) layer.mask!! else layer.surface
    private val original: Array<IntArray?> = Array(surface.tileCount) { surface.tile(it) }
    private val touched = HashSet<Int>()
    private var lastArea: IRect? = null
    private val content: IRect? = surface.contentBounds()

    var a = 0f; private set
    var b = 0f; private set
    var c = 0f; private set

    val isEmpty: Boolean get() = content == null && kind != Adjustment.NOISE

    /** Re-renders with new settings. Returns the area to recomposite. */
    fun render(a: Float, b: Float, c: Float): IRect? {
        this.a = a; this.b = b; this.c = c
        for (i in touched) surface.setTile(i, original[i])
        touched.clear()
        val area = area()
        val dirty = IRect.unionOf(lastArea, area)
        lastArea = area
        if (area == null || isIdentity()) return dirty
        if (kind.spatial) renderSpatial(area) else renderPointwise()
        return dirty
    }

    private fun isIdentity(): Boolean = when (kind) {
        Adjustment.HUE_SATURATION, Adjustment.COLOR_BALANCE -> a == 0f && b == 0f && c == 0f
        Adjustment.BRIGHTNESS_CONTRAST -> a == 0f && b == 0f
        Adjustment.LEVELS -> a <= 0f && b >= 1f && c == 0f
        Adjustment.BLUR -> Adjustment.blurRadius(a) < 0.3f
        Adjustment.SHARPEN, Adjustment.NOISE -> a <= 0f
        Adjustment.INVERT -> false
    }

    private fun area(): IRect? {
        val docBounds = surface.bounds
        var r = content ?: return null
        if (kind.spatial) {
            val radius = if (kind == Adjustment.BLUR) Adjustment.blurRadius(a) else Adjustment.sharpenRadius(b)
            r = r.inset(ceil(radius * 3f).toInt() + 2)
        }
        r = r.intersect(docBounds)
        val sel = selection?.bounds()
        if (sel != null) r = r.intersect(sel)
        return r.takeUnless { it.isEmpty }
    }

    private fun mix(orig: Int, adjusted: Int, x: Int, y: Int): Int {
        val sel = selection ?: return adjusted
        val cov = sel.coverage(x, y)
        return if (cov >= 1f) adjusted else Pixels.lerp(orig, adjusted, cov)
    }

    // ---- Colour changes, one pixel at a time ------------------------------------------------------

    private fun renderPointwise() {
        val f = pointFunction()
        for (index in original.indices) {
            val o = original[index] ?: continue
            if (selection != null && selection.tileEmpty(index)) continue
            val tr = surface.tileRect(index)
            val out = IntArray(o.size)
            var changed = false
            for (y in tr.top until tr.bottom) for (x in tr.left until tr.right) {
                val k = ((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)
                val p = o[k]
                if (p == 0) continue
                val q = mix(p, f(p, x, y), x, y)
                out[k] = q
                if (q != p) changed = true
            }
            if (!changed) continue
            surface.setTile(index, out)
            touched += index
        }
    }

    /** Maps a premultiplied pixel to its adjusted premultiplied value. */
    private fun pointFunction(): (Int, Int, Int) -> Int {
        when (kind) {
            Adjustment.HUE_SATURATION -> {
                val hueShift = a * 180f
                val sat = b
                val light = c
                return { p, _, _ -> straight(p) { r, g, bl -> hsl(r, g, bl, hueShift, sat, light) } }
            }
            Adjustment.BRIGHTNESS_CONTRAST -> {
                val lut = IntArray(256) { v ->
                    var x = v / 255f
                    x += a * 0.5f
                    val k = if (b >= 0) 1f + b * 3f else 1f + b
                    x = (x - 0.5f) * k + 0.5f
                    (x.coerceIn(0f, 1f) * 255f).roundToInt()
                }
                return { p, _, _ -> straightLut(p, lut, lut, lut) }
            }
            Adjustment.LEVELS -> {
                val lo = a.coerceIn(0f, 0.99f)
                val hi = b.coerceIn(lo + 0.01f, 1f)
                val gamma = 2f.pow(-c * 2f)
                val lut = IntArray(256) { v ->
                    val x = ((v / 255f - lo) / (hi - lo)).coerceIn(0f, 1f)
                    (x.pow(gamma) * 255f).roundToInt()
                }
                return { p, _, _ -> straightLut(p, lut, lut, lut) }
            }
            Adjustment.COLOR_BALANCE -> {
                // Each slider pushes one channel and pulls its opposite, strongest in the midtones.
                fun lut(shift: Float) = IntArray(256) { v ->
                    val x = v / 255f
                    val mid = 4f * x * (1f - x)
                    ((x + shift * 0.35f * (0.35f + 0.65f * mid)).coerceIn(0f, 1f) * 255f).roundToInt()
                }
                val r = lut(a)
                val g = lut(-b)
                val bl = lut(-c)
                // Warm (a > 0) adds red and takes away a little blue.
                val warmB = lut(-a * 0.5f)
                val rb = IntArray(256) { bl[warmB[it]] }
                return { p, _, _ -> straightLut(p, r, g, rb) }
            }
            Adjustment.NOISE -> {
                val amount = a
                return { p, x, y ->
                    straight(p) { r, g, bl ->
                        val n = ((hash(x, y) and 0xFFFF) / 65535f - 0.5f) * amount * 160f
                        Triple((r + n).roundToInt(), (g + n).roundToInt(), (bl + n).roundToInt())
                    }
                }
            }
            Adjustment.INVERT -> return { p, _, _ -> straight(p) { r, g, bl -> Triple(255 - r, 255 - g, 255 - bl) } }
            else -> return { p, _, _ -> p }
        }
    }

    private inline fun straight(p: Int, f: (Int, Int, Int) -> Triple<Int, Int, Int>): Int {
        val s = Pixels.unpremultiply(p)
        val (r, g, b) = f(Pixels.red(s), Pixels.green(s), Pixels.blue(s))
        return Pixels.premultiply(Pixels.pack(Pixels.alpha(s), r, g, b))
    }

    private fun straightLut(p: Int, lr: IntArray, lg: IntArray, lb: IntArray): Int {
        val s = Pixels.unpremultiply(p)
        return Pixels.premultiply(Pixels.pack(Pixels.alpha(s), lr[Pixels.red(s)], lg[Pixels.green(s)], lb[Pixels.blue(s)]))
    }

    private fun hsl(r: Int, g: Int, b: Int, hueShift: Float, sat: Float, light: Float): Triple<Int, Int, Int> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val mx = max(rf, max(gf, bf))
        val mn = min(rf, min(gf, bf))
        var l = (mx + mn) / 2f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d > 1e-6f) {
            s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            h = when (mx) {
                rf -> ((gf - bf) / d + (if (gf < bf) 6f else 0f))
                gf -> (bf - rf) / d + 2f
                else -> (rf - gf) / d + 4f
            } * 60f
        }
        h = ((h + hueShift) % 360f + 360f) % 360f
        s = if (sat >= 0) (if (s > 0f) s + (1f - s) * sat else 0f) else s * (1f + sat)
        s = s.coerceIn(0f, 1f)
        l = if (light >= 0) l + (1f - l) * light else l * (1f + light)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        fun hue(t0: Float): Float {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 0.5f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        val hn = h / 360f
        return Triple((hue(hn + 1f / 3f) * 255f).roundToInt(), (hue(hn) * 255f).roundToInt(), (hue(hn - 1f / 3f) * 255f).roundToInt())
    }

    private fun hash(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    // ---- Blur and sharpen ---------------------------------------------------------------------------

    private fun renderSpatial(target: IRect) {
        val radius = if (kind == Adjustment.BLUR) Adjustment.blurRadius(a) else Adjustment.sharpenRadius(b)
        // Read a margin around the area so its edges blur with what is really next to them.
        val area = target.inset(ceil(radius * 3f).toInt() + 2).intersect(surface.bounds)
        val w = area.width
        val h = area.height
        val src = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val gx = area.left + x
            val gy = area.top + y
            val t = original[(gy shr TiledSurface.SHIFT) * surface.tilesX + (gx shr TiledSurface.SHIFT)] ?: continue
            src[y * w + x] = t[((gy and TiledSurface.MASK) shl TiledSurface.SHIFT) or (gx and TiledSurface.MASK)]
        }
        val blurred = IntArray(w * h)
        val channel = FloatArray(w * h)
        val tmp = FloatArray(max(w, h))
        val boxes = boxesForGauss(radius, 3)
        for (shift in intArrayOf(24, 16, 8, 0)) {
            for (i in src.indices) channel[i] = ((src[i] ushr shift) and 0xFF).toFloat()
            for (r in boxes) {
                boxBlurH(channel, w, h, r, tmp)
                boxBlurV(channel, w, h, r, tmp)
            }
            for (i in src.indices) blurred[i] = blurred[i] or ((channel[i] + 0.5f).toInt().coerceIn(0, 255) shl shift)
        }
        val amount = a * 2.5f
        for (ti in surface.tilesIn(target)) {
            val tr = surface.tileRect(ti).intersect(target)
            val o = original[ti]
            val out = o?.copyOf() ?: IntArray(TiledSurface.TILE * TiledSurface.TILE)
            var any = false
            for (y in tr.top until tr.bottom) for (x in tr.left until tr.right) {
                val k = ((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)
                val i = (y - area.top) * w + (x - area.left)
                val orig = src[i]
                var q = blurred[i]
                if (kind == Adjustment.SHARPEN) q = unsharp(orig, q, amount)
                q = mix(orig, q, x, y)
                out[k] = q
                if (q != 0) any = true
            }
            if (!any && o == null) continue
            surface.setTile(ti, out)
            touched += ti
        }
    }

    private fun unsharp(orig: Int, blur: Int, amount: Float): Int {
        val a0 = orig ushr 24
        if (a0 == 0) return 0
        fun ch(s: Int): Int {
            val o = (orig ushr s) and 0xFF
            val b = (blur ushr s) and 0xFF
            return (o + (o - b) * amount).roundToInt().coerceIn(0, a0)
        }
        return Pixels.pack(a0, ch(16), ch(8), ch(0))
    }

    /** Box sizes whose repeated application approximates a Gaussian of [sigma] (Kovesi). */
    private fun boxesForGauss(sigma: Float, n: Int): IntArray {
        if (sigma < 0.3f) return IntArray(0)
        val wIdeal = sqrt(12f * sigma * sigma / n + 1f)
        var wl = wIdeal.toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val mIdeal = (12f * sigma * sigma - n * wl * wl - 4f * n * wl - 3f * n) / (-4f * wl - 4f)
        val m = mIdeal.roundToInt()
        return IntArray(n) { ((if (it < m) wl else wu) - 1) / 2 }
    }

    private fun boxBlurH(ch: FloatArray, w: Int, h: Int, r: Int, tmp: FloatArray) {
        if (r <= 0) return
        val inv = 1f / (r + r + 1)
        for (y in 0 until h) {
            val row = y * w
            var acc = 0f
            // Edges are treated as transparent, so paint fades out rather than smearing at the border.
            for (x in -r..r) if (x in 0 until w) acc += ch[row + x]
            for (x in 0 until w) {
                tmp[x] = acc * inv
                val add = x + r + 1
                val sub = x - r
                if (add < w) acc += ch[row + add]
                if (sub >= 0) acc -= ch[row + sub]
            }
            tmp.copyInto(ch, row, 0, w)
        }
    }

    private fun boxBlurV(ch: FloatArray, w: Int, h: Int, r: Int, tmp: FloatArray) {
        if (r <= 0) return
        val inv = 1f / (r + r + 1)
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) if (y in 0 until h) acc += ch[y * w + x]
            for (y in 0 until h) {
                tmp[y] = acc * inv
                val add = y + r + 1
                val sub = y - r
                if (add < h) acc += ch[add * w + x]
                if (sub >= 0) acc -= ch[sub * w + x]
            }
            for (y in 0 until h) ch[y * w + x] = tmp[y]
        }
    }

    // ---- Finishing ----------------------------------------------------------------------------------

    /** Returns the undo step, or null if nothing changed. The pixels stay as last rendered. */
    fun finish(label: String): PixelCommand? {
        val before = HashMap<Int, IntArray?>()
        for (i in touched) before[i] = original[i]
        return PixelCommand.capture(label, layer, before, onMask)
    }

    /** Puts the original pixels back. */
    fun cancel(): IRect? {
        for (i in touched) surface.setTile(i, original[i])
        touched.clear()
        return lastArea
    }
}
