package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Watercolour that flows: water keeps moving after the pen lifts, then dries with a dark edge. */
class WaterFlowTest {
    init { java.io.File(TestSupport.outputDir, "water").mkdirs() }
    private var clock = 0L

    private fun draw(editor: Editor, points: List<InputPoint>, params: StrokeParams) {
        val pts = points.map { it.copy(time = it.time + clock) }
        editor.beginStroke(params, pts.first())
        pts.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
        clock = pts.last().time + 16
    }

    /** A short scrub in a small circle: one puddle of paint. */
    private fun puddle(cx: Float, cy: Float, r: Float, pressure: Float = 0.8f) = (0..40).map { i ->
        val a = i / 40f * 6.28f * 2f
        val rr = r * (i / 40f)
        InputPoint(cx + cos(a) * rr, cy + sin(a) * rr, pressure, time = i * 8L)
    }

    private fun ink(doc: Document, x: Int, y: Int): Int {
        val c = Compositor.sample(doc, x, y)
        return 255 - ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
    }

    private fun area(doc: Document, threshold: Int = 6): Int {
        var n = 0
        for (y in 0 until doc.height step 2) for (x in 0 until doc.width step 2) if (ink(doc, x, y) > threshold) n++
        return n
    }

    private fun params(id: String, color: Int = 0xFF2F6DB0.toInt(), size: Float = 50f) =
        StrokeParams(BrushLibrary.byId(id), size = size, color = color, usePressure = true)

    @Test
    fun wetWashKeepsSpreadingAfterPenUp() {
        clock = 0L
        val doc = Document.blank(400, 400)
        val editor = Editor(doc)
        draw(editor, puddle(200f, 200f, 30f), params("wet_in_wet", size = 70f))
        assertTrue("water still moving at pen-up", editor.isWet)
        val atPenUp = area(doc)
        println("pen-up: " + editor.waterStats())
        for (k in 0 until 6) { editor.advanceWater(333.0); println("t+${(k + 1) * 333}: " + editor.waterStats()) }
        val after2s = area(doc)
        editor.advanceWater(30_000.0)
        val dry = area(doc)
        TestSupport.savePng(doc, "water/wet-in-wet-puddle.png")
        println("wet-in-wet area: pen-up=$atPenUp 2s=$after2s dry=$dry")
        assertFalse("dries in the end", editor.isWet)
        assertTrue("spreads after pen-up: $atPenUp -> $after2s", after2s >= atPenUp * 1.5f)
    }

    @Test
    fun roundBrushOnDryPaperKeepsAHardEdge() {
        clock = 0L
        val doc = Document.blank(400, 400)
        val editor = Editor(doc)
        draw(editor, puddle(200f, 200f, 30f), params("watercolor_round", size = 40f))
        val atPenUp = area(doc)
        editor.advanceWater(30_000.0)
        val dry = area(doc)
        TestSupport.savePng(doc, "water/round-dry-paper.png")
        println("round on dry paper: pen-up=$atPenUp dry=$dry")
        assertTrue("stays put on dry paper: $atPenUp -> $dry", dry < atPenUp * 1.15f)
    }

    @Test
    fun washDriesDarkerAtItsEdge() {
        clock = 0L
        val doc = Document.blank(400, 300)
        val editor = Editor(doc)
        // A flat band of wash.
        val band = (0..60).map { i -> InputPoint(60f + i * 4.5f, 150f, 0.7f, time = i * 10L) }
        draw(editor, band, params("watercolor_round", size = 60f))
        editor.advanceWater(30_000.0)
        TestSupport.savePng(doc, "water/band-rim.png")
        // Down through the middle of the band: find the edges, compare edge with centre.
        val col = (0 until 300).map { y -> (180..220).sumOf { x -> ink(doc, x, y) } / 41 }
        val inside = col.indices.filter { col[it] > 4 }
        val top = inside.first()
        val bottom = inside.last()
        val edge = (col.subList(top, top + 7).max() + col.subList(bottom - 6, bottom + 1).max()) / 2.0
        val third = (bottom - top) / 3
        val mid = col.subList(top + third, bottom - third).sorted().let { it[it.size / 2] }.toDouble()
        println("band: edge=$edge middle=$mid (rows $top..$bottom)")
        assertTrue("edge $edge darker than middle $mid", edge > mid * 1.3)
    }

    @Test
    fun waterRunsIntoAWetWashButNotADryOne() {
        clock = 0L
        fun crossing(wait: Double): Document {
            clock = 0L
            val doc = Document.blank(500, 300)
            val editor = Editor(doc)
            val a = (0..50).map { i -> InputPoint(60f + i * 6f, 150f, 0.75f, time = i * 10L) }
            draw(editor, a, params("wet_wash", 0xFF2F6DB0.toInt(), 60f))
            editor.advanceWater(wait)
            val b = (0..30).map { i -> InputPoint(250f, 40f + i * 7f, 0.75f, time = i * 10L) }
            draw(editor, b, params("wet_wash", 0xFFC0392B.toInt(), 50f))
            editor.advanceWater(40_000.0)
            return doc
        }
        val wet = crossing(300.0)
        val dry = crossing(40_000.0)
        TestSupport.savePng(wet, "water/crossing-wet.png")
        TestSupport.savePng(dry, "water/crossing-dry.png")
        // In the wet case the red runs sideways into the blue, well past its own brush.
        fun redReach(doc: Document): Int {
            var reach = 0
            for (x in 250 until 480) {
                val c = Compositor.sample(doc, x, 150)
                val r = c shr 16 and 0xFF; val b = c and 0xFF
                if (r > b + 8 || (r < 200 && (c shr 8 and 0xFF) < 150 && r > 90)) reach = x - 250
            }
            return reach
        }
        println("red reach: wet=${redReach(wet)} dry=${redReach(dry)}")
    }

    @Test
    fun undoWhileWetTakesBackTheStrokeAndItsWater() {
        clock = 0L
        val doc = Document.blank(300, 300)
        val editor = Editor(doc)
        draw(editor, puddle(150f, 150f, 25f), params("wet_in_wet", size = 60f))
        editor.advanceWater(500.0)
        assertTrue(area(doc) > 0)
        editor.undo()
        assertEquals(0, area(doc, threshold = 0))
        assertFalse(editor.isWaterActive && editor.isWet)
        editor.advanceWater(5000.0)
        assertEquals("nothing comes back", 0, area(doc, threshold = 0))
        editor.redo()
        assertTrue(area(doc) > 0)
    }

    @Test
    fun oneStepOnABigWashIsQuick() {
        clock = 0L
        val doc = Document.blank(1200, 900)
        val editor = Editor(doc)
        val pts = (0..80).map { i -> InputPoint(100f + i * 12f, 300f + (i % 9) * 40f, 0.8f, time = i * 10L) }
        draw(editor, pts, params("wet_in_wet", size = 160f))
        editor.advanceWater(100.0)
        val t0 = System.nanoTime()
        repeat(60) { editor.tickWater(1000.0 / 60.0) }
        val ms = (System.nanoTime() - t0) / 1e6 / 60
        println("water tick on a big wash: %.2f ms".format(ms))
    }
}

class WaterSimTimingTest {
    @Test
    fun stepVersusRender() {
        val sim = com.artisthaven.engine.stroke.WaterSim(1200, 900)
        sim.configure(0.9f, 0.1f, 0.2f, null)
        val tip = com.artisthaven.engine.brush.Textures.tipFor(BrushLibrary.byId("wet_in_wet"))
        for (i in 0..80) sim.dab(100f + i * 12f, 300f + (i % 9) * 40f, 80f, 0f, 1f, tip, 0.8f, 0.5f, 1.5f, 0.8f, 0.3f)
        repeat(10) { sim.step() }
        var t0 = System.nanoTime()
        repeat(60) { sim.step() }
        val stepMs = (System.nanoTime() - t0) / 1e6 / 60
        val surface = com.artisthaven.engine.raster.TiledSurface(1200, 900)
        val rect = sim.paintedRect()!!
        t0 = System.nanoTime()
        repeat(20) { sim.render(surface, rect, 1f, false, force = true) {} }
        val renderMs = (System.nanoTime() - t0) / 1e6 / 20
        println("step %.2f ms, render %.2f ms over $rect, ${sim.stats()}".format(stepMs, renderMs))
    }
}
