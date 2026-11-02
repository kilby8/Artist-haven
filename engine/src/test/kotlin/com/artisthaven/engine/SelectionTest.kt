package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.Selection
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.tools.Affine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    private val blue = 0xFF1A237E.toInt()
    private val red = 0xFFE53935.toInt()

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)

    private fun paint(editor: Editor, y: Float, color: Int = blue, size: Float = 24f) {
        val pts = TestSupport.line(10f, y, 290f, y)
        editor.beginStroke(StrokeParams(BrushLibrary.byId("technical_pen"), color = color, size = size), pts.first())
        pts.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    private fun alpha(doc: Document, x: Int, y: Int) = Pixels.alpha(doc.activeLayer!!.surface.getPixel(x, y))

    @Test
    fun polygonHasSoftEdgesAndCombines() {
        val s = Selection.polygon(200, 200, rect(10.5f, 10f, 50.5f, 50f))
        assertEquals(255, s.at(30, 30))
        assertEquals(0, s.at(60, 30))
        assertTrue(s.at(10, 30) in 100..160) // half-covered edge pixel
        val b = s.bounds()!!
        assertEquals(10, b.left)
        assertEquals(51, b.right)
        val both = s.combine(Selection.polygon(200, 200, rect(100f, 100f, 150f, 150f)), add = true)
        assertEquals(255, both.at(120, 120))
        val cut = both.combine(Selection.polygon(200, 200, rect(0f, 0f, 30f, 200f)), add = false)
        assertEquals(0, cut.at(20, 30))
        assertEquals(255, cut.at(40, 30))
        assertEquals(0, s.inverted().at(30, 30))
        assertEquals(255, s.inverted().at(150, 150))
    }

    @Test
    fun paintingStaysInsideTheSelection() {
        val doc = Document.blank(300, 200)
        val editor = Editor(doc)
        editor.selectPolygon(rect(100f, 0f, 200f, 200f))
        paint(editor, 100f)
        assertEquals(0, alpha(doc, 50, 100))
        assertEquals(0, alpha(doc, 250, 100))
        assertEquals(255, alpha(doc, 150, 100))
        // Undo the stroke, then undo the selection itself.
        editor.undo()
        editor.undo()
        assertNull(doc.selection)
        paint(editor, 100f)
        assertEquals(255, alpha(doc, 50, 100))
    }

    @Test
    fun transformMovesOnlySelectedPixelsAndTheSelection() {
        val doc = Document.blank(300, 200)
        val editor = Editor(doc)
        paint(editor, 50f)
        editor.selectPolygon(rect(0f, 0f, 100f, 200f))
        assertNotNull(editor.beginTransform())
        editor.commitTransform(Affine.translate(0f, 100f))
        assertEquals(0, alpha(doc, 50, 50)) // lifted
        assertEquals(255, alpha(doc, 50, 150)) // landed
        assertEquals(255, alpha(doc, 200, 50)) // unselected part stayed
        assertEquals(255, doc.selection!!.at(50, 150))
        assertEquals(0, doc.selection!!.at(50, 50))
        TestSupport.savePng(doc, "selection_move.png")
        editor.undo()
        assertEquals(255, alpha(doc, 50, 50))
        assertEquals(255, doc.selection!!.at(50, 50))
    }

    @Test
    fun clearFillCopyCutAndMagicWand() {
        val doc = Document.blank(300, 200)
        val editor = Editor(doc)
        paint(editor, 50f)
        editor.selectPolygon(rect(0f, 0f, 100f, 200f))
        editor.selectionToNewLayer(cut = true)
        assertEquals(2, doc.layers.size)
        assertEquals(255, alpha(doc, 50, 50)) // new layer has it
        assertEquals(0, Pixels.alpha(doc.layers[0].surface.getPixel(50, 50)))
        editor.undo()
        assertEquals(1, doc.layers.size)
        assertEquals(255, alpha(doc, 50, 50))

        editor.clearSelected()
        assertEquals(0, alpha(doc, 50, 50))
        assertEquals(255, alpha(doc, 200, 50))
        editor.fillSelection(red)
        assertEquals(255, alpha(doc, 50, 180))
        assertEquals(0, alpha(doc, 200, 180))

        editor.deselect()
        editor.selectSimilar(200, 50, 0.1f, allLayers = false)
        val s = doc.selection!!
        assertEquals(255, s.at(200, 50))
        assertEquals(0, s.at(200, 150))
    }
}

class BrushCodecTest {
    @Test
    fun customBrushesRoundTripAndResolve() {
        val base = com.artisthaven.engine.brush.BrushLibrary.byId("oil_paint")
        val mine = base.copy(id = "custom_1", name = "My oil", category = com.artisthaven.engine.brush.BrushCategory.MINE, pull = 0.9f)
        val text = com.artisthaven.engine.brush.BrushCodec.encode(listOf(mine))
        val back = com.artisthaven.engine.brush.BrushCodec.decode(text)
        assertEquals(listOf(mine), back)
        assertTrue(com.artisthaven.engine.brush.BrushCodec.decode("not a brush").isEmpty())
        com.artisthaven.engine.brush.BrushLibrary.custom = back
        try {
            assertEquals("My oil", com.artisthaven.engine.brush.BrushLibrary.byId("custom_1").name)
            assertTrue(com.artisthaven.engine.brush.BrushLibrary.isCustom("custom_1"))
        } finally {
            com.artisthaven.engine.brush.BrushLibrary.custom = emptyList()
        }
    }
}

class LayerMaskTest {
    private fun paint(editor: Editor, y: Float, color: Int, size: Float = 30f, x0: Float = 10f, x1: Float = 290f) {
        val pts = TestSupport.line(x0, y, x1, y)
        editor.beginStroke(StrokeParams(BrushLibrary.byId("technical_pen"), color = color, size = size), pts.first())
        pts.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    @Test
    fun blackHidesWhiteRevealsAndItSavesAndUndoes() {
        val doc = Document.blank(300, 200, paperColor = 0xFFFFFFFF.toInt())
        val editor = Editor(doc)
        val blue = 0xFF1A237E.toInt()
        paint(editor, 100f, blue)
        val id = doc.activeLayerId
        editor.addMask(id)
        assertTrue(doc.editingMask)
        // Painting black on the mask hides the left half of the stroke; layer pixels are untouched.
        paint(editor, 100f, 0xFF000000.toInt(), size = 60f, x0 = 10f, x1 = 140f)
        assertEquals(255, Pixels.alpha(doc.activeLayer!!.surface.getPixel(60, 100)))
        assertEquals(0xFFFFFFFF.toInt(), Compositor.sample(doc, 60, 100))
        assertTrue(Compositor.sample(doc, 220, 100) != 0xFFFFFFFF.toInt())
        // White paints it back.
        paint(editor, 100f, 0xFFFFFFFF.toInt(), size = 60f, x0 = 40f, x1 = 80f)
        assertTrue(Compositor.sample(doc, 60, 100) != 0xFFFFFFFF.toInt())
        editor.undo()
        assertEquals(0xFFFFFFFF.toInt(), Compositor.sample(doc, 60, 100))

        // Survives save and load.
        val dir = java.nio.file.Files.createTempDirectory("mask").toFile()
        com.artisthaven.engine.io.ProjectCodec.save(doc, dir, "m", 0, 0)
        val (loaded, _) = com.artisthaven.engine.io.ProjectCodec.load(dir)
        assertNotNull(loaded.layer(id)!!.mask)
        assertEquals(0xFFFFFFFF.toInt(), Compositor.sample(loaded, 60, 100))

        // Apply bakes it in; undo brings the mask back.
        editor.applyMask(id)
        assertNull(doc.activeLayer!!.mask)
        assertEquals(0, Pixels.alpha(doc.activeLayer!!.surface.getPixel(60, 100)))
        editor.undo()
        assertNotNull(doc.activeLayer!!.mask)
        assertEquals(255, Pixels.alpha(doc.activeLayer!!.surface.getPixel(60, 100)))
    }
}
