package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws a study sheet for each natural-media brush, the way an artist tests a real pencil or brush:
 * a pressure ramp, hatching, layered strokes and one long stroke. The sheets go to
 * build/test-output/brushes for comparing against photos of the real medium.
 */
class BrushLookTest {
    private fun draw(editor: Editor, points: List<InputPoint>, params: StrokeParams) {
        editor.beginStroke(params, points.first())
        points.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    /** A stroke whose pressure ramps from [p0] to [p1], with a slight hand wobble. */
    private fun ramp(x0: Float, y0: Float, x1: Float, y1: Float, p0: Float, p1: Float, steps: Int = 80, tilt: Float = 0.3f) =
        (0..steps).map { i ->
            val t = i / steps.toFloat()
            InputPoint(
                x0 + (x1 - x0) * t, y0 + (y1 - y0) * t + sin(t * 9f) * 1.5f,
                p0 + (p1 - p0) * t, tilt = tilt, azimuth = 0.8f, time = i * 8L,
            )
        }

    private fun sheet(id: String, size: Float, color: Int) {
        val brush = BrushLibrary.byId(id)
        val doc = Document.blank(960, 560)
        val editor = Editor(doc)
        fun p(seed: Int, s: Float = size) = StrokeParams(brush, size = s, color = color, usePressure = true, seed = seed)
        // 1. Pressure ramp, light to heavy.
        draw(editor, ramp(40f, 60f, 900f, 60f, 0.05f, 1f), p(1))
        // 2. Long S-curve with a pressure swell.
        draw(editor, TestSupport.sCurve(40f, 100f, 860f, 110f), p(2))
        // 3. Hatching patch: diagonal strokes at medium pressure.
        for (i in 0 until 14) {
            val x = 40f + i * 14f
            draw(editor, ramp(x, 500f, x + 120f, 260f, 0.5f, 0.55f, steps = 40), p(10 + i))
        }
        // 4. Cross-hatching on top of half of it.
        for (i in 0 until 8) {
            val x = 40f + i * 18f
            draw(editor, ramp(x, 260f, x + 140f, 420f, 0.5f, 0.5f, steps = 40), p(30 + i))
        }
        // 5. Layered strokes: three overlapping horizontal strokes at full size.
        for (i in 0 until 3) {
            draw(editor, ramp(380f, 290f + i * size * 0.5f, 640f, 300f + i * size * 0.5f, 0.6f, 0.6f), p(50 + i, size * 1.6f))
        }
        // 6. Tilted shading (side of the lead / flat brush).
        draw(editor, ramp(380f, 470f, 640f, 470f, 0.6f, 0.6f, tilt = 1.2f), p(60, size * 1.6f))
        // 7. Short dabs / ticks with quick lifts.
        for (i in 0 until 6) {
            val x = 700f + (i % 3) * 80f
            val y = 300f + (i / 3) * 120f
            draw(editor, (0..20).map { k ->
                val t = k / 20f
                InputPoint(x + 60f * t, y + 40f * t, sin(t * PI).toFloat(), tilt = 0.3f, time = k * 6L)
            }, p(70 + i))
        }
        TestSupport.savePng(doc, "brushes/${System.getProperty("brushTag") ?: "now"}-$id.png")
    }

    @Test
    fun studySheets() {
        java.io.File(TestSupport.outputDir, "brushes").mkdirs()
        val graphite = 0xFF3A3A3E.toInt()
        sheet("hb_pencil", 6f, graphite)
        sheet("6b_pencil", 14f, graphite)
        sheet("charcoal", 30f, 0xFF1C1B1A.toInt())
        sheet("soft_pastel", 34f, 0xFF7A3E9D.toInt())
        sheet("gouache", 40f, 0xFF2E5E8C.toInt())
        sheet("oil_paint", 46f, 0xFFB5462B.toInt())
        sheet("round_brush", 32f, 0xFF2E7D4F.toInt())
        sheet("wet_wash", 70f, 0xFF2F6DB0.toInt())
        sheet("watercolor_round", 36f, 0xFFC0392B.toInt())
    }

    private fun inkAt(doc: Document, x: Int, y: Int): Int {
        val c = com.artisthaven.engine.document.Compositor.sample(doc, x, y)
        return 255 - ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
    }

    @Test
    fun pencilCatchesOnlyThePaperPeaksUnderLightPressure() {
        val doc = Document.blank(400, 120)
        val editor = Editor(doc)
        val pencil = BrushLibrary.byId("6b_pencil")
        draw(editor, ramp(20f, 30f, 380f, 30f, 0.15f, 0.15f, tilt = 0f), StrokeParams(pencil, size = 14f, usePressure = true))
        draw(editor, ramp(20f, 80f, 380f, 80f, 1f, 1f, tilt = 0f), StrokeParams(pencil, size = 14f, usePressure = true))
        fun bare(y: Int) = (60..340).count { x -> inkAt(doc, x, y) < 20 }
        assertTrue("light pressure leaves paper showing (${bare(30)} vs ${bare(80)})", bare(30) > bare(80) + 40)
    }

    @Test
    fun watercolourDriesDarkerAtTheEdge() {
        val doc = Document.blank(400, 160)
        val editor = Editor(doc)
        val brush = BrushLibrary.byId("watercolor_round")
        draw(editor, TestSupport.line(40f, 80f, 360f, 80f, steps = 80), StrokeParams(brush, size = 60f, color = 0xFF2F6DB0.toInt()))
        // Find the top edge of the wash at x = 200 and compare the rim with the middle.
        val top = (40..80).first { inkAt(doc, 200, it) > 10 }
        val rim = (top..top + 3).maxOf { y -> (190..210).sumOf { inkAt(doc, it, y) } }
        val middle = (190..210).sumOf { inkAt(doc, it, 80) }
        assertTrue("rim $rim is darker than the middle $middle", rim > middle * 1.15)
    }

    @Test
    fun bristlesRunDryAlongALongStroke() {
        val doc = Document.blank(1200, 120)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(20f, 60f, 1180f, 60f, steps = 200), StrokeParams(BrushLibrary.byId("oil_paint"), size = 40f))
        fun gaps(x0: Int) = (x0 until x0 + 80).sumOf { x -> (50..70).count { y -> inkAt(doc, x, y) < 30 } }
        assertTrue("start ${gaps(60)} gaps, end ${gaps(1060)} gaps", gaps(1060) > gaps(60) + 100)
    }

    @Test
    fun charcoalLeavesDustBesideTheStroke() {
        val doc = Document.blank(400, 120)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(20f, 60f, 380f, 60f, steps = 80), StrokeParams(BrushLibrary.byId("charcoal"), size = 30f))
        val specks = (20..380).sumOf { x -> (20..42).count { y -> inkAt(doc, x, y) > 15 } + (78..100).count { y -> inkAt(doc, x, y) > 15 } }
        assertTrue("dust specks outside the stroke: $specks", specks in 5..2000)
    }
}
