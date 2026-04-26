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
    /** Backing bitmap. ARGB_8888 ensures 32-bit color + full alpha channel. */
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
     * layer's [opacity].
     *
     * NORMAL mode uses hardware-accelerated SRC_OVER compositing via [Canvas.drawBitmap].
     * MULTIPLY and SCREEN blending are handled by [LayerManager.flatten], which has
     * access to the dst pixel data from the composite buffer below this layer and
     * performs the per-pixel blend without any intermediate bitmap allocation.
     *
     * @param targetCanvas Canvas to composite onto.
     * @param paint        Reusable [Paint] object (avoids allocation on the render path).
     */
    fun compositeOnto(targetCanvas: Canvas, paint: Paint) {
        if (!isVisible || bitmap.isRecycled) return
        paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        targetCanvas.drawBitmap(bitmap, 0f, 0f, paint)
    }
}
