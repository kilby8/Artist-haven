package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushCategory
import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.BlendMode
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.io.ProjectCodec
import com.artisthaven.engine.io.PsdWriter
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.stroke.Symmetry
import com.artisthaven.engine.tools.QuickShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.cos
import kotlin.math.sin

class ToolsAndFilesTest {

    private fun draw(editor: Editor, points: List<InputPoint>, params: StrokeParams) {
        editor.beginStroke(params, points.first())
        points.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    private fun sampleDoc(): Document {
        val doc = Document.blank(320, 240, paperColor = 0xFFFAF6EE.toInt())
        val editor = Editor(doc)
        draw(editor, TestSupport.sCurve(20f, 20f, 280f, 200f), StrokeParams(BrushLibrary.default, color = 0xFF1A237E.toInt(), size = 14f, usePressure = true))
        editor.addLayer()
        editor.setLayerProps(doc.activeLayerId) { it.copy(name = "Wash", blendMode = BlendMode.MULTIPLY, opacity = 0.8f) }
        draw(editor, TestSupport.line(40f, 120f, 280f, 120f), StrokeParams(BrushLibrary.byId("wet_wash"), color = 0xFFFFB300.toInt(), size = 60f))
        editor.addLayer()
        editor.setLayerProps(doc.activeLayerId) { it.copy(name = "Hidden", visible = false) }
        return doc
    }

    @Test
    fun projectRoundTripsExactly() {
        val doc = sampleDoc()
        val dir = Files.createTempDirectory("ah-project").toFile()
        ProjectCodec.save(doc, dir, "Test", 1L, 2L)
        val (loaded, manifest) = ProjectCodec.load(dir)
        assertEquals("Test", manifest.name)
        assertEquals(doc.layers.map { it.props }, loaded.layers.map { it.props })
        assertEquals(doc.activeLayerId, loaded.activeLayerId)
        assertEquals(doc.paperColor, loaded.paperColor)
        assertTrue(Compositor.flatten(doc).contentEquals(Compositor.flatten(loaded)))
        // Saving again after deleting a layer removes its file.
        val editor = Editor(loaded)
        editor.deleteLayer(loaded.layers.last().id)
        ProjectCodec.save(loaded, dir, "Test", 1L, 3L)
        assertEquals(2, dir.listFiles()!!.count { it.name.startsWith("layer_") && it.name.endsWith(".bin") })
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun psdHasHeaderAndAllLayers() {
        val doc = sampleDoc()
        val f = File(TestSupport.outputDir, "sample.psd")
        f.outputStream().buffered().use { PsdWriter.write(doc, it) }
        val bytes = f.readBytes()
        assertEquals("8BPS", String(bytes, 0, 4))
        assertTrue(bytes.size > 1000)
        TestSupport.savePng(doc, "sample.png")
    }

    @Test
    fun packBitsDecodesBack() {
        val src = ByteArray(300) { i -> if (i in 50..200) 7 else (i % 5).toByte() }
        val packed = PsdWriter.packBits(src)
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < packed.size) {
            val n = packed[i].toInt()
            if (n >= 0) { out.write(packed, i + 1, n + 1); i += n + 2 } else { repeat(1 - n) { out.write(packed[i + 1].toInt()) }; i += 2 }
        }
        assertTrue(src.contentEquals(out.toByteArray()))
    }

    @Test
    fun quickShapeRecognisesLinesCirclesAndRectangles() {
        val wobble = (0..60).map { i -> InputPoint(10f + i * 4f, 100f + sin(i * 0.9f) * 3f, time = i * 8L) }
        assertEquals(QuickShape.Kind.LINE, QuickShape.recognise(wobble)?.kind)

        val circle = (0..80).map { i ->
            val a = i / 80f * 2f * Math.PI.toFloat()
            InputPoint(200f + (80f + sin(i * 1.3f) * 3f) * cos(a), 200f + (80f + cos(i * 0.7f) * 3f) * sin(a), time = i * 8L)
        }
        val shape = QuickShape.recognise(circle)
        assertEquals(QuickShape.Kind.ELLIPSE, shape?.kind)
        val r = shape!!.points.map { kotlin.math.hypot(it.x - 200f, it.y - 200f) }
        assertTrue(r.maxOrNull()!! - r.minOrNull()!! < 3f)

        val rect = buildList {
            fun edge(x0: Float, y0: Float, x1: Float, y1: Float) {
                for (i in 0 until 20) add(InputPoint(x0 + (x1 - x0) * i / 20f + sin(i * 1f) * 1.5f, y0 + (y1 - y0) * i / 20f, time = size * 8L))
            }
            edge(50f, 50f, 250f, 50f); edge(250f, 50f, 250f, 180f); edge(250f, 180f, 50f, 180f); edge(50f, 180f, 50f, 52f)
        }
        assertEquals(QuickShape.Kind.RECTANGLE, QuickShape.recognise(rect)?.kind)

        val scribble = (0..60).map { i -> InputPoint(10f + i * 3f, 100f + (if (i % 10 < 5) 40f else -40f), time = i * 8L) }
        assertNull(QuickShape.recognise(scribble))
    }

    @Test
    fun quickShapeReplacesTheStrokeInPlace() {
        val doc = Document.blank(300, 200)
        val editor = Editor(doc)
        val wobble = (0..60).map { i -> InputPoint(20f + i * 4f, 100f + sin(i * 0.9f) * 4f, time = i * 8L) }
        editor.beginStroke(StrokeParams(BrushLibrary.byId("technical_pen"), color = 0xFF000000.toInt(), size = 3f), wobble.first())
        wobble.drop(1).forEach { editor.strokeTo(it) }
        assertNotNull(editor.quickShape())
        editor.endStroke()
        val s = doc.layers[0].surface
        // The snapped line is straight: ink stays on the row y = 100 +- 3.
        for (x in 40..240 step 20) {
            val inkRows = (90..110).filter { y -> s.getPixel(x, y) ushr 24 > 128 }
            assertTrue("x=$x rows=$inkRows", inkRows.isNotEmpty() && inkRows.all { it in 94..103 })
        }
        editor.undo()
        assertTrue(s.isBlank())
    }

    /** Renders every brush so they can be checked by eye (written to build/test-output). */
    @Test
    fun wetBrushesMixWithPaintUnderneathButStayOpaqueOnBareCanvas() {
        val doc = Document.blank(400, 200, paperColor = 0xFFFFFFFF.toInt())
        val editor = Editor(doc)
        draw(editor, TestSupport.line(200f, 10f, 200f, 190f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = 0xFF1565C0.toInt(), size = 60f))
        draw(editor, TestSupport.line(20f, 100f, 380f, 100f, steps = 120), StrokeParams(BrushLibrary.byId("oil_paint"), color = 0xFFC62828.toInt(), size = 30f))
        TestSupport.savePng(doc, "wet-mix.png")
        val layer = doc.activeLayer!!
        val before = layer.surface.getPixel(80, 100)
        assertTrue("oil stays opaque on bare canvas (alpha ${before ushr 24})", before ushr 24 > 200)
        // Just past the blue band, the brush carries blue into the red.
        val after = Compositor.sample(doc, 250, 100)
        val r = (after shr 16) and 0xFF
        val b = after and 0xFF
        assertTrue("paint picked up blue (r=$r b=$b)", b > 55 && r > 70)
    }

    @Test
    fun renderBrushSheet() {
        val brushes = BrushLibrary.all
        val rowH = 110
        val doc = Document.blank(1240, rowH * ((brushes.size + 1) / 2) + 20, paperColor = 0xFFFBF8F1.toInt())
        val editor = Editor(doc)
        brushes.forEachIndexed { i, brush ->
            val col = i % 2
            val row = i / 2
            val x = 30f + col * 620f
            val y = 15f + row * rowH
            val color = when (brush.category) {
                BrushCategory.SKETCHING -> 0xFF37474F.toInt()
                BrushCategory.INKING -> 0xFF111111.toInt()
                BrushCategory.PAINTING -> 0xFFC62828.toInt()
                BrushCategory.WATERCOLOR -> 0xFF1565C0.toInt()
                BrushCategory.ARTISTIC -> 0xFF6A1B9A.toInt()
                BrushCategory.AIRBRUSHING -> 0xFFEF6C00.toInt()
                BrushCategory.TEXTURES -> 0xFF2E7D32.toInt()
            }
            if (brush.category == BrushCategory.PAINTING || brush.id == "watercolor_round") {
                // Wet brushes need something to mix with.
                draw(editor, TestSupport.line(x + 120f, y + 20f, x + 120f, y + 80f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = 0xFFFDD835.toInt(), size = 40f))
            }
            draw(editor, TestSupport.sCurve(x, y, 560f, 85f, steps = 140), StrokeParams(brush, color = color, size = brush.defaultSize.coerceIn(6f, 48f), usePressure = true))
        }
        draw(editor, TestSupport.line(620f, 0f, 620f, doc.height.toFloat()), StrokeParams(BrushLibrary.byId("technical_pen"), color = 0x22000000, size = 1f, symmetry = Symmetry.NONE))
        val f = TestSupport.savePng(doc, "brush-sheet.png")
        assertTrue(f.length() > 10_000)
    }

    @Test
    fun longStrokeIsFastEnough() {
        val doc = Document.blank(2048, 2048)
        val editor = Editor(doc)
        val points = (0..600).map { i ->
            InputPoint(100f + i * 3f, 1000f + 600f * sin(i / 60f), 0.5f + 0.5f * sin(i / 20f), time = i * 4L)
        }
        val params = StrokeParams(BrushLibrary.byId("round_brush"), color = 0xFF3949AB.toInt(), size = 80f, usePressure = true)
        // Warm up the JIT, then time a second run.
        draw(editor, points, params)
        val t0 = System.nanoTime()
        draw(editor, points, params)
        val ms = (System.nanoTime() - t0) / 1e6
        println("Long wet stroke (601 points, 80 px): ${"%.1f".format(ms)} ms")
        assertTrue("stroke took $ms ms", ms < 4000)
    }
}
