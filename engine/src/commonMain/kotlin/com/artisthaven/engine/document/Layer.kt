package com.artisthaven.engine.document

import com.artisthaven.engine.raster.TiledSurface

/** Properties of a layer that can change without touching its pixels. */
data class LayerProps(
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val alphaLock: Boolean = false,
    val clipping: Boolean = false,
)

class Layer(
    val id: String,
    var props: LayerProps,
    val surface: TiledSurface,
    /**
     * Optional layer mask, painted like a layer: black hides, white (or nothing) shows. Stored as
     * ordinary paint so every brush works on it; see [maskVisibility].
     */
    var mask: TiledSurface? = null,
) {
    val name: String get() = props.name
    val visible: Boolean get() = props.visible
    val opacity: Float get() = props.opacity
    val blendMode: BlendMode get() = props.blendMode
    val alphaLock: Boolean get() = props.alphaLock
    val clipping: Boolean get() = props.clipping

    fun duplicate(newId: String, newName: String): Layer =
        Layer(newId, props.copy(name = newName), surface.copy(), mask?.copy())

    /** The layer's pixel at (x, y) with its mask applied (premultiplied). */
    fun maskedPixel(x: Int, y: Int): Int {
        val c = surface.getPixel(x, y)
        val m = mask ?: return c
        if (c == 0) return c
        return com.artisthaven.engine.raster.Pixels.scale(c, maskVisibility(m.getPixel(x, y)))
    }

    companion object {
        /**
         * How visible a layer is under a mask pixel, 0..1. An empty mask pixel shows everything;
         * paint hides in proportion to how dark it is, so black hides and white reveals.
         */
        fun maskVisibility(maskPixel: Int): Float {
            if (maskPixel == 0) return 1f
            val a = maskPixel ushr 24
            val lum = (((maskPixel shr 16) and 0xFF) * 77 + ((maskPixel shr 8) and 0xFF) * 150 + (maskPixel and 0xFF) * 29) shr 8
            return 1f - (a - lum).coerceIn(0, 255) / 255f
        }
    }
}
