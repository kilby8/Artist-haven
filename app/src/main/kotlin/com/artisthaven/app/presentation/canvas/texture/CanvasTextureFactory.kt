package com.artisthaven.app.presentation.canvas.texture

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import kotlin.random.Random

/**
 * Factory for generating seamless, professional canvas textures.
 * Uses procedural generation with noise functions to create realistic surfaces.
 */
object CanvasTextureFactory {

    /**
     * Create a cold-press paper texture using grayscale noise with subtle grain.
     * Uses Perlin-like noise for realistic paper appearance.
     */
    fun createColdPressPaperTexture(
        size: Int = 256,
        seed: Long = 4242L,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = Random(seed)

        // Generate base noise layer
        val noise = Array(size) { FloatArray(size) }
        for (y in 0 until size) {
            for (x in 0 until size) {
                noise[x][y] = random.nextFloat() * 0.6f
            }
        }

        // Smooth noise with weighted blur
        val smoothed = Array(size) { FloatArray(size) }
        for (y in 1 until size - 1) {
            for (x in 1 until size - 1) {
                var sum = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val weight = if (dx == 0 && dy == 0) 0.4f else 0.1f
                        sum += noise[x + dx][y + dy] * weight
                    }
                }
                smoothed[x][y] = sum
            }
        }

        // Apply to bitmap with paper-like colors
        for (y in 0 until size) {
            for (x in 0 until size) {
                val intensity = smoothed[x][y].coerceIn(0f, 1f)
                val base = 240
                val variation = (intensity * 30f).toInt()
                val value = base - variation

                val argb = AndroidColor.argb(255, value, value - 2, value - 8)
                bitmap.setPixel(x, y, argb)
            }
        }
        return bitmap
    }

    /**
     * Create a fine-grain linen texture with woven pattern simulation.
     * Uses cross-hatching to simulate fabric weave.
     * Improved contrast for better color visibility while maintaining linen aesthetic.
     */
    fun createFineGrainLinenTexture(
        size: Int = 512,
        seed: Long = 2048L,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val random = Random(seed)

        // Fill base color - slightly warmer tone
        canvas.drawColor(AndroidColor.rgb(250, 247, 242))

        // Draw woven pattern - horizontal threads
        val paint = AndroidPaint().apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = 1.2f
        }

        val threadSpacing = 12f
        var y = 0f
        var phase = 0
        while (y < size) {
            // Alternating thread colors for depth - slightly darker for better contrast
            val intensity = 215 - (phase % 2) * 18
            paint.color = AndroidColor.rgb(intensity, intensity, intensity - 5)

            // Slight undulation to threads
            var x = 0f
            while (x < size - 1) {
                val nextX = (x + 8f).coerceAtMost(size.toFloat())
                val yOffset = if (random.nextFloat() > 0.5f) 0.5f else -0.5f
                canvas.drawLine(x, y + yOffset, nextX, y - yOffset, paint)
                x = nextX
            }
            y += threadSpacing
            phase++
        }

        // Vertical threads at slight angle
        var x = 0f
        phase = 0
        while (x < size) {
            val intensity = 218 - (phase % 2) * 15
            paint.color = AndroidColor.rgb(intensity - 3, intensity, intensity)

            var y2 = 0f
            while (y2 < size - 1) {
                val nextY = (y2 + 8f).coerceAtMost(size.toFloat())
                val xOffset = if (random.nextFloat() > 0.5f) 0.5f else -0.5f
                canvas.drawLine(x + xOffset, y2, x - xOffset, nextY, paint)
                y2 = nextY
            }
            x += threadSpacing
            phase++
        }

        return bitmap
    }

    /**
     * Create a dark slate texture with subtle reflective noise grain.
     * Simulates slate with specular highlights.
     */
    fun createDarkSlateTexture(
        size: Int = 256,
        seed: Long = 1618L,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = Random(seed)

        for (y in 0 until size) {
            for (x in 0 until size) {
                // Base dark color with subtle variation
                val darkNoise = random.nextInt(25)
                val baseValue = 42 + darkNoise

                // Add subtle diagonal veining like real slate
                val veinPattern = ((x + y) % 40).toFloat() / 40f
                val veinValue = (baseValue + veinPattern * 8).toInt()

                // Random specular highlights (reflective grain)
                val specular = if (random.nextFloat() > 0.95f) random.nextInt(80) else 0
                val finalValue = (veinValue + specular).coerceIn(0, 255)

                val argb = AndroidColor.argb(255, finalValue, finalValue, finalValue + 3)
                bitmap.setPixel(x, y, argb)
            }
        }
        return bitmap
    }

    /**
     * Create a standard transparency checkerboard pattern.
     * Standard 8x8 pixel squares in alternating light/dark gray.
     */
    fun createTransparentGridTexture(size: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val squareSize = 8
        val darkColor = AndroidColor.rgb(210, 210, 210)
        val lightColor = AndroidColor.rgb(240, 240, 240)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val squareX = x / squareSize
                val squareY = y / squareSize
                val color = if ((squareX + squareY) % 2 == 0) darkColor else lightColor
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    /**
     * Create a smooth vellum texture ideal for ink and precise lines.
     * Very fine, almost imperceptible surface variation.
     */
    fun createVellumTexture(
        size: Int = 128,
        seed: Long = 314L,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = Random(seed)

        // Minimal noise, very smooth
        for (y in 0 until size) {
            for (x in 0 until size) {
                val microNoise = random.nextInt(4) // Only 0-3 variation
                val value = 255 - microNoise
                val argb = AndroidColor.argb(255, value, value - 1, value - 2)
                bitmap.setPixel(x, y, argb)
            }
        }
        return bitmap
    }

    /**
     * Create a primed canvas with cross-hatch texture for oil painting simulation.
     * Uses diagonal lines to simulate canvas weave and primer.
     */
    fun createPrimedCanvasTexture(
        size: Int = 512,
        seed: Long = 7777L,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val random = Random(seed)

        // Base primed color
        canvas.drawColor(AndroidColor.rgb(251, 250, 248))

        val paint = AndroidPaint().apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = 0.8f
        }

        // Diagonal hatch lines (/)
        val spacing = 14f
        var pos = -size.toFloat()
        while (pos < size * 2) {
            paint.color = AndroidColor.rgb(242, 240, 238)
            canvas.drawLine(pos, 0f, pos + size, size.toFloat(), paint)
            pos += spacing
        }

        // Counter diagonal hatch lines (\)
        pos = -size.toFloat()
        while (pos < size * 2) {
            paint.color = AndroidColor.rgb(245, 243, 240)
            canvas.drawLine(pos, size.toFloat(), pos + size, 0f, paint)
            pos += spacing
        }

        // Add fine noise over top
        for (y in 0 until size step 4) {
            for (x in 0 until size step 4) {
                if (random.nextFloat() > 0.85f) {
                    val intensity = random.nextInt(8)
                    val color = AndroidColor.rgb(
                        (244 - intensity).coerceAtLeast(0),
                        (242 - intensity).coerceAtLeast(0),
                        (240 - intensity).coerceAtLeast(0)
                    )
                    paint.color = color
                    paint.strokeWidth = 0.5f
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }
        }

        return bitmap
    }

    /**
     * Blend two textures together with weighted opacity.
     * Useful for creating layered texture effects.
     */
    fun blendTextures(
        base: Bitmap,
        overlay: Bitmap,
        alpha: Float = 0.5f,
    ): Bitmap {
        val result = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val blendAlpha = (alpha * 255).toInt().coerceIn(0, 255)

        for (y in 0 until base.height) {
            for (x in 0 until base.width) {
                val basePixel = base.getPixel(x, y)
                val overlayPixel = overlay.getPixel(x % overlay.width, y % overlay.height)

                val baseA = AndroidColor.alpha(basePixel)
                val baseR = AndroidColor.red(basePixel)
                val baseG = AndroidColor.green(basePixel)
                val baseB = AndroidColor.blue(basePixel)

                val overlayR = AndroidColor.red(overlayPixel)
                val overlayG = AndroidColor.green(overlayPixel)
                val overlayB = AndroidColor.blue(overlayPixel)

                val blendR = (baseR * (255 - blendAlpha) + overlayR * blendAlpha) / 255
                val blendG = (baseG * (255 - blendAlpha) + overlayG * blendAlpha) / 255
                val blendB = (baseB * (255 - blendAlpha) + overlayB * blendAlpha) / 255

                val blended = AndroidColor.argb(baseA, blendR, blendG, blendB)
                result.setPixel(x, y, blended)
            }
        }
        return result
    }

    /**
     * Apply a random offset to a seamless texture to hide repeating patterns.
     * Used on canvas open to ensure users never see the same pattern twice.
     */
    fun applyRandomOffset(bitmap: Bitmap, offsetX: Int, offsetY: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourceX = (x + offsetX) % width
                val sourceY = (y + offsetY) % height
                val pixel = bitmap.getPixel(sourceX, sourceY)
                result.setPixel(x, y, pixel)
            }
        }
        return result
    }
}

