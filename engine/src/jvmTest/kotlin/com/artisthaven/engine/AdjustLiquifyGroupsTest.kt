package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.Selection
import com.artisthaven.engine.io.PsdReader
import com.artisthaven.engine.io.PsdWriter
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.tools.Adjustment
import com.artisthaven.engine.tools.Affine
import com.artisthaven.engine.tools.LiquifyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustLiquifyGroupsTest {
    private val red = 0xFFD03030.toInt()

    private fun paintBar(editor: Editor, y: Float, color: Int = red, size: Float = 30f) {
        val params = StrokeParams(BrushLibrary.byId("technical_pen"), color = color, size = size)
        val pts = TestSupport.line(20f, y, 180f, y)
        editor.beginStroke(params, pts.first())
        pts.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    private fun straight(doc: Document, x: Int, y: Int) = Pixels.unpremultiply(doc.activeLayer!!.surface.getPixel(x, y))

    @Test
    fun hueShiftChangesColourAndUndoRestoresIt() {
        val doc = Document.blank(200, 120)
        val editor = Editor(doc)
        paintBar(editor, 60f)
        val before = straight(doc, 100, 60)
        assertTrue(editor.beginAdjust(Adjustment.HUE_SATURATION))
        editor.previewAdjust(1f / 3f, 0f, 0f) // +60 degrees: red towards yellow
        editor.commitAdjust()
        val after = straight(doc, 100, 60)
        assertTrue("green should rise", Pixels.green(after) > Pixels.green(before) + 60)
        assertEquals(Pixels.alpha(before), Pixels.alpha(after))
        editor.undo()
        assertEquals(before, straight(doc, 100, 60))
    }

    @Test
    fun cancelledAdjustmentLeavesPixelsAlone() {
        val doc = Document.blank(200, 120)
        val editor = Editor(doc)
        paintBar(editor, 60f)
        val before = doc.activeLayer!!.surface.copy()
        assertTrue(editor.beginAdjust(Adjustment.INVERT))
        editor.previewAdjust(0f, 0f, 0f)
        assertNotEquals(before.getPixel(100, 60), doc.activeLayer!!.surface.getPixel(100, 60))
        editor.cancelAdjust()
        assertEquals(before.getPixel(100, 60), doc.activeLayer!!.surface.getPixel(100, 60))
    }

    @Test
    fun blurSpreadsPaintAndRespectsSelection() {
        val doc = Document.blank(200, 120)
        val editor = Editor(doc)
        paintBar(editor, 60f, size = 10f)
        // Only the left half may change.
        editor.selectPolygon(floatArrayOf(0f, 0f, 100f, 0f, 100f, 120f, 0f, 120f))
        val rightBefore = doc.activeLayer!!.surface.getPixel(150, 72)
        assertTrue(editor.beginAdjust(Adjustment.BLUR))
        editor.previewAdjust(0.3f, 0f, 0f)
        editor.commitAdjust()
        assertTrue("blur should reach above the bar", Pixels.alpha(doc.activeLayer!!.surface.getPixel(50, 72)) > 0)
        assertEquals(rightBefore, doc.activeLayer!!.surface.getPixel(150, 72))
    }

    @Test
    fun liquifyPushMovesPaintAndIsOneUndoStep() {
        val doc = Document.blank(200, 120)
        val editor = Editor(doc)
        paintBar(editor, 40f, size = 12f)
        val steps = editor.history.undoCount
        assertEquals(0, Pixels.alpha(doc.activeLayer!!.surface.getPixel(100, 70)))
        editor.beginLiquify(LiquifyMode.PUSH, 80f, 1f, 100f, 38f, 1f)
        for (i in 1..30) editor.liquifyTo(100f, 38f + i, 1f)
        editor.endLiquify()
        assertTrue("paint pushed down", Pixels.alpha(doc.activeLayer!!.surface.getPixel(100, 60)) > 0)
        assertEquals(steps + 1, editor.history.undoCount)
        editor.undo()
        assertEquals(0, Pixels.alpha(doc.activeLayer!!.surface.getPixel(100, 60)))
    }

    @Test
    fun hiddenGroupHidesItsLayersAndUngroupUndoes() {
        val doc = Document.blank(200, 120, paperColor = 0xFFFFFFFF.toInt())
        val editor = Editor(doc)
        paintBar(editor, 30f)
        editor.addLayer()
        paintBar(editor, 90f, color = 0xFF2040C0.toInt())
        editor.groupLayer(doc.activeLayerId, withBelow = true)
        val gid = doc.layers[0].group
        assertTrue(gid.isNotEmpty())
        assertEquals(gid, doc.layers[1].group)
        editor.setGroupProps(gid) { it.copy(visible = false) }
        assertEquals(0xFFFFFFFF.toInt(), Compositor.sample(doc, 100, 30))
        assertEquals(0xFFFFFFFF.toInt(), Compositor.sample(doc, 100, 90))
        editor.undo()
        assertNotEquals(0xFFFFFFFF.toInt(), Compositor.sample(doc, 100, 30))
        // New layers join the active layer's group; moving one out of the run leaves it.
        editor.addLayer()
        assertEquals(gid, doc.activeLayer!!.group)
        editor.ungroup(gid)
        assertTrue(doc.layers.all { it.group.isEmpty() })
        assertTrue(doc.groups.isEmpty())
        editor.undo()
        assertEquals(3, doc.layers.count { it.group == gid })
    }

    @Test
    fun movingALayerBetweenGroupLayersJoinsTheGroup() {
        val doc = Document.blank(100, 100)
        val editor = Editor(doc)
        editor.addLayer()
        editor.addLayer()
        editor.addLayer()
        // Layers 1..4 bottom to top; group the top two.
        val top = doc.layers[3].id
        editor.groupLayer(top, withBelow = true)
        val gid = doc.layers[3].group
        editor.moveLayer(0, 2) // bottom layer moves between the two grouped ones
        assertEquals(gid, doc.layers[2].group)
        editor.moveLayer(2, 0)
        assertEquals("", doc.layers[0].group)
    }

    @Test
    fun groupOpacityAndVisibilityGoIntoPsdLayers() {
        val doc = Document.blank(120, 80)
        val editor = Editor(doc)
        paintBar(editor, 40f)
        editor.groupLayer(doc.activeLayerId, withBelow = false)
        editor.setGroupProps(doc.activeLayer!!.group) { it.copy(opacity = 0.5f) }
        val back = PsdReader.read(PsdWriter.encode(doc)).doc
        assertEquals(0.5f, back.layers.last().opacity, 0.01f)
    }

    @Test
    fun selectionOnlyAdjustLeavesOutsideAlone() {
        val doc = Document.blank(200, 120)
        val editor = Editor(doc)
        paintBar(editor, 60f)
        editor.selectPolygon(floatArrayOf(0f, 0f, 100f, 0f, 100f, 120f, 0f, 120f))
        val outside = straight(doc, 150, 60)
        assertTrue(editor.beginAdjust(Adjustment.INVERT))
        editor.previewAdjust(0f, 0f, 0f)
        editor.commitAdjust()
        assertEquals(outside, straight(doc, 150, 60))
        assertNotEquals(outside, straight(doc, 50, 60))
        @Suppress("UNUSED_VARIABLE") val unused: Selection? = doc.selection
    }

    @Test
    fun movingASelectionOverPaintKeepsThePaintUnderneath() {
        val doc = Document.blank(200, 120)
        val editor = Editor(doc)
        paintBar(editor, 30f, size = 10f)
        paintBar(editor, 90f, size = 10f)
        val under = doc.activeLayer!!.surface.getPixel(150, 90)
        editor.selectPolygon(floatArrayOf(0f, 0f, 100f, 0f, 100f, 60f, 0f, 60f))
        editor.beginTransform()
        editor.previewTransform(Affine.translate(40f, 40f))
        editor.previewTransform(Affine.translate(50f, 60f))
        editor.commitTransform()
        assertEquals("paint outside the selection stays", under, doc.activeLayer!!.surface.getPixel(150, 90))
        assertTrue("moved paint arrives", Pixels.alpha(doc.activeLayer!!.surface.getPixel(140, 90)) > 0)
        editor.undo()
        assertEquals(0, Pixels.alpha(doc.activeLayer!!.surface.getPixel(140, 60)))
        assertEquals(under, doc.activeLayer!!.surface.getPixel(150, 90))
        assertTrue(Pixels.alpha(doc.activeLayer!!.surface.getPixel(50, 30)) > 0)
    }

    @Test
    fun fillPolygonFillsInsideOnlyAndKeepsTheSelection() {
        val doc = Document.blank(200, 120)
        val editor = Editor(doc)
        editor.selectPolygon(floatArrayOf(0f, 0f, 100f, 0f, 100f, 120f, 0f, 120f))
        val sel = doc.selection
        val steps = editor.history.undoCount
        editor.fillPolygon(floatArrayOf(20f, 20f, 180f, 20f, 180f, 100f, 20f, 100f), red)
        assertEquals(red, straight(doc, 50, 60))
        assertEquals("outside the selection", 0, Pixels.alpha(doc.activeLayer!!.surface.getPixel(150, 60)))
        assertEquals("outside the shape", 0, Pixels.alpha(doc.activeLayer!!.surface.getPixel(50, 10)))
        assertEquals(sel, doc.selection)
        assertEquals(steps + 1, editor.history.undoCount)
        editor.undo()
        assertEquals(0, Pixels.alpha(doc.activeLayer!!.surface.getPixel(50, 60)))
    }
}
