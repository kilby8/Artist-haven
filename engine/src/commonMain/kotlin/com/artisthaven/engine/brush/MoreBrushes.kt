package com.artisthaven.engine.brush

import com.artisthaven.engine.brush.BrushCategory as C
import com.artisthaven.engine.brush.GrainTexture as G
import com.artisthaven.engine.brush.RenderMode as R
import com.artisthaven.engine.brush.TipAngle as A
import com.artisthaven.engine.brush.TipShape as T
import com.artisthaven.engine.document.BlendMode as B

/**
 * The wider brush library: the kinds of brush found across Procreate, Photoshop, Krita, Clip
 * Studio, Fresco, Rebelle, ArtRage and Painter, rebuilt from our own engine settings (no brush
 * files or artwork from those apps). See docs/BRUSH-SURVEY.md for the survey behind it.
 */
internal object MoreBrushes {
    /** A stamp: one whole shape per dab, spaced out, varied in size and angle. */
    private fun stamp(
        id: String, name: String, category: C, tip: T, size: Float,
        spacing: Float = 1.1f, rotation: Float = 180f, sizeJitter: Float = 0.35f,
        scatter: Float = 0f, count: Int = 1, hue: Float = 0f, bright: Float = 0f, sat: Float = 0f,
        opacity: Float = 1f, hardness: Float = 0.92f, blend: B = B.NORMAL, opacityJitter: Float = 0f,
        flip: Float = 1f, angle: A = A.FIXED, angleDegrees: Float = 0f, pressureSize: Float = 0.45f,
        roundness: Float = 1f, roundnessJitter: Float = 0f, flow: Float = 1f,
    ) = BrushPreset(
        id = id, name = name, category = category,
        defaultSize = size, maxSize = maxOf(400f, size * 4f), defaultOpacity = opacity,
        tip = tip, hardness = hardness, roundness = roundness, roundnessJitter = roundnessJitter,
        tipAngle = angle, angleDegrees = angleDegrees,
        rotationJitter = rotation, sizeJitter = sizeJitter, opacityJitter = opacityJitter, flipJitter = flip,
        spacing = spacing, scatter = scatter, count = count, streamline = 0.1f,
        hueJitter = hue, saturationJitter = sat, brightnessJitter = bright,
        flow = flow, blendMode = blend, buildUp = true,
        pressureSize = pressureSize, pressureOpacity = 0.2f,
    )

    /** Rows of evenly spaced dots running along the stroke. [spacing] is the dot pitch. */
    private fun dots(id: String, name: String, tip: T, size: Float, spacing: Float, hue: Float = 0f) = BrushPreset(
        id = id, name = name, category = C.STAMPS,
        defaultSize = size, maxSize = 400f,
        tip = tip, hardness = 1f, tipAngle = A.FOLLOW_STROKE, spacing = spacing, streamline = 0.5f,
        hueJitter = hue, buildUp = true, pressureSize = 0.15f,
    )

    /** Teeth side by side across the stroke, dragged into parallel lines. */
    private fun rake(id: String, name: String, tip: T, size: Float) = BrushPreset(
        id = id, name = name, category = C.RAKES,
        defaultSize = size, maxSize = 400f,
        tip = tip, hardness = 0.9f, tipAngle = A.FOLLOW_STROKE, spacing = 0.02f, streamline = 0.3f,
        taperStart = 0.5f, taperEnd = 1.5f, taperMinSize = 0.5f, pressureSize = 0.35f, pressureOpacity = 0.2f,
    )

    /** A paint roller: a wide even band, square ends, the nap's texture streaked along it. */
    private fun roller(id: String, name: String, tip: T, grain: G, depth: Float, flow: Float = 0.9f, tooth: Float = 0f, hardness: Float = 0.95f) = BrushPreset(
        id = id, name = name, category = C.RAKES,
        defaultSize = 90f, maxSize = 600f,
        tip = tip, hardness = hardness, tipAngle = A.FOLLOW_STROKE, spacing = 0.03f, streamline = 0.4f,
        grain = grain, grainScale = 1.2f, grainDepth = depth, tooth = tooth, flow = flow,
        pressureSize = 0f, pressureOpacity = 0.25f,
    )

    /** Fills the stroke with a repeating pattern fixed to the canvas. */
    private fun pattern(g: G, name: String, scale: Float = 1f) = BrushPreset(
        id = "pattern_" + g.name.lowercase(), name = name, category = C.PATTERNS,
        defaultSize = 90f, maxSize = 600f,
        tip = T.ROUND, hardness = 0.8f, spacing = 0.05f, streamline = 0.2f,
        grain = g, grainScale = scale, grainDepth = 1f,
        pressureSize = 0.25f, pressureOpacity = 0.15f,
    )

    /** Tiny dots thrown in a cloud around the pen, like a spray can or a toothbrush flick. */
    private fun spray(
        id: String, name: String, category: C, dot: Float, spread: Float, count: Int,
        opacity: Float = 0.9f, hardness: Float = 0.6f, grain: G = G.NONE, hue: Float = 0f,
        bright: Float = 0f, blend: B = B.NORMAL, spacing: Float = 1f, tip: T = T.ROUND,
    ) = BrushPreset(
        id = id, name = name, category = category,
        defaultSize = dot, minSize = 1f, maxSize = 60f, defaultOpacity = opacity,
        tip = tip, hardness = hardness, spacing = spacing, scatter = spread, count = count,
        sizeJitter = 0.7f, opacityJitter = 0.6f, rotationJitter = 180f, streamline = 0.05f,
        grain = grain, grainDepth = if (grain == G.NONE) 0f else 0.6f,
        hueJitter = hue, brightnessJitter = bright, blendMode = blend, buildUp = true,
        pressureSize = 0.2f, pressureOpacity = 0.6f,
    )

    val all: List<BrushPreset> = listOf(
        // ---- Sketching ----
        BrushPreset(
            id = "2h_pencil", name = "2H Pencil", category = C.SKETCHING,
            defaultSize = 4f, maxSize = 30f, defaultOpacity = 0.6f,
            tip = T.PENCIL, hardness = 0.9f, spacing = 0.1f, streamline = 0.15f,
            taperStart = 1f, taperEnd = 1f, taperMinSize = 0.6f,
            grain = G.DRAWING, grainDepth = 1f, tooth = 1f,
            pressureSize = 0.2f, pressureOpacity = 0.5f, pressureGamma = 1.2f, tiltSize = 1.5f, tiltOpacity = 0.6f,
        ),
        BrushPreset(
            id = "4b_pencil", name = "4B Pencil", category = C.SKETCHING,
            defaultSize = 9f, maxSize = 60f, defaultOpacity = 0.9f,
            tip = T.PENCIL, hardness = 0.82f, spacing = 0.09f, streamline = 0.12f,
            taperStart = 0.8f, taperEnd = 0.8f, taperMinSize = 0.6f,
            grain = G.DRAWING, grainScale = 1.1f, grainDepth = 0.75f, tooth = 1f,
            pressureSize = 0.25f, pressureOpacity = 0.35f, pressureGamma = 1.1f, tiltSize = 2f, tiltOpacity = 0.6f,
        ),
        BrushPreset(
            id = "coloured_pencil", name = "Coloured Pencil", category = C.SKETCHING,
            defaultSize = 7f, maxSize = 40f, defaultOpacity = 0.85f,
            tip = T.PENCIL, hardness = 0.8f, spacing = 0.09f, streamline = 0.15f,
            grain = G.DRAWING, grainScale = 1.3f, grainDepth = 0.8f, tooth = 0.9f,
            pressureSize = 0.2f, pressureOpacity = 0.6f, tiltSize = 1.8f, tiltOpacity = 0.5f,
        ),
        BrushPreset(
            id = "shader_pencil", name = "Shading Pencil", category = C.SKETCHING,
            defaultSize = 28f, maxSize = 120f, defaultOpacity = 0.5f,
            tip = T.PENCIL, hardness = 0.4f, roundness = 0.6f, tipAngle = A.STYLUS, spacing = 0.08f, streamline = 0.1f,
            grain = G.DRAWING, grainScale = 1.2f, grainDepth = 0.9f, tooth = 1f,
            pressureSize = 0.2f, pressureOpacity = 0.6f, tiltSize = 3f, tiltOpacity = 0.4f,
        ),
        BrushPreset(
            id = "graphite_stick", name = "Graphite Stick", category = C.SKETCHING,
            defaultSize = 30f, maxSize = 160f, defaultOpacity = 0.85f,
            tip = T.CHARCOAL, spacing = 0.06f, rotationJitter = 4f, streamline = 0.1f,
            grain = G.DRAWING, grainScale = 1.2f, grainDepth = 0.8f, tooth = 0.85f,
            pressureSize = 0.3f, pressureOpacity = 0.45f, tiltSize = 2.5f,
        ),
        BrushPreset(
            id = "rough_sketch", name = "Rough Sketch", category = C.SKETCHING,
            defaultSize = 5f, maxSize = 40f, defaultOpacity = 0.7f,
            tip = T.PENCIL, hardness = 0.8f, spacing = 0.12f, lateralJitter = 0.08f, sizeJitter = 0.25f,
            opacityJitter = 0.3f, streamline = 0f,
            grain = G.DRAWING, grainDepth = 0.8f, tooth = 0.9f,
            pressureSize = 0.3f, pressureOpacity = 0.5f,
        ),
        BrushPreset(
            id = "silverpoint", name = "Silverpoint", category = C.SKETCHING,
            defaultSize = 2.5f, maxSize = 12f, defaultOpacity = 0.5f,
            tip = T.PENCIL, hardness = 0.95f, spacing = 0.1f, streamline = 0.25f,
            grain = G.FINE, grainDepth = 0.5f, tooth = 0.6f,
            pressureSize = 0.1f, pressureOpacity = 0.4f,
        ),
        BrushPreset(
            id = "hatching_pencil", name = "Hatching", category = C.SKETCHING,
            defaultSize = 40f, maxSize = 200f, defaultOpacity = 0.7f,
            tip = T.HATCH, hardness = 0.95f, tipAngle = A.FIXED, spacing = 0.9f, streamline = 0.2f,
            grain = G.DRAWING, grainDepth = 0.5f, tooth = 0.6f, buildUp = true,
            pressureSize = 0.2f, pressureOpacity = 0.6f,
        ),
        BrushPreset(
            id = "blue_line", name = "Construction Line", category = C.SKETCHING,
            defaultSize = 3f, maxSize = 20f, defaultOpacity = 0.4f,
            tip = T.ROUND, hardness = 0.9f, spacing = 0.08f, streamline = 0.6f,
            pressureSize = 0f, pressureOpacity = 0.3f,
        ),

        // ---- Inking ----
        BrushPreset(
            id = "ballpoint", name = "Ballpoint", category = C.INKING,
            defaultSize = 3f, maxSize = 20f, defaultOpacity = 0.85f,
            tip = T.ROUND, hardness = 0.9f, spacing = 0.05f, streamline = 0.4f,
            grain = G.FINE, grainDepth = 0.35f, pressureSize = 0.15f, pressureOpacity = 0.6f,
        ),
        BrushPreset(
            id = "felt_tip", name = "Felt Tip", category = C.INKING,
            defaultSize = 6f, maxSize = 40f, defaultOpacity = 0.9f,
            tip = T.ROUND, hardness = 0.8f, spacing = 0.05f, streamline = 0.4f, flow = 0.8f,
            grain = G.PAPER, grainDepth = 0.2f, pressureSize = 0.2f,
        ),
        BrushPreset(
            id = "dip_pen", name = "Dip Pen", category = C.INKING,
            defaultSize = 10f, maxSize = 80f,
            tip = T.ROUND, hardness = 0.95f, roundness = 0.7f, tipAngle = A.FIXED, angleDegrees = 30f,
            spacing = 0.03f, streamline = 0.45f, taperStart = 3f, taperEnd = 5f, taperMinSize = 0.03f,
            grain = G.PAPER, grainScale = 0.8f, grainDepth = 0.45f, speedOpacity = 0.3f,
            pressureSize = 1f, pressureGamma = 1.8f,
        ),
        BrushPreset(
            id = "comic_inker", name = "Comic Inker", category = C.INKING,
            defaultSize = 12f, maxSize = 120f,
            tip = T.ROUND, hardness = 0.96f, spacing = 0.03f, streamline = 0.65f,
            taperStart = 5f, taperEnd = 8f, taperMinSize = 0.02f, pressureSize = 1f, pressureGamma = 1.5f,
        ),
        BrushPreset(
            id = "sumi_ink", name = "Sumi Ink", category = C.INKING,
            defaultSize = 30f, maxSize = 200f,
            tip = T.ROUND, hardness = 0.75f, spacing = 0.04f, streamline = 0.3f,
            taperStart = 2f, taperEnd = 4f, taperMinSize = 0.05f,
            renderMode = R.WET, flow = 0.9f, charge = 0.8f, pull = 0.15f,
            grain = G.PAPER, grainScale = 1.3f, grainDepth = 0.7f, tooth = 0.35f, dust = 0.2f,
            pressureSize = 1f, pressureGamma = 1.4f,
        ),
        BrushPreset(
            id = "bleed_ink", name = "Bleeding Ink", category = C.INKING,
            defaultSize = 12f, maxSize = 100f,
            tip = T.ROUND, hardness = 0.45f, spacing = 0.05f, sizeJitter = 0.12f, streamline = 0.4f,
            grain = G.PAPER, grainScale = 0.8f, grainDepth = 0.5f, dust = 0.35f,
            pressureSize = 0.8f, taperStart = 2f, taperEnd = 3f,
        ),
        BrushPreset(
            id = "stipple_pen", name = "Stipple Pen", category = C.INKING,
            defaultSize = 40f, maxSize = 200f,
            tip = T.STIPPLE, hardness = 0.95f, tipAngle = A.FIXED, rotationJitter = 180f,
            spacing = 1f, scatter = 0.2f, sizeJitter = 0.2f, streamline = 0.1f, buildUp = true,
            pressureSize = 0.3f, pressureOpacity = 0.3f,
        ),
        BrushPreset(
            id = "scratchy_pen", name = "Scratchy Pen", category = C.INKING,
            defaultSize = 5f, maxSize = 40f,
            tip = T.ROUND, hardness = 0.9f, spacing = 0.06f, lateralJitter = 0.04f, streamline = 0.2f,
            grain = G.CHALK, grainDepth = 0.6f, pressureSize = 0.7f, taperStart = 1f, taperEnd = 2f,
        ),

        // ---- Calligraphy ----
        BrushPreset(
            id = "chisel_nib", name = "Chisel Nib", category = C.CALLIGRAPHY,
            defaultSize = 26f, maxSize = 160f,
            tip = T.ROUND, hardness = 0.97f, roundness = 0.15f, tipAngle = A.FIXED, angleDegrees = 45f,
            spacing = 0.02f, streamline = 0.5f, pressureSize = 0.2f,
        ),
        BrushPreset(
            id = "italic_nib", name = "Italic Nib", category = C.CALLIGRAPHY,
            defaultSize = 20f, maxSize = 120f,
            tip = T.ROUND, hardness = 0.97f, roundness = 0.2f, tipAngle = A.FIXED, angleDegrees = 30f,
            spacing = 0.02f, streamline = 0.55f, pressureSize = 0.35f,
        ),
        BrushPreset(
            id = "parallel_pen", name = "Parallel Pen", category = C.CALLIGRAPHY,
            defaultSize = 32f, maxSize = 200f,
            tip = T.SQUARE, hardness = 1f, roundness = 0.1f, tipAngle = A.FIXED, angleDegrees = 40f,
            spacing = 0.015f, streamline = 0.5f, pressureSize = 0f,
        ),
        BrushPreset(
            id = "pointed_nib", name = "Pointed Nib", category = C.CALLIGRAPHY,
            defaultSize = 14f, maxSize = 100f,
            tip = T.ROUND, hardness = 0.96f, spacing = 0.03f, streamline = 0.6f,
            taperStart = 2f, taperEnd = 5f, taperMinSize = 0.03f, pressureSize = 1f, pressureGamma = 2f,
        ),
        BrushPreset(
            id = "brush_lettering", name = "Brush Lettering", category = C.CALLIGRAPHY,
            defaultSize = 26f, maxSize = 160f,
            tip = T.ROUND, hardness = 0.8f, roundness = 0.35f, tipAngle = A.FIXED, angleDegrees = 60f,
            spacing = 0.025f, streamline = 0.65f, taperEnd = 3f, taperMinSize = 0.2f,
            pressureSize = 0.85f, pressureGamma = 1.2f,
        ),
        BrushPreset(
            id = "script_pen", name = "Script", category = C.CALLIGRAPHY,
            defaultSize = 12f, maxSize = 80f,
            tip = T.ROUND, hardness = 0.96f, roundness = 0.8f, tipAngle = A.FIXED, angleDegrees = 50f,
            spacing = 0.03f, streamline = 0.7f, taperStart = 2f, taperEnd = 6f, taperMinSize = 0.1f,
            pressureSize = 0.9f, pressureGamma = 1.3f,
        ),
        BrushPreset(
            id = "chalk_lettering", name = "Chalk Lettering", category = C.CALLIGRAPHY,
            defaultSize = 24f, maxSize = 140f,
            tip = T.ROUND, hardness = 0.85f, roundness = 0.3f, tipAngle = A.FIXED, angleDegrees = 45f,
            spacing = 0.04f, streamline = 0.45f,
            grain = G.CHALK, grainScale = 1.2f, grainDepth = 0.7f, tooth = 0.6f, pressureSize = 0.4f,
        ),

        // ---- Painting ----
        BrushPreset(
            id = "rigger", name = "Rigger", category = C.PAINTING,
            defaultSize = 5f, maxSize = 40f,
            tip = T.ROUND, hardness = 0.95f, spacing = 0.04f, streamline = 0.35f,
            taperStart = 1f, taperEnd = 10f, taperMinSize = 0.05f,
            renderMode = R.WET, flow = 0.6f, charge = 0.9f, pull = 0.1f, dilution = 0.35f,
            pressureSize = 0.5f, pressureGamma = 1.3f,
        ),
        BrushPreset(
            id = "sable_round", name = "Sable Round", category = C.PAINTING,
            defaultSize = 26f, maxSize = 200f,
            tip = T.ROUND, hardness = 0.7f, spacing = 0.05f, streamline = 0.25f,
            taperStart = 1.5f, taperEnd = 2.5f, taperMinSize = 0.2f,
            renderMode = R.WET, flow = 0.7f, charge = 0.9f, pull = 0.25f, dilution = 0.05f,
            pressureSize = 0.8f, pressureFlow = 0.5f,
        ),
        BrushPreset(
            id = "scumble", name = "Scumble", category = C.PAINTING,
            defaultSize = 60f, maxSize = 300f, defaultOpacity = 0.75f,
            tip = T.BRISTLE, spacing = 0.03f, tipAngle = A.FOLLOW_STROKE, streamline = 0.1f,
            renderMode = R.WET, flow = 0.55f, charge = 0.4f, pull = 0.3f,
            grain = G.CANVAS, grainScale = 1.3f, grainDepth = 1f, tooth = 1f, dryOut = 0.85f, streakColour = 0.3f,
            pressureSize = 0.4f, pressureFlow = 0.6f,
        ),
        BrushPreset(
            id = "glazing_medium", name = "Glazing Medium", category = C.PAINTING,
            defaultSize = 70f, maxSize = 400f, defaultOpacity = 0.35f,
            tip = T.ROUND, hardness = 0.3f, spacing = 0.05f, streamline = 0.2f, flow = 0.3f,
            pressureSize = 0.3f, pressureFlow = 0.7f,
        ),
        BrushPreset(
            id = "thick_acrylic", name = "Thick Acrylic", category = C.PAINTING,
            defaultSize = 45f, maxSize = 300f,
            tip = T.BRISTLE, spacing = 0.03f, tipAngle = A.FOLLOW_STROKE, streamline = 0.15f,
            renderMode = R.WET, flow = 1f, charge = 0.9f, pull = 0.25f,
            grain = G.CANVAS, grainDepth = 0.3f, tooth = 0.2f, dryOut = 0.15f, streakColour = 0.1f, relief = 0.6f,
            pressureSize = 0.8f, pressureGamma = 1.2f, pressureFlow = 0.4f,
        ),
        BrushPreset(
            id = "gouache_flat", name = "Gouache Flat", category = C.PAINTING,
            defaultSize = 40f, maxSize = 260f,
            tip = T.ROUND, hardness = 0.85f, roundness = 0.35f, tipAngle = A.FOLLOW_STROKE, angleDegrees = 90f,
            spacing = 0.03f, streamline = 0.2f,
            renderMode = R.WET, flow = 0.9f, charge = 0.65f, pull = 0.2f,
            grain = G.CANVAS, grainDepth = 0.3f, tooth = 0.25f, pressureSize = 0.6f, pressureFlow = 0.4f,
        ),
        BrushPreset(
            id = "mixer_round", name = "Wet Round Mixer", category = C.PAINTING,
            defaultSize = 40f, maxSize = 300f,
            tip = T.ROUND, hardness = 0.5f, spacing = 0.05f, streamline = 0.2f,
            renderMode = R.MIXER, flow = 0.8f, mixerWet = 0.7f, mixerLoad = 0.6f, mixerMix = 0.5f,
            pressureSize = 0.6f, pressureFlow = 0.5f,
        ),
        BrushPreset(
            id = "dabbing_brush", name = "Dabbing Brush", category = C.PAINTING,
            defaultSize = 50f, maxSize = 300f,
            tip = T.CHARCOAL, hardness = 0.7f, spacing = 0.6f, rotationJitter = 180f, sizeJitter = 0.25f,
            streamline = 0.05f, renderMode = R.WET, flow = 0.9f, charge = 0.7f, pull = 0.3f,
            grain = G.CANVAS, grainDepth = 0.4f, pressureSize = 0.4f,
        ),

        // ---- Blending ----
        BrushPreset(
            id = "smudge_stick", name = "Smudge Stick", category = C.BLENDING,
            defaultSize = 40f, maxSize = 300f,
            tip = T.CHARCOAL, spacing = 0.05f, rotationJitter = 6f, streamline = 0.2f,
            renderMode = R.MIXER, flow = 0.7f, mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            grain = G.CHALK, grainScale = 1.2f, grainDepth = 0.6f, tooth = 0.5f,
            pressureSize = 0.5f, pressureFlow = 0.6f, smudgeStrength = 0.75f,
        ),
        BrushPreset(
            id = "tortillon", name = "Tortillon", category = C.BLENDING,
            defaultSize = 16f, maxSize = 100f,
            tip = T.PENCIL, hardness = 0.8f, spacing = 0.05f, streamline = 0.3f,
            renderMode = R.MIXER, flow = 0.7f, mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            grain = G.DRAWING, grainDepth = 0.8f, tooth = 0.7f, pressureSize = 0.4f, pressureFlow = 0.5f, smudgeStrength = 0.7f,
        ),
        BrushPreset(
            id = "rough_blend", name = "Rough Blend", category = C.BLENDING,
            defaultSize = 70f, maxSize = 400f,
            tip = T.ROUND, hardness = 0.5f, spacing = 0.05f, streamline = 0.2f,
            renderMode = R.MIXER, flow = 0.75f, mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            grain = G.CANVAS, grainScale = 1.4f, grainDepth = 0.9f, tooth = 0.7f,
            pressureSize = 0.5f, pressureFlow = 0.6f, smudgeStrength = 0.8f,
        ),
        BrushPreset(
            id = "stipple_blend", name = "Stipple Blend", category = C.BLENDING,
            defaultSize = 60f, maxSize = 300f,
            tip = T.STIPPLE, spacing = 0.4f, rotationJitter = 180f, streamline = 0.05f,
            renderMode = R.MIXER, flow = 0.8f, mixerWet = 1f, mixerLoad = 0f, mixerMix = 1f, mixerSampleAll = true, blendOnly = true,
            pressureSize = 0.4f, smudgeStrength = 0.7f,
        ),

        // ---- Rakes & Rollers ----
        rake("fine_rake", "Fine Rake", T.RAKE_FINE, 40f),
        rake("wide_rake", "Wide Rake", T.RAKE_WIDE, 60f),
        rake("uneven_rake", "Worn Rake", T.RAKE_UNEVEN, 50f),
        BrushPreset(
            id = "wet_rake", name = "Wet Rake", category = C.RAKES,
            defaultSize = 50f, maxSize = 300f,
            tip = T.RAKE, tipAngle = A.FOLLOW_STROKE, spacing = 0.02f, streamline = 0.3f,
            renderMode = R.WET, flow = 0.85f, charge = 0.75f, pull = 0.5f,
            taperEnd = 1.5f, taperMinSize = 0.5f, pressureSize = 0.3f, pressureFlow = 0.4f,
        ),
        roller("paint_roller", "Paint Roller", T.ROLLER, grain = G.CANVAS, depth = 0.5f),
        roller("foam_roller", "Foam Roller", T.ROLLER, grain = G.NONE, depth = 0f, flow = 0.6f, hardness = 0.45f),
        roller("dry_roller", "Dry Roller", T.ROLLER, grain = G.CHALK, depth = 0.85f, tooth = 0.7f),
        roller("stripe_roller", "Stripe Roller", T.ROLLER_STRIPES, grain = G.NONE, depth = 0f),

        // ---- Watercolour ----
        BrushPreset(
            id = "big_wash", name = "Big Wash", category = C.WATERCOLOR,
            defaultSize = 140f, maxSize = 500f, defaultOpacity = 0.5f,
            tip = T.ROUND, hardness = 0.25f, spacing = 0.05f, streamline = 0.25f,
            renderMode = R.WASH, flow = 0.45f, blendMode = B.MULTIPLY,
            grain = G.COLD_PRESS, edgeDarken = 0.6f, granulation = 0.25f, pigmentFlow = 0.5f, edgeWobble = 0.6f,
            pressureSize = 0.4f, pressureOpacity = 0.4f,
        ),
        BrushPreset(
            id = "bleed_wash", name = "Bleeding Wash", category = C.WATERCOLOR,
            defaultSize = 50f, maxSize = 300f, defaultOpacity = 0.55f,
            tip = T.ROUND, hardness = 0.3f, spacing = 0.05f, streamline = 0.3f,
            renderMode = R.WASH, flow = 0.5f, blendMode = B.MULTIPLY,
            grain = G.COLD_PRESS, edgeDarken = 0.5f, granulation = 0.3f, pigmentFlow = 1f, edgeWobble = 1f,
            pressureSize = 0.5f, pressureOpacity = 0.4f,
        ),
        BrushPreset(
            id = "hard_edge_wash", name = "Hard-edge Wash", category = C.WATERCOLOR,
            defaultSize = 40f, maxSize = 300f, defaultOpacity = 0.6f,
            tip = T.ROUND, hardness = 0.5f, spacing = 0.05f, streamline = 0.3f,
            renderMode = R.WASH, flow = 0.55f, blendMode = B.MULTIPLY,
            grain = G.COLD_PRESS, edgeDarken = 1f, granulation = 0.2f, pigmentFlow = 0.3f, edgeWobble = 0.4f,
            pressureSize = 0.5f, pressureOpacity = 0.3f,
        ),
        BrushPreset(
            id = "soft_glaze_wash", name = "Soft Glaze", category = C.WATERCOLOR,
            defaultSize = 60f, maxSize = 400f, defaultOpacity = 0.4f,
            tip = T.ROUND, hardness = 0.2f, spacing = 0.05f, streamline = 0.3f,
            renderMode = R.WASH, flow = 0.4f, blendMode = B.MULTIPLY,
            grain = G.COLD_PRESS, edgeDarken = 0f, granulation = 0f, pigmentFlow = 0f, edgeWobble = 0.05f,
            pressureSize = 0.4f, pressureOpacity = 0.5f,
        ),
        stamp("watercolour_spatter", "Watercolour Spatter", C.WATERCOLOR, T.ROUND, 14f,
            spacing = 1.2f, sizeJitter = 0.85f, scatter = 2.5f, count = 3, opacity = 0.6f, hardness = 0.55f,
            blend = B.MULTIPLY, opacityJitter = 0.5f, pressureSize = 0.3f),

        // ---- Artistic ----
        BrushPreset(
            id = "highlighter", name = "Highlighter", category = C.ARTISTIC,
            defaultSize = 26f, maxSize = 100f, defaultOpacity = 0.5f,
            tip = T.SQUARE, hardness = 0.95f, roundness = 0.35f, tipAngle = A.FIXED, angleDegrees = 90f,
            spacing = 0.03f, streamline = 0.5f, blendMode = B.MULTIPLY, pressureSize = 0f,
        ),
        BrushPreset(
            id = "brush_marker", name = "Brush Marker", category = C.ARTISTIC,
            defaultSize = 20f, maxSize = 120f, defaultOpacity = 0.7f,
            tip = T.ROUND, hardness = 0.85f, spacing = 0.04f, streamline = 0.4f,
            taperStart = 2f, taperEnd = 3f, taperMinSize = 0.1f, blendMode = B.MULTIPLY,
            pressureSize = 0.9f, pressureGamma = 1.3f,
        ),
        BrushPreset(
            id = "conte", name = "Conté Crayon", category = C.ARTISTIC,
            defaultSize = 20f, maxSize = 120f, defaultOpacity = 0.9f,
            tip = T.CHARCOAL, roundness = 0.5f, tipAngle = A.STYLUS, spacing = 0.06f, streamline = 0.1f,
            grain = G.LAID, grainDepth = 0.6f, tooth = 0.9f, pressureSize = 0.3f, pressureOpacity = 0.4f, tiltSize = 1.5f,
        ),
        BrushPreset(
            id = "woodcut", name = "Woodcut", category = C.ARTISTIC,
            defaultSize = 30f, maxSize = 200f,
            tip = T.ROUND, hardness = 0.98f, roundness = 0.45f, tipAngle = A.FOLLOW_STROKE, angleDegrees = 90f,
            spacing = 0.03f, streamline = 0.4f, taperStart = 1f, taperEnd = 2f, taperMinSize = 0.1f,
            grain = G.WOOD, grainScale = 1.5f, grainDepth = 0.75f, pressureSize = 0.7f,
        ),
        BrushPreset(
            id = "linocut", name = "Linocut", category = C.ARTISTIC,
            defaultSize = 40f, maxSize = 260f,
            tip = T.CHARCOAL, hardness = 1f, spacing = 0.04f, streamline = 0.3f,
            grain = G.STATIC, grainScale = 1.5f, grainDepth = 0.25f, pressureSize = 0.5f,
        ),
        BrushPreset(
            id = "crayon_rough", name = "Kids' Crayon", category = C.ARTISTIC,
            defaultSize = 18f, maxSize = 80f,
            tip = T.CHARCOAL, spacing = 0.06f, rotationJitter = 8f, streamline = 0.05f,
            grain = G.PAPER, grainScale = 0.7f, grainDepth = 0.8f, tooth = 0.95f, lateralJitter = 0.02f,
            pressureSize = 0.25f, pressureOpacity = 0.4f,
        ),

        // ---- Charcoal & Pastel ----
        BrushPreset(
            id = "vine_charcoal", name = "Vine Charcoal", category = C.CHARCOAL,
            defaultSize = 18f, maxSize = 120f, defaultOpacity = 0.75f,
            tip = T.CHARCOAL, spacing = 0.06f, rotationJitter = 5f, streamline = 0.1f,
            grain = G.LAID, grainDepth = 0.75f, tooth = 1f, dust = 0.3f,
            pressureSize = 0.3f, pressureOpacity = 0.5f, tiltSize = 1.5f,
        ),
        BrushPreset(
            id = "compressed_charcoal", name = "Compressed Charcoal", category = C.CHARCOAL,
            defaultSize = 26f, maxSize = 160f,
            tip = T.CHARCOAL, spacing = 0.05f, rotationJitter = 5f, streamline = 0.1f,
            grain = G.LAID, grainDepth = 0.4f, tooth = 0.8f, dust = 0.5f,
            pressureSize = 0.3f, pressureOpacity = 0.3f, tiltSize = 1.5f,
        ),
        BrushPreset(
            id = "charcoal_block", name = "Charcoal Block", category = C.CHARCOAL,
            defaultSize = 70f, maxSize = 300f, defaultOpacity = 0.8f,
            tip = T.CHARCOAL, roundness = 0.4f, tipAngle = A.FOLLOW_STROKE, angleDegrees = 90f,
            spacing = 0.04f, streamline = 0.1f,
            grain = G.LAID, grainScale = 1.2f, grainDepth = 0.8f, tooth = 1f, dust = 0.25f,
            pressureSize = 0.2f, pressureOpacity = 0.5f,
        ),
        BrushPreset(
            id = "hard_pastel", name = "Hard Pastel", category = C.CHARCOAL,
            defaultSize = 20f, maxSize = 120f,
            tip = T.CHARCOAL, roundness = 0.6f, spacing = 0.06f, streamline = 0.1f,
            grain = G.CHALK, grainDepth = 0.6f, tooth = 0.8f, dust = 0.15f,
            pressureSize = 0.25f, pressureOpacity = 0.4f, tiltSize = 1.2f,
        ),
        BrushPreset(
            id = "pastel_pencil", name = "Pastel Pencil", category = C.CHARCOAL,
            defaultSize = 8f, maxSize = 40f,
            tip = T.PENCIL, hardness = 0.7f, spacing = 0.08f, streamline = 0.15f,
            grain = G.LAID, grainDepth = 0.7f, tooth = 1f, pressureSize = 0.2f, pressureOpacity = 0.5f, tiltSize = 1.8f,
        ),
        BrushPreset(
            id = "pastel_side", name = "Pastel on its Side", category = C.CHARCOAL,
            defaultSize = 90f, maxSize = 400f, defaultOpacity = 0.6f,
            tip = T.CHARCOAL, roundness = 0.35f, tipAngle = A.FOLLOW_STROKE, angleDegrees = 90f,
            spacing = 0.04f, streamline = 0.1f,
            grain = G.PAPER, grainScale = 1.3f, grainDepth = 1f, tooth = 1f,
            pressureSize = 0.15f, pressureOpacity = 0.5f,
        ),
        BrushPreset(
            id = "sanguine", name = "Sanguine", category = C.CHARCOAL,
            defaultSize = 16f, maxSize = 100f, defaultOpacity = 0.85f,
            tip = T.CHARCOAL, spacing = 0.06f, rotationJitter = 4f, streamline = 0.1f,
            grain = G.DRAWING, grainDepth = 0.6f, tooth = 0.9f, pressureSize = 0.3f, pressureOpacity = 0.45f, tiltSize = 1.6f,
        ),
        BrushPreset(
            id = "charcoal_dust", name = "Charcoal Dust", category = C.CHARCOAL,
            defaultSize = 100f, maxSize = 500f, defaultOpacity = 0.5f,
            tip = T.GAUSSIAN, spacing = 0.06f, streamline = 0.1f, flow = 0.3f,
            grain = G.LAID, grainScale = 1.2f, grainDepth = 0.8f, tooth = 0.7f, dust = 0.6f,
            pressureSize = 0.2f, pressureFlow = 0.7f,
        ),

        // ---- Airbrushing ----
        BrushPreset(
            id = "medium_airbrush", name = "Medium Airbrush", category = C.AIRBRUSHING,
            defaultSize = 60f, maxSize = 400f, defaultOpacity = 0.85f,
            tip = T.ROUND, hardness = 0.5f, spacing = 0.05f, streamline = 0.2f, flow = 0.25f,
            pressureSize = 0.2f, pressureFlow = 0.8f,
        ),
        BrushPreset(
            id = "fine_airbrush", name = "Fine Airbrush", category = C.AIRBRUSHING,
            defaultSize = 12f, maxSize = 80f,
            tip = T.GAUSSIAN, spacing = 0.05f, streamline = 0.3f, flow = 0.35f,
            pressureSize = 0.4f, pressureFlow = 0.7f,
        ),
        BrushPreset(
            id = "flat_airbrush", name = "Flat Airbrush", category = C.AIRBRUSHING,
            defaultSize = 80f, maxSize = 500f, defaultOpacity = 0.9f,
            tip = T.ROUND, hardness = 0.97f, spacing = 0.05f, streamline = 0.2f, flow = 0.2f,
            pressureSize = 0f, pressureFlow = 0.8f,
        ),
        spray("speckle_airbrush", "Speckle Airbrush", C.AIRBRUSHING, dot = 3f, spread = 5f, count = 8, opacity = 0.8f),

        // ---- Spray Paint ----
        spray("spray_can", "Spray Can", C.SPRAY, dot = 4f, spread = 4.5f, count = 14),
        spray("fat_cap", "Fat Cap", C.SPRAY, dot = 7f, spread = 4f, count = 16),
        spray("fine_mist", "Fine Mist", C.SPRAY, dot = 2f, spread = 8f, count = 16, opacity = 0.5f, hardness = 0.3f),
        spray("dirty_spray", "Dirty Spray", C.SPRAY, dot = 5f, spread = 4f, count = 10, grain = G.CHALK),
        spray("toothbrush", "Toothbrush Flick", C.SPRAY, dot = 3f, spread = 6f, count = 4, spacing = 2f, hardness = 0.9f),
        BrushPreset(
            id = "graffiti_line", name = "Graffiti Line", category = C.SPRAY,
            defaultSize = 18f, maxSize = 120f,
            tip = T.GAUSSIAN, spacing = 0.04f, streamline = 0.3f, flow = 0.6f, dust = 0.5f,
            pressureSize = 0.3f, pressureFlow = 0.4f,
        ),
        stamp("splatter_spray", "Paint Splats", C.SPRAY, T.SPLATTER, 50f, spacing = 0.9f, sizeJitter = 0.7f,
            scatter = 1.2f, count = 2, opacityJitter = 0.3f),

        // ---- Textures ----
        stamp("stipple_brush", "Stipple Brush", C.TEXTURES, T.STIPPLE, 60f, spacing = 0.45f, sizeJitter = 0.2f, scatter = 0.2f),
        BrushPreset(
            id = "rough_texture", name = "Rough Texture", category = C.TEXTURES,
            defaultSize = 80f, maxSize = 400f,
            tip = T.CHARCOAL, spacing = 0.08f, rotationJitter = 30f, streamline = 0.1f,
            grain = G.STATIC, grainScale = 1.5f, grainDepth = 0.6f, pressureSize = 0.3f,
        ),
        BrushPreset(
            id = "canvas_dry", name = "Dry Canvas", category = C.TEXTURES,
            defaultSize = 90f, maxSize = 400f,
            tip = T.ROUND, hardness = 0.6f, spacing = 0.05f, streamline = 0.15f,
            grain = G.CANVAS, grainScale = 1.4f, grainDepth = 1f, tooth = 1f, pressureSize = 0.2f,
        ),
        BrushPreset(
            id = "crackle_texture", name = "Crackle Glaze", category = C.TEXTURES,
            defaultSize = 160f, maxSize = 600f, defaultOpacity = 0.7f,
            tip = T.GAUSSIAN, spacing = 0.06f, streamline = 0.2f, flow = 0.6f,
            grain = G.CRACKLE, grainScale = 1.2f, grainDepth = 1f, grainInvert = true, pressureSize = 0.2f,
        ),
        spray("grit", "Grit", C.TEXTURES, dot = 2.5f, spread = 6f, count = 10, hardness = 0.9f, bright = 0.15f),
        BrushPreset(
            id = "burlap", name = "Burlap", category = C.TEXTURES,
            defaultSize = 100f, maxSize = 400f,
            tip = T.ROUND, hardness = 0.7f, spacing = 0.05f, streamline = 0.15f,
            grain = G.TWILL, grainScale = 2f, grainDepth = 0.95f, tooth = 0.6f, pressureSize = 0.2f,
        ),
        stamp("speckles", "Speckles", C.TEXTURES, T.SPLATTER, 40f, spacing = 0.7f, scatter = 0.6f, sizeJitter = 0.6f),
        BrushPreset(
            id = "concrete", name = "Concrete", category = C.TEXTURES,
            defaultSize = 120f, maxSize = 500f, defaultOpacity = 0.8f,
            tip = T.CHARCOAL, spacing = 0.1f, rotationJitter = 180f, streamline = 0.05f,
            grain = G.CHALK, grainScale = 0.7f, grainDepth = 0.9f, tooth = 0.4f, dust = 0.4f, pressureSize = 0.3f,
        ),

        // ---- Patterns ----
        pattern(G.HALFTONE, "Halftone"),
        pattern(G.POLKA, "Polka Dots"),
        pattern(G.STRIPES, "Stripes"),
        pattern(G.CHECKER, "Checkerboard"),
        pattern(G.CROSSHATCH, "Crosshatch"),
        pattern(G.BRICK, "Brick Wall"),
        pattern(G.HONEYCOMB, "Honeycomb"),
        pattern(G.SCALES, "Scales"),
        pattern(G.WAVES, "Waves"),
        pattern(G.CHEVRON, "Chevron"),
        pattern(G.PLAID, "Plaid"),
        pattern(G.TWILL, "Denim"),
        pattern(G.WOOD, "Wood Grain", scale = 3f),
        pattern(G.MARBLE, "Marble Veins"),
        pattern(G.CRACKLE, "Crackle"),
        pattern(G.LEOPARD, "Leopard"),
        pattern(G.STARFIELD, "Little Stars"),
        pattern(G.STATIC, "TV Static"),

        // ---- Stamps ----
        stamp("stamp_stars", "Stars", C.STAMPS, T.STAR, 40f, spacing = 1.4f, rotation = 40f),
        stamp("stamp_star_scatter", "Star Scatter", C.STAMPS, T.STAR, 24f, spacing = 1f, scatter = 1.6f, count = 2, sizeJitter = 0.7f, bright = 0.2f),
        stamp("stamp_sparkle", "Sparkles", C.STAMPS, T.SPARKLE, 40f, spacing = 1.6f, rotation = 20f, sizeJitter = 0.6f),
        stamp("stamp_hearts", "Hearts", C.STAMPS, T.HEART, 36f, spacing = 1.3f, rotation = 30f),
        stamp("stamp_confetti", "Confetti", C.STAMPS, T.SQUARE, 12f, spacing = 1f, scatter = 2.5f, count = 3, hue = 1f, sat = 0.2f,
            roundness = 0.5f, roundnessJitter = 0.5f),
        stamp("stamp_triangles", "Triangles", C.STAMPS, T.TRIANGLE, 30f, spacing = 1.2f, scatter = 0.8f, sizeJitter = 0.6f),
        stamp("stamp_rings", "Rings", C.STAMPS, T.RING, 34f, spacing = 1.2f, scatter = 0.6f, sizeJitter = 0.6f),
        stamp("stamp_crosses", "Crosses", C.STAMPS, T.CROSS, 20f, spacing = 1.4f, rotation = 45f, sizeJitter = 0.3f),
        stamp("stamp_moons", "Moons", C.STAMPS, T.MOON, 36f, spacing = 1.5f, rotation = 40f),
        stamp("stamp_dots", "Dot Trail", C.STAMPS, T.ROUND, 20f, spacing = 1.6f, rotation = 0f, sizeJitter = 0f, hardness = 1f, pressureSize = 0.7f),
        stamp("stamp_flowers", "Flowers", C.STAMPS, T.FLOWER, 34f, spacing = 1.3f, sizeJitter = 0.4f, hue = 0.08f, bright = 0.1f),
        stamp("stamp_squares", "Squares", C.STAMPS, T.SQUARE, 26f, spacing = 1.3f, rotation = 0f, sizeJitter = 0.2f, hardness = 1f),
        stamp("stamp_geometric", "Geometric Mix", C.STAMPS, T.TRIANGLE, 30f, spacing = 1f, scatter = 1.2f, sizeJitter = 0.8f, hue = 0.4f),
        // Rows of dots: the tip is a line of dots across the stroke, stamped every dot pitch.
        dots("dot_rows_2", "Double Dot Row", T.DOT_ROWS_2, 40f, 0.85f),
        dots("dot_rows_3", "Dot Rows", T.DOT_ROWS_3, 50f, 0.43f),
        dots("dot_rows_5", "Dot Band", T.DOT_ROWS_5, 70f, 0.21f),
        dots("dot_stagger", "Packed Dots", T.DOT_STAGGER, 70f, 0.2f),
        dots("dot_fade", "Fading Dots", T.DOT_FADE, 70f, 0.2f),
        dots("rainbow_dots", "Rainbow Dots", T.DOT_ROWS_5, 70f, 0.32f, hue = 1f),
        dots("square_rows", "Square Rows", T.SQUARE_ROWS, 50f, 0.3f),
        stamp("dot_cloud", "Dot Cloud", C.STAMPS, T.ROUND, 10f, spacing = 0.8f, scatter = 2.5f, count = 4,
            rotation = 0f, sizeJitter = 0f, hardness = 1f, flip = 0f, pressureSize = 0.3f),
        stamp("dot_confetti", "Confetti Dots", C.STAMPS, T.ROUND, 12f, spacing = 0.8f, scatter = 2.5f, count = 3,
            rotation = 0f, sizeJitter = 0.6f, hardness = 1f, flip = 0f, hue = 1f),
        stamp("stamp_diamonds", "Diamonds", C.STAMPS, T.DIAMOND, 30f, spacing = 1.3f, rotation = 0f, sizeJitter = 0.25f, flip = 0f),
        stamp("stamp_pentagons", "Pentagons", C.STAMPS, T.PENTAGON, 30f, spacing = 1.3f, rotation = 36f, sizeJitter = 0.3f),
        stamp("stamp_hexagons", "Hexagons", C.STAMPS, T.HEXAGON, 30f, spacing = 1.15f, rotation = 0f, sizeJitter = 0f, flip = 0f, hardness = 1f),
        stamp("stamp_drops", "Raindrops", C.STAMPS, T.DROP, 26f, spacing = 1.2f, rotation = 15f, sizeJitter = 0.4f, scatter = 0.6f),
        stamp("stamp_targets", "Targets", C.STAMPS, T.TARGET, 40f, spacing = 1.3f, rotation = 0f, sizeJitter = 0.3f, flip = 0f),
        stamp("stamp_spirals", "Spirals", C.STAMPS, T.SPIRAL, 40f, spacing = 1.3f, sizeJitter = 0.3f),
        stamp("stamp_halftone", "Halftone Blob", C.STAMPS, T.DOTS, 80f, spacing = 0.8f, rotation = 0f, sizeJitter = 0.1f, hardness = 1f),

        // ---- Nature ----
        stamp("grass", "Grass", C.NATURE, T.GRASS, 70f, spacing = 0.35f, rotation = 12f, sizeJitter = 0.3f,
            hue = 0.04f, bright = 0.12f, flip = 1f),
        stamp("tall_grass", "Tall Grass", C.NATURE, T.GRASS, 130f, spacing = 0.25f, rotation = 8f, sizeJitter = 0.35f,
            hue = 0.03f, bright = 0.15f, roundness = 0.6f),
        stamp("foliage", "Foliage", C.NATURE, T.FOLIAGE, 70f, spacing = 0.4f, scatter = 0.4f, sizeJitter = 0.3f,
            hue = 0.05f, bright = 0.15f),
        stamp("tree_canopy", "Tree Canopy", C.NATURE, T.FOLIAGE, 150f, spacing = 0.35f, scatter = 0.5f, count = 2,
            sizeJitter = 0.4f, hue = 0.04f, bright = 0.2f),
        stamp("leaves", "Leaves", C.NATURE, T.LEAF, 40f, spacing = 1f, scatter = 0.6f, sizeJitter = 0.3f, hue = 0.05f, bright = 0.15f),
        stamp("petals", "Falling Petals", C.NATURE, T.LEAF, 28f, spacing = 1.6f, scatter = 2f, sizeJitter = 0.5f,
            hue = 0.03f, bright = 0.1f, roundness = 0.8f, roundnessJitter = 0.5f),
        stamp("wildflowers", "Wildflowers", C.NATURE, T.FLOWER, 22f, spacing = 1f, scatter = 1.2f, count = 2, sizeJitter = 0.6f,
            hue = 0.15f, bright = 0.1f),
        stamp("moss", "Moss", C.NATURE, T.STIPPLE, 40f, spacing = 0.3f, sizeJitter = 0.3f, hue = 0.05f, bright = 0.2f),
        stamp("pebbles", "Pebbles", C.NATURE, T.ROUND, 22f, spacing = 1f, scatter = 1f, sizeJitter = 0.6f, bright = 0.25f,
            hardness = 0.75f, roundness = 0.65f, roundnessJitter = 0.4f),
        BrushPreset(
            id = "bark", name = "Tree Bark", category = C.NATURE,
            defaultSize = 50f, maxSize = 300f,
            tip = T.RAKE, tipAngle = A.FOLLOW_STROKE, spacing = 0.03f, lateralJitter = 0.03f, streamline = 0.2f,
            grain = G.WOOD, grainScale = 1.2f, grainDepth = 0.5f, brightnessJitter = 0.08f, pressureSize = 0.5f,
        ),
        BrushPreset(
            id = "water_reflection", name = "Water Reflections", category = C.NATURE,
            defaultSize = 50f, maxSize = 300f, defaultOpacity = 0.7f,
            tip = T.ROUND, hardness = 0.8f, roundness = 0.12f, tipAngle = A.FIXED, spacing = 0.6f,
            scatter = 0.5f, sizeJitter = 0.6f, streamline = 0.1f, buildUp = true, brightnessJitter = 0.15f,
            pressureSize = 0.4f,
        ),

        // ---- Hair & Fur ----
        BrushPreset(
            id = "fine_hair", name = "Fine Hair", category = C.HAIR,
            defaultSize = 30f, maxSize = 200f, defaultOpacity = 0.9f,
            tip = T.HAIR, tipAngle = A.FOLLOW_STROKE, spacing = 0.02f, streamline = 0.4f,
            taperStart = 2f, taperEnd = 4f, taperMinSize = 0.2f, pressureSize = 0.6f,
        ),
        BrushPreset(
            id = "fur", name = "Fur", category = C.HAIR,
            defaultSize = 26f, maxSize = 160f,
            tip = T.HAIR, tipAngle = A.FOLLOW_STROKE, spacing = 0.03f, rotationJitter = 6f, lateralJitter = 0.06f,
            streamline = 0.15f, taperStart = 1f, taperEnd = 3f, taperMinSize = 0.1f, brightnessJitter = 0.1f,
            pressureSize = 0.7f,
        ),
        BrushPreset(
            id = "thick_hair", name = "Thick Hair", category = C.HAIR,
            defaultSize = 70f, maxSize = 300f,
            tip = T.HAIR, tipAngle = A.FOLLOW_STROKE, spacing = 0.015f, streamline = 0.35f,
            taperStart = 1.5f, taperEnd = 3f, taperMinSize = 0.3f, pressureSize = 0.5f,
        ),
        BrushPreset(
            id = "rake_brush", name = "Rake", category = C.HAIR,
            defaultSize = 40f, maxSize = 200f,
            tip = T.RAKE, tipAngle = A.FOLLOW_STROKE, spacing = 0.03f, streamline = 0.25f,
            taperEnd = 2f, taperMinSize = 0.4f, pressureSize = 0.4f,
        ),
        BrushPreset(
            id = "whiskers", name = "Whiskers", category = C.HAIR,
            defaultSize = 3f, maxSize = 20f,
            tip = T.ROUND, hardness = 0.97f, spacing = 0.04f, streamline = 0.5f,
            taperStart = 4f, taperEnd = 14f, taperMinSize = 0.02f, pressureSize = 1f,
        ),

        // ---- Elements ----
        stamp("clouds", "Clouds", C.ELEMENTS, T.CLOUD, 160f, spacing = 0.35f, rotation = 15f, sizeJitter = 0.3f,
            scatter = 0.3f, opacity = 0.6f, hardness = 0.55f, flow = 0.6f),
        stamp("soft_clouds", "Soft Clouds", C.ELEMENTS, T.CLOUD, 200f, spacing = 0.3f, rotation = 20f, sizeJitter = 0.3f,
            scatter = 0.3f, opacity = 0.35f, hardness = 0.1f, flow = 0.5f),
        stamp("smoke", "Smoke", C.ELEMENTS, T.CLOUD, 120f, spacing = 0.3f, scatter = 0.3f, opacity = 0.15f, hardness = 0f, flow = 0.6f),
        stamp("rain", "Rain", C.ELEMENTS, T.ROUND, 34f, spacing = 0.8f, rotation = 3f, sizeJitter = 0.5f, scatter = 3f, count = 3,
            opacity = 0.6f, hardness = 0.7f, roundness = 0.07f, angleDegrees = 80f, flip = 0f, opacityJitter = 0.4f),
        spray("snow", "Snow", C.ELEMENTS, dot = 7f, spread = 4f, count = 3, hardness = 0.35f, spacing = 1.5f),
        stamp("snowflakes", "Snowflakes", C.ELEMENTS, T.SNOWFLAKE, 30f, spacing = 1.2f, scatter = 2f, rotation = 60f, sizeJitter = 0.6f),
        stamp("fire", "Fire", C.ELEMENTS, T.LEAF, 50f, spacing = 0.3f, rotation = 14f, sizeJitter = 0.5f, scatter = 0.35f,
            hue = 0.08f, bright = 0.1f, opacity = 0.5f, hardness = 0.4f, blend = B.ADD),
        stamp("ripples", "Water Ripples", C.ELEMENTS, T.RING, 70f, spacing = 1.2f, rotation = 0f, sizeJitter = 0.5f,
            opacity = 0.6f, roundness = 0.35f, flip = 0f),
        stamp("bubbles", "Bubbles", C.ELEMENTS, T.BUBBLE, 40f, spacing = 1.2f, scatter = 1.5f, sizeJitter = 0.8f, rotation = 0f, flip = 0f),
        stamp("starry_sky", "Starry Sky", C.ELEMENTS, T.SPARKLE, 10f, spacing = 1f, scatter = 4f, count = 3, sizeJitter = 0.9f,
            rotation = 10f, bright = 0.3f, blend = B.SCREEN),

        // ---- Light & Glow ----
        BrushPreset(
            id = "glow", name = "Glow", category = C.LIGHT,
            defaultSize = 90f, maxSize = 600f, defaultOpacity = 0.75f,
            tip = T.GAUSSIAN, spacing = 0.05f, streamline = 0.3f, flow = 0.5f, blendMode = B.ADD,
            pressureSize = 0.3f, pressureFlow = 0.7f,
        ),
        BrushPreset(
            id = "neon", name = "Neon Tube", category = C.LIGHT,
            defaultSize = 26f, maxSize = 200f,
            tip = T.GAUSSIAN, spacing = 0.03f, streamline = 0.6f, flow = 1f, blendMode = B.ADD, pressureSize = 0.2f,
        ),
        BrushPreset(
            id = "light_pen", name = "Light Pen", category = C.LIGHT,
            defaultSize = 7f, maxSize = 60f,
            tip = T.ROUND, hardness = 0.75f, spacing = 0.04f, streamline = 0.5f, blendMode = B.ADD,
            taperStart = 2f, taperEnd = 4f, taperMinSize = 0.1f, pressureSize = 0.8f,
        ),
        stamp("bokeh", "Bokeh", C.LIGHT, T.ROUND, 60f, spacing = 1.5f, scatter = 2f, sizeJitter = 0.7f, rotation = 0f,
            opacity = 0.5f, hardness = 0.85f, blend = B.SCREEN, hue = 0.1f, bright = 0.2f, opacityJitter = 0.5f),
        stamp("glint", "Glints", C.LIGHT, T.SPARKLE, 40f, spacing = 2f, sizeJitter = 0.7f, rotation = 20f, blend = B.ADD),
        stamp("glitter", "Glitter", C.LIGHT, T.SPARKLE, 14f, spacing = 0.6f, scatter = 2.5f, count = 4, sizeJitter = 0.9f,
            bright = 0.5f, hue = 0.06f, rotation = 45f, blend = B.ADD),
        BrushPreset(
            id = "shine", name = "Shine", category = C.LIGHT,
            defaultSize = 120f, maxSize = 600f, defaultOpacity = 0.35f,
            tip = T.GAUSSIAN, spacing = 0.05f, streamline = 0.3f, flow = 0.3f, blendMode = B.COLOR_DODGE,
            pressureSize = 0.3f, pressureFlow = 0.7f,
        ),
        spray("fairy_dust", "Fairy Dust", C.LIGHT, dot = 4f, spread = 4f, count = 4, hardness = 0.5f, hue = 0.2f,
            bright = 0.2f, blend = B.ADD, spacing = 0.5f),

        // ---- Retro & Comic ----
        BrushPreset(
            id = "halftone_shader", name = "Halftone Shader", category = C.RETRO,
            defaultSize = 200f, maxSize = 800f,
            tip = T.GAUSSIAN, spacing = 0.05f, streamline = 0.2f, flow = 1f,
            grain = G.DOT_SCREEN, grainScale = 1.5f, grainDepth = 0.5f, tooth = 1f,
            pressureSize = 0.2f,
        ),
        BrushPreset(
            id = "crosshatch_shader", name = "Hatch Shader", category = C.RETRO,
            defaultSize = 160f, maxSize = 600f,
            tip = T.GAUSSIAN, spacing = 0.05f, streamline = 0.2f, flow = 0.7f,
            grain = G.CROSSHATCH, grainScale = 1.2f, grainDepth = 1f, pressureSize = 0.2f, pressureFlow = 0.6f,
        ),
        stamp("pop_dots", "Pop Dots", C.RETRO, T.ROUND, 30f, spacing = 1.3f, rotation = 0f, sizeJitter = 0f, hardness = 1f, pressureSize = 0.5f),
        BrushPreset(
            id = "risograph", name = "Risograph", category = C.RETRO,
            defaultSize = 50f, maxSize = 300f, defaultOpacity = 0.85f,
            tip = T.ROUND, hardness = 0.6f, spacing = 0.05f, streamline = 0.2f, blendMode = B.MULTIPLY,
            grain = G.STATIC, grainScale = 1f, grainDepth = 0.55f, pressureSize = 0.4f,
        ),
        BrushPreset(
            id = "pixel_pen", name = "Pixel Pen", category = C.RETRO,
            defaultSize = 8f, minSize = 1f, maxSize = 64f,
            tip = T.SQUARE, hardness = 1f, tipAngle = A.FIXED, spacing = 0.3f, streamline = 0f, pressureSize = 0f,
        ),
        BrushPreset(
            id = "retro_grain", name = "Retro Grain", category = C.RETRO,
            defaultSize = 40f, maxSize = 300f,
            tip = T.ROUND, hardness = 0.8f, spacing = 0.05f, streamline = 0.2f,
            grain = G.STATIC, grainScale = 1f, grainDepth = 0.35f, pressureSize = 0.5f,
        ),

        // ---- Abstract ----
        BrushPreset(
            id = "colour_noise", name = "Colour Confetti Line", category = C.ABSTRACT,
            defaultSize = 30f, maxSize = 200f,
            tip = T.ROUND, hardness = 0.8f, spacing = 0.35f, sizeJitter = 0.3f, streamline = 0.2f, buildUp = true,
            hueJitter = 0.6f, saturationJitter = 0.2f, brightnessJitter = 0.2f, pressureSize = 0.6f,
        ),
        BrushPreset(
            id = "surprise_colour", name = "Surprise Colour", category = C.ABSTRACT,
            defaultSize = 24f, maxSize = 200f,
            tip = T.ROUND, hardness = 0.6f, spacing = 0.04f, streamline = 0.4f,
            hueJitter = 1f, saturationJitter = 0.3f, colourJitterPerStroke = true,
            pressureSize = 0.3f, taperStart = 3f, taperEnd = 4f,
        ),
        BrushPreset(
            id = "ribbon", name = "Ribbon", category = C.ABSTRACT,
            defaultSize = 40f, maxSize = 300f,
            tip = T.SQUARE, hardness = 1f, roundness = 0.08f, tipAngle = A.STYLUS, spacing = 0.02f, streamline = 0.6f,
            pressureSize = 0.2f,
        ),
        BrushPreset(
            id = "scribble", name = "Scribble", category = C.ABSTRACT,
            defaultSize = 3f, maxSize = 30f,
            tip = T.ROUND, hardness = 0.9f, spacing = 0.06f, lateralJitter = 1.2f, streamline = 0f, pressureSize = 0.3f,
        ),
        stamp("cells", "Cells", C.ABSTRACT, T.RING, 40f, spacing = 1f, scatter = 1f, count = 2, sizeJitter = 0.8f, hue = 0.1f),
        stamp("shards", "Shards", C.ABSTRACT, T.TRIANGLE, 40f, spacing = 0.8f, scatter = 1f, sizeJitter = 0.7f,
            roundness = 0.4f, roundnessJitter = 0.6f, bright = 0.25f),
        stamp("ink_blots", "Ink Blots", C.ABSTRACT, T.SPLATTER, 90f, spacing = 1.6f, sizeJitter = 0.5f, scatter = 0.5f, flip = 1f),
    )
}
