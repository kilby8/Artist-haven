package com.artisthaven.app.canvas

import android.graphics.Bitmap
import com.artisthaven.engine.Editor
import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.brush.RenderMode
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Live stroke previews for the brush library, painted by the real engine so what you see in the
 * list is exactly what the brush does. Rendered once in the background and cached.
 */
object BrushPreviews {
    private const val W = 300
    private const val H = 72

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _previews = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val previews: StateFlow<Map<String, Bitmap>> = _previews.asStateFlow()
    private var started = false

    fun ensureRendered() {
        if (started) return
        started = true
        scope.launch {
            for (brush in BrushLibrary.all) {
                val bmp = render(brush)
                _previews.update { it + (brush.id to bmp) }
            }
        }
    }

    /** Re-renders one brush's preview, after it was made or changed in the Brush Studio. */
    fun refresh(brush: BrushPreset) {
        scope.launch {
            val bmp = render(brush)
            _previews.update { it + (brush.id to bmp) }
        }
    }

    fun render(brush: BrushPreset, color: Int = 0xFFF2F2F2.toInt()): Bitmap {
        val doc = Document.blank(W, H, paperColor = 0xFF2B2B31.toInt())
        val editor = Editor(doc)
        if (brush.renderMode == RenderMode.WET) {
            stroke(editor, BrushLibrary.byId("hard_airbrush"), 0xFF8E7DBE.toInt(), 22f, line(W * 0.55f, 14f, W * 0.55f, H - 14f))
        }
        val size = brush.defaultSize.coerceIn(5f, 30f)
        stroke(editor, brush, color, size, curve())
        val px = Compositor.flatten(doc)
        return Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888)
    }

    private fun stroke(editor: Editor, brush: BrushPreset, color: Int, size: Float, points: List<InputPoint>) {
        editor.beginStroke(StrokeParams(brush, color = color, size = size, usePressure = true), points.first())
        points.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    private fun curve(): List<InputPoint> = (0..80).map { i ->
        val t = i / 80f
        InputPoint(
            x = 20f + (W - 40f) * t,
            y = H / 2f + sin(t * 2 * PI).toFloat() * (H / 4.2f),
            pressure = (0.2f + 0.8f * sin(t * PI).toFloat()),
            tilt = 0.3f,
            time = i * 8L,
        )
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
        (0..20).map { i -> InputPoint(x0 + (x1 - x0) * i / 20f, y0 + (y1 - y0) * i / 20f, time = i * 8L) }
}
