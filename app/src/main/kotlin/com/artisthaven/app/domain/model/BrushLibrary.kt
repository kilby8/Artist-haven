package com.artisthaven.app.domain.model

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color

/**
 * Comprehensive brush definition with technical parameters for artist-quality drawing.
 * Designed for integration with the Google Ink API and AGSL shader rendering.
 */
data class BrushDefinition(
    val id: String,
    val name: String,
    val category: BrushCategory,
    val displayName: String,
    val description: String,
    val coreCharacteristics: String,
    val blendMode: BlendMode,
    val defaultSize: Float,
    val defaultOpacity: Float,
    val defaultHardness: Float,
    val defaultFlow: Float = 1f,
    val spacing: Float = 0.1f,
    val scatter: Float = 0f,
    val jitter: Float = 0f,
    val pressureSensitivity: PressureSensitivityMode,
    val usesShader: Boolean = false,
    val shaderId: String? = null,
)

/**
 * Controls how stylus pressure influences brush behavior.
 */
enum class PressureSensitivityMode {
    /**
     * Pressure increases brush size (width and height).
     */
    PRESSURE_TO_SIZE,

    /**
     * Pressure modulates opacity (alpha channel).
     */
    PRESSURE_TO_OPACITY,

    /**
     * Pressure modulates flow rate (coverage per unit distance).
     */
    PRESSURE_TO_FLOW,

    /**
     * Pressure affects both size and opacity.
     */
    PRESSURE_TO_SIZE_AND_OPACITY,

    /**
     * Pressure affects brush hardness/softness.
     */
    PRESSURE_TO_HARDNESS,

    /**
     * No pressure sensitivity.
     */
    NONE,
}

/**
 * Categories for organizing brushes by artistic style and function.
 */
enum class BrushCategory {
    SKETCHING,
    PAINTING,
    INKING,
    TEXTURAL,
    SPECIAL_EFFECTS,
}

/**
 * Master brush library with 50 artist-quality brush presets.
 * Organized into 5 functional categories.
 */
object BrushLibrary {

    // ─────────────────────────────────────────────────────────────────────────
    // SKETCHING BRUSHES (10 brushes)
    // Low opacity, high grain, responsive to pressure and tilt
    // ─────────────────────────────────────────────────────────────────────────

    private val SOFT_HB_PENCIL = BrushDefinition(
        id = "sketch_01",
        name = "Soft HB Pencil",
        category = BrushCategory.SKETCHING,
        displayName = "Soft HB Pencil",
        description = "Traditional graphite pencil with natural paper grain texture",
        coreCharacteristics = "Slightly tapered ends, natural grain pattern, responsive to pressure",
        blendMode = BlendMode.Multiply,
        defaultSize = 7f,
        defaultOpacity = 0.6f,
        defaultHardness = 0.5f,
        defaultFlow = 0.8f,
        spacing = 0.15f,
        scatter = 2f,
        jitter = 1f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE_AND_OPACITY,
        usesShader = true,
        shaderId = "pencil_grain",
    )

    private val ROUGH_CONTE_CRAYON = BrushDefinition(
        id = "sketch_02",
        name = "Rough Conté Crayon",
        category = BrushCategory.SKETCHING,
        displayName = "Rough Conté Crayon",
        description = "Coarse crayon stick with significant texture and drag",
        coreCharacteristics = "Heavy grain, texture streaking, color saturation with pressure",
        blendMode = BlendMode.Multiply,
        defaultSize = 12f,
        defaultOpacity = 0.7f,
        defaultHardness = 0.4f,
        defaultFlow = 0.7f,
        spacing = 0.2f,
        scatter = 5f,
        jitter = 2f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "conte_texture",
    )

    private val MECHANICAL_PENCIL = BrushDefinition(
        id = "sketch_03",
        name = "Mechanical Pencil",
        category = BrushCategory.SKETCHING,
        displayName = "Mechanical Pencil",
        description = "Precise, uniform line with minimal texture",
        coreCharacteristics = "Consistent width, sharp point, minimal grain, precise control",
        blendMode = BlendMode.Multiply,
        defaultSize = 4f,
        defaultOpacity = 0.75f,
        defaultHardness = 0.9f,
        defaultFlow = 1f,
        spacing = 0.05f,
        scatter = 0.5f,
        jitter = 0.3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = false,
    )

    private val CHARCOAL_STICK = BrushDefinition(
        id = "sketch_04",
        name = "Charcoal Stick",
        category = BrushCategory.SKETCHING,
        displayName = "Charcoal Stick",
        description = "Soft charcoal with heavy grain and smudge potential",
        coreCharacteristics = "Very grainy, heavy opacity buildup, soft edges, pressure-sensitive",
        blendMode = BlendMode.Multiply,
        defaultSize = 15f,
        defaultOpacity = 0.8f,
        defaultHardness = 0.2f,
        defaultFlow = 0.9f,
        spacing = 0.25f,
        scatter = 8f,
        jitter = 3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE_AND_OPACITY,
        usesShader = true,
        shaderId = "charcoal_smudge",
    )

    private val BLENDING_STUMP = BrushDefinition(
        id = "sketch_05",
        name = "Blending Stump",
        category = BrushCategory.SKETCHING,
        displayName = "Blending Stump",
        description = "Soft blending tool for smoothing and mixing tones",
        coreCharacteristics = "Soft edges, low opacity, minimal texture, smooth blending",
        blendMode = BlendMode.SrcOver,
        defaultSize = 20f,
        defaultOpacity = 0.3f,
        defaultHardness = 0.1f,
        defaultFlow = 0.6f,
        spacing = 0.3f,
        scatter = 0f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = false,
    )

    private val TINTED_CHARCOAL = BrushDefinition(
        id = "sketch_06",
        name = "Tinted Charcoal",
        category = BrushCategory.SKETCHING,
        displayName = "Tinted Charcoal",
        description = "Pre-tinted charcoal pencil in warm, cool, or neutral tones",
        coreCharacteristics = "Medium grain, warm undertones, semi-transparent buildup",
        blendMode = BlendMode.Multiply,
        defaultSize = 8f,
        defaultOpacity = 0.65f,
        defaultHardness = 0.4f,
        defaultFlow = 0.75f,
        spacing = 0.12f,
        scatter = 3f,
        jitter = 1.5f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE_AND_OPACITY,
        usesShader = true,
        shaderId = "tinted_charcoal",
    )

    private val GRAPHITE_POWDER = BrushDefinition(
        id = "sketch_07",
        name = "Graphite Powder",
        category = BrushCategory.SKETCHING,
        displayName = "Graphite Powder",
        description = "Loose graphite particles applied with soft brush",
        coreCharacteristics = "Heavy grain, low opacity per stroke, builds with layering",
        blendMode = BlendMode.Multiply,
        defaultSize = 25f,
        defaultOpacity = 0.4f,
        defaultHardness = 0f,
        defaultFlow = 0.5f,
        spacing = 0.35f,
        scatter = 12f,
        jitter = 4f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "powder_scatter",
    )

    private val CROSS_HATCHING_PEN = BrushDefinition(
        id = "sketch_08",
        name = "Cross-Hatching Pen",
        category = BrushCategory.SKETCHING,
        displayName = "Cross-Hatching Pen",
        description = "Fine-tipped pen optimized for rapid hatching and stippling",
        coreCharacteristics = "Very fine lines, high precision, minimal texture",
        blendMode = BlendMode.Multiply,
        defaultSize = 2f,
        defaultOpacity = 0.9f,
        defaultHardness = 1f,
        defaultFlow = 1f,
        spacing = 0.02f,
        scatter = 0f,
        jitter = 0.1f,
        pressureSensitivity = PressureSensitivityMode.NONE,
        usesShader = false,
    )

    private val KNEADED_ERASER = BrushDefinition(
        id = "sketch_09",
        name = "Kneaded Eraser",
        category = BrushCategory.SKETCHING,
        displayName = "Kneaded Eraser",
        description = "Soft eraser for selective removal with soft edges",
        coreCharacteristics = "Very soft edges, gentle removal, minimal impact on paper",
        blendMode = BlendMode.Clear,
        defaultSize = 18f,
        defaultOpacity = 0.7f,
        defaultHardness = 0.1f,
        defaultFlow = 0.8f,
        spacing = 0.2f,
        scatter = 0f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = false,
    )

    private val ROUGH_SKETCH = BrushDefinition(
        id = "sketch_10",
        name = "Rough Sketch",
        category = BrushCategory.SKETCHING,
        displayName = "Rough Sketch",
        description = "Fast, loose sketching brush with directional streaks",
        coreCharacteristics = "Directional texture, high scatter, loose and organic",
        blendMode = BlendMode.Multiply,
        defaultSize = 10f,
        defaultOpacity = 0.6f,
        defaultHardness = 0.3f,
        defaultFlow = 0.85f,
        spacing = 0.18f,
        scatter = 10f,
        jitter = 3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = true,
        shaderId = "sketch_streaks",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PAINTING BRUSHES (10 brushes)
    // Color mixing, wetness, flow simulation, responsive to pressure
    // ─────────────────────────────────────────────────────────────────────────

    private val THICK_IMPASTO = BrushDefinition(
        id = "paint_01",
        name = "Thick Impasto",
        category = BrushCategory.PAINTING,
        displayName = "Thick Impasto",
        description = "Heavy oil paint application with visible brush strokes",
        coreCharacteristics = "Thick edges, high opacity, visible brush marks, texture buildup",
        blendMode = BlendMode.SrcOver,
        defaultSize = 18f,
        defaultOpacity = 0.95f,
        defaultHardness = 0.6f,
        defaultFlow = 0.9f,
        spacing = 0.08f,
        scatter = 2f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = true,
        shaderId = "impasto_relief",
    )

    private val DILUTED_WATERCOLOR = BrushDefinition(
        id = "paint_02",
        name = "Diluted Watercolor",
        category = BrushCategory.PAINTING,
        displayName = "Diluted Watercolor",
        description = "Transparent watercolor with soft edges and color bleeding",
        coreCharacteristics = "Transparent, soft edges, color mixing, slight feathering",
        blendMode = BlendMode.Multiply,
        defaultSize = 28f,
        defaultOpacity = 0.35f,
        defaultHardness = 0.1f,
        defaultFlow = 0.7f,
        spacing = 0.4f,
        scatter = 3f,
        jitter = 2f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "watercolor_flow",
    )

    private val FLAT_BRUSH = BrushDefinition(
        id = "paint_03",
        name = "Flat Brush",
        category = BrushCategory.PAINTING,
        displayName = "Flat Brush",
        description = "Traditional flat brush for wide strokes and coverage",
        coreCharacteristics = "Rectangular shape, uniform edge, directional strokes",
        blendMode = BlendMode.SrcOver,
        defaultSize = 25f,
        defaultOpacity = 0.85f,
        defaultHardness = 0.7f,
        defaultFlow = 0.8f,
        spacing = 0.1f,
        scatter = 1f,
        jitter = 0.5f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = false,
    )

    private val ROUND_MOP = BrushDefinition(
        id = "paint_04",
        name = "Round Mop",
        category = BrushCategory.PAINTING,
        displayName = "Round Mop",
        description = "Large, soft, rounded brush for blending and color mixing",
        coreCharacteristics = "Very soft, large bristle area, excellent for blending",
        blendMode = BlendMode.SrcOver,
        defaultSize = 35f,
        defaultOpacity = 0.6f,
        defaultHardness = 0.2f,
        defaultFlow = 0.65f,
        spacing = 0.3f,
        scatter = 2f,
        jitter = 1f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = false,
    )

    private val WET_ON_WET = BrushDefinition(
        id = "paint_05",
        name = "Wet-on-Wet",
        category = BrushCategory.PAINTING,
        displayName = "Wet-on-Wet",
        description = "Simulates painting on pre-wetted paper with maximum color diffusion",
        coreCharacteristics = "High diffusion, very transparent, feathered edges, color bloom",
        blendMode = BlendMode.Multiply,
        defaultSize = 32f,
        defaultOpacity = 0.25f,
        defaultHardness = 0.05f,
        defaultFlow = 0.5f,
        spacing = 0.5f,
        scatter = 5f,
        jitter = 3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "wet_diffusion",
    )

    private val GLAZING_BRUSH = BrushDefinition(
        id = "paint_06",
        name = "Glazing Brush",
        category = BrushCategory.PAINTING,
        displayName = "Glazing Brush",
        description = "Thin, transparent layers for color glazing and atmosphere",
        coreCharacteristics = "Very transparent, smooth flow, no visible texture",
        blendMode = BlendMode.Multiply,
        defaultSize = 22f,
        defaultOpacity = 0.2f,
        defaultHardness = 0.5f,
        defaultFlow = 0.9f,
        spacing = 0.12f,
        scatter = 0f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.NONE,
        usesShader = false,
    )

    private val DRYBRUSH = BrushDefinition(
        id = "paint_07",
        name = "Drybrush",
        category = BrushCategory.PAINTING,
        displayName = "Drybrush",
        description = "Textured strokes with minimal paint, creates scratchy effects",
        coreCharacteristics = "High texture, sparse coverage, broken strokes, directional",
        blendMode = BlendMode.SrcOver,
        defaultSize = 16f,
        defaultOpacity = 0.5f,
        defaultHardness = 0.8f,
        defaultFlow = 0.4f,
        spacing = 0.15f,
        scatter = 6f,
        jitter = 2f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "drybrush_breaks",
    )

    private val OIL_BLENDING = BrushDefinition(
        id = "paint_08",
        name = "Oil Blending",
        category = BrushCategory.PAINTING,
        displayName = "Oil Blending",
        description = "Soft blending tool for oil painting with natural color mixing",
        coreCharacteristics = "Soft bristles, smooth blending, color mixing, slightly creamy",
        blendMode = BlendMode.SrcOver,
        defaultSize = 24f,
        defaultOpacity = 0.7f,
        defaultHardness = 0.3f,
        defaultFlow = 0.75f,
        spacing = 0.2f,
        scatter = 1f,
        jitter = 0.5f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = false,
    )

    private val STIPPLE_BRUSH = BrushDefinition(
        id = "paint_09",
        name = "Stipple Brush",
        category = BrushCategory.PAINTING,
        displayName = "Stipple Brush",
        description = "Creates pointillist dots rather than continuous strokes",
        coreCharacteristics = "Scattered dots, high spacing, organic color mixing",
        blendMode = BlendMode.SrcOver,
        defaultSize = 12f,
        defaultOpacity = 0.6f,
        defaultHardness = 0.8f,
        defaultFlow = 0.85f,
        spacing = 0.6f,
        scatter = 15f,
        jitter = 3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "stipple_dots",
    )

    private val SUMI_WATERCOLOR = BrushDefinition(
        id = "paint_10",
        name = "Sumi Watercolor",
        category = BrushCategory.PAINTING,
        displayName = "Sumi Watercolor",
        description = "East Asian-style water-based ink with gradual tonal transitions",
        coreCharacteristics = "Transparent edges, tonal gradation, soft diffusion, pressure-responsive",
        blendMode = BlendMode.Multiply,
        defaultSize = 20f,
        defaultOpacity = 0.45f,
        defaultHardness = 0.15f,
        defaultFlow = 0.65f,
        spacing = 0.25f,
        scatter = 2f,
        jitter = 1f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE_AND_OPACITY,
        usesShader = true,
        shaderId = "sumi_gradient",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // INKING BRUSHES (10 brushes)
    // High contrast, sharp lines, smoothing algorithms, ink flow
    // ─────────────────────────────────────────────────────────────────────────

    private val TECHNICAL_FINELINER = BrushDefinition(
        id = "ink_01",
        name = "Technical Fineliner",
        category = BrushCategory.INKING,
        displayName = "Technical Fineliner",
        description = "Ultra-precise technical pen with uniform line weight",
        coreCharacteristics = "Perfectly uniform width, sharp edges, high opacity, zero texture",
        blendMode = BlendMode.SrcOver,
        defaultSize = 2.5f,
        defaultOpacity = 1f,
        defaultHardness = 1f,
        defaultFlow = 1f,
        spacing = 0.05f,
        scatter = 0f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.NONE,
        usesShader = false,
    )

    private val SUMI_E_INK = BrushDefinition(
        id = "ink_02",
        name = "Sumi-e Ink",
        category = BrushCategory.INKING,
        displayName = "Sumi-e Ink",
        description = "Traditional Japanese ink with tapered, expressive strokes",
        coreCharacteristics = "Tapered ends, expressive line variation, smooth flow, pressure-responsive",
        blendMode = BlendMode.SrcOver,
        defaultSize = 8f,
        defaultOpacity = 0.95f,
        defaultHardness = 0.8f,
        defaultFlow = 0.95f,
        spacing = 0.08f,
        scatter = 0.5f,
        jitter = 0.2f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = true,
        shaderId = "sumi_taper",
    )

    private val COMIC_INK = BrushDefinition(
        id = "ink_03",
        name = "Comic Ink",
        category = BrushCategory.INKING,
        displayName = "Comic Ink",
        description = "Bold, consistent ink for comic and manga illustration",
        coreCharacteristics = "Bold lines, consistent weight, high contrast, slight tapers",
        blendMode = BlendMode.SrcOver,
        defaultSize = 5f,
        defaultOpacity = 1f,
        defaultHardness = 0.95f,
        defaultFlow = 1f,
        spacing = 0.06f,
        scatter = 0.3f,
        jitter = 0.1f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = false,
    )

    private val CALLIGRAPHY_BRUSH = BrushDefinition(
        id = "ink_04",
        name = "Calligraphy Brush",
        category = BrushCategory.INKING,
        displayName = "Calligraphy Brush",
        description = "Wide chisel-tip for expressive calligraphic lettering",
        coreCharacteristics = "Flat chisel tip, thick/thin variation with angle, smooth ink flow",
        blendMode = BlendMode.SrcOver,
        defaultSize = 12f,
        defaultOpacity = 0.98f,
        defaultHardness = 0.85f,
        defaultFlow = 0.9f,
        spacing = 0.1f,
        scatter = 0f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = true,
        shaderId = "calligraphy_angle",
    )

    private val VECTOR_INK = BrushDefinition(
        id = "ink_05",
        name = "Vector Ink",
        category = BrushCategory.INKING,
        displayName = "Vector Ink",
        description = "Post-smoothed lines for clean, digital vector-like output",
        coreCharacteristics = "Maximum smoothing, sharp turns, clean geometry, anti-aliased",
        blendMode = BlendMode.SrcOver,
        defaultSize = 3f,
        defaultOpacity = 1f,
        defaultHardness = 1f,
        defaultFlow = 1f,
        spacing = 0.04f,
        scatter = 0f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.NONE,
        usesShader = false,
    )

    private val BALLPOINT_PEN = BrushDefinition(
        id = "ink_06",
        name = "Ballpoint Pen",
        category = BrushCategory.INKING,
        displayName = "Ballpoint Pen",
        description = "Classic ballpoint pen with slight texture and uniform flow",
        coreCharacteristics = "Slight grain, uniform weight, everyday appearance, minimal variation",
        blendMode = BlendMode.SrcOver,
        defaultSize = 3.5f,
        defaultOpacity = 0.92f,
        defaultHardness = 0.9f,
        defaultFlow = 1f,
        spacing = 0.05f,
        scatter = 0.5f,
        jitter = 0.2f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = false,
    )

    private val LIQUID_INK = BrushDefinition(
        id = "ink_07",
        name = "Liquid Ink",
        category = BrushCategory.INKING,
        displayName = "Liquid Ink",
        description = "Smooth, flowing ink with consistent color saturation",
        coreCharacteristics = "Smooth flow, consistent opacity, sharp edges, liquid appearance",
        blendMode = BlendMode.SrcOver,
        defaultSize = 4f,
        defaultOpacity = 0.98f,
        defaultHardness = 0.95f,
        defaultFlow = 0.95f,
        spacing = 0.05f,
        scatter = 0f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = false,
    )

    private val BRUSH_PEN = BrushDefinition(
        id = "ink_08",
        name = "Brush Pen",
        category = BrushCategory.INKING,
        displayName = "Brush Pen",
        description = "Flexible brush pen with natural line variation",
        coreCharacteristics = "Flexible nib, line variation with angle, smooth ink flow",
        blendMode = BlendMode.SrcOver,
        defaultSize = 6f,
        defaultOpacity = 0.95f,
        defaultHardness = 0.75f,
        defaultFlow = 0.9f,
        spacing = 0.08f,
        scatter = 0.5f,
        jitter = 0.3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE_AND_OPACITY,
        usesShader = true,
        shaderId = "brush_pen_flex",
    )

    private val MARKER_INK = BrushDefinition(
        id = "ink_09",
        name = "Marker Ink",
        category = BrushCategory.INKING,
        displayName = "Marker Ink",
        description = "Alcohol-based marker with vibrant, opaque color",
        coreCharacteristics = "High opacity, vibrant color, slight feathering on edges",
        blendMode = BlendMode.SrcOver,
        defaultSize = 14f,
        defaultOpacity = 0.9f,
        defaultHardness = 0.8f,
        defaultFlow = 0.85f,
        spacing = 0.1f,
        scatter = 1f,
        jitter = 0.5f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = false,
    )

    private val FELT_LINER = BrushDefinition(
        id = "ink_10",
        name = "Felt Liner",
        category = BrushCategory.INKING,
        displayName = "Felt Liner",
        description = "Soft felt-tip pen with fuzzy edges and quick-dry appearance",
        coreCharacteristics = "Felt texture, slightly fuzzy edges, matte finish, fast drying look",
        blendMode = BlendMode.SrcOver,
        defaultSize = 5f,
        defaultOpacity = 0.88f,
        defaultHardness = 0.7f,
        defaultFlow = 0.9f,
        spacing = 0.07f,
        scatter = 1.5f,
        jitter = 0.5f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "felt_edge",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // TEXTURAL / GRUNGE BRUSHES (10 brushes)
    // Dual-brush stamps, noise patterns, displacement, organic textures
    // ─────────────────────────────────────────────────────────────────────────

    private val DISTRESSED_CONCRETE = BrushDefinition(
        id = "tex_01",
        name = "Distressed Concrete",
        category = BrushCategory.TEXTURAL,
        displayName = "Distressed Concrete",
        description = "Rough, porous concrete texture with random pitting",
        coreCharacteristics = "Heavy grain, random pits, rough surface, desaturated appearance",
        blendMode = BlendMode.SrcOver,
        defaultSize = 30f,
        defaultOpacity = 0.65f,
        defaultHardness = 0.4f,
        defaultFlow = 0.6f,
        spacing = 0.4f,
        scatter = 12f,
        jitter = 5f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "concrete_pits",
    )

    private val SPONGE_DAB = BrushDefinition(
        id = "tex_02",
        name = "Sponge Dab",
        category = BrushCategory.TEXTURAL,
        displayName = "Sponge Dab",
        description = "Natural sponge with irregular, organic texture",
        coreCharacteristics = "Organic holes, irregular edges, porous appearance, natural randomness",
        blendMode = BlendMode.SrcOver,
        defaultSize = 28f,
        defaultOpacity = 0.7f,
        defaultHardness = 0.3f,
        defaultFlow = 0.7f,
        spacing = 0.5f,
        scatter = 8f,
        jitter = 4f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "sponge_holes",
    )

    private val CRACKLE_TEXTURE = BrushDefinition(
        id = "tex_03",
        name = "Crackle Texture",
        category = BrushCategory.TEXTURAL,
        displayName = "Crackle Texture",
        description = "Aged, cracked surface like dried mud or ancient paint",
        coreCharacteristics = "Fine cracks, weathered appearance, fragmented coverage",
        blendMode = BlendMode.Multiply,
        defaultSize = 32f,
        defaultOpacity = 0.55f,
        defaultHardness = 0.5f,
        defaultFlow = 0.65f,
        spacing = 0.35f,
        scatter = 10f,
        jitter = 3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "crackle_fractal",
    )

    private val RUST_CORROSION = BrushDefinition(
        id = "tex_04",
        name = "Rust & Corrosion",
        category = BrushCategory.TEXTURAL,
        displayName = "Rust & Corrosion",
        description = "Corroded metal surface with orange/brown speckles",
        coreCharacteristics = "Speckled oxidation, irregular coverage, warm earth tones",
        blendMode = BlendMode.Multiply,
        defaultSize = 25f,
        defaultOpacity = 0.6f,
        defaultHardness = 0.6f,
        defaultFlow = 0.7f,
        spacing = 0.3f,
        scatter = 15f,
        jitter = 4f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "rust_speckle",
    )

    private val MOSS_GROWTH = BrushDefinition(
        id = "tex_05",
        name = "Moss Growth",
        category = BrushCategory.TEXTURAL,
        displayName = "Moss Growth",
        description = "Organic moss or lichen growth pattern",
        coreCharacteristics = "Organic clusters, irregular edges, natural color variation",
        blendMode = BlendMode.Multiply,
        defaultSize = 35f,
        defaultOpacity = 0.5f,
        defaultHardness = 0.2f,
        defaultFlow = 0.6f,
        spacing = 0.4f,
        scatter = 14f,
        jitter = 5f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "moss_organic",
    )

    private val SAND_BLAST = BrushDefinition(
        id = "tex_06",
        name = "Sand Blast",
        category = BrushCategory.TEXTURAL,
        displayName = "Sand Blast",
        description = "High-impact sand-blasted surface with aggressive texture",
        coreCharacteristics = "Aggressive grain, rough edges, scattered impact marks",
        blendMode = BlendMode.SrcOver,
        defaultSize = 28f,
        defaultOpacity = 0.6f,
        defaultHardness = 0.5f,
        defaultFlow = 0.75f,
        spacing = 0.25f,
        scatter = 16f,
        jitter = 6f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "sand_blast",
    )

    private val INK_SPLATTER = BrushDefinition(
        id = "tex_07",
        name = "Ink Splatter",
        category = BrushCategory.TEXTURAL,
        displayName = "Ink Splatter",
        description = "Organic ink splatters and drips for dynamic effects",
        coreCharacteristics = "Random splatters, organic edges, high contrast, chaotic pattern",
        blendMode = BlendMode.SrcOver,
        defaultSize = 24f,
        defaultOpacity = 0.8f,
        defaultHardness = 0.7f,
        defaultFlow = 0.8f,
        spacing = 0.3f,
        scatter = 20f,
        jitter = 7f,
        pressureSensitivity = PressureSensitivityMode.NONE,
        usesShader = true,
        shaderId = "splatter_organic",
    )

    private val WEATHERED_WOOD = BrushDefinition(
        id = "tex_08",
        name = "Weathered Wood",
        category = BrushCategory.TEXTURAL,
        displayName = "Weathered Wood",
        description = "Wood grain with weathering and age cracks",
        coreCharacteristics = "Directional grain, aging cracks, natural color variation",
        blendMode = BlendMode.Multiply,
        defaultSize = 30f,
        defaultOpacity = 0.65f,
        defaultHardness = 0.55f,
        defaultFlow = 0.7f,
        spacing = 0.32f,
        scatter = 8f,
        jitter = 3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "wood_grain",
    )

    private val FABRIC_WEAVE = BrushDefinition(
        id = "tex_09",
        name = "Fabric Weave",
        category = BrushCategory.TEXTURAL,
        displayName = "Fabric Weave",
        description = "Woven fabric texture with thread visibility",
        coreCharacteristics = "Regular weave pattern, thread texture, slight irregularities",
        blendMode = BlendMode.Multiply,
        defaultSize = 26f,
        defaultOpacity = 0.7f,
        defaultHardness = 0.6f,
        defaultFlow = 0.75f,
        spacing = 0.2f,
        scatter = 4f,
        jitter = 1f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "fabric_weave",
    )

    private val DIGITAL_NOISE = BrushDefinition(
        id = "tex_10",
        name = "Digital Noise",
        category = BrushCategory.TEXTURAL,
        displayName = "Digital Noise",
        description = "Perlin noise texture for organic digital effects",
        coreCharacteristics = "Smooth noise patterns, organic appearance, soft randomness",
        blendMode = BlendMode.SrcOver,
        defaultSize = 32f,
        defaultOpacity = 0.5f,
        defaultHardness = 0.4f,
        defaultFlow = 0.65f,
        spacing = 0.35f,
        scatter = 6f,
        jitter = 2f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "perlin_noise",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SPECIAL EFFECTS BRUSHES (10 brushes)
    // Particle systems, glow, geometry, digital first
    // ─────────────────────────────────────────────────────────────────────────

    private val BINARY_BOKEH = BrushDefinition(
        id = "fx_01",
        name = "Binary Bokeh",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Binary Bokeh",
        description = "Digital bokeh circles with binary (0/1) appearance",
        coreCharacteristics = "Crisp bokeh circles, digital glitch aesthetic, high contrast",
        blendMode = BlendMode.Screen,
        defaultSize = 20f,
        defaultOpacity = 0.6f,
        defaultHardness = 0.9f,
        defaultFlow = 0.7f,
        spacing = 0.8f,
        scatter = 25f,
        jitter = 5f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "binary_bokeh",
    )

    private val GEOMETRIC_SCATTER = BrushDefinition(
        id = "fx_02",
        name = "Geometric Scatter",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Geometric Scatter",
        description = "Geometric shapes (triangles, hexagons) scattered randomly",
        coreCharacteristics = "Repeating geometry, precise angles, randomized placement",
        blendMode = BlendMode.SrcOver,
        defaultSize = 25f,
        defaultOpacity = 0.7f,
        defaultHardness = 1f,
        defaultFlow = 0.8f,
        spacing = 0.6f,
        scatter = 20f,
        jitter = 3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "geometric_shapes",
    )

    private val GLITCH_RIBBON = BrushDefinition(
        id = "fx_03",
        name = "Glitch Ribbon",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Glitch Ribbon",
        description = "Pixelated, color-separated RGB glitch effect",
        coreCharacteristics = "RGB channel separation, pixel blocks, digital corruption look",
        blendMode = BlendMode.Screen,
        defaultSize = 18f,
        defaultOpacity = 0.8f,
        defaultHardness = 0.95f,
        defaultFlow = 0.85f,
        spacing = 0.15f,
        scatter = 5f,
        jitter = 2f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = true,
        shaderId = "glitch_rgb",
    )

    private val NEON_VAPOR = BrushDefinition(
        id = "fx_04",
        name = "Neon Vapor",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Neon Vapor",
        description = "Glowing neon glow with vaporwave aesthetics",
        coreCharacteristics = "Intense glow, neon colors, soft blur halo, cyberpunk look",
        blendMode = BlendMode.Screen,
        defaultSize = 22f,
        defaultOpacity = 0.9f,
        defaultHardness = 0.3f,
        defaultFlow = 0.8f,
        spacing = 0.2f,
        scatter = 3f,
        jitter = 1f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "neon_glow",
    )

    private val PARTICLE_STREAM = BrushDefinition(
        id = "fx_05",
        name = "Particle Stream",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Particle Stream",
        description = "Flowing particle emitter following the stroke path",
        coreCharacteristics = "Particle emission, directional flow, fading particles",
        blendMode = BlendMode.Screen,
        defaultSize = 16f,
        defaultOpacity = 0.7f,
        defaultHardness = 0.5f,
        defaultFlow = 0.75f,
        spacing = 0.3f,
        scatter = 18f,
        jitter = 4f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "particle_emitter",
    )

    private val HALFTONE_DOTS = BrushDefinition(
        id = "fx_06",
        name = "Halftone Dots",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Halftone Dots",
        description = "CMY halftone dot pattern with pressure-responsive density",
        coreCharacteristics = "Regular dot array, CMY pattern, density changes with pressure",
        blendMode = BlendMode.Multiply,
        defaultSize = 24f,
        defaultOpacity = 0.75f,
        defaultHardness = 0.8f,
        defaultFlow = 0.8f,
        spacing = 0.4f,
        scatter = 8f,
        jitter = 1f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "halftone_dots",
    )

    private val LIGHTNING_SPARK = BrushDefinition(
        id = "fx_07",
        name = "Lightning Spark",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Lightning Spark",
        description = "Jagged, electric lightning bolts with glow",
        coreCharacteristics = "Jagged geometry, electric glow, high contrast, chaotic edges",
        blendMode = BlendMode.Screen,
        defaultSize = 14f,
        defaultOpacity = 0.85f,
        defaultHardness = 0.9f,
        defaultFlow = 0.9f,
        spacing = 0.08f,
        scatter = 10f,
        jitter = 3f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = true,
        shaderId = "lightning_bolt",
    )

    private val CHROMATIC_ABERRATION = BrushDefinition(
        id = "fx_08",
        name = "Chromatic Aberration",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Chromatic Aberration",
        description = "Color separation effect simulating lens aberration",
        coreCharacteristics = "RGB separation, soft edges, layered color channels",
        blendMode = BlendMode.Screen,
        defaultSize = 20f,
        defaultOpacity = 0.8f,
        defaultHardness = 0.6f,
        defaultFlow = 0.85f,
        spacing = 0.15f,
        scatter = 2f,
        jitter = 1f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
        usesShader = true,
        shaderId = "chroma_aberration",
    )

    private val STARBURST_RAYS = BrushDefinition(
        id = "fx_09",
        name = "Starburst Rays",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Starburst Rays",
        description = "Radiating rays from center creating starburst patterns",
        coreCharacteristics = "Radiating geometry, sharp rays, bright center, lens flare look",
        blendMode = BlendMode.Screen,
        defaultSize = 28f,
        defaultOpacity = 0.9f,
        defaultHardness = 1f,
        defaultFlow = 0.8f,
        spacing = 0.25f,
        scatter = 0f,
        jitter = 0f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_OPACITY,
        usesShader = true,
        shaderId = "starburst_rays",
    )

    private val SMOKE_WISP = BrushDefinition(
        id = "fx_10",
        name = "Smoke Wisp",
        category = BrushCategory.SPECIAL_EFFECTS,
        displayName = "Smoke Wisp",
        description = "Soft, curling smoke trails with organic noise",
        coreCharacteristics = "Soft edges, organic swirls, transparent trails, diffuse edges",
        blendMode = BlendMode.Lighten,
        defaultSize = 26f,
        defaultOpacity = 0.4f,
        defaultHardness = 0.1f,
        defaultFlow = 0.6f,
        spacing = 0.3f,
        scatter = 4f,
        jitter = 2f,
        pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_FLOW,
        usesShader = true,
        shaderId = "smoke_wisp",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API for accessing brush library
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * All 50 brushes organized by category.
     */
    val allBrushes: List<BrushDefinition> = listOf(
        // Sketching
        SOFT_HB_PENCIL,
        ROUGH_CONTE_CRAYON,
        MECHANICAL_PENCIL,
        CHARCOAL_STICK,
        BLENDING_STUMP,
        TINTED_CHARCOAL,
        GRAPHITE_POWDER,
        CROSS_HATCHING_PEN,
        KNEADED_ERASER,
        ROUGH_SKETCH,
        // Painting
        THICK_IMPASTO,
        DILUTED_WATERCOLOR,
        FLAT_BRUSH,
        ROUND_MOP,
        WET_ON_WET,
        GLAZING_BRUSH,
        DRYBRUSH,
        OIL_BLENDING,
        STIPPLE_BRUSH,
        SUMI_WATERCOLOR,
        // Inking
        TECHNICAL_FINELINER,
        SUMI_E_INK,
        COMIC_INK,
        CALLIGRAPHY_BRUSH,
        VECTOR_INK,
        BALLPOINT_PEN,
        LIQUID_INK,
        BRUSH_PEN,
        MARKER_INK,
        FELT_LINER,
        // Textural
        DISTRESSED_CONCRETE,
        SPONGE_DAB,
        CRACKLE_TEXTURE,
        RUST_CORROSION,
        MOSS_GROWTH,
        SAND_BLAST,
        INK_SPLATTER,
        WEATHERED_WOOD,
        FABRIC_WEAVE,
        DIGITAL_NOISE,
        // Special Effects
        BINARY_BOKEH,
        GEOMETRIC_SCATTER,
        GLITCH_RIBBON,
        NEON_VAPOR,
        PARTICLE_STREAM,
        HALFTONE_DOTS,
        LIGHTNING_SPARK,
        CHROMATIC_ABERRATION,
        STARBURST_RAYS,
        SMOKE_WISP,
    )

    /**
     * Get brushes filtered by category.
     */
    fun getBrushesByCategory(category: BrushCategory): List<BrushDefinition> =
        allBrushes.filter { it.category == category }

    /**
     * Get a brush by ID.
     */
    fun getBrushById(id: String): BrushDefinition? =
        allBrushes.find { it.id == id }

    /**
     * Get all available shader IDs.
     */
    fun getAllShaderIds(): Set<String> =
        allBrushes.mapNotNull { it.shaderId }.toSet()
}
