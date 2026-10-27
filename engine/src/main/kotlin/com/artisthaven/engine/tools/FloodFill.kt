package com.artisthaven.engine.tools

import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels

/**
 * Colour fill, like Procreate's ColorDrop. The fill region is found on the whole picture (so
 * line art on another layer still contains it) or on the active layer only, then grown by one
 * pixel so it tucks under anti-aliased line edges instead of leaving a pale halo.
 */
object FloodFill {

    /**
     * Fills from ([x], [y]) on [layer] with straight colour [color]. Returns the changed area and
     * the tiles as they were before, or null if the point is outside the canvas.
     */
    fun fill(
        doc: Document,
        layer: Layer,
        x: Int,
        y: Int,
        color: Int,
        tolerance: Float = 0.12f,
        sampleAllLayers: Boolean = true,
        opacity: Float = 1f,
    ): Pair<IRect, Map<Int, IntArray?>>? {
        val (rect, cover) = region(doc, layer, x, y, tolerance, sampleAllLayers) ?: return null
        val sel = doc.selection
        val before = HashMap<Int, IntArray?>()
        for (index in layer.surface.tilesIn(rect)) before[index] = layer.surface.copyTile(index)
        val src = Pixels.premultiply(color)
        for (yy in rect.top until rect.bottom) for (xx in rect.left until rect.right) {
            var a = cover[(yy - rect.top) * rect.width + (xx - rect.left)] * opacity
            if (sel != null) a *= sel.coverage(xx, yy)
            if (a <= 0f) continue
            val d = layer.surface.getPixel(xx, yy)
            val out = if (layer.alphaLock) {
                val da = Pixels.alpha(d)
                if (da == 0) continue
                Pixels.lerp(d, Pixels.scale(src, da / 255f).let { Pixels.pack(da, Pixels.red(it), Pixels.green(it), Pixels.blue(it)) }, a)
            } else {
                Pixels.srcOver(d, Pixels.scale(src, a))
            }
            layer.surface.setPixel(xx, yy, out)
        }
        return rect to before
    }

    /**
     * The connected area of similar colour around ([x], [y]), as coverage 0..1 over the returned
     * rectangle (row-major), grown by a pixel to sit under anti-aliased edges. Also used by the
     * automatic (magic wand) selection.
     */
    fun region(
        doc: Document,
        layer: Layer,
        x: Int,
        y: Int,
        tolerance: Float,
        sampleAllLayers: Boolean,
    ): Pair<IRect, FloatArray>? {
        val w = doc.width
        val h = doc.height
        if (x !in 0 until w || y !in 0 until h) return null

        val reference: IntArray = if (sampleAllLayers) {
            Compositor.flatten(doc, withPaper = false)
        } else {
            IntArray(w * h).also { buf ->
                for (yy in 0 until h) for (xx in 0 until w) buf[yy * w + xx] = Pixels.unpremultiply(layer.surface.getPixel(xx, yy))
            }
        }
        val seed = reference[y * w + x]
        val mask = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        var minX = x
        var maxX = x
        var minY = y
        var maxY = y
        fun matches(i: Int) = !mask[i] && Pixels.distance(reference[i], seed) <= tolerance

        stack[sp++] = y * w + x
        mask[y * w + x] = true
        while (sp > 0) {
            val i = stack[--sp]
            val py = i / w
            var lx = i % w
            var rx = lx
            while (lx > 0 && matches(py * w + lx - 1)) { lx--; mask[py * w + lx] = true }
            while (rx < w - 1 && matches(py * w + rx + 1)) { rx++; mask[py * w + rx] = true }
            if (lx < minX) minX = lx
            if (rx > maxX) maxX = rx
            if (py < minY) minY = py
            if (py > maxY) maxY = py
            for (ny in intArrayOf(py - 1, py + 1)) {
                if (ny < 0 || ny >= h) continue
                var xx = lx
                while (xx <= rx) {
                    val j = ny * w + xx
                    if (matches(j)) {
                        mask[j] = true
                        stack[sp++] = j
                        // Skip the rest of this run; the pushed pixel's own scan will claim it.
                        while (xx + 1 <= rx && matches(ny * w + xx + 1)) xx++
                    }
                    xx++
                }
            }
        }

        // Grow by one pixel with partial coverage to sit under anti-aliased edges.
        val rect = IRect(maxOf(0, minX - 1), maxOf(0, minY - 1), minOf(w, maxX + 2), minOf(h, maxY + 2))
        val cover = FloatArray(rect.width * rect.height)
        for (yy in rect.top until rect.bottom) for (xx in rect.left until rect.right) {
            val i = yy * w + xx
            val c = (yy - rect.top) * rect.width + (xx - rect.left)
            if (mask[i]) {
                cover[c] = 1f
            } else {
                var n = 0
                if (xx > 0 && mask[i - 1]) n++
                if (xx < w - 1 && mask[i + 1]) n++
                if (yy > 0 && mask[i - w]) n++
                if (yy < h - 1 && mask[i + w]) n++
                if (n > 0) cover[c] = 1f
            }
        }

        return rect to cover
    }
}
