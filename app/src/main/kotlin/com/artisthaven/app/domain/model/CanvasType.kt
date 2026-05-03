package com.artisthaven.app.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Professional canvas texture types for drawing simulation.
 * Each type provides unique surface characteristics for different artistic mediums.
 */
enum class CanvasType(
    val displayName: String,
    val description: String,
    val recommendedMediums: List<String>,
    val baseColor: Color,
    val roughness: Float, // 0f = smooth, 1f = very rough
    val scale: Float,     // Texture scale multiplier
) {
    COLD_PRESS_PAPER(
        displayName = "Cold-Press Paper",
        description = "High-grit watercolor paper with subtle texture",
        recommendedMediums = listOf("Watercolor", "Gouache", "Ink"),
        baseColor = Color(0xFFFAF6F0),
        roughness = 0.55f,
        scale = 1.2f,
    ),
    FINE_GRAIN_LINEN(
        displayName = "Fine-Grain Linen",
        description = "Tight woven pattern for precise work",
        recommendedMediums = listOf("Oil", "Acrylic", "Pastel"),
        baseColor = Color(0xFFFEF9F0),
        roughness = 0.68f,
        scale = 1.8f,
    ),
    DARK_SLATE(
        displayName = "Dark Mode Slate",
        description = "Dark, slightly reflective surface with subtle grain",
        recommendedMediums = listOf("Charcoal", "Chalk", "Ink"),
        baseColor = Color(0xFF2A2A2A),
        roughness = 0.45f,
        scale = 0.95f,
    ),
    TRANSPARENT_GRID(
        displayName = "Transparent Grid",
        description = "Standard checkerboard for professional export workflows",
        recommendedMediums = listOf("Digital", "All"),
        baseColor = Color.Transparent,
        roughness = 0f,
        scale = 1f,
    ),
    VELLUM(
        displayName = "Vellum",
        description = "Smooth surface ideal for ink and sharp lines",
        recommendedMediums = listOf("Ink", "Pen", "Marker"),
        baseColor = Color(0xFFFFFCF7),
        roughness = 0.15f,
        scale = 0.6f,
    ),
    PRIMED_CANVAS(
        displayName = "Primed Canvas",
        description = "Cross-hatch texture for oil painting simulation",
        recommendedMediums = listOf("Oil", "Heavy Paint"),
        baseColor = Color(0xFFFBFAF8),
        roughness = 0.72f,
        scale = 2.2f,
    ),
}

/**
 * Canvas layer configuration with texture and rendering properties.
 */
data class CanvasLayerConfig(
    val canvasType: CanvasType = CanvasType.COLD_PRESS_PAPER,
    val tintColor: Color = Color.White,
    val opacity: Float = 1f,
    val randomSeedOffset: Long = 0L,
    val enableToothInteraction: Boolean = true,
    val enableLighting: Boolean = false,
    val lightingIntensity: Float = 0.15f,
)

/**
 * Blend behavior enum extended for professional canvas interactions.
 */
enum class CanvasBlendMode {
    MULTIPLY,        // Paint fills paper valleys
    SCREEN,          // Light-based blending
    OVERLAY,         // Combine multiply and screen
    SOFT_LIGHT,      // Subtle blending
    HARD_LIGHT,      // Strong blending
    COLOR_DODGE,     // Dodge effect
    COLOR_BURN,      // Burn effect
    DARKEN,          // Darken only
    LIGHTEN,         // Lighten only
    DST_IN,          // Canvas as mask (tooth interaction)
}

