package com.artisthaven.app.domain.model

/**
 * Represents a single drawing stroke composed of multiple input points.
 * Each point captures position, pressure, and tilt for realistic input rendering.
 *
 * Inspired by the androidx.ink API design for low-latency stylus input.
 */
data class DrawingStroke(
    val id: String,
    val layerId: String,
    val brushSnapshot: Brush,
    val points: List<StrokePoint>,
)

/**
 * A single point in a drawing stroke.
 * Captures raw input data from MotionEvent for pressure-sensitive rendering.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val timestamp: Long = 0L,
)
