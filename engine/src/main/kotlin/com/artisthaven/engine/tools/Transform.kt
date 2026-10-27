package com.artisthaven.engine.tools

import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.document.PixelCommand
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.raster.TiledSurface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A 2D affine transform: x' = a*x + c*y + tx, y' = b*x + d*y + ty.
 * `p * q` applies q first, then p.
 */
data class Affine(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val tx: Float = 0f,
    val ty: Float = 0f,
) {
    fun mapX(x: Float, y: Float): Float = a * x + c * y + tx
    fun mapY(x: Float, y: Float): Float = b * x + d * y + ty

    operator fun times(o: Affine): Affine = Affine(
        a * o.a + c * o.b,
        b * o.a + d * o.b,
        a * o.c + c * o.d,
        b * o.c + d * o.d,
        a * o.tx + c * o.ty + tx,
        b * o.tx + d * o.ty + ty,
    )

    val determinant: Float get() = a * d - b * c

    fun inverse(): Affine {
        val det = determinant
        if (abs(det) < 1e-9f) return IDENTITY
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Affine(ia, ib, ic, id, -(ia * tx + ic * ty), -(ib * tx + id * ty))
    }

    /** Rotation in degrees. */
    val rotationDegrees: Float get() = Math.toDegrees(kotlin.math.atan2(b, a).toDouble()).toFloat()

    /** Smallest scale along any axis, used to decide how much to supersample when shrinking. */
    val minScale: Float get() = min(hypot(a, b), hypot(c, d))

    val isIdentity: Boolean get() = this == IDENTITY

    /** Bounding box of [r] after mapping. */
    fun mapBounds(r: IRect): IRect {
        val xs = floatArrayOf(mapX(r.left.toFloat(), r.top.toFloat()), mapX(r.right.toFloat(), r.top.toFloat()),
            mapX(r.left.toFloat(), r.bottom.toFloat()), mapX(r.right.toFloat(), r.bottom.toFloat()))
        val ys = floatArrayOf(mapY(r.left.toFloat(), r.top.toFloat()), mapY(r.right.toFloat(), r.top.toFloat()),
            mapY(r.left.toFloat(), r.bottom.toFloat()), mapY(r.right.toFloat(), r.bottom.toFloat()))
        return IRect(
            floor(xs.min()).toInt() - 1,
            floor(ys.min()).toInt() - 1,
            ceil(xs.max()).toInt() + 1,
            ceil(ys.max()).toInt() + 1,
        )
    }

    companion object {
        val IDENTITY = Affine()

        fun translate(x: Float, y: Float) = Affine(tx = x, ty = y)
        fun scale(sx: Float, sy: Float = sx) = Affine(a = sx, d = sy)
        fun rotate(degrees: Float): Affine {
            val r = Math.toRadians(degrees.toDouble())
            val cs = cos(r).toFloat()
            val sn = sin(r).toFloat()
            return Affine(cs, sn, -sn, cs)
        }

        /** [m] applied about the point (x, y) instead of the origin. */
        fun about(x: Float, y: Float, m: Affine) = translate(x, y) * m * translate(-x, -y)
    }
}

/** How pixels are resampled when a transform is committed. */
enum class Resample(val displayName: String) {
    /** Bilinear, with extra samples when shrinking so fine lines don't break up. */
    SMOOTH("Smooth"),

    /** Bicubic: keeps edges crisper when enlarging. */
    SHARP("Sharp"),

    /** Nearest neighbour, for pixel art. */
    PIXEL("Pixel"),
}

/**
 * Moves, scales, rotates or flips a layer's pixels. The layer shows a fast bilinear preview while
 * the user drags; [finish] renders the final quality and returns one undoable command.
 *
 * The original tiles are kept untouched (the layer gets fresh tiles), so they double as the
 * source for sampling and as the "before" state for undo.
 */
class TransformSession(private val layer: Layer) {
    private val surface = layer.surface
    private val src: Array<IntArray?> = Array(surface.tileCount) { surface.tile(it) }
    private val touched = HashSet<Int>()

    /** Area of the layer that had pixels when the transform began. */
    val source: IRect = surface.contentBounds() ?: IRect.EMPTY

    val isEmpty: Boolean get() = source.isEmpty

    var matrix: Affine = Affine.IDENTITY
        private set
    private var lastOut: IRect? = null

    init {
        for (i in src.indices) if (src[i] != null) {
            touched += i
            surface.setTile(i, null)
        }
    }

    /** Redraws the layer with [m] applied. Returns the area to recomposite. */
    fun render(m: Affine, quality: Resample? = null): IRect? {
        matrix = m
        val docBounds = surface.bounds
        val out = m.mapBounds(source).intersect(docBounds)
        for (i in touched) surface.setTile(i, null)
        val dirty = IRect.unionOf(IRect.unionOf(lastOut, if (lastOut == null) source else null), out.takeUnless { it.isEmpty })
        lastOut = out.takeUnless { it.isEmpty }
        if (out.isEmpty || source.isEmpty) return dirty
        touched.addAll(surface.tilesIn(out).toList())
        rasterize(m, out, quality)
        return dirty
    }

    private fun rasterize(m: Affine, out: IRect, quality: Resample?) {
        val inv = m.inverse()
        // Exact integer moves copy pixels as they are, whatever the quality.
        val integerMove = m.a == 1f && m.b == 0f && m.c == 0f && m.d == 1f &&
            m.tx == floor(m.tx) && m.ty == floor(m.ty)
        val mode = if (integerMove) Resample.PIXEL else quality
        val ss = if (mode == Resample.SMOOTH) ceil(1f / m.minScale.coerceAtLeast(0.01f)).toInt().coerceIn(1, 4) else 1
        val sl = source.left
        val st = source.top
        val sr = source.right
        val sb = source.bottom
        for (ti in surface.tilesIn(out)) {
            val tr = surface.tileRect(ti).intersect(out)
            var tile: IntArray? = surface.tile(ti)
            for (y in tr.top until tr.bottom) {
                for (x in tr.left until tr.right) {
                    val px: Int
                    if (ss == 1) {
                        val cx = x + 0.5f
                        val cy = y + 0.5f
                        val sx = inv.mapX(cx, cy) - 0.5f
                        val sy = inv.mapY(cx, cy) - 0.5f
                        if (sx < sl - 1 || sy < st - 1 || sx > sr || sy > sb) continue
                        px = when (mode) {
                            null, Resample.SMOOTH -> bilinear(sx, sy)
                            Resample.SHARP -> bicubic(sx, sy)
                            Resample.PIXEL -> sample(floor(sx + 0.5f).toInt(), floor(sy + 0.5f).toInt())
                        }
                    } else {
                        var a = 0
                        var r = 0
                        var g = 0
                        var bl = 0
                        for (j in 0 until ss) for (k in 0 until ss) {
                            val cx = x + (k + 0.5f) / ss
                            val cy = y + (j + 0.5f) / ss
                            val p = bilinear(inv.mapX(cx, cy) - 0.5f, inv.mapY(cx, cy) - 0.5f)
                            a += p ushr 24
                            r += (p shr 16) and 0xFF
                            g += (p shr 8) and 0xFF
                            bl += p and 0xFF
                        }
                        val n = ss * ss
                        px = Pixels.pack((a + n / 2) / n, (r + n / 2) / n, (g + n / 2) / n, (bl + n / 2) / n)
                    }
                    if (px == 0) continue
                    val t = tile ?: surface.tileOrCreate(ti).also { tile = it }
                    t[((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)] = px
                }
            }
        }
    }

    private fun sample(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= surface.width || y >= surface.height) return 0
        val t = src[(y shr TiledSurface.SHIFT) * surface.tilesX + (x shr TiledSurface.SHIFT)] ?: return 0
        return t[((y and TiledSurface.MASK) shl TiledSurface.SHIFT) or (x and TiledSurface.MASK)]
    }

    private fun bilinear(sx: Float, sy: Float): Int {
        val x0 = floor(sx).toInt()
        val y0 = floor(sy).toInt()
        val fx = sx - x0
        val fy = sy - y0
        val p00 = sample(x0, y0)
        val p10 = sample(x0 + 1, y0)
        val p01 = sample(x0, y0 + 1)
        val p11 = sample(x0 + 1, y0 + 1)
        if (p00 == p10 && p00 == p01 && p00 == p11) return p00
        val w00 = (1 - fx) * (1 - fy)
        val w10 = fx * (1 - fy)
        val w01 = (1 - fx) * fy
        val w11 = fx * fy
        fun ch(shift: Int): Int =
            (((p00 ushr shift) and 0xFF) * w00 + ((p10 ushr shift) and 0xFF) * w10 +
                ((p01 ushr shift) and 0xFF) * w01 + ((p11 ushr shift) and 0xFF) * w11 + 0.5f).toInt()
        return Pixels.pack(ch(24), ch(16), ch(8), ch(0))
    }

    /** Catmull-Rom bicubic, clamped so colour never exceeds alpha (premultiplied). */
    private fun bicubic(sx: Float, sy: Float): Int {
        val x0 = floor(sx).toInt()
        val y0 = floor(sy).toInt()
        val wx = weights(sx - x0)
        val wy = weights(sy - y0)
        var a = 0f
        var r = 0f
        var g = 0f
        var b = 0f
        for (j in 0..3) {
            val yy = y0 - 1 + j
            for (i in 0..3) {
                val p = sample(x0 - 1 + i, yy)
                if (p == 0) continue
                val w = wx[i] * wy[j]
                a += (p ushr 24) * w
                r += ((p shr 16) and 0xFF) * w
                g += ((p shr 8) and 0xFF) * w
                b += (p and 0xFF) * w
            }
        }
        val ai = (a + 0.5f).toInt().coerceIn(0, 255)
        if (ai == 0) return 0
        return Pixels.pack(ai, min(ai, max(0, (r + 0.5f).toInt())), min(ai, max(0, (g + 0.5f).toInt())), min(ai, max(0, (b + 0.5f).toInt())))
    }

    private fun weights(t: Float): FloatArray {
        val t2 = t * t
        val t3 = t2 * t
        return floatArrayOf(
            -0.5f * t3 + t2 - 0.5f * t,
            1.5f * t3 - 2.5f * t2 + 1f,
            -1.5f * t3 + 2f * t2 + 0.5f * t,
            0.5f * t3 - 0.5f * t2,
        )
    }

    /** Renders [m] at full quality and returns the undo step (null if nothing moved). */
    fun finish(m: Affine, quality: Resample): Pair<IRect?, PixelCommand?> {
        val dirty = render(m, quality)
        val before = HashMap<Int, IntArray?>()
        for (i in touched) before[i] = src[i]
        return dirty to PixelCommand.capture("Transform", layer, before)
    }

    /** Puts the original pixels back. */
    fun cancel(): IRect? {
        for (i in touched) surface.setTile(i, src[i])
        return IRect.unionOf(lastOut, source.takeUnless { it.isEmpty })
    }
}
