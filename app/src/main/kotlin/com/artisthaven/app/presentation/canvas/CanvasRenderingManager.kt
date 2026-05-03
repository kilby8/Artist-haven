package com.artisthaven.app.presentation.canvas

import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.Color
import com.artisthaven.app.domain.model.CanvasLayerConfig
import com.artisthaven.app.domain.model.CanvasType

/**
 * Canvas rendering manager that coordinates texture rendering with brush strokes.
 * Provides high-level API for integrating CanvasProvider with the main drawing flow.
 */
class CanvasRenderingManager(
    private val provider: CanvasProvider = CanvasProvider.getInstance(),
) {

    private var currentLayer: CanvasLayer? = null
    private var currentConfig: CanvasLayerConfig? = null

    /**
     * Initialize canvas rendering for a new project.
     * Call this once when creating or opening a project.
     */
    fun initialize(
        width: Int,
        height: Int,
        canvasType: CanvasType = CanvasType.COLD_PRESS_PAPER,
        tintColor: Color = Color.White,
        enableToothInteraction: Boolean = true,
    ) {
        currentConfig = CanvasLayerConfig(
            canvasType = canvasType,
            tintColor = tintColor,
            opacity = 1f,
            randomSeedOffset = System.currentTimeMillis(),
            enableToothInteraction = enableToothInteraction,
            enableLighting = false,
        )

        currentLayer = provider.createCanvasLayer(
            config = currentConfig!!,
            width = width,
            height = height,
        )
    }

    /**
     * Render the canvas background to a canvas.
     * Should be called before rendering brush strokes to ensure background is underneath.
     */
    fun renderBackground(canvas: AndroidCanvas) {
        currentLayer?.render(canvas)
    }

    /**
     * Update canvas type without reinitializing.
     * Useful for dynamic canvas switching in the UI.
     */
    fun setCanvasType(canvasType: CanvasType) {
        val config = currentConfig?.copy(canvasType = canvasType)
            ?: throw IllegalStateException("Call initialize() first")
        updateConfig(config)
    }

    /**
     * Update tint color for the canvas texture.
     * Color filtering is applied without recreating textures.
     */
    fun setTintColor(color: Color) {
        val config = currentConfig?.copy(tintColor = color)
            ?: throw IllegalStateException("Call initialize() first")
        updateConfig(config)
    }

    /**
     * Toggle tooth interaction (dry brush effect).
     */
    fun setToothInteraction(enabled: Boolean) {
        val config = currentConfig?.copy(enableToothInteraction = enabled)
            ?: throw IllegalStateException("Call initialize() first")
        updateConfig(config)
    }

    /**
     * Toggle lighting simulation for depth perception.
     */
    fun setLighting(enabled: Boolean) {
        val config = currentConfig?.copy(enableLighting = enabled)
            ?: throw IllegalStateException("Call initialize() first")
        updateConfig(config)
    }

    /**
     * Set lighting intensity (0f - 1f, default 0.15f).
     */
    fun setLightingIntensity(intensity: Float) {
        val config = currentConfig?.copy(lightingIntensity = intensity.coerceIn(0f, 1f))
            ?: throw IllegalStateException("Call initialize() first")
        updateConfig(config)
    }

    /**
     * Set canvas opacity (for blending with layers below).
     */
    fun setOpacity(opacity: Float) {
        val config = currentConfig?.copy(opacity = opacity.coerceIn(0f, 1f))
            ?: throw IllegalStateException("Call initialize() first")
        updateConfig(config)
    }

    /**
     * Get current canvas configuration.
     */
    fun getCurrentConfig(): CanvasLayerConfig? = currentConfig

    /**
     * Get current canvas type.
     */
    fun getCurrentCanvasType(): CanvasType? = currentConfig?.canvasType

    /**
     * Clean up resources.
     */
    fun destroy() {
        currentLayer?.destroy()
        currentLayer = null
        currentConfig = null
    }

    private fun updateConfig(newConfig: CanvasLayerConfig) {
        currentConfig = newConfig
        currentLayer?.destroy()
        currentLayer = provider.createCanvasLayer(
            config = newConfig,
            width = currentLayer?.width ?: 1024,
            height = currentLayer?.height ?: 1024,
        )
    }

    companion object {
        private var instance: CanvasRenderingManager? = null

        @Synchronized
        fun getInstance(): CanvasRenderingManager {
            if (instance == null) {
                instance = CanvasRenderingManager()
            }
            return instance!!
        }
    }
}

/**
 * Preset canvas configurations for common artistic scenarios.
 */
object CanvasPresets {

    /**
     * Watercolor painting setup: high-grit paper, neutral tint, tooth interaction enabled.
     */
    fun watercolorPreset(): CanvasLayerConfig = CanvasLayerConfig(
        canvasType = CanvasType.COLD_PRESS_PAPER,
        tintColor = Color.White,
        opacity = 1f,
        randomSeedOffset = System.currentTimeMillis(),
        enableToothInteraction = true,
        enableLighting = true,
        lightingIntensity = 0.1f,
    )

    /**
     * Oil painting setup: primed canvas with cross-hatch, subtle lighting.
     */
    fun oilPaintingPreset(): CanvasLayerConfig = CanvasLayerConfig(
        canvasType = CanvasType.PRIMED_CANVAS,
        tintColor = Color.White,
        opacity = 1f,
        randomSeedOffset = System.currentTimeMillis(),
        enableToothInteraction = true,
        enableLighting = true,
        lightingIntensity = 0.15f,
    )

    /**
     * Technical drawing setup: smooth vellum, grid reference, precise lines.
     */
    fun technicalDrawingPreset(): CanvasLayerConfig = CanvasLayerConfig(
        canvasType = CanvasType.VELLUM,
        tintColor = Color(0xFFFFFCF7),
        opacity = 1f,
        randomSeedOffset = 0L,
        enableToothInteraction = false,
        enableLighting = false,
    )

    /**
     * Dark mode slate for charcoal and ink work.
     */
    fun darkmodeSlatePreset(): CanvasLayerConfig = CanvasLayerConfig(
        canvasType = CanvasType.DARK_SLATE,
        tintColor = Color(0xFF2A2A2A),
        opacity = 1f,
        randomSeedOffset = System.currentTimeMillis(),
        enableToothInteraction = true,
        enableLighting = false,
    )

    /**
     * Digital export: transparent grid for professional workflows.
     */
    fun digitalExportPreset(): CanvasLayerConfig = CanvasLayerConfig(
        canvasType = CanvasType.TRANSPARENT_GRID,
        tintColor = Color.Transparent,
        opacity = 1f,
        randomSeedOffset = 0L,
        enableToothInteraction = false,
        enableLighting = false,
    )
}

