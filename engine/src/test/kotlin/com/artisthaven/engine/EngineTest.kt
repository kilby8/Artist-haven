package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.BlendMode
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.History
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.stroke.Symmetry
import com.artisthaven.engine.stroke.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val red = 0xFFE53935.toInt()

    private fun draw(editor: Editor, points: List<InputPoint>, params: StrokeParams) {
        editor.beginStroke(params, points.first())
        points.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    private fun snapshot(doc: Document) = Compositor.flatten(doc)

    @Test
    fun premultiplyRoundTrip() {
        for (a in listOf(0, 1, 37, 128, 254, 255)) {
            val c = Pixels.pack(a, 200, 100, 50)
            val back = Pixels.unpremultiply(Pixels.premultiply(c))
            if (a == 0) assertEquals(0, back) else {
                assertEquals(a, Pixels.alpha(back))
                if (a > 64) assertTrue(kotlin.math.abs(Pixels.red(back) - 200) <= 3)
            }
        }
    }

    @Test
    fun multiplyDarkensAndScreenLightens() {
        val grey = Pixels.pack(255, 128, 128, 128)
        assertTrue(Pixels.red(BlendMode.MULTIPLY.blend(grey, grey)) < 128)
        assertTrue(Pixels.red(BlendMode.SCREEN.blend(grey, grey)) > 128)
        assertEquals(grey, BlendMode.NORMAL.blend(0, grey))
    }

    @Test
    fun strokePaintsAndUndoRestoresExactly() {
        val doc = Document.blank(300, 200)
        val editor = Editor(doc)
        val before = snapshot(doc)
        draw(editor, TestSupport.sCurve(20f, 20f, 260f, 160f), StrokeParams(BrushLibrary.default, color = red, size = 12f))
        val after = snapshot(doc)
        assertFalse(before.contentEquals(after))
        assertTrue(editor.history.canUndo)
        editor.undo()
        assertTrue(before.contentEquals(snapshot(doc)))
        editor.redo()
        assertTrue(after.contentEquals(snapshot(doc)))
    }

    @Test
    fun repeatedUndoAlwaysReportsAChange() {
        val doc = Document.blank(200, 200)
        val editor = Editor(doc)
        repeat(3) { i ->
            draw(editor, TestSupport.line(10f, 20f + i * 50, 190f, 20f + i * 50), StrokeParams(BrushLibrary.default, color = red))
        }
        repeat(3) { assertTrue("undo ${it + 1} must report a dirty area", editor.undo().dirty != null) }
        assertFalse(editor.history.canUndo)
    }

    @Test
    fun glazeOpacityIsCappedWithinOneStroke() {
        val doc = Document.blank(200, 200)
        val editor = Editor(doc)
        val zigzag = (0..60).map { i -> InputPoint(20f + (i % 2) * 160f, 100f + (i % 3), 1f, time = i * 8L) }
        draw(editor, zigzag, StrokeParams(BrushLibrary.byId("hard_airbrush"), color = 0xFF000000.toInt(), size = 30f, opacity = 0.5f))
        val maxAlpha = doc.layers[0].surface.let { s -> (0 until 200).maxOf { x -> Pixels.alpha(s.getPixel(x, 100)) } }
        assertTrue("alpha $maxAlpha should stay near 50%", maxAlpha in 115..135)
    }

    @Test
    fun eraserRemovesPaint() {
        val doc = Document.blank(200, 100)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(10f, 50f, 190f, 50f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = red, size = 30f))
        assertEquals(255, Pixels.alpha(doc.layers[0].surface.getPixel(100, 50)))
        draw(editor, TestSupport.line(100f, 10f, 100f, 90f), StrokeParams(BrushLibrary.byId("hard_airbrush"), tool = Tool.ERASER, size = 30f))
        assertEquals(0, Pixels.alpha(doc.layers[0].surface.getPixel(100, 50)))
        assertEquals(255, Pixels.alpha(doc.layers[0].surface.getPixel(40, 50)))
    }

    @Test
    fun smudgeDragsColourIntoEmptyArea() {
        val doc = Document.blank(200, 100)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(20f, 20f, 20f, 80f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = red, size = 30f))
        assertEquals(0, Pixels.alpha(doc.layers[0].surface.getPixel(70, 50)))
        draw(editor, TestSupport.line(15f, 50f, 120f, 50f, steps = 60), StrokeParams(BrushLibrary.defaultSmudge, tool = Tool.SMUDGE, size = 30f))
        assertTrue(Pixels.alpha(doc.layers[0].surface.getPixel(50, 50)) > 0)
    }

    @Test
    fun alphaLockOnlyRecoloursExistingPaint() {
        val doc = Document.blank(200, 100)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(20f, 50f, 100f, 50f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = red, size = 20f))
        editor.setLayerProps(doc.activeLayerId) { it.copy(alphaLock = true) }
        draw(editor, TestSupport.line(20f, 50f, 180f, 50f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = 0xFF1E88E5.toInt(), size = 20f))
        val inside = Pixels.unpremultiply(doc.layers[0].surface.getPixel(60, 50))
        assertTrue(Pixels.blue(inside) > 200)
        assertEquals(0, Pixels.alpha(doc.layers[0].surface.getPixel(150, 50)))
    }

    @Test
    fun verticalSymmetryMirrorsTheStroke() {
        val doc = Document.blank(200, 100)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(20f, 30f, 80f, 70f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = red, size = 10f, symmetry = Symmetry.VERTICAL))
        val s = doc.layers[0].surface
        var mismatches = 0
        for (y in 0 until 100) for (x in 0 until 100) {
            if ((Pixels.alpha(s.getPixel(x, y)) > 128) != (Pixels.alpha(s.getPixel(199 - x, y)) > 128)) mismatches++
        }
        assertTrue("mismatches $mismatches", mismatches < 40)
    }

    @Test
    fun floodFillStaysInsideLineArt() {
        val doc = Document.blank(200, 200)
        val editor = Editor(doc)
        val circle = (0..120).map { i ->
            val a = i / 120.0 * 2 * Math.PI
            InputPoint(100f + 60f * kotlin.math.cos(a).toFloat(), 100f + 60f * kotlin.math.sin(a).toFloat(), time = i * 8L)
        }
        draw(editor, circle, StrokeParams(BrushLibrary.byId("technical_pen"), color = 0xFF000000.toInt(), size = 4f))
        editor.addLayer()
        editor.fill(100, 100, red, tolerance = 0.12f, allLayers = true)
        val fillLayer = doc.activeLayer!!.surface
        assertEquals(255, Pixels.alpha(fillLayer.getPixel(100, 100)))
        assertEquals(255, Pixels.alpha(fillLayer.getPixel(100, 155)))
        assertEquals(0, Pixels.alpha(fillLayer.getPixel(10, 10)))
        assertEquals(0, Pixels.alpha(fillLayer.getPixel(100, 175)))
    }

    @Test
    fun layerOperationsAreUndoable() {
        val doc = Document.blank(100, 100)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(10f, 50f, 90f, 50f), StrokeParams(BrushLibrary.default, color = red))
        editor.addLayer()
        assertEquals(2, doc.layers.size)
        draw(editor, TestSupport.line(50f, 10f, 50f, 90f), StrokeParams(BrushLibrary.default, color = 0xFF000000.toInt()))
        val beforeMerge = snapshot(doc)
        editor.mergeDown(doc.activeLayerId)
        assertEquals(1, doc.layers.size)
        assertTrue(beforeMerge.contentEquals(snapshot(doc)))
        editor.undo()
        assertEquals(2, doc.layers.size)
        assertTrue(beforeMerge.contentEquals(snapshot(doc)))
        editor.clearLayer(doc.activeLayerId)
        assertFalse(beforeMerge.contentEquals(snapshot(doc)))
        editor.undo()
        assertTrue(beforeMerge.contentEquals(snapshot(doc)))
        editor.deleteLayer(doc.activeLayerId)
        assertEquals(1, doc.layers.size)
        editor.undo()
        assertEquals(2, doc.layers.size)
        editor.moveLayer(1, 0)
        editor.undo()
        assertTrue(beforeMerge.contentEquals(snapshot(doc)))
    }

    @Test
    fun clippingMaskOnlyShowsOverTheLayerBelow() {
        val doc = Document.blank(200, 100)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(20f, 50f, 100f, 50f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = red, size = 20f))
        editor.addLayer()
        editor.setLayerProps(doc.activeLayerId) { it.copy(clipping = true) }
        draw(editor, TestSupport.line(20f, 50f, 180f, 50f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = 0xFF1E88E5.toInt(), size = 20f))
        val img = snapshot(doc)
        assertTrue(Pixels.blue(img[50 * 200 + 60]) > 200)
        assertEquals(0xFFFFFFFF.toInt(), img[50 * 200 + 150])
    }

    @Test
    fun historyStaysWithinItsMemoryBudget() {
        val doc = Document.blank(256, 256)
        val history = History(maxBytes = 200_000)
        val editor = Editor(doc, history)
        repeat(30) { i ->
            draw(editor, TestSupport.line(0f, i * 8f, 255f, i * 8f), StrokeParams(BrushLibrary.default, color = red))
        }
        assertTrue(history.sizeBytes <= 200_000 || history.undoCount == 1)
        assertTrue(history.undoCount in 1..29)
    }

    @Test
    fun everyBrushLooksDifferent() {
        val renders = BrushLibrary.all.map { brush ->
            val doc = Document.blank(260, 120)
            val editor = Editor(doc)
            draw(editor, TestSupport.sCurve(20f, 15f, 220f, 90f), StrokeParams(brush, color = 0xFF263238.toInt(), size = 26f, usePressure = true))
            brush.name to snapshot(doc)
        }
        for (i in renders.indices) for (j in i + 1 until renders.size) {
            val a = renders[i].second
            val b = renders[j].second
            var diff = 0L
            for (k in a.indices) diff += kotlin.math.abs(Pixels.red(a[k]) - Pixels.red(b[k]))
            val mean = diff.toDouble() / a.size
            assertTrue("${renders[i].first} and ${renders[j].first} look the same ($mean)", mean > 1.0)
        }
    }

    @Test
    fun pressureChangesTheLine() {
        val brush = BrushLibrary.default
        fun ink(pressure: Float): Int {
            val doc = Document.blank(200, 100)
            val editor = Editor(doc)
            draw(editor, TestSupport.line(20f, 50f, 180f, 50f, pressure = pressure), StrokeParams(brush, color = red, size = 20f, usePressure = true))
            return doc.layers[0].surface.let { s -> (0 until 100).count { y -> Pixels.alpha(s.getPixel(100, y)) > 128 } }
        }
        assertTrue(ink(1f) > ink(0.3f) + 3)
        assertNotEquals(ink(1f), ink(0.6f))
    }
}
