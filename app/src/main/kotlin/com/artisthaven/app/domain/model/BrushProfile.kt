package com.artisthaven.app.domain.model

enum class BrushStyle(val displayName: String) {
    STANDARD("Standard"),
    TEXTURED_CHARCOAL("Textured"),
    CALLIGRAPHY("Calligraphy"),
    NEON_GLOW("Neon"),
    PATTERN_STAMP("Pattern"),
}

enum class TextureTiling {
    REPEAT,
    MIRROR,
    CLAMP,
}

enum class TipShape {
    CIRCLE,
    SQUARE,
    DIAMOND,
    SLASH,
}

enum class BlendBehavior {
    NORMAL,
    MULTIPLY,
    DARKEN,
    SRC_ATOP,
    CLEAR,
}

data class GrainSettings(
    val enabled: Boolean = false,
    val scale: Float = 1f,
    val strength: Float = 0.35f,
    val tiling: TextureTiling = TextureTiling.REPEAT,
)

data class DynamicsSettings(
    val pressureToWidth: Float = 0.8f,
    val velocityToWidth: Float = 0.4f,
    val pressureToAlpha: Float = 0.55f,
    val velocityToAlpha: Float = 0.3f,
    val powerCurveExponent: Float = 1.7f,
    val velocityNormalization: Float = 2.2f,
    val minWidthMultiplier: Float = 0.35f,
    val maxWidthMultiplier: Float = 1.85f,
    val minAlphaMultiplier: Float = 0.2f,
    val maxAlphaMultiplier: Float = 1f,
)

data class TipSettings(
    val useStamp: Boolean = false,
    val useBitmapStamp: Boolean = false,
    val shape: TipShape = TipShape.CIRCLE,
    val spacing: Float = 0.16f,
    val jitter: Float = 0f,
    val stampScale: Float = 1f,
    val overlapFactor: Float = 0.8f,
    val alphaSmoothing: Float = 0.15f,
    val enableMicroDab: Boolean = true,
    val useMicroDabs: Boolean = false,
    val minGapClamping: Float = 1.0f,
    val fluidJitterPercent: Float = 0.02f,
    val fluidAccumulationAlpha: Float = 0.22f,
    val fluidVelocitySpacingTightening: Float = 0.65f,
)

data class EdgeTreatment(
    val softness: Float = 0f,
    val cornerSmoothingPx: Float = 0f,
)

data class BrushProfile(
    val style: BrushStyle = BrushStyle.STANDARD,
    val grain: GrainSettings = GrainSettings(),
    val dynamics: DynamicsSettings = DynamicsSettings(),
    val tip: TipSettings = TipSettings(),
    val edge: EdgeTreatment = EdgeTreatment(),
    val blend: BlendBehavior = BlendBehavior.NORMAL,
) {
    companion object {
        fun preset(style: BrushStyle): BrushProfile = when (style) {
            BrushStyle.STANDARD -> BrushProfile(
                style = style,
                edge = EdgeTreatment(softness = 0.05f, cornerSmoothingPx = 8f),
            )

            BrushStyle.TEXTURED_CHARCOAL -> BrushProfile(
                style = style,
                grain = GrainSettings(enabled = true, scale = 0.65f, strength = 0.55f),
                dynamics = DynamicsSettings(
                    pressureToWidth = 0.85f,
                    velocityToWidth = 0.55f,
                    pressureToAlpha = 0.65f,
                    velocityToAlpha = 0.35f,
                    powerCurveExponent = 1.8f,
                    minWidthMultiplier = 0.45f,
                    maxWidthMultiplier = 2.1f,
                    minAlphaMultiplier = 0.28f,
                ),
                edge = EdgeTreatment(softness = 0.2f, cornerSmoothingPx = 10f),
                blend = BlendBehavior.MULTIPLY,
            )

            BrushStyle.CALLIGRAPHY -> BrushProfile(
                style = style,
                dynamics = DynamicsSettings(
                    pressureToWidth = 0.95f,
                    velocityToWidth = 0.8f,
                    pressureToAlpha = 0.75f,
                    velocityToAlpha = 0.25f,
                    powerCurveExponent = 2.1f,
                    minWidthMultiplier = 0.2f,
                    maxWidthMultiplier = 2.35f,
                    minAlphaMultiplier = 0.25f,
                ),
                edge = EdgeTreatment(softness = 0.03f, cornerSmoothingPx = 18f),
                blend = BlendBehavior.SRC_ATOP,
            )

            BrushStyle.NEON_GLOW -> BrushProfile(
                style = style,
                dynamics = DynamicsSettings(
                    pressureToWidth = 0.7f,
                    velocityToWidth = 0.25f,
                    pressureToAlpha = 0.75f,
                    velocityToAlpha = 0.1f,
                    powerCurveExponent = 1.5f,
                    minWidthMultiplier = 0.5f,
                    maxWidthMultiplier = 1.6f,
                    minAlphaMultiplier = 0.35f,
                ),
                edge = EdgeTreatment(softness = 0.4f, cornerSmoothingPx = 14f),
                blend = BlendBehavior.NORMAL,
            )

            BrushStyle.PATTERN_STAMP -> BrushProfile(
                style = style,
                dynamics = DynamicsSettings(
                    pressureToWidth = 0.65f,
                    velocityToWidth = 0.2f,
                    pressureToAlpha = 0.5f,
                    velocityToAlpha = 0.1f,
                    powerCurveExponent = 1.4f,
                    minWidthMultiplier = 0.65f,
                    maxWidthMultiplier = 1.55f,
                    minAlphaMultiplier = 0.35f,
                ),
                tip = TipSettings(
                    useStamp = true,
                    useBitmapStamp = true,
                    shape = TipShape.DIAMOND,
                    spacing = 0.34f,
                    jitter = 0.16f,
                    stampScale = 0.95f,
                ),
                edge = EdgeTreatment(softness = 0.08f, cornerSmoothingPx = 8f),
                blend = BlendBehavior.DARKEN,
            )
        }
    }
}

