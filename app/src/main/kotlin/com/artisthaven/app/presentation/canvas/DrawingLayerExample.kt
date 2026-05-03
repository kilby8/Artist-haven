package com.artisthaven.app.presentation.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import androidx.compose.ui.graphics.Color
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.CanvasLayerConfig
import com.artisthaven.app.domain.model.CanvasType
import com.artisthaven.app.domain.model.DrawingStroke
import com.artisthaven.app.domain.model.StrokePoint
import com.artisthaven.app.presentation.canvas.texture.CanvasTextureFactory

/**
 * Comprehensive example showing how to integrate the CanvasProvider system
 * with the existing drawing workflow in Artist-Haven.
 *
 * This class demonstrates:
 * 1. Canvas initialization
 * 2. Background rendering before brush strokes
 * 3. Tooth interaction for dry brush effects
 * 4. Lighting simulation
 * 5. Runtime canvas type switching
 */
class DrawingLayerWithCanvasExample(
    private val context: Context,
    private val width: Int,
    private val height: Int,
) {

    private val canvasManager = CanvasRenderingManager.getInstance()
    private val canvasProvider = CanvasProvider.getInstance()
    private val paintBrush = PaintBrush(context)

    // Layer bitmap for accumulated strokes
    private val layerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val layerCanvas = AndroidCanvas(layerBitmap)

    // Configuration state
    private var currentCanvasType = CanvasType.COLD_PRESS_PAPER
    private var enableToothInteraction = true
    private var enableLighting = false
    private var toothIntensity = 0.35f

    init {
        // Initialize canvas for the project
        canvasManager.initialize(
            width = width,
            height = height,
            canvasType = currentCanvasType,
            enableToothInteraction = enableToothInteraction,
        )
    }

    /**
     * Example: Render the complete drawing (background + all strokes).
     * Call this in your main draw/render loop.
     */
    fun renderComplete(targetCanvas: AndroidCanvas) {
        // Step 1: Draw canvas background (must be first!)
        canvasManager.renderBackground(targetCanvas)

        // Step 2: Draw all accumulated strokes
        targetCanvas.drawBitmap(layerBitmap, 0f, 0f, null)
    }

    /**
     * Example: Add a new stroke with optional tooth interaction.
     * This is called from touch input or CanvasViewModel.
     */
    fun addStroke(stroke: DrawingStroke, brush: Brush) {
        val points = stroke.points
        if (points.isEmpty()) return

        // Render stroke to layer bitmap
        if (enableToothInteraction) {
            // Get canvas texture for tooth masking
            val textureConfig = canvasManager.getCurrentConfig() ?: return
            val texture = canvasProvider.getTextureForType(textureConfig.canvasType)

            // Use enhanced tooth interaction method
            paintBrush.renderStrokeWithToothInteraction(
                canvas = layerCanvas,
                points = points,
                brush = brush,
                canvasTexture = texture,
                toothIntensity = toothIntensity,
            )
        } else {
            // Normal rendering without tooth masking
            paintBrush.renderStroke(
                canvas = layerCanvas,
                points = points,
                brush = brush,
            )
        }
    }

    /**
     * Example: Switch canvas type at runtime.
     * Used when user changes canvas in UI.
     */
    fun switchCanvasType(newType: CanvasType) {
        currentCanvasType = newType
        canvasManager.setCanvasType(newType)

        // Mark layer as needing redraw
        invalidateLayer()
    }

    /**
     * Example: Toggle dry brush effect.
     * Users can enable/disable tooth interaction from settings.
     */
    fun toggleToothInteraction(enabled: Boolean) {
        enableToothInteraction = enabled
        canvasManager.setToothInteraction(enabled)
        invalidateLayer()
    }

    /**
     * Example: Toggle lighting simulation.
     * Adds/removes 3D depth effect.
     */
    fun toggleLighting(enabled: Boolean) {
        enableLighting = enabled
        canvasManager.setLighting(enabled)
        invalidateLayer()
    }

    /**
     * Example: Adjust tooth intensity.
     * Higher values = stronger dry brush effect.
     */
    fun setToothIntensity(intensity: Float) {
        toothIntensity = intensity.coerceIn(0f, 1f)
        invalidateLayer()
    }

    /**
     * Example: Tint the canvas color.
     * Useful for warm/cool paper variations.
     */
    fun setCanvasTintColor(color: Color) {
        canvasManager.setTintColor(color)
        invalidateLayer()
    }

    /**
     * Example: Export to PNG preserving canvas texture.
     * This would be called from export functionality.
     */
    fun exportToPNG(outputPath: String): Boolean {
        val exportBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val exportCanvas = AndroidCanvas(exportBitmap)

        // Draw complete rendering to export bitmap
        renderComplete(exportCanvas)

        // Save to file
        return saveBitmapPNG(exportBitmap, outputPath)
    }

    /**
     * Example: Create a watercolor-specific setup.
     * Demonstrates preset usage for specific artistic styles.
     */
    fun setupForWatercolor() {
        val config = CanvasPresets.watercolorPreset()

        currentCanvasType = config.canvasType
        enableToothInteraction = config.enableToothInteraction
        enableLighting = config.enableLighting
        toothIntensity = 0.35f

        canvasManager.initialize(
            width = width,
            height = height,
        )
        invalidateLayer()
    }

    /**
     * Example: Create an oil painting-specific setup.
     * Heavy texture, strong tooth interaction.
     */
    fun setupForOilPainting() {
        val config = CanvasPresets.oilPaintingPreset()

        currentCanvasType = config.canvasType
        enableToothInteraction = config.enableToothInteraction
        enableLighting = config.enableLighting
        toothIntensity = 0.42f  // Stronger for oil

        canvasManager.initialize(
            width = width,
            height = height,
        )
        invalidateLayer()
    }

    /**
     * Example: Create a technical drawing setup.
     * Smooth surface, precision grid reference.
     */
    fun setupForTechnicalDrawing() {
        val config = CanvasPresets.technicalDrawingPreset()

        currentCanvasType = config.canvasType
        enableToothInteraction = config.enableToothInteraction
        enableLighting = config.enableLighting

        canvasManager.initialize(
            width = width,
            height = height,
        )
        invalidateLayer()
    }

    /**
     * Example: Clear the layer and reset to blank canvas.
     * Used when creating new projects or clearing canvas.
     */
    fun clearLayer() {
        layerBitmap.eraseColor(0)  // Transparent
    }

    /**
     * Example: Get current canvas configuration for UI display.
     */
    fun getCurrentCanvasConfig(): CanvasLayerConfig? {
        return canvasManager.getCurrentConfig()
    }

    /**
     * Example: Get texture preview for UI thumbnail.
     * Can be used to show canvas preview in settings.
     */
    fun getCanvasPreviewBitmap(canvasType: CanvasType): Bitmap {
        return canvasProvider.getTextureForType(canvasType, size = 256)
    }

    // Helper functions

    private fun invalidateLayer() {
        // Trigger redraw of the layer
        // In a real implementation, this would call invalidate() or queue a recompose
        layerBitmap.eraseColor(0)  // Clear and redraw
    }

    private fun saveBitmapPNG(bitmap: Bitmap, path: String): Boolean {
        return try {
            val fos = java.io.FileOutputStream(path)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Example: Advanced - use canvas texture analysis for brush adaptation.
     * Could analyze texture roughness and suggest brush size.
     */
    fun sugggestBrushSizeForCanvas(canvasType: CanvasType): Float {
        return when (canvasType) {
            // Rough textures benefit from larger brushes
            CanvasType.FINE_GRAIN_LINEN -> 16f
            CanvasType.PRIMED_CANVAS -> 18f
            CanvasType.DARK_SLATE -> 14f

            // Fine textures work better with smaller brushes
            CanvasType.COLD_PRESS_PAPER -> 12f
            CanvasType.VELLUM -> 8f

            // Grid is digital, any size works
            CanvasType.TRANSPARENT_GRID -> 10f
        }
    }

    /**
     * Example: Advanced - compose multiple canvas effects.
     * Blend two canvas types for custom surfaces.
     */
    fun createBlendedCanvas(
        primary: CanvasType,
        secondary: CanvasType,
        blendAlpha: Float = 0.5f,
    ): Bitmap {
        val primaryTexture = canvasProvider.getTextureForType(primary, size = 512)
        val secondaryTexture = canvasProvider.getTextureForType(secondary, size = 512)
        return CanvasTextureFactory.blendTextures(primaryTexture, secondaryTexture, blendAlpha)
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        layerBitmap.recycle()
        canvasManager.destroy()
        canvasProvider.clearCache()
    }
}

/**
 * Example integration with existing CanvasViewModel pattern.
 * Shows how to use DrawingLayerWithCanvasExample in a real app.
 */
class CanvasViewModelIntegrationExample(
    private val context: Context,
) {

    private var drawingLayer: DrawingLayerWithCanvasExample? = null

    /**
     * Initialize drawing when project is opened.
     */
    fun initializeProject(projectWidth: Int, projectHeight: Int) {
        drawingLayer = DrawingLayerWithCanvasExample(
            context = context,
            width = projectWidth,
            height = projectHeight,
        )

        // Can optionally set initial canvas preset
        drawingLayer?.setupForWatercolor()
    }

    /**
     * Handle new stroke added by user.
     */
    fun onStrokeCommitted(stroke: DrawingStroke, brush: Brush) {
        drawingLayer?.addStroke(stroke, brush)
    }

    /**
     * Handle canvas type change from UI.
     */
    fun onCanvasTypeChanged(canvasType: CanvasType) {
        drawingLayer?.switchCanvasType(canvasType)
    }

    /**
     * Handle tooth interaction toggle.
     */
    fun onToothInteractionToggled(enabled: Boolean) {
        drawingLayer?.toggleToothInteraction(enabled)
    }

    /**
     * Handle lighting toggle.
     */
    fun onLightingToggled(enabled: Boolean) {
        drawingLayer?.toggleLighting(enabled)
    }

    /**
     * Handle tooth intensity slider change (0-100 from UI).
     */
    fun onToothIntensityChanged(intensityPercent: Int) {
        drawingLayer?.setToothIntensity(intensityPercent / 100f)
    }

    /**
     * Render the complete drawing.
     */
    fun render(targetCanvas: AndroidCanvas) {
        drawingLayer?.renderComplete(targetCanvas)
    }

    /**
     * Export drawing to PNG.
     */
    fun exportDrawing(outputPath: String): Boolean {
        return drawingLayer?.exportToPNG(outputPath) ?: false
    }

    /**
     * Get current artistic style recommendation.
     */
    fun getArtisticStylePreset(): String {
        return when (drawingLayer?.getCurrentCanvasConfig()?.canvasType) {
            CanvasType.COLD_PRESS_PAPER -> "Watercolor"
            CanvasType.FINE_GRAIN_LINEN -> "Oil Painting"
            CanvasType.PRIMED_CANVAS -> "Heavy Oil"
            CanvasType.DARK_SLATE -> "Dark Medium (Charcoal/Ink)"
            CanvasType.VELLUM -> "Technical Drawing"
            CanvasType.TRANSPARENT_GRID -> "Digital"
            null -> "None"
        }
    }

    /**
     * Clean up when project is closed.
     */
    fun cleanup() {
        drawingLayer?.destroy()
        drawingLayer = null
    }
}

