package com.artisthaven.drawing

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.artisthaven.drawing.brush.BrushProvider
import com.artisthaven.drawing.canvas.CustomDrawingCanvas
import com.artisthaven.drawing.databinding.ActivityMainBinding
import com.artisthaven.drawing.layer.Layer
import com.artisthaven.drawing.layer.LayerManager

/**
 * Entry-point activity that wires the drawing engine subsystems together.
 *
 * Clean Architecture — Presentation layer (Activity / Coordinator).
 *
 * Responsibilities:
 *  - Inflate the layout containing [CustomDrawingCanvas].
 *  - Configure the initial brush and layer settings.
 *  - Expose simple helper functions that a toolbar / bottom-sheet can call
 *    to change brushes, add layers, and toggle blend modes.
 *
 * The Activity does **not** contain any drawing logic; all rendering is
 * delegated to [CustomDrawingCanvas] and its owned subsystems.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Convenience reference to the drawing canvas. */
    private val canvas: CustomDrawingCanvas get() = binding.drawingCanvas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure defaults.  These can later be driven by a toolbar UI.
        configureDefaultBrush()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Brush helpers (called from toolbar / menu items)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Switches the active brush to [type] and updates the brush colour and size.
     *
     * @param type   GPU brush program to activate.
     * @param r      Red component 0.0 … 1.0.
     * @param g      Green component.
     * @param b      Blue component.
     * @param a      Alpha component.
     * @param radius Tip radius in canvas pixels.
     */
    fun setBrush(
        type: BrushProvider.BrushType,
        r: Float = 0f,
        g: Float = 0f,
        b: Float = 0f,
        a: Float = 1f,
        radius: Float = 18f
    ) {
        canvas.activeBrushType = type
        canvas.brushR = r
        canvas.brushG = g
        canvas.brushB = b
        canvas.brushA = a
        canvas.brushRadius = radius
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Layer helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Adds a new transparent layer above the current active layer.
     */
    fun addLayer() {
        canvas.getLayerManager()?.addLayer()
        Toast.makeText(this, "Layer added", Toast.LENGTH_SHORT).show()
    }

    /**
     * Removes the currently active layer (no-op if only one layer remains).
     */
    fun removeActiveLayer() {
        val lm = canvas.getLayerManager() ?: return
        lm.removeLayer(lm.activeLayerIndex)
        Toast.makeText(this, "Layer removed", Toast.LENGTH_SHORT).show()
    }

    /**
     * Cycles the active layer's blend mode between Normal → Multiply → Screen → Normal.
     */
    fun cycleBlendMode() {
        val lm    = canvas.getLayerManager() ?: return
        val layer = lm.activeLayer ?: return
        layer.blendMode = when (layer.blendMode) {
            Layer.BlendMode.NORMAL   -> Layer.BlendMode.MULTIPLY
            Layer.BlendMode.MULTIPLY -> Layer.BlendMode.SCREEN
            Layer.BlendMode.SCREEN   -> Layer.BlendMode.NORMAL
        }
        Toast.makeText(this, "Blend mode: ${layer.blendMode}", Toast.LENGTH_SHORT).show()
    }

    /**
     * Clears the currently active layer.
     */
    fun clearActiveLayer() {
        canvas.clearActiveLayer()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun configureDefaultBrush() {
        setBrush(
            type   = BrushProvider.BrushType.ROUND,
            r      = 0f,
            g      = 0f,
            b      = 0f,
            a      = 1f,
            radius = 16f
        )
    }
}
