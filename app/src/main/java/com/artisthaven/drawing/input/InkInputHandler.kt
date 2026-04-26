package com.artisthaven.drawing.input

import android.graphics.Matrix
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.input.motionevents.MotionEventExt
import com.artisthaven.drawing.canvas.StrokePoint

/**
 * Converts raw [MotionEvent]s from the Android input system into validated,
 * high-fidelity [StrokePoint] sequences ready for the drawing engine.
 *
 * Clean Architecture — Interface Adapter layer.
 *
 * Key responsibilities:
 *  1. **Historical sampling** — extracts all batched historical samples from
 *     a [MotionEvent] so 120 Hz stylus data is not decimated to the display
 *     frame rate.
 *  2. **Palm rejection** — discards touch events when the contact area
 *     exceeds [PALM_TOOL_MINOR_THRESHOLD], indicating a palm rather than a
 *     stylus tip or finger.
 *  3. **Pressure-sensitive stroke smoothing** — applies an exponential
 *     moving average to position and pressure so abrupt sensor jitter does
 *     not produce jagged strokes.
 *  4. **View-to-canvas coordinate transform** — maps view-space event
 *     coordinates to canvas-space using an injected [Matrix].
 *
 * Usage:
 * ```kotlin
 * val handler = InkInputHandler(viewToCanvasMatrix)
 * override fun onTouchEvent(event: MotionEvent): Boolean {
 *     val points = handler.process(event)
 *     points.forEach { layerManager.activeLayer?.canvas?.drawPoint(it) }
 *     return true
 * }
 * ```
 *
 * @param viewToCanvas Affine [Matrix] mapping view pixel coordinates to canvas
 *                     pixel coordinates.  Pass [Matrix.IDENTITY_MATRIX] when the
 *                     view and canvas share the same coordinate space.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class InkInputHandler(private val viewToCanvas: Matrix = Matrix()) {

    companion object {
        /**
         * Maximum [MotionEvent.AXIS_TOOL_MINOR] value (pixels) accepted as a stylus
         * or fingertip.  Events with a larger minor axis are classified as palm contacts
         * and rejected.  Tune this constant for the target device.
         */
        private const val PALM_TOOL_MINOR_THRESHOLD = 60f

        /**
         * Exponential moving average (EMA) smoothing factor α.
         * α = 1.0 → no smoothing (raw samples).
         * α = 0.0 → fully smoothed (never changes).
         * 0.6 provides good responsiveness while removing sensor jitter.
         */
        private const val SMOOTHING_ALPHA = 0.6f
    }

    // EMA state — reset on each ACTION_DOWN.
    private var smoothX: Float = 0f
    private var smoothY: Float = 0f
    private var smoothPressure: Float = 0f
    private var isFirstPoint: Boolean = true

    /** Reusable float array for [Matrix.mapPoints]. */
    private val mappedCoord = FloatArray(2)

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Processes a single [MotionEvent] and returns the validated, smoothed
     * stroke points in canvas coordinates.
     *
     * Returns an empty list if:
     *  - The event is a palm contact (tool minor axis exceeds threshold).
     *  - The pointer tool type is [MotionEvent.TOOL_TYPE_PALM].
     *  - The action is neither MOVE nor DOWN nor UP.
     *
     * @param event The raw [MotionEvent] from [android.view.View.onTouchEvent].
     * @return List of [StrokePoint]s in canvas-coordinate space, ordered oldest → newest.
     */
    fun process(event: MotionEvent): List<StrokePoint> {
        // ── Palm rejection ────────────────────────────────────────────────────
        if (isPalmContact(event)) return emptyList()

        val result = mutableListOf<StrokePoint>()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isFirstPoint = true
                processSample(event, event.x, event.y, event.pressure, result)
            }

            MotionEvent.ACTION_MOVE -> {
                // Extract all historical samples first — these represent the
                // high-frequency (120 Hz) data batched since the last event.
                val historicalPoints = MotionEventExt.getHistoricalData(event)
                for (historical in historicalPoints) {
                    val hX = historical.getAxisValue(MotionEvent.AXIS_X)
                    val hY = historical.getAxisValue(MotionEvent.AXIS_Y)
                    val hP = historical.getAxisValue(MotionEvent.AXIS_PRESSURE)
                        .coerceIn(0f, 1f)
                    processSample(event, hX, hY, hP, result)
                }
                // Current (latest) sample.
                processSample(event, event.x, event.y, event.pressure, result)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                processSample(event, event.x, event.y, event.pressure, result)
                isFirstPoint = true
            }

            else -> { /* Ignore pointer-down/up for secondary fingers, etc. */ }
        }

        return result
    }

    /**
     * Resets smoothing state.  Call this when the active drawing tool or layer changes
     * to prevent residual EMA state bleeding into the next stroke.
     */
    fun reset() {
        isFirstPoint = true
        smoothX = 0f
        smoothY = 0f
        smoothPressure = 0f
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns `true` if [event] should be rejected as a palm contact.
     *
     * Detection heuristics (applied in order — short-circuit on first match):
     *  1. Tool type is explicitly [MotionEvent.TOOL_TYPE_PALM].
     *  2. AXIS_TOOL_MINOR (contact width) exceeds [PALM_TOOL_MINOR_THRESHOLD].
     */
    private fun isPalmContact(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        if (toolType == MotionEvent.TOOL_TYPE_PALM) return true

        val toolMinor = event.getAxisValue(MotionEvent.AXIS_TOOL_MINOR)
        if (toolMinor > PALM_TOOL_MINOR_THRESHOLD && toolMinor > 0f) return true

        return false
    }

    /**
     * Applies EMA smoothing to (rawX, rawY, rawPressure), maps the result to
     * canvas space, and appends the resulting [StrokePoint] to [out].
     */
    private fun processSample(
        event: MotionEvent,
        rawX: Float,
        rawY: Float,
        rawPressure: Float,
        out: MutableList<StrokePoint>
    ) {
        if (isFirstPoint) {
            // Bootstrap EMA with the first raw value to avoid a jump from (0,0).
            smoothX = rawX
            smoothY = rawY
            smoothPressure = rawPressure.coerceIn(0f, 1f)
            isFirstPoint = false
        } else {
            smoothX = SMOOTHING_ALPHA * rawX + (1f - SMOOTHING_ALPHA) * smoothX
            smoothY = SMOOTHING_ALPHA * rawY + (1f - SMOOTHING_ALPHA) * smoothY
            smoothPressure = SMOOTHING_ALPHA * rawPressure.coerceIn(0f, 1f) +
                (1f - SMOOTHING_ALPHA) * smoothPressure
        }

        // Map from view space → canvas space.
        mappedCoord[0] = smoothX
        mappedCoord[1] = smoothY
        viewToCanvas.mapPoints(mappedCoord)

        out.add(
            StrokePoint(
                x = mappedCoord[0],
                y = mappedCoord[1],
                pressure = smoothPressure,
                tilt = event.getAxisValue(MotionEvent.AXIS_TILT),
                orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION),
                eventTime = event.eventTime
            )
        )
    }
}
