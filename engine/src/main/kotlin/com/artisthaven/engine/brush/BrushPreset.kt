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

    // Rendering.
    val renderMode: RenderMode = RenderMode.GLAZE,
    val flow: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL,

    // Wet mix (WET mode only).
    val dilution: Float = 0f,
    val charge: Float = 1f,
    val pull: Float = 0f,

    // Dynamics.
    val pressureSize: Float = 0.6f,
    val pressureOpacity: Float = 0f,
    val pressureFlow: Float = 0f,
    val pressureGamma: Float = 1f,
    val tiltSize: Float = 0f,
    val tiltOpacity: Float = 0f,
    val speedSize: Float = 0f,
    val speedOpacity: Float = 0f,

    /** Strength when this brush is used by the Smudge tool, 0..1. */
    val smudgeStrength: Float = 0.75f,
)

enum class BrushCategory(val displayName: String) {
    MINE("My brushes"),
    SKETCHING("Sketching"),
    INKING("Inking"),
    PAINTING("Painting"),
    WATERCOLOR("Watercolour"),
    ARTISTIC("Artistic"),
    AIRBRUSHING("Airbrushing"),
    TEXTURES("Textures"),
}

/** The built-in brush set. Each brush is tuned to look clearly different from the others. */
object BrushLibrary {
    /** Brushes the user made in the Brush Studio; the app loads and saves them. */
    @Volatile var custom: List<BrushPreset> = emptyList()

    val all: List<BrushPreset> get() = custom + builtIn

    val builtIn: List<BrushPreset> = listOf(
        // Sketching
        BrushPreset(
            id = "hb_pencil", name = "HB Pencil", category = BrushCategory.SKETCHING,
            defaultSize = 5f, maxSize = 40f, defaultOpacity = 0.9f,
            tip = TipShape.PENCIL, hardness = 0.7f, spacing = 0.12f,
            streamline = 0.15f, taperStart = 1.5f, taperEnd = 1.5f, taperMinSize = 0.5f,
            grain = GrainTexture.PAPER, grainScale = 0.6f, grainDepth = 0.75f,
            pressureSize = 0.35f, pressureOpacity = 0.85f, pressureGamma = 1.3f,
            tiltSize = 1.6f, tiltOpacity = 0.5f,
        ),
        BrushPreset(
            id = "6b_pencil", name = "6B Pencil", category = BrushCategory.SKETCHING,
            defaultSize = 12f, maxSize = 80f,
            tip = TipShape.PENCIL, hardness = 0.45f, spacing = 0.1f,
            streamline = 0.1f, taperStart = 1f, taperEnd = 1f, taperMinSize = 0.6f,
            grain = GrainTexture.PAPER, grainScale = 0.9f, grainDepth = 0.9f,
            pressureSize = 0.3f, pressureOpacity = 0.9f, pressureGamma = 1.1f,
            tiltSize = 2.2f, tiltOpacity = 0.6f,
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
            tip = TipShape.BRISTLE, hardness = 0.8f, spacing = 0.05f,
            streamline = 0.15f, renderMode = RenderMode.WET, flow = 0.7f,
            dilution = 0f, charge = 0.6f, pull = 0.25f,
            grain = GrainTexture.CANVAS, grainScale = 1f, grainDepth = 0.35f,
            pressureSize = 0.4f, pressureFlow = 0.5f, smudgeStrength = 0.8f,
        ),
        BrushPreset(
            id = "oil_paint", name = "Oil Paint", category = BrushCategory.PAINTING,
            defaultSize = 50f, maxSize = 350f,
            tip = TipShape.BRISTLE, hardness = 0.8f, spacing = 0.04f,
            tipAngle = TipAngle.FOLLOW_STROKE, rotationJitter = 4f,
            streamline = 0.1f, renderMode = RenderMode.WET, flow = 0.85f,
            dilution = 0f, charge = 0.75f, pull = 0.55f,
            grain = GrainTexture.CANVAS, grainScale = 1.3f, grainDepth = 0.45f, grainMoving = true,
            pressureSize = 0.3f, pressureFlow = 0.6f, smudgeStrength = 0.85f,
        ),
        // Watercolour
        BrushPreset(
            id = "wet_wash", name = "Wet Wash", category = BrushCategory.WATERCOLOR,
            defaultSize = 70f, maxSize = 400f, defaultOpacity = 0.55f,
            tip = TipShape.WATERCOLOR, spacing = 0.025f,
            streamline = 0.25f, flow = 0.4f, blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.PAPER, grainScale = 1.6f, grainDepth = 0.4f,
            pressureSize = 0.3f, pressureFlow = 0.6f,
        ),
        BrushPreset(
            id = "watercolor_round", name = "Watercolour Round", category = BrushCategory.WATERCOLOR,
            defaultSize = 30f, maxSize = 200f, defaultOpacity = 0.7f,
            tip = TipShape.WATERCOLOR, spacing = 0.06f,
            streamline = 0.3f, taperStart = 1f, taperEnd = 2f, taperMinSize = 0.2f,
            renderMode = RenderMode.WET, flow = 0.35f, dilution = 0.45f, charge = 0.7f, pull = 0.45f,
            blendMode = BlendMode.MULTIPLY,
            grain = GrainTexture.PAPER, grainScale = 1.2f, grainDepth = 0.5f,
            pressureSize = 0.5f, pressureFlow = 0.7f, smudgeStrength = 0.6f,
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
            defaultSize = 26f, maxSize = 160f,
            tip = TipShape.CHARCOAL, spacing = 0.09f, rotationJitter = 180f, lateralJitter = 0.04f,
            streamline = 0.1f, flow = 0.8f,
            grain = GrainTexture.CHALK, grainScale = 1f, grainDepth = 0.85f,
            pressureSize = 0.3f, pressureOpacity = 0.6f, pressureFlow = 0.4f, tiltSize = 1.5f,
        ),
        BrushPreset(
            id = "soft_pastel", name = "Soft Pastel", category = BrushCategory.ARTISTIC,
            defaultSize = 34f, maxSize = 200f,
            tip = TipShape.CHARCOAL, spacing = 0.07f, rotationJitter = 180f,
            streamline = 0.1f, flow = 0.7f,
            grain = GrainTexture.CANVAS, grainScale = 0.9f, grainDepth = 0.8f,
            pressureSize = 0.25f, pressureOpacity = 0.5f, pressureFlow = 0.5f,
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
    val defaultSmudge: BrushPreset get() = byId("soft_airbrush")

    fun byId(id: String?): BrushPreset =
        custom.firstOrNull { it.id == id } ?: builtIn.firstOrNull { it.id == id } ?: builtIn.first { it.id == "studio_pen" }

    fun isCustom(id: String): Boolean = custom.any { it.id == id }

    fun inCategory(category: BrushCategory): List<BrushPreset> = all.filter { it.category == category }
}

/** Reads and writes custom brushes as JSON, for storage and for sharing a brush as a file. */
object BrushCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }

    fun encode(brushes: List<BrushPreset>): String = json.encodeToString(brushes)

    /** Returns the brushes in [text], or an empty list if it isn't a brush file. */
    fun decode(text: String): List<BrushPreset> =
        runCatching { json.decodeFromString<List<BrushPreset>>(text) }.getOrElse {
            runCatching { listOf(json.decodeFromString<BrushPreset>(text)) }.getOrDefault(emptyList())
        }
}
