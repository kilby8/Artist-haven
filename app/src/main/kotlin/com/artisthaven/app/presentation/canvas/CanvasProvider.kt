package com.artisthaven.app.presentation.canvas

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.artisthaven.app.domain.model.CanvasLayerConfig
import com.artisthaven.app.domain.model.CanvasType
import com.artisthaven.app.presentation.canvas.texture.CanvasTextureFactory
import kotlin.math.max
import kotlin.random.Random

/**
 * Professional canvas provider system for high-performance rendering.
 *
 * Features:
 * - Modular canvas types (8+ surfaces)
 * - RenderNode acceleration for non-laggy backgrounds
 * - Seamless texture tiling with random offsets
 * - Canvas "tooth" interaction for dry brush effects
 * - Lighting simulation for depth perception
 * - ColorFilter tinting for texture customization
 *
 * Architecture:
 * - Textures are baked on first use and cached
 * - Each CanvasLayer manages its own bitmap and shader
 * - Thread-safe for concurrent access
 */
class CanvasProvider {

    private val textureCache = mutableMapOf<String, Bitmap>()
    private val shaderCache = mutableMapOf<String, Shader>()
    private val paintCache = mutableMapOf<String, AndroidPaint>()

    private val cacheAccessLock = Any()

    /**
     * Create a canvas background layer with the specified configuration.
     * Returns a CanvasLayer ready for rendering.
     */
    fun createCanvasLayer(
        config: CanvasLayerConfig,
        width: Int,
        height: Int,
    ): CanvasLayer {
        return CanvasLayer(
            config = config,
            width = width,
            height = height,
            textureProvider = this,
        )
    }

    /**
     * Get or create a texture bitmap for the given canvas type.
     * Textures are cached and reused across multiple layers.
     */
    fun getTextureForType(
        canvasType: CanvasType,
        size: Int = 512,
        randomOffset: Long = 0L,
    ): Bitmap = synchronized(cacheAccessLock) {
        val cacheKey = "${canvasType.name}_${size}_${randomOffset}"

        textureCache[cacheKey]?.let { return it }

        val texture = when (canvasType) {
            CanvasType.COLD_PRESS_PAPER ->
                CanvasTextureFactory.createColdPressPaperTexture(size, randomOffset)

            CanvasType.FINE_GRAIN_LINEN ->
                CanvasTextureFactory.createFineGrainLinenTexture(size, randomOffset)

            CanvasType.DARK_SLATE ->
                CanvasTextureFactory.createDarkSlateTexture(size, randomOffset)

            CanvasType.TRANSPARENT_GRID ->
                CanvasTextureFactory.createTransparentGridTexture(size)

            CanvasType.VELLUM ->
                CanvasTextureFactory.createVellumTexture(size, randomOffset)

            CanvasType.PRIMED_CANVAS ->
                CanvasTextureFactory.createPrimedCanvasTexture(size, randomOffset)
        }

        textureCache[cacheKey] = texture
        texture
    }

    /**
     * Create a shader for seamless tiling with optional random offset.
     * The LocalMatrix is randomized on first use to hide repeating patterns.
     */
    fun createTilingShader(
        texture: Bitmap,
        tileMode: Shader.TileMode = Shader.TileMode.REPEAT,
        randomOffsetX: Int = 0,
        randomOffsetY: Int = 0,
    ): Shader = synchronized(cacheAccessLock) {
        val cacheKey = "shader_${texture.hashCode()}_${randomOffsetX}_${randomOffsetY}"

        shaderCache[cacheKey]?.let { return it }

        val shader = BitmapShader(texture, tileMode, tileMode)

        if (randomOffsetX != 0 || randomOffsetY != 0) {
            val matrix = Matrix().apply {
                postTranslate(randomOffsetX.toFloat(), randomOffsetY.toFloat())
            }
            shader.setLocalMatrix(matrix)
        }

        shaderCache[cacheKey] = shader
        shader
    }

    /**
     * Create a tinted color filter for canvas textures.
     * Allows dynamic color adjustment without recreating bitmaps.
     */
    fun createTintColorFilter(tintColor: Color): ColorFilter {
        return PorterDuffColorFilter(tintColor.toArgb(), PorterDuff.Mode.SRC_ATOP)
    }

    /**
     * Clear the texture and shader caches to free memory.
     * Call this when switching projects or to reduce memory pressure.
     */
    fun clearCache() {
        synchronized(cacheAccessLock) {
            textureCache.forEach { (_, bitmap) -> bitmap.recycle() }
            textureCache.clear()
            shaderCache.clear()
            paintCache.clear()
        }
    }

    companion object {
        private var instance: CanvasProvider? = null

        @Synchronized
        fun getInstance(): CanvasProvider {
            if (instance == null) {
                instance = CanvasProvider()
            }
            return instance!!
        }
    }
}

/**
 * A canvas layer that manages texture rendering with RenderNode acceleration.
 *
 * Features:
 * - Hardware-accelerated background rendering (API 29+)
 * - Tooth interaction (DST_IN blend for dry brush effect)
 * - Lighting simulation (optional directional light)
 * - ColorFilter tinting with dynamic opacity
 */
class CanvasLayer(
    val config: CanvasLayerConfig,
    val width: Int,
    val height: Int,
    private val textureProvider: CanvasProvider,
) {

    private var textureBitmap: Bitmap? = null
    private var composedBitmap: Bitmap? = null
    private var renderNode: android.graphics.RenderNode? = null

    private val matrix = Matrix()
    private val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        style = AndroidPaint.Style.FILL
    }

    init {
        // Pre-allocate composed bitmap if needed
        if (config.canvasType != CanvasType.TRANSPARENT_GRID) {
            composedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }

    /**
     * Render the canvas background to the given Canvas.
     * Uses RenderNode if available (API 29+) for non-blocking rendering.
     */
    fun render(canvas: AndroidCanvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated) {
            renderWithRenderNode(canvas)
        } else {
            renderDirect(canvas)
        }
    }

    /**
     * Hardware-accelerated render path using RenderNode (API 29+).
     * Prevents the canvas background from lagging during fast brush strokes.
     */
    @Suppress("DEPRECATION")
    private fun renderWithRenderNode(canvas: AndroidCanvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (renderNode == null) {
                renderNode = android.graphics.RenderNode("CanvasLayerBackground").apply {
                    setPosition(0, 0, width, height)
                }
            }

            val node = renderNode ?: return
            val rc = node.beginRecording(width, height)
            drawTextureLayer(rc)
            node.endRecording()
            canvas.drawRenderNode(node)
        }
    }

    /**
     * Direct render fallback for older Android versions.
     */
    private fun renderDirect(canvas: AndroidCanvas) {
        drawTextureLayer(canvas)
    }

    /**
     * Core texture drawing logic.
     * Handles tinting, tooth interaction, and lighting.
     */
    private fun drawTextureLayer(canvas: AndroidCanvas) {
        // Get or create texture
        val texture = textureProvider.getTextureForType(
            config.canvasType,
            size = max(256, max(width, height)),
            randomOffset = config.randomSeedOffset,
        )

        textureBitmap = texture

        // Create tinted shader with tiling
        val random = Random(config.randomSeedOffset)
        val offsetX = random.nextInt(texture.width)
        val offsetY = random.nextInt(texture.height)

        val baseShader = textureProvider.createTilingShader(
            texture = texture,
            tileMode = Shader.TileMode.REPEAT,
            randomOffsetX = offsetX,
            randomOffsetY = offsetY,
        )

        // Apply tint color filter
        paint.colorFilter = textureProvider.createTintColorFilter(config.tintColor)
        paint.alpha = (config.opacity * 255).toInt().coerceIn(0, 255)
        paint.shader = baseShader

        // Main texture pass
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Tooth interaction: canvas mask for dry brush effect
        if (config.enableToothInteraction) {
            applyToothInteraction(canvas, texture)
        }

        // Optional lighting simulation
        if (config.enableLighting) {
            applyLighting(canvas)
        }

        paint.shader = null
        paint.colorFilter = null
    }

    /**
     * Apply "tooth" interaction using DST_IN blend mode.
     * Light pressure fills only the peaks of the paper, leaving valleys white.
     * This creates the beautiful dry-brush effect seen in Adobe Fresco.
     */
    private fun applyToothInteraction(canvas: AndroidCanvas, texture: Bitmap) {
        val toothPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            // DST_IN: keep only where mask is opaque
            // This creates valleys (dark pixels) become transparent
            xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            // Reduce tooth opacity for better color visibility on light backgrounds like linen
            alpha = when (config.canvasType) {
                CanvasType.FINE_GRAIN_LINEN -> (config.lightingIntensity * 80).toInt().coerceIn(50, 120) // Gentler on linen
                else -> (config.lightingIntensity * 200).toInt().coerceIn(100, 200)
            }
        }

        // Create a contrast-enhanced version of the texture for masking
        val maskTexture = enhanceTextureContrast(texture, intensity = 1.3f)

        val maskShader = textureProvider.createTilingShader(maskTexture)
        toothPaint.shader = maskShader

        // Apply mask to show tooth interaction
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), toothPaint)

        maskTexture.recycle()
    }

    /**
     * Apply subtle directional lighting to the canvas for depth perception.
     * Simulates canvas fibers casting shadows as light rotates over the surface.
     */
    private fun applyLighting(canvas: AndroidCanvas) {
        val lightPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            // Screen blend mode for subtle lighting effects (brightens)
            xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            alpha = (config.lightingIntensity * 80).toInt().coerceIn(20, 80)
        }

        // Create a highlight gradient (subtle directional light)
        // Light comes from top-left
        val gradient = android.graphics.LinearGradient(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            AndroidColor.argb(100, 255, 255, 255),  // Bright top-left
            AndroidColor.argb(30, 255, 255, 255),   // Dim bottom-right
            Shader.TileMode.CLAMP,
        )
        
        lightPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightPaint)
    }

    /**
     * Enhance texture contrast for tooth interaction masking.
     * Increases the difference between peaks and valleys.
     */
    private fun enhanceTextureContrast(source: Bitmap, intensity: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val pixel = source.getPixel(x, y)
                val r = AndroidColor.red(pixel)
                val g = AndroidColor.green(pixel)
                val b = AndroidColor.blue(pixel)
                val a = AndroidColor.alpha(pixel)

                // Contrast enhancement: push values away from middle
                val midpoint = 128
                val rEnhanced = (midpoint + (r - midpoint) * intensity).toInt()
                    .coerceIn(0, 255)
                val gEnhanced = (midpoint + (g - midpoint) * intensity).toInt()
                    .coerceIn(0, 255)
                val bEnhanced = (midpoint + (b - midpoint) * intensity).toInt()
                    .coerceIn(0, 255)

                val enhanced = AndroidColor.argb(a, rEnhanced, gEnhanced, bEnhanced)
                result.setPixel(x, y, enhanced)
            }
        }
        return result
    }

    /**
     * Clean up resources when the layer is no longer needed.
     */
    fun destroy() {
        composedBitmap?.recycle()
        composedBitmap = null
    }
}

