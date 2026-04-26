package com.artisthaven.app.domain.model

import androidx.compose.ui.graphics.BlendMode

/**
 * Represents a single drawing layer in the canvas.
 * Each layer maintains its own bitmap for raster drawing.
 */
data class Layer(
    val id: String,
    val name: String,
    val isVisible: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val isLocked: Boolean = false,
    val bitmapPath: String? = null,
    val order: Int = 0,
)

/**
 * Layer blending modes using open-source Porter-Duff / photographic compositing algorithms.
 */
enum class LayerBlendMode(val displayName: String, val composeBlendMode: BlendMode) {
    NORMAL("Normal", BlendMode.SrcOver),
    MULTIPLY("Multiply", BlendMode.Multiply),
    SCREEN("Screen", BlendMode.Screen),
    OVERLAY("Overlay", BlendMode.Overlay),
    DARKEN("Darken", BlendMode.Darken),
    LIGHTEN("Lighten", BlendMode.Lighten),
    COLOR_DODGE("Color Dodge", BlendMode.ColorDodge),
    COLOR_BURN("Color Burn", BlendMode.ColorBurn),
    HARD_LIGHT("Hard Light", BlendMode.Hardlight),
    SOFT_LIGHT("Soft Light", BlendMode.Softlight),
    DIFFERENCE("Difference", BlendMode.Difference),
    EXCLUSION("Exclusion", BlendMode.Exclusion),
}
