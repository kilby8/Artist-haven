@file:OptIn(ExperimentalJsExport::class)

package com.artisthaven.web

import com.artisthaven.engine.Editor
import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.brush.BrushCategory
import com.artisthaven.engine.brush.BrushCodec
import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.brush.GrainTexture
import com.artisthaven.engine.brush.TipAngle
import com.artisthaven.engine.brush.TipShape
import com.artisthaven.engine.brush.RenderMode
import com.artisthaven.engine.document.BlendMode
import com.artisthaven.engine.document.Change
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.document.LayerProps
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.raster.TiledSurface
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.stroke.Symmetry
import com.artisthaven.engine.stroke.Tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.sin

/**
 * The engine as the web app's worker sees it. Only numbers, strings and typed arrays cross this
 * boundary. Pixels go out as Int32Arrays whose bytes are R, G, B, A (little-endian ABGR ints),
 * so JavaScript can wrap them in an ImageData without copying.
 */
@JsExport
class WebEditor private constructor(private val doc: Document) {
    private val editor = Editor(doc)
    private var dirty: IRect? = null
    private var structure = false
    private val dirtyLayers = HashSet<String>()

    // ---- Changes ----------------------------------------------------------------------------------

    private fun apply(c: Change?, layer: String? = null, all: Boolean = false) {
        val settled = editor.takeSettledChange()
        val change = (c ?: Change.NONE).merge(settled)
        val d = change.dirty
        if (d != null) {
            dirty = IRect.unionOf(dirty, d.inset(1).intersect(doc.bounds))
            if (all || layer == null || settled.dirty != null) markAll() else dirtyLayers.add(layer)
        }
        if (change.layersChanged) {
            structure = true
            markAll()
        }
    }

    private fun markAll() {
        doc.layers.forEach { dirtyLayers.add(it.id) }
        dirtyLayers.add(PROJECT)
    }

    /** The area to redraw since the last call, as [left, top, right, bottom, layersChanged], or empty. */
    fun takeDirty(): IntArray {
        val d = dirty
        val s = structure
        dirty = null
        structure = false
        if (d == null) return if (s) intArrayOf(0, 0, 0, 0, 1) else IntArray(0)
        return intArrayOf(d.left, d.top, d.right, d.bottom, if (s) 1 else 0)
    }

    /** Layer ids changed since the last save, plus "project" if the layer list or paper changed. */
    fun takeUnsavedLayers(): Array<String> = dirtyLayers.toTypedArray().also { dirtyLayers.clear() }

    fun markEverythingUnsaved() = markAll()

    val revision: Double get() = editor.revision.toDouble()
    val width: Int get() = doc.width
    val height: Int get() = doc.height

    // ---- Display ----------------------------------------------------------------------------------

    /** Composites a rectangle with the paper, ready for an ImageData of (right-left) x (bottom-top). */
    fun render(left: Int, top: Int, right: Int, bottom: Int): IntArray {
        val r = IRect(left, top, right, bottom).intersect(doc.bounds)
        val w = r.right - r.left
        val h = r.bottom - r.top
        if (w <= 0 || h <= 0) return IntArray(0)
        val out = IntArray(w * h)
        Compositor.composite(doc, r, out, r.left, r.top, w, withPaper = true)
        toRgba(out)
        return out
    }

    fun flatten(withPaper: Boolean): IntArray = toRgba(Compositor.flatten(doc, withPaper))

    /** [width, height, pixels...] scaled to fit in maxSize. */
    fun thumbnail(maxSize: Int): IntArray {
        val (px, w, h) = Compositor.thumbnail(doc, maxSize, withPaper = true)
        toRgba(px)
        return intArrayOf(w, h) + px
    }

    /** One layer on a checkerboard-free transparent background, [width, height, pixels...]. */
    fun layerThumbnail(id: String, maxSize: Int): IntArray {
        val layer = doc.layer(id) ?: return intArrayOf(0, 0)
        val scale = minOf(maxSize.toFloat() / doc.width, maxSize.toFloat() / doc.height, 1f)
        val tw = maxOf(1, (doc.width * scale).toInt())
        val th = maxOf(1, (doc.height * scale).toInt())
        val out = IntArray(2 + tw * th)
        out[0] = tw
        out[1] = th
        for (y in 0 until th) {
            val sy = minOf(doc.height - 1, ((y + 0.5f) / scale).toInt())
            for (x in 0 until tw) {
                val sx = minOf(doc.width - 1, ((x + 0.5f) / scale).toInt())
                out[2 + y * tw + x] = abgr(Pixels.unpremultiply(layer.surface.getPixel(sx, sy)))
            }
        }
        return out
    }

    fun sampleColor(x: Int, y: Int): Int = editor.sampleColor(x, y) or (0xFF shl 24)

    // ---- Strokes ----------------------------------------------------------------------------------

    /**
     * tool: 0 brush, 1 eraser, 2 smudge. symmetry: 0 off, 1 vertical, 2 horizontal, 3 quadrant.
     * Returns false if the active layer can't be painted (hidden layers can't).
     */
    fun beginStroke(
        brushId: String, tool: Int, size: Float, opacity: Float, color: Int, symmetry: Int,
        usePressure: Boolean, extraStreamline: Float, paintLoad: Float,
        x: Float, y: Float, pressure: Float, tilt: Float, azimuth: Float, time: Double,
    ): Boolean {
        if (editor.isStroking) endStroke()
        val layer = doc.activeLayer ?: return false
        if (!layer.visible) return false
        val params = StrokeParams(
            brush = BrushLibrary.byId(brushId),
            tool = when (tool) { 1 -> Tool.ERASER; 2 -> Tool.SMUDGE; else -> Tool.BRUSH },
            size = size,
            opacity = opacity,
            color = color,
            symmetry = Symmetry.entries.getOrElse(symmetry) { Symmetry.NONE },
            usePressure = usePressure,
            extraStreamline = extraStreamline,
            paintLoad = paintLoad,
        )
        apply(editor.beginStroke(params, InputPoint(x, y, pressure, tilt, azimuth, time.toLong())), layer.id)
        return editor.isStroking
    }

    fun strokeTo(x: Float, y: Float, pressure: Float, tilt: Float, azimuth: Float, time: Double) {
        apply(editor.strokeTo(InputPoint(x, y, pressure, tilt, azimuth, time.toLong())), doc.activeLayerId)
    }

    fun endStroke() {
        val id = doc.activeLayerId
        apply(editor.endStroke(), id)
    }

    fun cancelStroke() {
        apply(editor.cancelStroke(), doc.activeLayerId)
    }

    val isStroking: Boolean get() = editor.isStroking

    // ---- Watercolour water -----------------------------------------------------------------------

    /** True while watercolour is wet or damp: keep calling [tickWater] every frame. */
    val isWaterActive: Boolean get() = editor.isWaterActive

    /** Lets the water flow for [elapsedMillis]. Returns true if undo/redo availability may have changed. */
    fun tickWater(elapsedMillis: Double): Boolean {
        val undoBefore = editor.history.canUndo
        val rev = editor.revision
        apply(editor.tickWater(elapsedMillis), all = true)
        return rev != editor.revision || undoBefore != editor.history.canUndo
    }

    /** Dries all wet watercolour where it is. */
    fun dryWater() = apply(editor.dryWater(), all = true)

    /** Snaps the stroke in progress to a line, circle or shape. True if it did. */
    fun quickShape(): Boolean {
        val c = editor.quickShape() ?: return false
        apply(c, doc.activeLayerId)
        return true
    }

    // ---- Tools ------------------------------------------------------------------------------------

    fun fill(x: Int, y: Int, color: Int, tolerance: Float, allLayers: Boolean) =
        apply(editor.fill(x, y, color, tolerance, allLayers), doc.activeLayerId)

    fun undo() = apply(editor.undo(), all = true)
    fun redo() = apply(editor.redo(), all = true)
    val canUndo: Boolean get() = editor.history.canUndo
    val canRedo: Boolean get() = editor.history.canRedo

    fun setPaper(color: Int, visible: Boolean) {
        apply(editor.setPaper(color, visible), all = true)
        dirtyLayers.add(PROJECT)
    }

    val paperColor: Int get() = doc.paperColor
    val paperVisible: Boolean get() = doc.paperVisible

    // ---- Layers -----------------------------------------------------------------------------------

    /** The layer list, bottom first, as JSON. */
    fun layersJson(): String = buildJsonArray {
        for (l in doc.layers) add(buildJsonObject {
            put("id", l.id)
            put("name", l.name)
            put("visible", l.visible)
            put("opacity", l.opacity)
            put("blendMode", l.blendMode.name)
            put("alphaLock", l.alphaLock)
            put("clipping", l.clipping)
            put("active", l.id == doc.activeLayerId)
        })
    }.toString()

    val activeLayerId: String get() = doc.activeLayerId

    fun addLayer() = apply(editor.addLayer(), all = true)
    fun duplicateLayer(id: String) = apply(editor.duplicateLayer(id), all = true)
    fun deleteLayer(id: String) = apply(editor.deleteLayer(id), all = true)
    fun selectLayer(id: String) {
        apply(editor.selectLayer(id))
        dirtyLayers.add(PROJECT)
    }
    fun moveLayer(from: Int, to: Int) = apply(editor.moveLayer(from, to), all = true)
    fun mergeDown(id: String) = apply(editor.mergeDown(id), all = true)
    fun clearLayer(id: String) = apply(editor.clearLayer(id), id)

    fun setLayerProps(
        id: String, name: String, visible: Boolean, opacity: Float, blendMode: String,
        alphaLock: Boolean, clipping: Boolean,
    ) {
        apply(editor.setLayerProps(id) { props(name, visible, opacity, blendMode, alphaLock, clipping) }, all = true)
    }

    /** Live opacity while a slider moves; call [commitLayerOpacity] when it is released. */
    fun previewLayerOpacity(id: String, opacity: Float) {
        val layer = doc.layer(id) ?: return
        if (previewBefore == null) previewBefore = layer.props
        apply(editor.previewLayerProps(id) { it.copy(opacity = opacity) }, all = true)
    }

    private var previewBefore: LayerProps? = null

    fun commitLayerOpacity(id: String) {
        val before = previewBefore ?: return
        previewBefore = null
        apply(editor.commitLayerProps(id, before), all = true)
    }

    /** Adds a picture as a new layer, centred. Pixels are RGBA bytes (ABGR ints). */
    fun addImageLayer(name: String, rgba: IntArray, width: Int, height: Int) {
        val argb = IntArray(rgba.size) { fromRgba(rgba[it]) }
        apply(editor.addImageLayer(name, argb, width, height), all = true)
    }

    // ---- Saving -----------------------------------------------------------------------------------

    /** Project settings as JSON: size, paper and active layer, plus the layer list. */
    fun projectJson(): String = buildJsonObject {
        put("width", doc.width)
        put("height", doc.height)
        put("paperColor", doc.paperColor)
        put("paperVisible", doc.paperVisible)
        put("activeLayerId", doc.activeLayerId)
        put("layers", JsonArray(doc.layers.map { l ->
            buildJsonObject {
                put("id", l.id)
                put("name", l.name)
                put("visible", l.visible)
                put("opacity", l.opacity)
                put("blendMode", l.blendMode.name)
                put("alphaLock", l.alphaLock)
                put("clipping", l.clipping)
            }
        }))
    }.toString()

    /** A layer's painted tiles: [count, then index and 4096 premultiplied ARGB pixels per tile]. */
    fun layerData(id: String): IntArray {
        val layer = doc.layer(id) ?: return IntArray(1)
        val s = layer.surface
        val indices = s.nonEmptyTileIndices()
        val per = TiledSurface.TILE * TiledSurface.TILE
        val out = IntArray(1 + indices.size * (1 + per))
        out[0] = indices.size
        var o = 1
        for (i in indices) {
            out[o++] = i
            s.tile(i)!!.copyInto(out, o)
            o += per
        }
        return out
    }

    /** Puts back a saved layer on top of the stack (used when opening a project). */
    fun restoreLayer(
        id: String, name: String, visible: Boolean, opacity: Float, blendMode: String,
        alphaLock: Boolean, clipping: Boolean, data: IntArray,
    ) {
        val surface = TiledSurface(doc.width, doc.height)
        val per = TiledSurface.TILE * TiledSurface.TILE
        if (data.isNotEmpty()) {
            val count = data[0]
            var o = 1
            repeat(count) {
                val index = data[o++]
                if (index in 0 until surface.tileCount && o + per <= data.size) {
                    surface.setTile(index, data.copyOfRange(o, o + per))
                }
                o += per
            }
        }
        doc.layers.add(Layer(id, props(name, visible, opacity, blendMode, alphaLock, clipping), surface))
    }

    /** Finishes opening a project: picks the active layer and makes sure there is one layer. */
    fun finishRestore(activeLayerId: String) {
        if (doc.layers.isEmpty()) doc.layers.add(doc.createLayer("Layer 1"))
        doc.activeLayerId = if (doc.layer(activeLayerId) != null) activeLayerId else doc.layers.last().id
        dirty = doc.bounds
        structure = true
    }

    companion object {
        const val PROJECT = "project"

        fun blank(width: Int, height: Int, paperColor: Int): WebEditor =
            WebEditor(Document.blank(width, height, paperColor)).also {
                it.dirty = it.doc.bounds
                it.markAll()
            }

        /** An empty document to fill with [restoreLayer] and [finishRestore]. */
        fun forRestore(width: Int, height: Int, paperColor: Int, paperVisible: Boolean): WebEditor =
            WebEditor(Document(width, height, paperColor, paperVisible))

        /** The brush library as JSON, grouped by category in display order. */
        fun brushesJson(): String = buildJsonArray {
            for (b in BrushLibrary.all) add(buildJsonObject {
                put("id", b.id)
                put("name", b.name)
                put("category", b.category.name)
                put("categoryName", b.category.displayName)
                put("defaultSize", b.defaultSize)
                put("minSize", b.minSize)
                put("maxSize", b.maxSize)
                put("defaultOpacity", b.defaultOpacity)
                if (b.usesPaintLoad) put("paintLoad", b.defaultPaintLoad)
                if (BrushLibrary.isCustom(b.id)) put("custom", true)
            })
        }.toString()

        /** Every setting of one brush, for the Brush Studio. */
        fun brushJson(id: String): String = BrushCodec.encodeFull(BrushLibrary.byId(id))

        /** Replaces the user's own brushes (a JSON array of brushes); returns how many loaded. */
        fun setCustomBrushes(json: String): Int {
            val list = BrushCodec.decode(json)
            BrushLibrary.custom = list
            return list.size
        }

        /** The choices the Brush Studio offers, with display names. */
        fun brushOptionsJson(): String = buildJsonObject {
            fun <E : Enum<E>> list(key: String, items: List<E>, name: (E) -> String) = put(key, buildJsonArray {
                for (e in items) add(buildJsonObject { put("id", e.name); put("name", name(e)) })
            })
            list("tip", TipShape.entries) { it.displayName }
            list("grain", GrainTexture.entries) { it.displayName }
            list("renderMode", RenderMode.entries) {
                when (it) {
                    RenderMode.GLAZE -> "Glaze (even, clean)"
                    RenderMode.WET -> "Wet paint (mixes)"
                    RenderMode.WASH -> "Watercolour wash"
                    RenderMode.MIXER -> "Mixer brush"
                }
            }
            list("tipAngle", TipAngle.entries) {
                when (it) {
                    TipAngle.FIXED -> "Fixed"
                    TipAngle.FOLLOW_STROKE -> "Follow stroke"
                    TipAngle.STYLUS -> "Pen direction"
                }
            }
            list("blendMode", BlendMode.entries) { it.displayName }
            list("category", BrushCategory.entries) { it.displayName }
        }.toString()

        /** Like [brushPreview], for a brush given as JSON (one being edited). */
        fun brushPreviewJson(json: String, width: Int, height: Int, color: Int, background: Int): IntArray {
            val brush = BrushCodec.decode(json).firstOrNull() ?: BrushLibrary.default
            return preview(brush, width, height, color, background)
        }

        fun blendModesJson(): String = buildJsonArray {
            for (m in BlendMode.entries) add(buildJsonObject {
                put("id", m.name)
                put("name", m.displayName)
            })
        }.toString()

        /** A stroke painted by the real engine, for the brush library. [width, height, pixels...]. */
        fun brushPreview(brushId: String, width: Int, height: Int, color: Int, background: Int): IntArray =
            preview(BrushLibrary.byId(brushId), width, height, color, background)

        private fun preview(brush: BrushPreset, width: Int, height: Int, color: Int, background: Int): IntArray {
            // Watercolour is see-through pigment that only darkens the paper, so pale paint on the
            // library's dark rows can't be seen at all. Show it as it really is: blue on paper.
            if (brush.renderMode == RenderMode.WASH && !brush.blendOnly) {
                return previewOn(brush, width, height, 0xFF2F6DB5.toInt(), 0xFFF6F1E7.toInt(), wash = true)
            }
            return previewOn(brush, width, height, color, background, wash = false)
        }

        private fun previewOn(brush: BrushPreset, width: Int, height: Int, color: Int, background: Int, wash: Boolean): IntArray {
            val doc = Document.blank(width, height, paperColor = background)
            val editor = Editor(doc)
            if (brush.blendOnly) {
                // Blenders only move paint, so give them two colours to push into each other.
                val bars = listOf(0.3f to 0xFF8E7DBE.toInt(), 0.5f to 0xFFE0A83A.toInt(), 0.7f to 0xFF4C8BD8.toInt())
                for ((fx, c) in bars) {
                    previewStroke(editor, BrushLibrary.byId("hard_airbrush"), c, width * 0.2f,
                        (0..20).map { i -> InputPoint(width * fx, 6f + (height - 12f) * i / 20f, time = i * 8L) })
                }
            } else if (brush.renderMode == RenderMode.WET || brush.renderMode == RenderMode.MIXER) {
                previewStroke(editor, BrushLibrary.byId("hard_airbrush"), 0xFF8E7DBE.toInt(), height * 0.3f,
                    (0..20).map { i -> InputPoint(width * 0.55f, 10f + (height - 20f) * i / 20f, time = i * 8L) })
            }
            val size = brush.defaultSize.coerceIn(4f, height * 0.42f)
            val shown = if (brush.blendMode == BlendMode.MULTIPLY) brush.copy(blendMode = BlendMode.NORMAL) else brush
            val points = (0..80).map { i ->
                val t = i / 80f
                InputPoint(
                    x = 16f + (width - 32f) * t,
                    y = height / 2f + sin(t * 2 * PI).toFloat() * (height / 4.2f),
                    pressure = 0.2f + 0.8f * sin(t * PI).toFloat(),
                    tilt = 0.3f,
                    time = i * 8L,
                )
            }
            previewStroke(editor, shown, color, size, points)
            // Let the water spread and settle, as it would on the canvas.
            if (wash) editor.advanceWater(4000.0)
            val px = toRgba(Compositor.flatten(doc))
            return intArrayOf(width, height) + px
        }

        private fun previewStroke(editor: Editor, brush: BrushPreset, color: Int, size: Float, points: List<InputPoint>) {
            editor.beginStroke(StrokeParams(brush, color = color, size = size, usePressure = true), points.first())
            points.drop(1).forEach { editor.strokeTo(it) }
            editor.endStroke()
        }

        private fun props(name: String, visible: Boolean, opacity: Float, blendMode: String, alphaLock: Boolean, clipping: Boolean) =
            LayerProps(
                name = name,
                visible = visible,
                opacity = opacity.coerceIn(0f, 1f),
                blendMode = BlendMode.entries.firstOrNull { it.name == blendMode } ?: BlendMode.NORMAL,
                alphaLock = alphaLock,
                clipping = clipping,
            )

        private fun abgr(argb: Int): Int =
            (argb and 0xFF00FF00.toInt()) or ((argb shr 16) and 0xFF) or ((argb and 0xFF) shl 16)

        private fun fromRgba(abgr: Int): Int = abgr(abgr)

        private fun toRgba(px: IntArray): IntArray {
            for (i in px.indices) px[i] = abgr(px[i])
            return px
        }
    }
}
