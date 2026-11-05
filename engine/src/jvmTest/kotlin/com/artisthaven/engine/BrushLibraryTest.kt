package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushCategory
import com.artisthaven.engine.brush.BrushCodec
import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.brush.GrainTexture
import com.artisthaven.engine.brush.TipShape
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wider brush library: every brush paints, ids are unique, and sheets for looking at. */
class BrushLibraryTest {
    private fun draw(editor: Editor, points: List<InputPoint>, params: StrokeParams) {
        editor.beginStroke(params, points.first())
        points.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
        editor.tickWater(60_000.0)
    }

    private val palette = intArrayOf(
        0xFF2E3A59.toInt(), 0xFFC2185B.toInt(), 0xFF2E7D32.toInt(), 0xFF1565C0.toInt(),
        0xFFEF6C00.toInt(), 0xFF6A1B9A.toInt(), 0xFF00838F.toInt(), 0xFF8D6E63.toInt(),
    )

    @Test
    fun idsAreUniqueAndTheLibraryIsBig() {
        val ids = BrushLibrary.builtIn.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("${ids.size} brushes", ids.size >= 180)
        // Every category the library lists has brushes in it.
        for (c in BrushCategory.entries) if (c != BrushCategory.MINE) {
            assertTrue("$c is empty", BrushLibrary.builtIn.any { it.category == c })
        }
        // Brushes stay grouped by category, in category order.
        val order = BrushLibrary.builtIn.map { it.category.ordinal }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun everyBrushPaints() {
        for (brush in BrushLibrary.builtIn) {
            if (brush.blendOnly || (brush.renderMode == com.artisthaven.engine.brush.RenderMode.MIXER && brush.mixerLoad == 0f)) continue
            val doc = Document.blank(300, 140, paperColor = 0xFFFFFFFF.toInt())
            val editor = Editor(doc)
            draw(editor, TestSupport.sCurve(20f, 20f, 260f, 100f), StrokeParams(brush, color = 0xFF1A237E.toInt(), size = brush.defaultSize.coerceIn(6f, 60f), usePressure = true))
            val layer = doc.layers.first()
            var painted = 0
            for (y in 0 until doc.height step 2) for (x in 0 until doc.width step 2) if (Pixels.alpha(layer.surface.getPixel(x, y)) > 20) painted++
            assertTrue("${brush.id} painted only $painted px", painted > 25)
        }
    }

    @Test
    fun customBrushSettingsRoundTrip() {
        val b = BrushLibrary.byId("stamp_stars").copy(id = "mine_1", name = "Mine", category = BrushCategory.MINE, hueJitter = 0.4f, count = 5, grainInvert = true)
        val back = BrushCodec.decode(BrushCodec.encodeFull(b)).single()
        assertEquals(b, back)
        // Brushes saved before these settings existed still load, with the new ones at defaults.
        val old = BrushCodec.decode("""{"id":"x","name":"Old","category":"MINE","tip":"ROUND"}""").single()
        assertEquals(1, old.count)
        assertEquals(0f, old.scatter)
    }

    @Test
    fun scatterAndCountSpreadDabsOut() {
        fun width(brush: BrushPreset): Int {
            val doc = Document.blank(400, 200)
            val editor = Editor(doc)
            draw(editor, TestSupport.line(50f, 100f, 350f, 100f), StrokeParams(brush, color = 0xFF000000.toInt(), size = 10f))
            val s = doc.layers.first().surface
            return (0 until 200).count { y -> (50 until 350).any { x -> Pixels.alpha(s.getPixel(x, y)) > 10 } }
        }
        val plain = BrushLibrary.byId("technical_pen")
        assertTrue(width(plain.copy(scatter = 3f, count = 6)) > width(plain) * 3)
    }

    @Test
    fun colourJitterVariesTheColour() {
        val doc = Document.blank(400, 120)
        val editor = Editor(doc)
        val brush = BrushLibrary.byId("colour_noise")
        draw(editor, TestSupport.line(30f, 60f, 370f, 60f), StrokeParams(brush, color = 0xFFD32F2F.toInt(), size = 30f))
        val s = doc.layers.first().surface
        val hues = HashSet<Int>()
        for (x in 30 until 370 step 4) {
            val c = Pixels.unpremultiply(s.getPixel(x, 60))
            if (Pixels.alpha(c) > 200) hues += (Pixels.red(c) / 32) * 64 + (Pixels.green(c) / 32) * 8 + Pixels.blue(c) / 32
        }
        assertTrue("${hues.size} colours", hues.size >= 5)
    }

    /** One sheet per category, to look at. */
    @Test
    fun categorySheets() {
        for (cat in BrushCategory.entries) {
            val list = BrushLibrary.builtIn.filter { it.category == cat }
            if (list.isEmpty()) continue
            val rowH = 120
            val doc = Document.blank(1240, rowH * ((list.size + 1) / 2) + 20, paperColor = 0xFFFBF8F1.toInt())
            val editor = Editor(doc)
            if (cat == BrushCategory.LIGHT || cat == BrushCategory.ELEMENTS) {
                draw(editor, TestSupport.line(0f, doc.height / 2f, 1240f, doc.height / 2f), StrokeParams(BrushLibrary.byId("flat_airbrush"), color = 0xFF1B2440.toInt(), size = doc.height * 1.2f))
            }
            list.forEachIndexed { i, brush ->
                val x = 30f + (i % 2) * 620f
                val y = 15f + (i / 2) * rowH
                if (brush.blendOnly || cat == BrushCategory.PAINTING) {
                    draw(editor, TestSupport.line(x + 160f, y + 10f, x + 160f, y + 95f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = 0xFFFDD835.toInt(), size = 50f))
                    draw(editor, TestSupport.line(x + 330f, y + 10f, x + 330f, y + 95f), StrokeParams(BrushLibrary.byId("hard_airbrush"), color = 0xFF3949AB.toInt(), size = 50f))
                }
                val color = when (cat) {
                    BrushCategory.LIGHT -> 0xFF4FC3F7.toInt()
                    BrushCategory.ELEMENTS -> if (brush.id == "fire") 0xFFFF7043.toInt() else 0xFFE3F2FD.toInt()
                    BrushCategory.NATURE -> 0xFF388E3C.toInt()
                    else -> palette[i % palette.size]
                }
                draw(editor, TestSupport.sCurve(x, y, 560f, 95f, steps = 140), StrokeParams(brush, color = color, size = brush.defaultSize.coerceIn(6f, 60f), usePressure = true, seed = i + 3))
            }
            TestSupport.savePng(doc, "brushes-${cat.name.lowercase()}.png")
        }
    }

    @Test
    fun tipsAndPatternsSheet() {
        val tips = TipShape.entries.filter { it.stamp }
        val doc = Document.blank(1240, 140 + 130 * ((GrainTexture.entries.size + 5) / 6), paperColor = 0xFFFFFFFF.toInt())
        val editor = Editor(doc)
        val base = BrushLibrary.byId("stamp_dots")
        tips.forEachIndexed { i, t ->
            val p = InputPoint(40f + i * 58f, 50f)
            draw(editor, listOf(p, p.copy(x = p.x + 0.1f)), StrokeParams(base.copy(tip = t, sizeJitter = 0f, rotationJitter = 0f, flipJitter = 0f, spacing = 5f), color = 0xFF111111.toInt(), size = 54f))
        }
        GrainTexture.entries.forEachIndexed { i, g ->
            val x = 20f + (i % 6) * 205f
            val y = 140f + (i / 6) * 130f
            val brush = BrushLibrary.byId("pattern_halftone").copy(grain = g, grainDepth = 1f, tooth = 0f)
            draw(editor, TestSupport.line(x + 40f, y + 60f, x + 150f, y + 60f), StrokeParams(brush, color = 0xFF263238.toInt(), size = 100f))
        }
        TestSupport.savePng(doc, "tips-and-patterns.png")
    }
}
