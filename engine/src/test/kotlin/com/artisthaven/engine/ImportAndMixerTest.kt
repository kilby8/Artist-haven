package com.artisthaven.engine

import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.brush.RenderMode
import com.artisthaven.engine.brush.TipAngle
import com.artisthaven.engine.brush.TipImage
import com.artisthaven.engine.document.BlendMode
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.io.AbrReader
import com.artisthaven.engine.io.FormatException
import com.artisthaven.engine.io.PsdReader
import com.artisthaven.engine.io.PsdWriter
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.abs

class ImportAndMixerTest {

    private fun draw(editor: Editor, points: List<InputPoint>, params: StrokeParams) {
        editor.beginStroke(params, points.first())
        points.drop(1).forEach { editor.strokeTo(it) }
        editor.endStroke()
    }

    // ---- .abr ---------------------------------------------------------------------------------

    /** A star-ish 40 x 30 tip: full paint in a cross, nothing in the corners. */
    private fun tipPixels(w: Int, h: Int) = ByteArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (abs(x - w / 2) < 4 || abs(y - h / 2) < 4) 255.toByte() else 0
    }

    private fun DataOutputStream.unicode(s: String) {
        writeInt(s.length + 1)
        s.forEach { writeChar(it.code) }
        writeChar(0)
    }

    private fun DataOutputStream.key(k: String) {
        if (k.length == 4) { writeInt(0); writeBytes(k) } else { writeInt(k.length); writeBytes(k) }
    }

    private fun DataOutputStream.sampleBody(w: Int, h: Int, rle: Boolean) {
        writeInt(0); writeInt(0); writeInt(h); writeInt(w)
        writeShort(8)
        val px = tipPixels(w, h)
        if (!rle) {
            writeByte(0)
            write(px)
        } else {
            writeByte(1)
            val rows = (0 until h).map { y -> PsdWriter.packBits(px.copyOfRange(y * w, (y + 1) * w)) }
            rows.forEach { writeShort(it.size) }
            rows.forEach { write(it) }
        }
    }

    private fun legacyAbr(): ByteArray {
        val out = ByteArrayOutputStream()
        val o = DataOutputStream(out)
        o.writeShort(2)
        o.writeShort(2)
        // A computed brush.
        val c = ByteArrayOutputStream()
        DataOutputStream(c).apply {
            writeInt(0); writeShort(30); writeShort(48); writeShort(60); writeShort(45); writeShort(80)
        }
        o.writeShort(1); o.writeInt(c.size()); o.write(c.toByteArray())
        // A sampled brush with a name.
        val s = ByteArrayOutputStream()
        DataOutputStream(s).apply {
            writeInt(0); writeShort(15); unicode("Leafy"); writeByte(1); write(ByteArray(8))
            sampleBody(40, 30, rle = true)
        }
        o.writeShort(2); o.writeInt(s.size()); o.write(s.toByteArray())
        return out.toByteArray()
    }

    private fun section(o: DataOutputStream, key: String, data: ByteArray) {
        o.writeBytes("8BIM"); o.writeBytes(key); o.writeInt(data.size); o.write(data)
        repeat((4 - data.size % 4) % 4) { o.writeByte(0) }
    }

    private fun modernAbr(): ByteArray {
        val samp = ByteArrayOutputStream()
        DataOutputStream(samp).apply {
            val body = ByteArrayOutputStream()
            DataOutputStream(body).apply {
                val id = "tip-uuid-1"
                writeByte(id.length); writeBytes(id); write(ByteArray(264))
                sampleBody(40, 30, rle = false)
            }
            writeInt(body.size()); write(body.toByteArray())
            repeat((4 - body.size() % 4) % 4) { writeByte(0) }
        }
        val desc = ByteArrayOutputStream()
        DataOutputStream(desc).apply {
            writeInt(16)
            unicode(""); key("null"); writeInt(1)
            key("Brsh"); writeBytes("VlLs"); writeInt(1)
            // One brushPreset.
            writeBytes("Objc"); unicode(""); key("brushPreset"); writeInt(5)
            key("Nm  "); writeBytes("TEXT"); unicode("Grass Clump")
            key("Brsh"); writeBytes("Objc"); unicode(""); key("sampledBrush"); writeInt(4)
            key("Dmtr"); writeBytes("UntF"); writeBytes("#Pxl"); writeDouble(64.0)
            key("Angl"); writeBytes("UntF"); writeBytes("#Ang"); writeDouble(30.0)
            key("Spcn"); writeBytes("UntF"); writeBytes("#Prc"); writeDouble(40.0)
            key("sampledData"); writeBytes("TEXT"); unicode("tip-uuid-1")
            key("useTipDynamics"); writeBytes("bool"); writeByte(1)
            key("szVr"); writeBytes("Objc"); unicode(""); key("brVr"); writeInt(2)
            key("bVTy"); writeBytes("long"); writeInt(2)
            key("jitter"); writeBytes("UntF"); writeBytes("#Prc"); writeDouble(20.0)
            key("useScatter"); writeBytes("bool"); writeByte(0)
        }
        val out = ByteArrayOutputStream()
        val o = DataOutputStream(out)
        o.writeShort(6); o.writeShort(2)
        section(o, "samp", samp.toByteArray())
        section(o, "desc", desc.toByteArray())
        return out.toByteArray()
    }

    @Test
    fun readsLegacyBrushFile() {
        val result = AbrReader.read(legacyAbr(), "Nature.abr")
        assertEquals(2, result.brushes.size)
        val computed = result.brushes[0]
        assertNull(computed.tipImage)
        assertEquals(48f, computed.defaultSize, 0.01f)
        assertEquals(0.3f, computed.spacing, 0.001f)
        assertEquals(0.6f, computed.roundness, 0.001f)
        assertEquals(0.8f, computed.hardness, 0.001f)
        val sampled = result.brushes[1]
        assertEquals("Leafy", sampled.name)
        assertEquals("Nature", sampled.importedFrom)
        val tip = TipImage.decode(sampled.tipImage!!)!!
        assertEquals(40, tip.size)
        // The 40 x 30 tip sits in the middle of a 40 x 40 square: its corners are empty.
        assertEquals(0, tip.data[0].toInt())
        assertEquals(255, tip.data[20 * 40 + 20].toInt() and 0xFF)
    }

    @Test
    fun readsModernBrushFileWithSettings() {
        val result = AbrReader.read(modernAbr(), "Grass.abr")
        val b = result.brushes.single()
        assertEquals("Grass Clump", b.name)
        assertEquals(64f, b.defaultSize, 0.01f)
        assertEquals(0.4f, b.spacing, 0.001f)
        assertEquals(-30f, b.angleDegrees, 0.01f)
        assertEquals(TipAngle.FIXED, b.tipAngle)
        assertEquals(1f, b.pressureSize, 0.001f)
        assertEquals(0.2f, b.sizeJitter, 0.001f)
        assertNotNull(TipImage.decode(b.tipImage!!))
    }

    @Test
    fun rejectsFilesThatAreNotBrushes() {
        try {
            AbrReader.read("hello world".toByteArray())
            fail("expected a FormatException")
        } catch (_: FormatException) {
        }
    }

    @Test
    fun importedBrushPaintsItsTipShape() {
        val brush = AbrReader.read(legacyAbr(), "Nature.abr").brushes[1].copy(spacing = 2f)
        val doc = Document.blank(200, 120)
        val editor = Editor(doc)
        // One dab: the cross shape shows, its empty corners don't.
        draw(editor, listOf(InputPoint(100f, 60f), InputPoint(100.1f, 60f, time = 8)), StrokeParams(brush, size = 80f, color = 0xFF000000.toInt()))
        val layer = doc.activeLayer!!
        assertTrue(Pixels.alpha(layer.surface.getPixel(100, 60)) > 200)
        assertTrue(Pixels.alpha(layer.surface.getPixel(100, 30)) > 200)
        assertEquals(0, Pixels.alpha(layer.surface.getPixel(80, 40)))
        // Brushes with picture tips survive the brush file round trip.
        val again = com.artisthaven.engine.brush.BrushCodec.decode(com.artisthaven.engine.brush.BrushCodec.encode(listOf(brush))).single()
        assertEquals(brush.tipImage, again.tipImage)
        TestSupport.savePng(doc, "abr_tip.png")
    }

    // ---- .psd ---------------------------------------------------------------------------------

    @Test
    fun psdRoundTripKeepsLayers() {
        val doc = Document.blank(160, 100, paperColor = 0xFFFAF6EE.toInt())
        val editor = Editor(doc)
        draw(editor, TestSupport.line(20f, 50f, 140f, 50f), StrokeParams(BrushLibrary.default, size = 12f, color = 0xFF1A237E.toInt()))
        editor.addLayer()
        editor.setLayerProps(doc.activeLayerId) { it.copy(name = "Glazé", blendMode = BlendMode.MULTIPLY, opacity = 0.5f, clipping = true) }
        draw(editor, TestSupport.line(20f, 30f, 140f, 70f), StrokeParams(BrushLibrary.byId("hard_airbrush"), size = 20f, color = 0xFFE53935.toInt()))
        editor.addLayer()
        editor.setLayerProps(doc.activeLayerId) { it.copy(name = "Off", visible = false) }

        val bytes = ByteArrayOutputStream().also { PsdWriter.write(doc, it) }.toByteArray()
        val result = PsdReader.read(bytes)
        val loaded = result.doc
        assertEquals(160, loaded.width)
        assertEquals(100, loaded.height)
        assertEquals(listOf("Background", "Layer 1", "Glazé", "Off"), loaded.layers.map { it.name })
        val glaze = loaded.layers[2]
        assertEquals(BlendMode.MULTIPLY, glaze.blendMode)
        assertEquals(0.5f, glaze.opacity, 0.01f)
        assertTrue(glaze.clipping)
        assertFalse(loaded.layers[3].visible)
        // Pixels come back to within rounding of premultiplying twice.
        val a = Compositor.flatten(doc)
        val b = Compositor.flatten(loaded)
        var worst = 0
        for (i in a.indices) {
            for (shift in intArrayOf(16, 8, 0)) worst = maxOf(worst, abs(((a[i] shr shift) and 0xFF) - ((b[i] shr shift) and 0xFF)))
        }
        assertTrue("worst channel difference $worst", worst <= 3)
    }

    @Test
    fun bigPsdIsScaledToFit() {
        val doc = Document.blank(400, 200)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(0f, 100f, 400f, 100f), StrokeParams(BrushLibrary.byId("hard_airbrush"), size = 30f, color = 0xFF2E7D32.toInt()))
        val bytes = ByteArrayOutputStream().also { PsdWriter.write(doc, it) }.toByteArray()
        val result = PsdReader.read(bytes, maxSide = 200)
        assertEquals(200, result.doc.width)
        assertEquals(100, result.doc.height)
        assertTrue(result.warnings.any { "scaled" in it })
        val px = result.doc.layers[1].surface.getPixel(100, 50)
        assertTrue(Pixels.alpha(px) > 200)
        assertTrue(Pixels.green(px) > Pixels.red(px))
    }

    @Test
    fun psdWithGroupsAndMaskOpens() {
        // Hand-built: a group (hidden) holding one red layer with a mask hiding its left half.
        val w = 8
        val h = 4
        fun channel(o: DataOutputStream, bytes: ByteArray) { o.writeShort(0); o.write(bytes) }
        val red = ByteArray(w * h) { 255.toByte() }
        val zero = ByteArray(w * h)
        val full = ByteArray(w * h) { 255.toByte() }
        val mask = ByteArray(w * h) { i -> if (i % w < w / 2) 0 else 255.toByte() }

        fun record(o: DataOutputStream, name: String, channels: List<Pair<Int, Int>>, section: Int?, hidden: Boolean, withMask: Boolean) {
            o.writeInt(0); o.writeInt(0); o.writeInt(if (section == null) h else 0); o.writeInt(if (section == null) w else 0)
            o.writeShort(channels.size)
            channels.forEach { (id, len) -> o.writeShort(id); o.writeInt(len) }
            o.writeBytes("8BIM"); o.writeBytes("norm"); o.writeByte(255); o.writeByte(0); o.writeByte(if (hidden) 2 else 0); o.writeByte(0)
            val extra = ByteArrayOutputStream()
            DataOutputStream(extra).apply {
                if (withMask) {
                    writeInt(20); writeInt(0); writeInt(0); writeInt(h); writeInt(w); writeByte(0); writeByte(0); writeShort(0)
                } else writeInt(0)
                writeInt(0)
                val nb = name.toByteArray()
                writeByte(nb.size); write(nb)
                repeat((4 - (1 + nb.size) % 4) % 4) { writeByte(0) }
                if (section != null) {
                    writeBytes("8BIM"); writeBytes("lsct"); writeInt(4); writeInt(section)
                }
            }
            o.writeInt(extra.size()); o.write(extra.toByteArray())
        }

        val info = ByteArrayOutputStream()
        val o = DataOutputStream(info)
        o.writeShort(3)
        val plane = 2 + w * h
        record(o, "</Layer group>", listOf(-1 to 2, 0 to 2, 1 to 2, 2 to 2), section = 3, hidden = false, withMask = false)
        record(o, "Red", listOf(-1 to plane, 0 to plane, 1 to plane, 2 to plane, -2 to plane), section = null, hidden = false, withMask = true)
        record(o, "Folder", listOf(-1 to 2, 0 to 2, 1 to 2, 2 to 2), section = 1, hidden = true, withMask = false)
        repeat(4) { o.writeShort(0) }
        channel(o, full); channel(o, red); channel(o, zero); channel(o, zero); channel(o, mask)
        repeat(4) { o.writeShort(0) }
        if (info.size() % 2 != 0) o.writeByte(0)

        val file = ByteArrayOutputStream()
        DataOutputStream(file).apply {
            writeBytes("8BPS"); writeShort(1); write(ByteArray(6)); writeShort(3); writeInt(h); writeInt(w); writeShort(8); writeShort(3)
            writeInt(0); writeInt(0)
            writeInt(info.size() + 8); writeInt(info.size()); write(info.toByteArray()); writeInt(0)
            writeShort(0); write(ByteArray(w * h * 3))
        }
        val doc = PsdReader.read(file.toByteArray()).doc
        val layer = doc.layers.single()
        assertEquals("Folder / Red", layer.name)
        assertFalse(layer.visible)
        assertNotNull(layer.mask)
        assertEquals(0f, Layer.maskVisibility(layer.mask!!.getPixel(1, 1)), 0.01f)
        assertEquals(1f, Layer.maskVisibility(layer.mask!!.getPixel(6, 1)), 0.01f)
        assertEquals(255, Pixels.red(layer.surface.getPixel(3, 2)))
    }

    // ---- Mixer Brush --------------------------------------------------------------------------

    private fun mixer(load: Float, wet: Float = 0f, mix: Float = 0f, clean: Boolean = true, sampleAll: Boolean = false) =
        BrushPreset(
            id = "test_mixer", name = "Mixer", category = com.artisthaven.engine.brush.BrushCategory.MINE,
            hardness = 1f, spacing = 0.1f, streamline = 0f, pressureSize = 0f, flow = 1f,
            renderMode = RenderMode.MIXER, mixerLoad = load, mixerWet = wet, mixerMix = mix,
            mixerClean = clean, mixerSampleAll = sampleAll,
        )

    @Test
    fun lowLoadRunsDryAndFullLoadDoesNot() {
        for ((load, dry) in listOf(0f to true, 1f to false)) {
            val doc = Document.blank(400, 40)
            val editor = Editor(doc)
            draw(editor, TestSupport.line(5f, 20f, 395f, 20f, steps = 80), StrokeParams(mixer(load), size = 10f, color = 0xFFD32F2F.toInt()))
            val start = Pixels.alpha(doc.activeLayer!!.surface.getPixel(20, 20))
            val end = Pixels.alpha(doc.activeLayer!!.surface.getPixel(380, 20))
            assertTrue("start $start", start > 200)
            if (dry) assertTrue("end $end should have run dry", end < start / 2) else assertTrue("end $end", end > 200)
        }
    }

    @Test
    fun wetBrushDragsColourThroughOtherPaint() {
        val doc = Document.blank(300, 60)
        val editor = Editor(doc)
        // A blue bar, then a wet yellow stroke from the blue out over bare paper.
        draw(editor, TestSupport.line(10f, 30f, 100f, 30f), StrokeParams(BrushLibrary.byId("hard_airbrush"), size = 30f, color = 0xFF1565C0.toInt()))
        draw(editor, TestSupport.line(40f, 30f, 290f, 30f, steps = 60), StrokeParams(mixer(load = 1f, wet = 1f, mix = 0.8f), size = 16f, color = 0xFFFFEB3B.toInt()))
        val dragged = Pixels.unpremultiply(doc.activeLayer!!.surface.getPixel(140, 30))
        // Past the blue, the paint is still bluish-green, not pure yellow.
        assertTrue("blue ${Pixels.blue(dragged)}", Pixels.blue(dragged) > 60)
        TestSupport.savePng(doc, "mixer_drag.png")
    }

    @Test
    fun blenderOnEmptyLayerSmearsWhatIsBelow() {
        val doc = Document.blank(240, 80)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(10f, 40f, 110f, 40f), StrokeParams(BrushLibrary.byId("hard_airbrush"), size = 50f, color = 0xFFC2185B.toInt()))
        editor.addLayer()
        draw(editor, TestSupport.line(60f, 40f, 220f, 40f, steps = 60), StrokeParams(BrushLibrary.byId("mixer_blender"), size = 30f, color = 0xFF000000.toInt()))
        val top = doc.activeLayer!!.surface
        // The blend lands on the new layer, carrying pink past the edge of the original paint.
        val c = Pixels.unpremultiply(top.getPixel(130, 40))
        assertTrue("alpha ${Pixels.alpha(c)}", Pixels.alpha(c) > 40)
        assertTrue("red ${Pixels.red(c)}", Pixels.red(c) > 120)
        assertTrue("never the black brush colour", Pixels.red(c) + Pixels.green(c) + Pixels.blue(c) > 150)
        TestSupport.savePng(doc, "mixer_blender.png")
    }

    @Test
    fun dirtyBrushKeepsColourBetweenStrokes() {
        fun secondStrokeBlue(clean: Boolean): Int {
            val doc = Document.blank(300, 80)
            val editor = Editor(doc)
            val b = mixer(load = 1f, wet = 1f, mix = 0.7f, clean = clean)
            draw(editor, TestSupport.line(10f, 20f, 100f, 20f), StrokeParams(BrushLibrary.byId("hard_airbrush"), size = 24f, color = 0xFF1565C0.toInt()))
            draw(editor, TestSupport.line(20f, 20f, 120f, 20f), StrokeParams(b, size = 14f, color = 0xFFFFFFFF.toInt()))
            draw(editor, TestSupport.line(150f, 60f, 290f, 60f), StrokeParams(b, size = 14f, color = 0xFFFFFFFF.toInt()))
            val c = Pixels.unpremultiply(doc.activeLayer!!.surface.getPixel(160, 60))
            return Pixels.blue(c) - Pixels.red(c)
        }
        assertTrue(secondStrokeBlue(clean = false) > secondStrokeBlue(clean = true) + 20)
    }

    @Test
    fun mixerStrokeUndoes() {
        val doc = Document.blank(200, 60)
        val editor = Editor(doc)
        draw(editor, TestSupport.line(10f, 30f, 190f, 30f), StrokeParams(BrushLibrary.byId("mixer_oil"), size = 30f, color = 0xFF6D4C41.toInt()))
        assertFalse(doc.activeLayer!!.surface.isBlank())
        editor.undo()
        assertTrue(doc.activeLayer!!.surface.isBlank())
    }

}
