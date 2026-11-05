package com.artisthaven.engine.brush

import com.artisthaven.engine.document.BlendMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** How a brush lays down paint. */
enum class RenderMode {
    /** Dabs build up coverage within a stroke, capped by opacity: even, clean strokes. */
    GLAZE,

    /** Dabs paint straight onto the layer and mix with the paint underneath. */
    WET,

    /**
     * Watercolour: the stroke is a pool of water that dries with darker edges, a wobbly outline
     * and pigment settling into the paper, then glazes over what is underneath.
     */
    WASH,

    /**
     * Mixer Brush: a reservoir of loaded paint that runs dry, mixed with colour the brush picks up
     * from the canvas, like real paint on a loaded brush.
     */
    MIXER,
}

/** Which way the tip faces. */
enum class TipAngle { FIXED, FOLLOW_STROKE, STYLUS }

/**
 * A brush, modelled on Procreate's Brush Studio groups (stroke path, stabilisation, taper, shape,
 * grain, rendering, wet mix, dynamics, pressure and tilt), trimmed to settings that visibly matter.
 */
@Serializable
data class BrushPreset(
    val id: String,
    val name: String,
    val category: BrushCategory,

    // Defaults the sidebar sliders start at.
    val defaultSize: Float = 12f,
    val minSize: Float = 1f,
    val maxSize: Float = 400f,
    val defaultOpacity: Float = 1f,

    // Shape.
    val tip: TipShape = TipShape.ROUND,
    /**
     * A picture tip (for example from an imported Photoshop brush), as [TipImage.encode] text.
     * When set it replaces [tip]; hardness doesn't apply to it.
     */
    val tipImage: String? = null,
    val hardness: Float = 0.9f,
    val roundness: Float = 1f,
    val tipAngle: TipAngle = TipAngle.FOLLOW_STROKE,
    val angleDegrees: Float = 0f,
    val rotationJitter: Float = 0f,

    // Stroke path.
    val spacing: Float = 0.08f,
    val lateralJitter: Float = 0f,
    val sizeJitter: Float = 0f,
    val opacityJitter: Float = 0f,
    /** How far each dab can land from the stroke in any direction, in brush diameters (0..4). */
    val scatter: Float = 0f,
    /** Dabs placed at every spacing step (1..16). Use with [scatter] for sprays and stamps. */
    val count: Int = 1,
    /** 0..1. Each dab's tip is squashed by a random amount. */
    val roundnessJitter: Float = 0f,
    /** 0..1. Each dab may be mirrored, so stamps don't all face the same way. */
    val flipJitter: Float = 0f,

    // Colour dynamics, all 0..1.
    /** How far the hue can wander from the chosen colour (1 = anywhere on the wheel). */
    val hueJitter: Float = 0f,
    val saturationJitter: Float = 0f,
    val brightnessJitter: Float = 0f,
    /** Pick the colour variation once per stroke instead of once per dab. */
    val colourJitterPerStroke: Boolean = false,

    // Stabilisation and taper.
    val streamline: Float = 0.3f,
    val taperStart: Float = 0f,
    val taperEnd: Float = 0f,
    val taperMinSize: Float = 0.1f,

    // Grain.
    val grain: GrainTexture = GrainTexture.NONE,
    val grainScale: Float = 1f,
    val grainDepth: Float = 0f,
    val grainMoving: Boolean = false,
    /** Paints the hollows of the grain or pattern instead of its peaks. */
    val grainInvert: Boolean = false,
    /**
     * 0..1. How much the grain acts as paper tooth: dry media only catch the peaks of the paper,
     * and pressing harder reaches down into the valleys.
     */
    val tooth: Float = 0f,
    /** 0..1. Loose particles scattered just outside the stroke, like charcoal or pastel dust. */
    val dust: Float = 0f,

    // Rendering.
    val renderMode: RenderMode = RenderMode.GLAZE,
    val flow: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    /**
     * Each dab paints over the ones before it, like stamping or an airbrush held still, instead of
     * the stroke evening out to one level of opacity (GLAZE mode only).
     */
    val buildUp: Boolean = false,

    // Wet mix (WET mode only).
    val dilution: Float = 0f,
    val charge: Float = 1f,
    val pull: Float = 0f,

    // Mixer Brush (MIXER mode only), all 0..1.
    /** How much paint the brush picks up from the canvas. */
    val mixerWet: Float = 0.5f,
    /** How much paint the brush holds; low Load runs dry quickly. */
    val mixerLoad: Float = 0.5f,
    /** How much of the picked-up colour is in the paint, against the loaded colour. */
    val mixerMix: Float = 0.5f,
    /** Pick up colour from everything you see, not just the current layer. */
    val mixerSampleAll: Boolean = false,
    /** Empty the brush after every stroke; off keeps it dirty from stroke to stroke. */
    val mixerClean: Boolean = true,

    // Bristles (bristle tip only).
    /** 0..1. How quickly bristles run out of paint, leaving dry-brush streaks. */
    val dryOut: Float = 0f,
    /** 0..1. Light and shade along the bristle ridges, like thick paint. */
    val impasto: Float = 0f,
    /** 0..1. How much the paint colour varies from bristle to bristle. */
    val streakColour: Float = 0f,
    /**
     * 0..1. Thick paint that stands up from the canvas (WET mode): bristle grooves, ridges pushed
     * up along the edges of the stroke and a blob where it starts, lit from the top left.
     */
    val relief: Float = 0f,

    // Watercolour (WASH mode only).
    /** 0..1. Pigment pooling at the edge of the wash as it dries. */
    val edgeDarken: Float = 0f,
    /** 0..1. Pigment settling into the hollows of the paper. */
    val granulation: Float = 0f,
    /** 0..1. Uneven pigment within the wash, from the water carrying it around. */
    val pigmentFlow: Float = 0f,
    /** 0..1. How ragged the edge of the wash is. */
    val edgeWobble: Float = 0f,

    // Dynamics.
    val pressureSize: Float = 0.6f,
    val pressureOpacity: Float = 0f,
    val pressureFlow: Float = 0f,
    val pressureGamma: Float = 1f,
    val tiltSize: Float = 0f,
    val tiltOpacity: Float = 0f,
    val speedSize: Float = 0f,
    val speedOpacity: Float = 0f,

    /** Moves paint around without adding colour, with the brush too: it works like the Smudge tool. */
    val blendOnly: Boolean = false,
    /** Smudge by blurring in place instead of dragging colour along. */
    val soften: Boolean = false,

    /** Strength when this brush is used by the Smudge tool, 0..1. */
    val smudgeStrength: Float = 0.75f,

    /** The brush file this brush was imported from, shown in the library; null for our own. */
    val importedFrom: String? = null,

    /** Set by [withPaintLoad]: how quickly a lightly loaded wet brush runs out, 0 = never. */
    val paintFade: Float = 0f,
) {
    /** True for brushes that carry a load of paint that can run out (wet paint and watercolour). */
    val usesPaintLoad: Boolean
        get() = !blendOnly && (renderMode == RenderMode.WET || renderMode == RenderMode.WASH || renderMode == RenderMode.MIXER)

    /** How much paint the brush starts with, 0..1, as shown on the Paint slider. */
    val defaultPaintLoad: Float
        get() = (if (renderMode == RenderMode.MIXER) mixerLoad else charge).coerceIn(0f, 1f)

    /**
     * This brush with [load] paint on it, 0 (nearly dry) to 1 (fully loaded). More paint lasts
     * longer along the stroke and, on thick paint, stands up higher.
     */
    fun withPaintLoad(load: Float): BrushPreset {
        if (!usesPaintLoad) return this
        val l = load.coerceIn(0f, 1f)
        val ratio = ((0.25f + l) / (0.25f + defaultPaintLoad)).coerceIn(0.2f, 2.5f)
        return copy(
            charge = if (l >= 0.99f) 1f else 0.04f + 0.95f * l,
            mixerLoad = if (renderMode == RenderMode.MIXER) l else mixerLoad,
            relief = (relief * ratio).coerceAtMost(1f),
            paintFade = ((defaultPaintLoad - l) / defaultPaintLoad.coerceAtLeast(0.05f)).coerceIn(0f, 1f),
            dryOut = if (l < defaultPaintLoad) dryOut + (1f - dryOut) * (defaultPaintLoad - l) else dryOut * ratio.coerceAtLeast(1f).let { 1f / it },
            impasto = (impasto * ratio).coerceAtMost(1f),
        )
    }
}

enum class BrushCategory(val displayName: String) {
    MINE("My brushes"),
    SKETCHING("Sketching"),
    INKING("Inking"),
    CALLIGRAPHY("Calligraphy"),
    PAINTING("Painting"),
    BLENDING("Blending"),
    RAKES("Rakes & Rollers"),
    WATERCOLOR("Watercolour"),
    ARTISTIC("Artistic"),
    CHARCOAL("Charcoal & Pastel"),
    AIRBRUSHING("Airbrushing"),
    SPRAY("Spray Paint"),
    TEXTURES("Textures"),
    PATTERNS("Patterns"),
    STAMPS("Stamps"),
    NATURE("Nature"),
    HAIR("Hair & Fur"),
    ELEMENTS("Elements"),
    LIGHT("Light & Glow"),
    RETRO("Retro & Comic"),
    ABSTRACT("Abstract"),
}

/** The built-in brush set. Each brush is tuned to look clearly different from the others. */
object BrushLibrary {
    /** Brushes the user made in the Brush Studio; the app loads and saves them. */
    @kotlin.concurrent.Volatile var custom: List<BrushPreset> = emptyList()

    val all: List<BrushPreset> get() = custom + builtIn

    val builtIn: List<BrushPreset> get() = builtInList

    private val builtInList: List<BrushPreset> by lazy { (coreBrushes + MoreBrushes.all).sortedBy { it.category.ordinal } }

    private val coreBrushes: List<BrushPreset> = listOf(
        // Sketching
        BrushPreset(
            id = "hb_pencil", name = "HB Pencil", category = BrushCategory.SKETCHING,
            defaultSize = 5f, maxSize = 40f, defaultOpacity = 0.8f,
            tip = TipShape.PENCIL, hardness = 0.85f, spacing = 0.1f,
            streamline = 0.15f, taperStart = 1f, taperEnd = 1f, taperMinSize = 0.6f,
            grain = GrainTexture.DRAWING, grainScale = 1f, grainDepth = 0.95f, tooth = 1f,
            pressureSize = 0.25f, pressureOpacity = 0.35f, pressureGamma = 1.2f,
            tiltSize = 1.8f, tiltOpacity = 0.6f,
        ),
        BrushPreset(
            id = "6b_pencil", name = "6B Pencil", category = BrushCategory.SKETCHING,
            defaultSize = 12f, maxSize = 80f, defaultOpacity = 0.95f,
            tip = TipShape.PENCIL, hardness = 0.8f, spacing = 0.08f,
            streamline = 0.1f, taperStart = 0.8f, taperEnd = 0.8f, taperMinSize = 0.6f,
            grain = GrainTexture.DRAWING, grainScale = 1.2f, grainDepth = 0.6f, tooth = 1f,
            pressureSize = 0.25f, pressureOpacity = 0.3f, pressureGamma = 1.1f,
            tiltSize = 2.2f, tiltOpacity = 0.6f,
        ),
        BrushPreset(
            id = "mechanical_pencil", name = "Mechanical Pencil", category = BrushCategory.SKETCHING,
            defaultSize = 3f, maxSize = 12f, defaultOpacity = 0.85f,
            tip = TipShape.PENCIL, hardness = 0.95f, spacing = 0.1f,
            streamline = 0.2f, taperStart = 0.5f, taperEnd = 0.5f, taperMinSize = 0.7f,
            grain = GrainTexture.DRAWING, grainScale = 0.8f, grainDepth = 0.7f, tooth = 0.8f,
            pressureSize = 0.1f, pressureOpacity = 0.55f, pressureGamma = 1.1f,
        ),
        // Inking
        BrushPreset(
            id = "studio_pen", name = "Studio Pen", category = BrushCategory.INKING,
            defaultSize = 8f, maxSize = 120f,
            tip = TipShape.ROUND, hardness = 0.92f, spacing = 0.04f,
            streamline = 0.55f, taperStart = 4f, taperEnd = 6f, taperMinSize = 0.05f,
            pressureSize = 0.9f, pressureGamma = 1.4f, speedSize = 0.15f,
        ),
        BrushPreset(
            id = "technical_pen", name = "Technical Pen", category = BrushCategory.INKING,
            defaultSize = 4f, maxSize = 60f,
            tip = TipShape.ROUND, hardness = 0.95f, spacing = 0.04f,
            streamline = 0.7f, pressureSize = 0f,
        ),
        BrushPreset(
            id = "dry_ink", name = "Dry Ink", category = BrushCategory.INKING,
            defaultSize = 14f, maxSize = 160f,
            tip = TipShape.ROUND, hardness = 0.85f, spacing = 0.05f,
            streamline = 0.35f, taperStart = 3f, taperEnd = 4f, taperMinSize = 0.15f,
            grain = GrainTexture.CHALK, grainScale = 1.2f, grainDepth = 0.5f,
            pressureSize = 0.8f, pressureGamma = 1.2f, speedOpacity = 0.25f,
        ),
        BrushPreset(
            id = "calligraphy", name = "Calligraphy", category = BrushCategory.INKING,
            defaultSize = 22f, maxSize = 150f,
            tip = TipShape.ROUND, hardness = 0.9f, roundness = 0.22f,
            tipAngle = TipAngle.FIXED, angleDegrees = 45f, spacing = 0.03f,
            streamline = 0.4f, pressureSize = 0.5f,
        ),
        BrushPreset(
            id = "brush_pen", name = "Brush Pen", category = BrushCategory.INKING,
            defaultSize = 16f, maxSize = 120f,
            tip = TipShape.ROUND, hardness = 0.95f, roundness = 0.85f, spacing = 0.03f,
            streamline = 0.5f, taperStart = 3f, taperEnd = 5f, taperMinSize = 0.02f,
            pressureSize = 1f, pressureGamma = 1.6f, speedSize = 0.2f,
        ),
        BrushPreset(
            id = "fineliner", name = "Fineliner", category = BrushCategory.INKING,
            defaultSize = 3f, maxSize = 30f,
            tip = TipShape.ROUND, hardness = 0.97f, spacing = 0.04f,
            streamline = 0.5f, pressureSize = 0.1f,
        ),
        // Painting
        BrushPreset(
            id = "round_brush", name = "Round Brush", category = BrushCategory.PAINTING,
            defaultSize = 30f, maxSize = 300f,
            tip = TipShape.ROUND, hardness = 0.6f, spacing = 0.06f,
            streamline = 0.2f, taperStart = 1f, taperEnd = 1.5f, taperMinSize = 0.3f,
            renderMode = RenderMode.WET, flow = 0.5f, dilution = 0.1f, charge = 0.85f, pull = 0.35f,
            pressureSize = 0.55f, pressureFlow = 0.6f, smudgeStrength = 0.7f,
        ),
        BrushPreset(
            id = "gouache", name = "Gouache", category = BrushCategory.PAINTING,
            defaultSize = 40f, maxSize = 300f,
            tip = TipShape.BRISTLE, spacing = 0.04f,
            streamline = 0.15f, renderMode = RenderMode.WET, flow = 0.85f,
            dilution = 0f, charge = 0.6f, pull = 0.2f,
            grain = GrainTexture.CANVAS, grainScale = 1f, grainDepth = 0.45f, tooth = 0.35f,
            dryOut = 0.12f, impasto = 0f, streakColour = 0.08f,
            pressureSize = 0.55f, pressureFlow = 0.5f, pressureOpacity = 0.3f, smudgeStrength = 0.8f,
        ),
        BrushPreset(
            id = "oil_paint", name = "Oil Paint", category = BrushCategory.PAINTING,
            defaultSize = 50f, maxSize = 350f,
            tip = TipShape.BRISTLE, spacing = 0.03f, tipAngle = TipAngle.FOLLOW_STROKE,
            streamline = 0.1f, renderMode = RenderMode.WET, flow = 0.9f,
            dilution = 0f, charge = 0.75f, pull = 0.45f,
            grain = GrainTexture.CANVAS, grainScale = 1f, grainDepth = 1f, tooth = 0.8f,
            dryOut = 0.7f, impasto = 0.6f, streakColour = 0.6f, relief = 0.35f,
            pressureSize = 0.75f, pressureGamma = 1.2f, pressureFlow = 0.6f, pressureOpacity = 0.3f, smudgeStrength = 0.85f,
        ),
        BrushPreset(
            id = "heavy_paint", name = "Heavy Paint", category = BrushCategory.PAINTING,
            defaultSize = 55f, maxSize = 350f,
            tip = TipShape.BRISTLE, spacing = 0.03f, tipAngle = TipAngle.FOLLOW_STROKE,
            streamline = 0.15f, renderMode = RenderMode.WET, flow = 1f,
            dilution = 0f, charge = 0.85f, pull = 0.35f,
            grain = GrainTexture.CANVAS, grainScale = 1f, grainDepth = 0.35f, tooth = 0.2f,
            dryOut = 0.3f, streakColour = 0.2f, relief = 1f,
            pressureSize = 0.85f, pressureGamma = 1.3f, pressureFlow = 0.4f, pressureOpacity = 0.2f, smudgeStrength = 0.85f,
        ),
        BrushPreset(
            id = "palette_knife", name = "Palette Knife", category = BrushCategory.PAINTING,
            defaultSize = 60f, maxSize = 350f,
            tip = TipShape.ROUND, hardness = 0.97f, roundness = 0.22f,
            tipAngle = TipAngle.FOLLOW_STROKE, angleDegrees = 90f, spacing = 0.03f,
            streamline = 0.3f, renderMode = RenderMode.WET, flow = 1f,
            dilution = 0f, charge = 0.7f, pull = 0.6f, relief = 0.8f,
            pressureSize = 0.8f, pressureGamma = 1.3f, pressureFlow = 0.5f, smudgeStrength = 0.9f,
        ),
        BrushPreset(
            id = "dry_brush", name = "Dry Brush", category = BrushCategory.PAINTING,
            defaultSize = 45f, maxSize = 300f,
            tip = TipShape.BRISTLE, spacing = 0.03f, tipAngle = TipAngle.FOLLOW_STROKE,
            streamline = 0.1f, renderMode = RenderMode.WET, flow = 0.75f,
            dilution = 0f, charge = 0.45f, pull = 0.2f,
            grain = GrainTexture.CANVAS, grainScale = 1.2f, grainDepth = 1f, tooth = 1f,
            dryOut = 1f, streakColour = 0.45f, impasto = 0.2f,
            pressureSize = 0.35f, pressureFlow = 0.7f, pressureOpacity = 0.3f,
        ),
        BrushPreset(
            id = "flat_acrylic", name = "Flat Acrylic", category = BrushCategory.PAINTING,
            defaultSize = 40f, maxSize = 300f,
            tip = TipShape.BRISTLE, spacing = 0.03f, tipAngle = TipAngle.FOLLOW_STROKE,
            streamline = 0.2f, renderMode = RenderMode.WET, flow = 0.95f,
            dilution = 0f, charge = 0.9f, pull = 0.15f,
            grain = GrainTexture.CANVAS, grainScale = 1f, grainDepth = 0.4f, tooth = 0.3f,
            dryOut = 0.2f, streakColour = 0.15f, relief = 0.25f,
            pressureSize = 0.8f, pressureGamma = 1.3f, pressureFlow = 0.5f, pressureOpacity = 0.25f,
        ),
        BrushPreset(
            id = "filbert", name = "Filbert", category = BrushCategory.PAINTING,
            defaultSize = 36f, maxSize = 300f,
            tip = TipShape.BRISTLE, spacing = 0.03f, tipAngle = TipAngle.FOLLOW_STROKE,
            streamline = 0.2f, taperStart = 0.6f, taperEnd = 0.9f, taperMinSize = 0.35f,
            renderMode = RenderMode.WET, flow = 0.85f, dilution = 0.05f, charge = 0.75f, pull = 0.4f,
            grain = GrainTexture.CANVAS, grainScale = 1f, grainDepth = 0.5f, tooth = 0.4f,
            dryOut = 0.45f, streakColour = 0.3f, relief = 0.45f,
            pressureSize = 0.8f, pressureFlow = 0.5f, pressureOpacity = 0.25f,
        ),
        BrushPreset(
            id = "hard_round", name = "Hard Round", category = BrushCategory.PAINTING,
            defaultSize = 30f, maxSize = 300f,
            tip = TipShape.ROUND, hardness = 0.95f, spacing = 0.02f,
            streamline = 0.2f, taperStart = 0.5f, taperEnd = 1f, taperMinSize = 0.2f,
            renderMode = RenderMode.WET, flow = 1f, dilution = 0f, charge = 0.9f, pull = 0.2f,
            pressureSize = 0.75f, pressureFlow = 0.4f,
        ),
        BrushPreset(
            id = "fan_brush", name = "Fan Brush", category = BrushCategory.PAINTING,
            defaultSize = 60f, maxSize = 300f,
            tip = TipShape.BRISTLE, spacing = 0.03f, tipAngle = TipAngle.FOLLOW_STROKE,
            streamline = 0.1f, renderMode = RenderMode.WET, flow = 0.8f,
            dilution = 0f, charge = 0.5f, pull = 0.25f,
            dryOut = 0.9f, streakColour = 0.5f, impasto = 0.15f,
            pressureSize = 0.5f, pressureFlow = 0.6f, pressureOpacity = 0.3f,
        ),
        BrushPreset(
            id = "oil_glaze", name = "Oil Glaze", category = BrushCategory.PAINTING,
            defaultSize = 70f, maxSize = 400f, defaultOpacity = 0.7f,
            tip = TipShape.ROUND, hardness = 0.35f, spacing = 0.025f,
            streamline = 0.25f, renderMode = RenderMode.WET, flow = 0.3f,
            dilution = 0.55f, charge = 1f, pull = 0.5f,
            pressureSize = 0.3f, pressureFlow = 0.7f,
        ),
        BrushPreset(
            id = "mixer_oil", name = "Mixer Oil", category = BrushCategory.PAINTING,
            defaultSize = 45f, maxSize = 350f,
            tip = TipShape.BRISTLE, spacing = 0.04f,
            streamline = 0.15f, renderMode = RenderMode.MIXER, flow = 0.9f,
            mixerWet = 0.6f, mixerLoad = 0.6f, mixerMix = 0.5f,
            grain = GrainTexture.CANVAS, grainScale = 1f, grainDepth = 0.6f, tooth = 0.4f,
            impasto = 0.25f, streakColour = 0.3f,
            pressureSize = 0.35f, pressureFlow = 0.5f,
        ),
        BrushPreset(
            id = "mixer_blender", name = "Blender", category = BrushCategory.PAINTING,
            defaultSize = 60f, maxSize = 400f,
            tip = TipShape.ROUND, hardness = 0.35f, spacing = 0.05f,
            streamline = 0.25f, renderMode = RenderMode.MIXER, flow = 0.8f,
            mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true,
            pressureSize = 0.3f, pressureFlow = 0.7f,
        ),
        BrushPreset(
            id = "mixer_dirty", name = "Dirty Brush", category = BrushCategory.PAINTING,
            defaultSize = 40f, maxSize = 300f,
            tip = TipShape.ROUND, hardness = 0.55f, spacing = 0.05f,
            streamline = 0.2f, taperStart = 1f, taperEnd = 1.5f, taperMinSize = 0.3f,
            renderMode = RenderMode.MIXER, flow = 0.85f,
            mixerWet = 0.5f, mixerLoad = 0.7f, mixerMix = 0.4f, mixerClean = false,
            grain = GrainTexture.PAPER, grainScale = 1f, grainDepth = 0.3f,
            pressureSize = 0.5f, pressureFlow = 0.5f,
        ),
        // Blending. With the brush they push paint around without adding colour; with the smudge
        // tool their tip and strength are what count.
        BrushPreset(
            id = "soft_blend", name = "Soft Blend", category = BrushCategory.BLENDING,
            defaultSize = 90f, maxSize = 500f,
            tip = TipShape.ROUND, hardness = 0.25f, spacing = 0.05f,
            streamline = 0.3f, renderMode = RenderMode.MIXER, flow = 0.6f,
            mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            pressureSize = 0.65f, pressureFlow = 0.7f, smudgeStrength = 0.6f,
        ),
        BrushPreset(
            id = "bristle_blend", name = "Bristle Blend", category = BrushCategory.BLENDING,
            defaultSize = 55f, maxSize = 350f,
            tip = TipShape.BRISTLE, spacing = 0.03f, tipAngle = TipAngle.FOLLOW_STROKE,
            streamline = 0.15f, renderMode = RenderMode.MIXER, flow = 0.9f,
            mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            grain = GrainTexture.CANVAS, grainScale = 1f, grainDepth = 0.4f, tooth = 0.3f,
            streakColour = 0.15f,
            pressureSize = 0.65f, pressureFlow = 0.6f, smudgeStrength = 0.9f,
        ),
        BrushPreset(
            id = "textured_blend", name = "Textured Blend", category = BrushCategory.BLENDING,
            defaultSize = 60f, maxSize = 350f,
            tip = TipShape.CHARCOAL, spacing = 0.05f, rotationJitter = 8f,
            streamline = 0.15f, renderMode = RenderMode.MIXER, flow = 0.8f,
            mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            grain = GrainTexture.LAID, grainScale = 1.3f, grainDepth = 0.85f, tooth = 0.8f,
            pressureSize = 0.65f, pressureFlow = 0.6f, smudgeStrength = 0.8f,
        ),
        BrushPreset(
            id = "wet_blend", name = "Wet Blend", category = BrushCategory.BLENDING,
            defaultSize = 60f, maxSize = 400f,
            tip = TipShape.GAUSSIAN, spacing = 0.03f,
            streamline = 0.35f, renderMode = RenderMode.MIXER, flow = 1f,
            mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            pressureSize = 0.5f, pressureFlow = 0.4f, smudgeStrength = 0.97f,
        ),
        BrushPreset(
            id = "finger_blend", name = "Finger", category = BrushCategory.BLENDING,
            defaultSize = 40f, maxSize = 250f,
            tip = TipShape.ROUND, hardness = 0.85f, roundness = 0.55f, tipAngle = TipAngle.FOLLOW_STROKE, angleDegrees = 90f, spacing = 0.04f,
            streamline = 0.2f, renderMode = RenderMode.MIXER, flow = 0.8f,
            mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            grain = GrainTexture.CANVAS, grainScale = 0.8f, grainDepth = 0.85f, tooth = 0.5f,
            pressureSize = 0.65f, pressureFlow = 0.8f, smudgeStrength = 0.55f,
        ),
        BrushPreset(
            id = "knife_blend", name = "Knife Blend", category = BrushCategory.BLENDING,
            defaultSize = 60f, maxSize = 350f,
            tip = TipShape.ROUND, hardness = 0.97f, roundness = 0.22f,
            tipAngle = TipAngle.FOLLOW_STROKE, angleDegrees = 90f, spacing = 0.03f,
            streamline = 0.3f, renderMode = RenderMode.MIXER, flow = 1f,
            mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            pressureSize = 0.65f, pressureFlow = 0.4f, smudgeStrength = 0.95f,
        ),
        BrushPreset(
            id = "blur_blend", name = "Soften", category = BrushCategory.BLENDING,
            defaultSize = 120f, maxSize = 600f,
            tip = TipShape.GAUSSIAN, spacing = 0.05f,
            streamline = 0.3f, renderMode = RenderMode.MIXER, flow = 0.8f,
            mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            pressureSize = 0.65f, pressureFlow = 0.3f, smudgeStrength = 0.5f, soften = true,
        ),
        // Watercolour
        BrushPreset(
            id = "wet_wash", name = "Wet Wash", category = BrushCategory.WATERCOLOR,
            defaultSize = 70f, maxSize = 400f, defaultOpacity = 0.6f,
            tip = TipShape.ROUND, hardness = 0.15f, spacing = 0.04f,
            streamline = 0.25f, renderMode = RenderMode.WASH, flow = 0.35f, blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.COLD_PRESS, grainScale = 1f,
            edgeDarken = 0.35f, granulation = 0.4f, pigmentFlow = 0.7f, edgeWobble = 1f,
            pressureSize = 0.3f, pressureOpacity = 0.5f,
        ),
        BrushPreset(
            id = "watercolor_round", name = "Watercolour Round", category = BrushCategory.WATERCOLOR,
            defaultSize = 30f, maxSize = 200f, defaultOpacity = 0.6f,
            tip = TipShape.ROUND, hardness = 0.35f, spacing = 0.05f,
            streamline = 0.3f, taperStart = 0.8f, taperEnd = 2f, taperMinSize = 0.15f,
            renderMode = RenderMode.WASH, flow = 0.5f, blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.COLD_PRESS, grainScale = 1f,
            edgeDarken = 0.85f, granulation = 0.35f, pigmentFlow = 0.4f, edgeWobble = 0.7f,
            pressureSize = 0.55f, pressureOpacity = 0.4f, smudgeStrength = 0.6f,
        ),
        BrushPreset(
            id = "flat_wash", name = "Flat Wash", category = BrushCategory.WATERCOLOR,
            defaultSize = 60f, maxSize = 300f, defaultOpacity = 0.6f,
            tip = TipShape.ROUND, hardness = 0.4f, roundness = 0.3f,
            tipAngle = TipAngle.FOLLOW_STROKE, angleDegrees = 90f, spacing = 0.02f,
            streamline = 0.3f, renderMode = RenderMode.WASH, flow = 0.5f, blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.COLD_PRESS, grainScale = 1f, charge = 1.5f,
            edgeDarken = 0.6f, granulation = 0.25f, pigmentFlow = 0.5f, edgeWobble = 0.6f,
            pressureSize = 0.2f, pressureOpacity = 0.4f,
        ),
        BrushPreset(
            id = "wet_in_wet", name = "Wet-in-Wet", category = BrushCategory.WATERCOLOR,
            defaultSize = 90f, maxSize = 450f, defaultOpacity = 0.55f,
            tip = TipShape.ROUND, hardness = 0.05f, spacing = 0.05f,
            streamline = 0.3f, renderMode = RenderMode.WASH, flow = 0.3f, blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.COLD_PRESS, grainScale = 1f, charge = 2f,
            edgeDarken = 0.1f, granulation = 0.2f, pigmentFlow = 0.9f, edgeWobble = 1f,
            pressureSize = 0.3f, pressureOpacity = 0.5f,
        ),
        BrushPreset(
            id = "dry_watercolour", name = "Dry Watercolour", category = BrushCategory.WATERCOLOR,
            defaultSize = 40f, maxSize = 250f, defaultOpacity = 0.7f,
            tip = TipShape.ROUND, hardness = 0.6f, spacing = 0.04f,
            streamline = 0.2f, renderMode = RenderMode.WASH, flow = 0.6f, blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.COLD_PRESS, grainScale = 1.3f, grainDepth = 1f, tooth = 0.7f, charge = 0.35f,
            edgeDarken = 0.35f, granulation = 0.35f, pigmentFlow = 0.3f, edgeWobble = 1f,
            pressureSize = 0.4f, pressureOpacity = 0.3f,
        ),
        BrushPreset(
            id = "detail_round", name = "Detail Round", category = BrushCategory.WATERCOLOR,
            defaultSize = 10f, maxSize = 60f, defaultOpacity = 0.7f,
            tip = TipShape.ROUND, hardness = 0.5f, spacing = 0.05f,
            streamline = 0.35f, taperStart = 1f, taperEnd = 2.5f, taperMinSize = 0.1f,
            renderMode = RenderMode.WASH, flow = 0.6f, blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.COLD_PRESS, grainScale = 1f,
            edgeDarken = 0.8f, granulation = 0.2f, pigmentFlow = 0.3f, edgeWobble = 0.4f,
            pressureSize = 0.8f, pressureOpacity = 0.3f,
        ),
        BrushPreset(
            id = "granulating_wash", name = "Granulating Wash", category = BrushCategory.WATERCOLOR,
            defaultSize = 70f, maxSize = 400f, defaultOpacity = 0.65f,
            tip = TipShape.ROUND, hardness = 0.2f, spacing = 0.04f,
            streamline = 0.25f, renderMode = RenderMode.WASH, flow = 0.4f, blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.COLD_PRESS, grainScale = 1f,
            edgeDarken = 0.5f, granulation = 1f, pigmentFlow = 0.6f, edgeWobble = 0.8f,
            pressureSize = 0.3f, pressureOpacity = 0.5f,
        ),
        // Artistic
        BrushPreset(
            id = "marker", name = "Marker", category = BrushCategory.ARTISTIC,
            defaultSize = 24f, maxSize = 120f, defaultOpacity = 0.75f,
            tip = TipShape.ROUND, hardness = 0.85f, roundness = 0.75f,
            tipAngle = TipAngle.FIXED, angleDegrees = 30f, spacing = 0.04f,
            streamline = 0.3f, blendMode = BlendMode.MULTIPLY, pressureSize = 0.15f,
        ),
        BrushPreset(
            id = "charcoal", name = "Charcoal", category = BrushCategory.ARTISTIC,
            defaultSize = 26f, maxSize = 160f, defaultOpacity = 0.95f,
            tip = TipShape.CHARCOAL, spacing = 0.06f, rotationJitter = 5f, lateralJitter = 0.01f,
            streamline = 0.1f, flow = 0.9f,
            grain = GrainTexture.LAID, grainScale = 1f, grainDepth = 0.65f, tooth = 1f, dust = 0.6f,
            pressureSize = 0.25f, pressureOpacity = 0.3f, tiltSize = 1.5f, tiltOpacity = 0.5f,
        ),
        BrushPreset(
            id = "soft_pastel", name = "Soft Pastel", category = BrushCategory.ARTISTIC,
            defaultSize = 34f, maxSize = 200f,
            tip = TipShape.CHARCOAL, spacing = 0.06f, rotationJitter = 8f,
            streamline = 0.1f, flow = 0.85f,
            grain = GrainTexture.LAID, grainScale = 1.4f, grainDepth = 0.75f, tooth = 1f, dust = 0.35f,
            pressureSize = 0.25f, pressureOpacity = 0.35f, tiltSize = 1.2f, tiltOpacity = 0.4f,
        ),
        BrushPreset(
            id = "oil_pastel", name = "Oil Pastel", category = BrushCategory.ARTISTIC,
            defaultSize = 30f, maxSize = 160f,
            tip = TipShape.CHARCOAL, spacing = 0.03f, rotationJitter = 25f,
            streamline = 0.1f, flow = 1f,
            grain = GrainTexture.CANVAS, grainScale = 1.1f, grainDepth = 0.7f, tooth = 0.75f,
            pressureSize = 0.25f, pressureFlow = 0.5f, pressureOpacity = 0.3f,
        ),
        BrushPreset(
            id = "wax_crayon", name = "Wax Crayon", category = BrushCategory.ARTISTIC,
            defaultSize = 14f, maxSize = 80f, defaultOpacity = 0.9f,
            tip = TipShape.CHARCOAL, spacing = 0.06f, rotationJitter = 4f,
            streamline = 0.15f, flow = 1f,
            grain = GrainTexture.DRAWING, grainScale = 1.4f, grainDepth = 1f, tooth = 1f,
            pressureSize = 0.2f, pressureOpacity = 0.45f, tiltSize = 0.8f, tiltOpacity = 0.3f,
        ),
        BrushPreset(
            id = "chalk", name = "Chalk", category = BrushCategory.ARTISTIC,
            defaultSize = 22f, maxSize = 150f,
            tip = TipShape.CHARCOAL, spacing = 0.06f, rotationJitter = 10f, lateralJitter = 0.01f,
            streamline = 0.1f, flow = 0.95f,
            grain = GrainTexture.FINE, grainScale = 1f, grainDepth = 0.75f, tooth = 1f, dust = 0.3f,
            pressureSize = 0.25f, pressureOpacity = 0.35f, tiltSize = 1.4f, tiltOpacity = 0.45f,
        ),
        // Airbrushing
        BrushPreset(
            id = "soft_airbrush", name = "Soft Airbrush", category = BrushCategory.AIRBRUSHING,
            defaultSize = 120f, maxSize = 600f, defaultOpacity = 0.8f,
            tip = TipShape.GAUSSIAN, spacing = 0.05f,
            streamline = 0.2f, flow = 0.12f,
            pressureSize = 0.2f, pressureFlow = 0.8f, smudgeStrength = 0.6f,
        ),
        BrushPreset(
            id = "hard_airbrush", name = "Hard Airbrush", category = BrushCategory.AIRBRUSHING,
            defaultSize = 50f, maxSize = 500f,
            tip = TipShape.ROUND, hardness = 0.97f, spacing = 0.04f,
            streamline = 0.2f, pressureSize = 0.25f, pressureFlow = 0.3f,
        ),
        // Textures
        BrushPreset(
            id = "sponge", name = "Sponge", category = BrushCategory.TEXTURES,
            defaultSize = 80f, maxSize = 400f, defaultOpacity = 0.85f,
            tip = TipShape.CHARCOAL, spacing = 0.35f, rotationJitter = 180f, sizeJitter = 0.25f, lateralJitter = 0.08f,
            streamline = 0f, flow = 0.9f,
            grain = GrainTexture.CHALK, grainScale = 2.6f, grainDepth = 1f, tooth = 0.85f,
            pressureSize = 0.3f, pressureOpacity = 0.5f,
        ),
        BrushPreset(
            id = "splatter", name = "Splatter", category = BrushCategory.TEXTURES,
            defaultSize = 90f, maxSize = 500f,
            tip = TipShape.SPLATTER, spacing = 0.9f, rotationJitter = 180f, sizeJitter = 0.4f,
            lateralJitter = 0.3f, streamline = 0f, pressureSize = 0.4f,
        ),
        BrushPreset(
            id = "noise", name = "Grain Shader", category = BrushCategory.TEXTURES,
            defaultSize = 80f, maxSize = 500f, defaultOpacity = 0.5f,
            tip = TipShape.GAUSSIAN, spacing = 0.06f, flow = 0.2f,
            grain = GrainTexture.FINE, grainScale = 1f, grainDepth = 1f,
            streamline = 0.1f, pressureFlow = 0.7f,
        ),
    )

    val default: BrushPreset get() = byId("studio_pen")
    val defaultEraser: BrushPreset get() = byId("hard_airbrush")
    val defaultSmudge: BrushPreset get() = byId("soft_blend")

    private val builtInById: Map<String, BrushPreset> by lazy { builtIn.associateBy { it.id } }

    fun byId(id: String?): BrushPreset =
        custom.firstOrNull { it.id == id } ?: builtInById[id] ?: builtInById.getValue("studio_pen")

    fun isCustom(id: String): Boolean = custom.any { it.id == id }

    fun inCategory(category: BrushCategory): List<BrushPreset> = all.filter { it.category == category }
}

/** Reads and writes custom brushes as JSON, for storage and for sharing a brush as a file. */
object BrushCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }

    fun encode(brushes: List<BrushPreset>): String = json.encodeToString(brushes)

    private val full = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** One brush with every setting written out, for editors that show them all. */
    fun encodeFull(brush: BrushPreset): String = full.encodeToString(brush)

    /** Returns the brushes in [text], or an empty list if it isn't a brush file. */
    fun decode(text: String): List<BrushPreset> =
        runCatching { json.decodeFromString<List<BrushPreset>>(text) }.getOrElse {
            runCatching { listOf(json.decodeFromString<BrushPreset>(text)) }.getOrDefault(emptyList())
        }
}
