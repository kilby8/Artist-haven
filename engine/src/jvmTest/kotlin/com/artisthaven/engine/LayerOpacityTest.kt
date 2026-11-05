package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerOpacityTest {
    private fun draw(editor: Editor, y: Float, color: Int, x1: Float = 180f) {
        val points = TestSupport.line(20f, y, x1, y)
        editor.beginStroke(StrokeParams(BrushLibrary.byId("hard_airbrush"), color = color, size = 30f), points.first())
        points.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    private fun px(doc: Document, x: Int, y: Int) = Compositor.flatten(doc)[y * doc.width + x]

    @Test
    fun opacityFadesTheLayerAndUndoes() {
        val doc = Document.blank(200, 100, paperColor = 0xFFFFFFFF.toInt())
        val editor = Editor(doc)
        draw(editor, 50f, 0xFF000000.toInt())
        val id = doc.activeLayerId
        editor.setLayerProps(id) { it.copy(opacity = 0.5f) }
        val grey = Pixels.red(px(doc, 100, 50))
        assertTrue("half opacity black on white is mid grey, was $grey", grey in 120..135)
        editor.undo()
        assertEquals(0, Pixels.red(px(doc, 100, 50)))
        // A slider drag is one undo step.
        val before = doc.layer(id)!!.props
        editor.previewLayerProps(id) { it.copy(opacity = 0.8f) }
        editor.previewLayerProps(id) { it.copy(opacity = 0.2f) }
        editor.commitLayerProps(id, before)
        assertTrue(Pixels.red(px(doc, 100, 50)) > 190)
        editor.undo()
        assertEquals(0, Pixels.red(px(doc, 100, 50)))
    }

    @Test
    fun clippedLayerKeepsItsBaseAtZeroOpacityAndHidesWithIt() {
        val doc = Document.blank(200, 100, paperColor = 0xFFFFFFFF.toInt())
        val editor = Editor(doc)
        // Bottom: a red line across the whole width. Middle (the base): a short line. Top: blue,
        // clipped to the middle one.
        draw(editor, 50f, 0xFFFF0000.toInt())
        editor.addLayer()
        val base = doc.activeLayerId
        draw(editor, 50f, 0xFF00FF00.toInt(), x1 = 80f)
        editor.addLayer()
        editor.setLayerProps(doc.activeLayerId) { it.copy(clipping = true) }
        draw(editor, 50f, 0xFF0000FF.toInt())
        assertEquals(255, Pixels.blue(px(doc, 50, 50)))
        assertEquals(255, Pixels.red(px(doc, 150, 50)))

        // Base faded out: the blue still only shows inside the base's shape.
        editor.setLayerProps(base) { it.copy(opacity = 0f) }
        assertEquals(255, Pixels.blue(px(doc, 50, 50)))
        assertEquals("blue must not spill onto the red layer", 0, Pixels.blue(px(doc, 150, 50)))

        // Base hidden: its clipping mask hides too.
        editor.setLayerProps(base) { it.copy(opacity = 1f, visible = false) }
        assertEquals(0, Pixels.blue(px(doc, 50, 50)))
        assertEquals(0, Pixels.blue(px(doc, 150, 50)))
        val thumb = Compositor.thumbnail(doc, 200).first
        assertEquals(0, Pixels.blue(thumb[50 * 200 + 50]))
    }
}
