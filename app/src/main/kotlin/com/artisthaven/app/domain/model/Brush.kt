package com.artisthaven.app.domain.model

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color

/**
 * Represents a drawing brush with configurable properties.
 * Each brush type can use a different AGSL shader for texture effects.
 */
data class Brush(
    val type: BrushType = BrushType.PEN,
    val size: Float = 10f,
    val opacity: Float = 1f,
    val color: Color = Color.Black,
    val hardness: Float = 1f,
    val spacing: Float = 0.1f,
    val textureStrength: Float = 0.5f,
)

/**
 * Brush types with associated rendering properties.
 * Each type uses different blend modes and shader configurations.
 */
enum class BrushType(
    val displayName: String,
    val blendMode: BlendMode,
    val defaultSize: Float,
    val defaultOpacity: Float,
    val defaultHardness: Float,
    val usesShader: Boolean,
) {
    PENCIL(
        displayName = "Pencil",
        blendMode = BlendMode.SrcOver,
        defaultSize = 6f,
        defaultOpacity = 0.8f,
        defaultHardness = 0.7f,
        usesShader = true,
    ),
    PEN(
        displayName = "Pen",
        blendMode = BlendMode.SrcOver,
        defaultSize = 4f,
        defaultOpacity = 1f,
        defaultHardness = 1f,
        usesShader = false,
    ),
    MARKER(
        displayName = "Marker",
        blendMode = BlendMode.SrcOver,
        defaultSize = 20f,
        defaultOpacity = 0.7f,
        defaultHardness = 1f,
        usesShader = false,
    ),
    WATERCOLOR(
        displayName = "Watercolor",
        blendMode = BlendMode.SrcOver,
        defaultSize = 30f,
        defaultOpacity = 0.4f,
        defaultHardness = 0f,
        usesShader = true,
    ),
    CHARCOAL(
        displayName = "Charcoal",
        blendMode = BlendMode.Multiply,
        defaultSize = 18f,
        defaultOpacity = 0.9f,
        defaultHardness = 0.3f,
        usesShader = true,
    ),
    ERASER(
        displayName = "Eraser",
        blendMode = BlendMode.Clear,
        defaultSize = 20f,
        defaultOpacity = 1f,
        defaultHardness = 0.9f,
        usesShader = false,
    ),
}
