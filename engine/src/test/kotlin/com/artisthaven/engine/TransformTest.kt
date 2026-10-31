package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.tools.Affine
import com.artisthaven.engine.tools.Resample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TransformTest {
    private val blue = 0xFF1A237E.toInt()

    private fun square(doc: Document, editor: Editor, x: Float, y: Float, size: Float) {
        // A filled block: a fat round stroke across the middle of the square.
        val params = StrokeParams(BrushLibrary.byId("technical_pen"), color = blue, size = size)
        val pts = TestSupport.line(x, y, x + size, y)
        editor.beginStroke(params, pts.first())
        pts.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    private fun alphaAt(doc: Document, x: Int, y: Int) = Pixels.alpha(doc.activeLayer!!.surface.getPixel(x, y))

    @Test
    fun affineInverseAndComposition() {
        val m = Affine.about(50f, 40f, Affine.rotate(30f) * Affine.scale(2f, 0.5f)) * Affine.translate(7f, -3f)
        val inv = m.inverse()
        val x = 13f
        val y = 77f
        val px = m.mapX(x, y)
        val py = m.mapY(x, y)
        assertEquals(x, inv.mapX(px, py), 1e-3f)
        assertEquals(y, inv.mapY(px, py), 1e-3f)
        assertEquals(30f, Affine.rotate(30f).rotationDegrees, 1e-3f)
    }

    @Test
    fun moveIsExactAndUndoRestores() {
        val doc = Document.blank(256, 192)
        val editor = Editor(doc)
        square(doc, editor, 40f, 60f, 30f)
        val before = Compositor.flatten(doc)
        val src = editor.beginTransform()
        assertNotNull(src)
        assertTrue(editor.isTransforming)
        editor.previewTransform(Affine.translate(100f, 50f))
        val change = editor.commitTransform()
        assertTrue(change.dirty != null)
        assertFalse(editor.isTransforming)
        // Pixels moved exactly.
        val layer = doc.activeLayer!!
        assertEquals(0, alphaAt(doc, 55, 60))
        assertEquals(255, alphaAt(doc, 155, 110))
        assertEquals(src!!.left + 100, layer.surface.contentBounds()!!.left)
        editor.undo()
        assertTrue(before.contentEquals(Compositor.flatten(doc)))
        editor.redo()
        assertEquals(255, alphaAt(doc, 155, 110))
    }

    @Test
    fun scaleRotateFlipAndCancel() {
        val doc = Document.blank(300, 300)
        val editor = Editor(doc)
        square(doc, editor, 100f, 150f, 40f)
        val before = Compositor.flatten(doc)
        val src = editor.beginTransform()!!
        val cx = (src.left + src.right) / 2f
        val cy = (src.top + src.bottom) / 2f
        // Cancel puts everything back.
        editor.previewTransform(Affine.about(cx, cy, Affine.scale(2f)))
        editor.cancelTransform()
        assertTrue(before.contentEquals(Compositor.flatten(doc)))

        editor.beginTransform()
        val m = Affine.about(cx, cy, Affine.rotate(90f) * Affine.scale(2f, 1f))
        editor.commitTransform(m, Resample.SHARP)
        val b = doc.activeLayer!!.surface.contentBounds()!!
        // Rotated a 2x-wide shape by 90 degrees: now taller than it is wide.
        assertTrue("bounds $b", b.height > b.width)
        assertTrue(abs(b.height - src.width * 2) <= 4)
        TestSupport.savePng(doc, "transform_rotate.png")

        editor.beginTransform()
        editor.commitTransform(Affine.about(cx, cy, Affine.scale(-1f, 1f)))
        assertTrue(abs(doc.activeLayer!!.surface.contentBounds()!!.width - b.width) <= 2)
    }

    @Test
    fun shrinkingKeepsThinLinesVisible() {
        val doc = Document.blank(400, 200)
        val editor = Editor(doc)
        val params = StrokeParams(BrushLibrary.byId("technical_pen"), color = blue, size = 2f)
        val pts = TestSupport.line(20f, 20f, 380f, 180f)
        editor.beginStroke(params, pts.first())
        pts.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
        editor.beginTransform()
        editor.commitTransform(Affine.scale(0.25f), Resample.SMOOTH)
        // The line stays continuous: every column it crosses still has some ink.
        val s = doc.activeLayer!!.surface
        val bounds = s.contentBounds()!!
        var gaps = 0
        for (x in bounds.left + 1 until bounds.right - 1) {
            if ((bounds.top until bounds.bottom).none { Pixels.alpha(s.getPixel(x, it)) > 0 }) gaps++
        }
        assertEquals(0, gaps)
    }

    @Test
    fun emptyLayerCannotTransformAndOtherEditsCommit() {
        val doc = Document.blank(128, 128)
        val editor = Editor(doc)
        assertNull(editor.beginTransform())
        square(doc, editor, 20f, 40f, 20f)
        editor.beginTransform()
        editor.previewTransform(Affine.translate(50f, 0f))
        editor.addLayer()
        assertFalse(editor.isTransforming)
        assertTrue(editor.takeSettledChange().dirty != null)
        editor.undo() // add layer
        editor.undo() // transform
        assertEquals(255, Pixels.alpha(doc.activeLayer!!.surface.getPixel(30, 40)))
    }
}

class ImageImportTest {
    @Test
    fun photoBecomesACentredLayerAndUndoes() {
        val doc = Document.blank(100, 80)
        val editor = Editor(doc)
        val px = IntArray(40 * 20) { 0x80FF0000.toInt() }
        editor.addImageLayer("Photo", px, 40, 20)
        assertEquals(2, doc.layers.size)
        assertEquals("Photo", doc.activeLayer!!.name)
        val b = doc.activeLayer!!.surface.contentBounds()!!
        assertEquals(com.artisthaven.engine.raster.IRect(30, 30, 70, 50), b)
        assertEquals(0x80, Pixels.alpha(doc.activeLayer!!.surface.getPixel(50, 40)))
        editor.undo()
        assertEquals(1, doc.layers.size)
    }
}
