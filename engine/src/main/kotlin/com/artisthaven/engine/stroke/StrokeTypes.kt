package com.artisthaven.engine.stroke

import com.artisthaven.engine.brush.BrushPreset

/** One input sample in document coordinates. */
data class InputPoint(
    val x: Float,
    val y: Float,
    /** 0..1. Finger input reports 1. */
    val pressure: Float = 1f,
    /** Pen tilt in radians: 0 is upright, PI/2 is flat on the glass. */
    val tilt: Float = 0f,
    /** Direction the pen leans, in radians. */
    val azimuth: Float = 0f,
    /** Milliseconds. */
    val time: Long = 0L,
)

enum class Tool { BRUSH, ERASER, SMUDGE }

enum class Symmetry(val displayName: String) {
    NONE("Off"),
    VERTICAL("Vertical"),
    HORIZONTAL("Horizontal"),
    QUADRANT("Quadrant"),
}

/** Everything that defines how one stroke is painted. */
data class StrokeParams(
    val brush: BrushPreset,
    val tool: Tool = Tool.BRUSH,
    /** Diameter in document pixels. */
    val size: Float = brush.defaultSize,
    /** 0..1, the sidebar opacity slider. */
    val opacity: Float = brush.defaultOpacity,
    /** Straight ARGB. */
    val color: Int = 0xFF000000.toInt(),
    val symmetry: Symmetry = Symmetry.NONE,
    /** True for stylus strokes: pressure and tilt dynamics apply. */
    val usePressure: Boolean = false,
    /** Extra stabilisation on top of the brush's own StreamLine, 0..1. */
    val extraStreamline: Float = 0f,
    val seed: Int = 1,
)
