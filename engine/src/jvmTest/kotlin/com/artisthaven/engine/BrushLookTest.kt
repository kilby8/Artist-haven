package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.brush.BrushCategory
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.stroke.Tool
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
    /** Time of the next stroke. Strokes are [gap] ms apart, so watercolour has dried by default. */
    private var clock = 0L

    private fun draw(editor: Editor, points: List<InputPoint>, params: StrokeParams, gap: Long = 30_000L) {
        val pts = points.map { it.copy(time = it.time + clock) }
        editor.beginStroke(params, pts.first())
        pts.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
        clock = pts.last().time + gap
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
        // Round 2 brushes.
        sheet("heavy_paint", 50f, 0xFFD08A2E.toInt())
        sheet("palette_knife", 50f, 0xFF3C7A8C.toInt())
        sheet("dry_brush", 44f, 0xFF5B3A29.toInt())
        sheet("flat_acrylic", 40f, 0xFF2E7D4F.toInt())
        sheet("filbert", 36f, 0xFF8E3B5E.toInt())
        sheet("oil_glaze", 70f, 0xFFB5462B.toInt())
        sheet("flat_wash", 60f, 0xFF2F6DB0.toInt())
        sheet("wet_in_wet", 90f, 0xFF7A3E9D.toInt())
        sheet("dry_watercolour", 40f, 0xFF2E5E8C.toInt())
        sheet("detail_round", 10f, 0xFF1F4E3D.toInt())
        sheet("granulating_wash", 70f, 0xFF3D5A80.toInt())
        sheet("mechanical_pencil", 3f, graphite)
        sheet("brush_pen", 16f, 0xFF111111.toInt())
        sheet("fineliner", 3f, 0xFF111111.toInt())
        sheet("oil_pastel", 30f, 0xFFE07A1F.toInt())
        sheet("wax_crayon", 14f, 0xFFC0392B.toInt())
        sheet("chalk", 22f, 0xFF4A6FA5.toInt())
        for (b in BrushLibrary.builtIn.filter { it.category == BrushCategory.BLENDING }) blendSheet(b.id)
        blendSheet("soft_airbrush", Tool.SMUDGE)
    }

    /** Three colour bands, then the blender dragged across them in a few ways. */
    private fun blendSheet(id: String, tool: Tool = Tool.BRUSH) {
        clock = 0L
        val doc = Document.blank(700, 420)
        val editor = Editor(doc)
        val fill = BrushLibrary.byId("hard_airbrush")
        val colours = intArrayOf(0xFFC0392B.toInt(), 0xFFF1C40F.toInt(), 0xFF2F6DB0.toInt())
        for ((k, c) in colours.withIndex()) {
            for (row in 0 until 3) {
                val y = 40f + k * 120f + row * 36f
                draw(editor, TestSupport.line(20f, y, 680f, y, steps = 40), StrokeParams(fill, size = 44f, color = c))
            }
        }
        val brush = BrushLibrary.byId(id)
        fun p(seed: Int) = StrokeParams(brush, tool = tool, size = 60f, opacity = 1f, usePressure = true, seed = seed)
        // Straight drags down across all three bands, at three pressures.
        for ((k, pr) in listOf(0.3f, 0.6f, 1f).withIndex()) {
            val x = 80f + k * 90f
            draw(editor, ramp(x, 20f, x + 10f, 400f, pr, pr), p(k))
        }
        // Circular scrubbing.
        draw(editor, (0..160).map { i ->
            val t = i / 160f * 6f * PI.toFloat()
            InputPoint(480f + kotlin.math.cos(t) * (40f + i * 0.3f), 200f + sin(t) * (60f + i * 0.4f), 0.7f, tilt = 0.3f, time = i * 8L)
        }, p(9))
        // A zig-zag.
        draw(editor, (0..120).map { i ->
            val t = i / 120f
            InputPoint(360f + t * 300f, 330f + (if ((i / 15) % 2 == 0) (i % 15) * 4f else (15 - i % 15) * 4f), 0.7f, tilt = 0.3f, time = i * 8L)
        }, p(10))
        TestSupport.savePng(doc, "brushes/${System.getProperty("brushTag") ?: "now"}-blend-$id.png")
    }

    /** Luminance 0..255. */
    private fun lumAt(doc: Document, x: Int, y: Int): Int {
        val c = com.artisthaven.engine.document.Compositor.sample(doc, x, y)
        return ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 6 + (c and 0xFF)) / 10
    }

    @Test
    fun heavyPaintStandsUpWithLitRidgesAndGrooves() {
        clock = 0L
        fun stroke(relief: Boolean): Document {
            val doc = Document.blank(600, 160)
            val editor = Editor(doc)
            var brush = BrushLibrary.byId("heavy_paint")
            if (!relief) brush = brush.copy(relief = 0f, impasto = 0f)
            draw(editor, ramp(40f, 80f, 560f, 80f, 0.8f, 0.8f), StrokeParams(brush, size = 60f, color = 0xFFD08A2E.toInt(), usePressure = true))
            return doc
        }
        val lit = stroke(true)
        val flat = stroke(false)
        // Per column in the middle of the stroke: brightest near the top edge, darkest near the
        // bottom edge, against the middle.
        var topGain = 0
        var bottomDrop = 0
        var grooves = 0.0
        var flatGrooves = 0.0
        for (x in 200 until 400) {
            val mid = lumAt(flat, x, 80)
            topGain += (52..66).maxOf { lumAt(lit, x, it) } - mid
            bottomDrop += mid - (94..108).minOf { lumAt(lit, x, it) }
            fun spread(d: Document): Double {
                val v = (66..94).map { lumAt(d, x, it).toDouble() }
                val m = v.average()
                return v.sumOf { (it - m) * (it - m) } / v.size
            }
            grooves += spread(lit)
            flatGrooves += spread(flat)
        }
        println("heavy paint: top ridge +${topGain / 200}, bottom shadow -${bottomDrop / 200}, grooves ${grooves / 200} vs flat ${flatGrooves / 200}")
        assertTrue("lit ridge along the top edge (+${topGain / 200})", topGain / 200 > 12)
        assertTrue("shadow along the bottom edge (-${bottomDrop / 200})", bottomDrop / 200 > 12)
        assertTrue("grooves ${grooves / 200} vs flat ${flatGrooves / 200}", grooves > flatGrooves * 2)
    }

    @Test
    fun flatBrushesLeaveNoSquareAtTheStart() {
        for (id in listOf("oil_paint", "gouache", "heavy_paint", "palette_knife")) {
            clock = 0L
            val doc = Document.blank(200, 400)
            val editor = Editor(doc)
            // A vertical stroke down from (100, 100); the brush is 60 wide.
            draw(editor, ramp(100f, 100f, 100f, 360f, 0.7f, 0.7f).map { it.copy(y = it.y, x = 100f) },
                StrokeParams(BrushLibrary.byId(id), size = 60f, color = 0xFF2E5E8C.toInt(), usePressure = true))
            // Above the start: a dab drawn the wrong way round reaches up to 30 px above it.
            val above = (60..140).sumOf { x -> (70..82).count { y -> inkAt(doc, x, y) > 20 } }
            println("$id ink above the start: $above")
            assertTrue("$id leaves $above inked pixels above the start", above < 40)
        }
    }

    /** Red at the top, blue below; a blender dragged down from the red into the blue. */
    private fun dragRedIntoBlue(id: String, tool: Tool): Pair<Double, Int> {
        clock = 0L
        val doc = Document.blank(300, 300)
        val editor = Editor(doc)
        val fill = BrushLibrary.byId("hard_airbrush")
        for (y in 20..140 step 20) draw(editor, TestSupport.line(10f, y.toFloat(), 290f, y.toFloat(), steps = 20), StrokeParams(fill, size = 30f, color = 0xFFD02020.toInt()))
        for (y in 160..280 step 20) draw(editor, TestSupport.line(10f, y.toFloat(), 290f, y.toFloat(), steps = 20), StrokeParams(fill, size = 30f, color = 0xFF2040D0.toInt()))
        draw(editor, ramp(150f, 60f, 150f, 260f, 0.8f, 0.8f).map { it.copy(x = 150f) },
            StrokeParams(BrushLibrary.byId(id), tool = tool, size = 60f, opacity = 1f, usePressure = true))
        TestSupport.savePng(doc, "brushes/${System.getProperty("brushTag") ?: "now"}-drag-$id-${tool.name.lowercase()}.png")
        // Across the stroke, just inside the blue: how much the redness varies from column to column.
        var variance = 0.0
        var reddish = 0
        for (y in 175..200) {
            val reds = (130..170).map { x ->
                val c = com.artisthaven.engine.document.Compositor.sample(doc, x, y)
                ((c shr 16 and 0xFF) - (c and 0xFF)).toDouble()
            }
            val m = reds.average()
            variance += reds.sumOf { (it - m) * (it - m) } / reds.size
            reddish += reds.count { it > 0 }
        }
        return variance / 26 to reddish
    }

    @Test
    fun bristleBlendDragsStreaksOfBothColours() {
        java.io.File(TestSupport.outputDir, "brushes").mkdirs()
        val (bristle, bristleRed) = dragRedIntoBlue("bristle_blend", Tool.SMUDGE)
        val (bristleBrush, _) = dragRedIntoBlue("bristle_blend", Tool.BRUSH)
        val (soft, _) = dragRedIntoBlue("soft_blend", Tool.SMUDGE)
        val (oldDefault, _) = dragRedIntoBlue("soft_airbrush", Tool.SMUDGE)
        println("redness variance across the smear: bristle ${bristle.toInt()} (brush ${bristleBrush.toInt()}, red columns $bristleRed), soft ${soft.toInt()}, old default ${oldDefault.toInt()}")
        assertTrue("bristle smear variance $bristle vs soft $soft", bristle > soft * 4 && bristle > 400)
        assertTrue("bristle smear variance with the brush $bristleBrush", bristleBrush > 400)
        assertTrue("some red streaks reach into the blue ($bristleRed)", bristleRed > 100)
    }

    @Test
    fun blendingCategoryHasRealChoices() {
        val blenders = BrushLibrary.builtIn.filter { it.category == BrushCategory.BLENDING }
        assertTrue("${blenders.size} blending brushes", blenders.size >= 5)
        assertTrue("the smudge tool defaults to a blender", BrushLibrary.defaultSmudge.category == BrushCategory.BLENDING)
        assertTrue("${BrushLibrary.builtIn.size} brushes", BrushLibrary.builtIn.size >= 36)
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
        // Along the middle of the stroke, find the top edge of the wash in each column and compare
        // the rim with the middle. The rim varies along the edge, so this is an average.
        val (rim, middle) = rimAndMiddle(doc, 100..300)
        assertTrue("rim $rim is darker than the middle $middle", rim.sum() > middle.sum() * 1.15)
    }

    /** Per 20-pixel window: rim darkness at the top edge and darkness in the middle (y = 80). */
    private fun rimAndMiddle(doc: Document, xs: IntRange): Pair<List<Int>, List<Int>> {
        val rims = ArrayList<Int>()
        val mids = ArrayList<Int>()
        for (x0 in xs step 20) {
            var rim = 0
            var mid = 0
            for (x in x0 until x0 + 20) {
                val top = (20..80).firstOrNull { inkAt(doc, x, it) > 10 } ?: continue
                rim += (top..top + 4).maxOf { y -> inkAt(doc, x, y) }
                mid += inkAt(doc, x, 80)
            }
            rims += rim
            mids += mid
        }
        return rims to mids
    }

    /** Ink across row [y] of a vertical wash at x = 200: (rim at its left edge) minus (its middle). */
    private fun crossingRim(gap: Long, save: String? = null): Int {
        clock = 0L
        val doc = Document.blank(400, 200)
        val editor = Editor(doc)
        val brush = BrushLibrary.byId("watercolor_round")
        val blue = StrokeParams(brush, size = 70f, color = 0xFF2F6DB0.toInt())
        draw(editor, TestSupport.line(30f, 100f, 370f, 100f, steps = 80), blue, gap = gap)
        draw(editor, TestSupport.line(200f, 20f, 200f, 180f, steps = 50), blue.copy(color = 0xFFC0392B.toInt()))
        if (save != null) TestSupport.savePng(doc, "brushes/$save.png")
        // A dark rim or a hard edge shows as a sharp step in ink across the row.
        var total = 0
        for (y in 92..108) total += (140 until 260).maxOf { kotlin.math.abs(inkAt(doc, it + 1, y) - inkAt(doc, it, y)) }
        return total
    }

    @Test
    fun wetInWetLeavesNoRimInsideTheWetWash() {
        java.io.File(TestSupport.outputDir, "brushes").mkdirs()
        val dry = crossingRim(gap = 30_000L, save = "${System.getProperty("brushTag") ?: "now"}-crossing-dry")
        val wet = crossingRim(gap = 2_000L, save = "${System.getProperty("brushTag") ?: "now"}-crossing-wet")
        println("crossing rim: dry $dry, wet $wet")
        assertTrue("rim inside a wet wash $wet against $dry when dry", wet < dry * 0.4)
    }

    @Test
    fun watercolourRimVariesAlongTheEdge() {
        val doc = Document.blank(1000, 160)
        val editor = Editor(doc)
        val brush = BrushLibrary.byId("watercolor_round")
        draw(editor, TestSupport.line(40f, 80f, 960f, 80f, steps = 200), StrokeParams(brush, size = 60f, color = 0xFF2F6DB0.toInt()))
        val (rim, middle) = rimAndMiddle(doc, 100..880)
        val contrast = rim.indices.map { (rim[it] - middle[it]).toDouble() / middle[it].coerceAtLeast(1) }
        val mean = contrast.average()
        val spread = kotlin.math.sqrt(contrast.sumOf { (it - mean) * (it - mean) } / contrast.size) / mean
        println("watercolour rim contrast mean $mean relative spread $spread")
        // The control (one even rim) measured 0.20.
        assertTrue("rim contrast relative spread $spread (mean $mean)", mean > 0.15 && spread > 0.35)
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
