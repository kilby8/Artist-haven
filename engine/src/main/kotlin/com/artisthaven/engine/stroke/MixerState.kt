package com.artisthaven.engine.stroke

/**
 * Paint held by the Mixer Brush between strokes: a reservoir of loaded colour that drains as you
 * paint, and a pickup well that fills with the colours the brush drags through.
 *
 * The model follows PhotoCraft's Mixer Brush (crates/paint/src/mixer.rs), Copyright (c) 2026
 * ArtCraft Team and the PhotoCraft contributors, MIT licence.
 */
class MixerState {
    /** Loaded paint, straight RGB 0..1, or null for a clean brush. */
    var reservoir: FloatArray? = null
    /** How much of the reservoir is left, 0..1. */
    var amount = 0f
    /** Colour picked up from the canvas, straight RGB 0..1, or null for a clean brush. */
    var pickup: FloatArray? = null
    /** How much paint the pickup holds, 0..1: it thins out over bare canvas. */
    var pickupAmount = 0f
    /** The brush colour the reservoir was last loaded with (straight ARGB). */
    var color: Int? = null

    fun load(color: Int) {
        reservoir = floatArrayOf(((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f)
        amount = 1f
        this.color = color
    }

    fun clean() {
        reservoir = null
        amount = 0f
        pickup = null
        pickupAmount = 0f
    }

    /** Choosing a new colour loads the brush with it. */
    fun beginStroke(color: Int) {
        if (this.color != color) load(color)
    }

    /** After a stroke: the automatic Clean empties the brush, then it is reloaded with [color]. */
    fun endStroke(color: Int, clean: Boolean) {
        if (clean) clean()
        load(color)
    }

    fun copy(): MixerState = MixerState().also {
        it.reservoir = reservoir?.copyOf()
        it.amount = amount
        it.pickup = pickup?.copyOf()
        it.pickupAmount = pickupAmount
        it.color = color
    }

    fun restore(from: MixerState) {
        reservoir = from.reservoir?.copyOf()
        amount = from.amount
        pickup = from.pickup?.copyOf()
        pickupAmount = from.pickupAmount
        color = from.color
    }

    companion object {
        /** How far the pickup moves towards the canvas colour per dab at full Wet. */
        const val PICKUP_RATE = 0.25f
    }
}
