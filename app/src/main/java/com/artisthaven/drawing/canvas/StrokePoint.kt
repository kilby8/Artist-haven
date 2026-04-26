package com.artisthaven.drawing.canvas

/**
 * A single validated, smoothed sample point along a drawing stroke.
 *
 * [StrokePoint] is a pure value object (no platform dependencies) and forms
 * the shared language between the input layer ([com.artisthaven.drawing.input])
 * and the rendering layer ([com.artisthaven.drawing.canvas]).
 *
 * Clean Architecture — Domain Entity.
 *
 * @param x            Canvas-space X coordinate in pixels.
 * @param y            Canvas-space Y coordinate in pixels.
 * @param pressure     Normalised stylus pressure in the range [0.0, 1.0].
 *                     1.0 = maximum pressure; 0.0 = stylus hovering.
 * @param tilt         Stylus tilt angle in radians, where 0 = perpendicular to screen
 *                     and π/2 = lying flat.  0 for finger / mouse input.
 * @param orientation  Stylus orientation (rotation around the Z axis) in radians.
 *                     0 for finger / mouse input.
 * @param eventTime    [android.os.SystemClock.uptimeMillis] timestamp of the
 *                     original [android.view.MotionEvent], used for velocity
 *                     calculations and stroke smoothing.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
    val eventTime: Long = 0L
)
