package com.artisthaven.drawing.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff

/**
 * Manages the ordered stack of [Layer] objects that make up a drawing document.
 *
 * Clean Architecture — Use-Case / Domain layer.
 *
 * Responsibilities:
 *  - Create, reorder, and delete layers.
 *  - Flatten (composite) all visible layers into a single output [Bitmap] for display.
 *  - Apply Multiply / Screen blending using open-source GLSL-equivalent per-pixel logic.
 *  - Provide the "active" layer that receives new drawing strokes.
 *
 * The composite output bitmap is reused across frames to avoid continuous GC pressure.
 */
class LayerManager(private val width: Int, private val height: Int) {

    /** Ordered list of layers, bottom-most first (index 0 = background). */
    private val _layers: MutableList<Layer> = mutableListOf()
    val layers: List<Layer> get() = _layers

    /** Index of the currently selected (active) layer. */
    var activeLayerIndex: Int = 0
        private set

    /** Returns the layer that receives drawing strokes right now. */
    val activeLayer: Layer? get() = _layers.getOrNull(activeLayerIndex)

    /**
     * Reusable flat composite bitmap.  Allocated once at construction and reused
     * every frame to avoid triggering the GC during rendering.
     */
    private val compositeBitmap: Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val compositeCanvas: Canvas = Canvas(compositeBitmap)

    /** Reusable paint object — allocated once, mutated per layer. */
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // Every document starts with one transparent layer.
        addLayer()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Layer management
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Adds a new transparent layer above the current active layer.
     *
     * @param name Optional display name. Defaults to "Layer N".
     * @return The newly created [Layer].
     */
    fun addLayer(name: String? = null): Layer {
        val id = (_layers.maxOfOrNull { it.id } ?: 0) + 1
        val layer = Layer(id, width, height, name ?: "Layer $id")
        val insertIndex = if (_layers.isEmpty()) 0 else activeLayerIndex + 1
        _layers.add(insertIndex, layer)
        activeLayerIndex = insertIndex
        return layer
    }

    /**
     * Removes the layer at [index] and recycles its bitmap.
     * The active index is adjusted to remain valid.
     * At least one layer is always kept in the stack.
     */
    fun removeLayer(index: Int) {
        if (_layers.size <= 1) return
        val removed = _layers.removeAt(index)
        removed.recycle()
        activeLayerIndex = activeLayerIndex.coerceIn(0, _layers.lastIndex)
    }

    /**
     * Moves a layer from [fromIndex] to [toIndex] in the stack.
     */
    fun moveLayer(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val layer = _layers.removeAt(fromIndex)
        _layers.add(toIndex, layer)
        activeLayerIndex = toIndex
    }

    /**
     * Changes the active layer to [index].
     */
    fun selectLayer(index: Int) {
        activeLayerIndex = index.coerceIn(0, _layers.lastIndex)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Compositing
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Flattens all visible layers into a single [Bitmap] using each layer's blend mode.
     *
     * The algorithm mirrors standard raster compositing:
     *   1. Clear the composite buffer to transparent.
     *   2. For each visible layer (bottom → top), sample the current composite as dst
     *      and apply the layer's blending formula.
     *
     * **Multiply** — `result.rgb = src.rgb × dst.rgb / 255`
     * **Screen**   — `result.rgb = 255 − (255 − src.rgb) × (255 − dst.rgb) / 255`
     *
     * For [Layer.BlendMode.NORMAL] the standard Android [Canvas.drawBitmap] with an
     * alpha-aware [Paint] achieves SRC_OVER in hardware.
     *
     * @return The composited output bitmap (same object every call — do **not** recycle).
     */
    fun flatten(): Bitmap {
        compositeCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Take a snapshot of the composite pixels for Multiply / Screen source reading.
        val dstPixels = IntArray(width * height)

        for (layer in _layers) {
            if (!layer.isVisible || layer.bitmap.isRecycled) continue

            when (layer.blendMode) {
                Layer.BlendMode.NORMAL -> {
                    // Hardware-accelerated SRC_OVER path.
                    paint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                    compositeCanvas.drawBitmap(layer.bitmap, 0f, 0f, paint)
                }

                Layer.BlendMode.MULTIPLY, Layer.BlendMode.SCREEN -> {
                    compositeBitmap.getPixels(dstPixels, 0, width, 0, 0, width, height)
                    val srcPixels = IntArray(width * height)
                    layer.bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

                    blendPixels(
                        src = srcPixels,
                        dst = dstPixels,
                        mode = layer.blendMode,
                        opacity = layer.opacity
                    )

                    // Write result back into the composite bitmap.
                    compositeBitmap.setPixels(dstPixels, 0, width, 0, 0, width, height)
                }
            }
        }

        return compositeBitmap
    }

    /**
     * Frees all layer bitmaps and the composite buffer.
     * Must be called when the document is closed to avoid memory leaks.
     */
    fun recycle() {
        _layers.forEach { it.recycle() }
        _layers.clear()
        if (!compositeBitmap.isRecycled) compositeBitmap.recycle()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private pixel-blend logic
    //
    // The formulas below are the standard open-source GLSL definitions as used in
    // the W3C Compositing and Blending Level 1 specification:
    //   https://www.w3.org/TR/compositing-1/
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Applies [mode] blending of [src] over [dst] in-place (result written into [dst]).
     * Alpha is taken from [src]; the layer [opacity] scales the final alpha contribution.
     *
     * Per-pixel loop is kept allocation-free to avoid triggering GC mid-frame.
     */
    private fun blendPixels(
        src: IntArray,
        dst: IntArray,
        mode: Layer.BlendMode,
        opacity: Float
    ) {
        val alphaScale = opacity.coerceIn(0f, 1f)

        for (i in src.indices) {
            val sp = src[i]
            val dp = dst[i]

            val sA = (sp ushr 24) and 0xFF
            val sR = (sp ushr 16) and 0xFF
            val sG = (sp ushr 8) and 0xFF
            val sB = sp and 0xFF

            val dA = (dp ushr 24) and 0xFF
            val dR = (dp ushr 16) and 0xFF
            val dG = (dp ushr 8) and 0xFF
            val dB = dp and 0xFF

            // Compute blended RGB channels using integer arithmetic (avoid float allocs).
            val (bR, bG, bB) = when (mode) {
                // Multiply: Cb = Cs × Cd
                // Integer form: result = src * dst / 255
                Layer.BlendMode.MULTIPLY -> Triple(
                    sR * dR / 255,
                    sG * dG / 255,
                    sB * dB / 255
                )
                // Screen: Cb = Cs + Cd – Cs × Cd
                // Integer form: result = src + dst - src*dst/255
                Layer.BlendMode.SCREEN -> Triple(
                    sR + dR - sR * dR / 255,
                    sG + dG - sG * dG / 255,
                    sB + dB - sB * dB / 255
                )
                else -> Triple(sR, sG, sB)
            }

            // Simple alpha compositing: out_a = sA * opacity + dA * (1 − sA*opacity/255)
            val srcAlpha = (sA * alphaScale).toInt().coerceIn(0, 255)
            val outA = (srcAlpha + dA * (255 - srcAlpha) / 255).coerceIn(0, 255)

            dst[i] = (outA shl 24) or
                (bR.coerceIn(0, 255) shl 16) or
                (bG.coerceIn(0, 255) shl 8) or
                bB.coerceIn(0, 255)
        }
    }
}
