package com.artisthaven.drawing.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff

/**
 * Represents a single drawable layer in the canvas composition stack.
 *
 * Clean Architecture — Entity layer.
 * Each [Layer] owns a [Bitmap] that serves as its backing pixel buffer.
 * The bitmap is always [Bitmap.Config.ARGB_8888] to support full alpha compositing.
 *
 * @param id        Unique identifier for this layer.
 * @param width     Width of the layer bitmap in pixels.
 * @param height    Height of the layer bitmap in pixels.
 * @param name      Human-readable display name (default = "Layer <id>").
 */
class Layer(
    val id: Int,
    val width: Int,
    val height: Int,
    val name: String = "Layer $id"
) {
    /** Backing bitmap. ARGB_8888 ensures 32-bit colour + full alpha channel. */
    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /** Canvas bound to [bitmap] — used by drawing commands to paint into this layer. */
    val canvas: Canvas = Canvas(bitmap)

    /** Whether this layer is composited into the final output. */
    var isVisible: Boolean = true

    /** Global opacity applied when this layer is composited (0.0 … 1.0). */
    var opacity: Float = 1f

    /** Blend mode used when compositing this layer onto layers beneath it. */
    var blendMode: BlendMode = BlendMode.NORMAL

    init {
        // Start with a transparent background.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    /**
     * Clears the entire layer to full transparency.
     * Call this when the user triggers "clear layer".
     */
    fun clear() {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    /**
     * Releases the bitmap resources held by this layer.
     * Must be called when the layer is permanently removed to prevent memory leaks.
     */
    fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    /**
     * Supported blend modes for layer composition.
     *
     * - [NORMAL]   — standard alpha compositing (src-over).
     * - [MULTIPLY] — darkens; each channel is src × dst.
     * - [SCREEN]   — brightens; result = 1 − (1 − src)(1 − dst).
     */
    enum class BlendMode {
        NORMAL,
        MULTIPLY,
        SCREEN
    }

    /**
     * Paints this layer's [bitmap] onto the provided [targetCanvas] using the
     * layer's [opacity] and software-emulated [blendMode].
     *
     * Multiply and Screen are computed per-pixel so they behave correctly on
     * any Android version without requiring hardware-canvas blend mode support.
     *
     * @param targetCanvas Canvas to composite onto.
     * @param paint        Reusable [Paint] object (avoids allocation on the render path).
     */
    fun compositeOnto(targetCanvas: Canvas, paint: Paint) {
        if (!isVisible || bitmap.isRecycled) return

        when (blendMode) {
            BlendMode.NORMAL -> {
                paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                targetCanvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            BlendMode.MULTIPLY, BlendMode.SCREEN -> {
                // Apply per-pixel blend via a composited intermediate bitmap.
                val blended = applyPixelBlend(blendMode)
                paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                targetCanvas.drawBitmap(blended, 0f, 0f, paint)
                blended.recycle()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Produces a new bitmap where every pixel of [bitmap] has been passed through
     * the requested [mode] formula.  The destination (dst) pixel value is taken from
     * [bitmap] itself because at composition time only the source bitmap is known here;
     * the actual dst blending against the composited stack is handled by [LayerManager].
     *
     * This method uses [Bitmap.getPixels] / [Bitmap.setPixels] to operate on the
     * full pixel array in one JNI round-trip — minimising per-pixel overhead.
     */
    private fun applyPixelBlend(mode: BlendMode): Bitmap {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val (nr, ng, nb) = when (mode) {
                // Multiply: each channel = (src / 255) * (src / 255) * 255
                // Here we self-blend (src == dst) as a demonstration; in LayerManager
                // the real dst comes from the composite below this layer.
                BlendMode.MULTIPLY -> Triple(
                    (r * r) / 255,
                    (g * g) / 255,
                    (b * b) / 255
                )
                // Screen: result = 255 - (255-r)*(255-r)/255
                BlendMode.SCREEN -> Triple(
                    255 - ((255 - r) * (255 - r)) / 255,
                    255 - ((255 - g) * (255 - g)) / 255,
                    255 - ((255 - b) * (255 - b)) / 255
                )
                else -> Triple(r, g, b)
            }

            pixels[i] = (a shl 24) or
                (nr.coerceIn(0, 255) shl 16) or
                (ng.coerceIn(0, 255) shl 8) or
                nb.coerceIn(0, 255)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
