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
import com.artisthaven.engine.document.GroupProps
import com.artisthaven.engine.io.PsdReader
import com.artisthaven.engine.io.PsdWriter
import com.artisthaven.engine.tools.Adjustment
import com.artisthaven.engine.tools.Affine
import com.artisthaven.engine.tools.LiquifyMode
import com.artisthaven.engine.tools.Resample
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
            put("hasMask", l.mask != null)
            put("editingMask", doc.paintsMask(l))
            put("group", l.group)
        })
    }.toString()

    /** Layer groups as JSON: id, name, visible, opacity. */
    fun groupsJson(): String = buildJsonArray {
        for ((id, g) in doc.groups) add(buildJsonObject {
            put("id", id)
            put("name", g.name)
            put("visible", g.visible)
            put("opacity", g.opacity)
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
        apply(editor.setLayerProps(id) { props(name, visible, opacity, blendMode, alphaLock, clipping).copy(group = it.group) }, all = true)
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

    /** Adds a picture as a new layer with its top-left corner at (x, y). Pixels are RGBA bytes. */
    fun addImageLayerAt(name: String, rgba: IntArray, width: Int, height: Int, x: Int, y: Int) {
        val argb = IntArray(rgba.size) { fromRgba(rgba[it]) }
        apply(editor.addImageLayerAt(name, argb, width, height, x, y), all = true)
    }

    // ---- Layer masks ------------------------------------------------------------------------------

    fun addMask(id: String) = apply(editor.addMask(id), all = true)
    fun deleteMask(id: String) = apply(editor.deleteMask(id), all = true)
    fun applyMask(id: String) = apply(editor.applyMask(id), all = true)
    fun setEditingMask(on: Boolean) = apply(editor.setEditingMask(on))
    val editingMask: Boolean get() = doc.activeLayer?.let { doc.paintsMask(it) } ?: false

    /** A layer's mask, white where it shows: [width, height, pixels...], or [0, 0] if none. */
    fun maskThumbnail(id: String, maxSize: Int): IntArray {
        val (px, w, h) = Compositor.maskThumbnail(doc, id, maxSize) ?: return intArrayOf(0, 0)
        toRgba(px)
        return intArrayOf(w, h) + px
    }

    // ---- Layer groups -----------------------------------------------------------------------------

    fun groupLayer(id: String, withBelow: Boolean) = apply(editor.groupLayer(id, withBelow), all = true)
    fun ungroup(groupId: String) = apply(editor.ungroup(groupId), all = true)
    fun removeFromGroup(id: String) = apply(editor.removeFromGroup(id), all = true)
    fun moveGroup(groupId: String, up: Boolean) = apply(editor.moveGroup(groupId, up), all = true)
    fun deleteGroup(groupId: String) = apply(editor.deleteGroup(groupId), all = true)
    fun flattenGroup(groupId: String) = apply(editor.flattenGroup(groupId), all = true)

    fun setGroupProps(groupId: String, name: String, visible: Boolean, opacity: Float) {
        groupPreviewBefore?.let { (gid, before) -> doc.groups[gid] = before }
        groupPreviewBefore = null
        apply(editor.setGroupProps(groupId) { GroupProps(name, visible, opacity.coerceIn(0f, 1f)) }, all = true)
    }

    private var groupPreviewBefore: Pair<String, GroupProps>? = null

    /** Live group opacity while a slider moves; finish with [setGroupProps]. */
    fun previewGroupOpacity(groupId: String, opacity: Float) {
        val g = doc.groups[groupId] ?: return
        if (groupPreviewBefore == null) groupPreviewBefore = groupId to g
        doc.groups[groupId] = g.copy(opacity = opacity.coerceIn(0f, 1f))
        apply(Change(doc.bounds, false), all = true)
    }

    // ---- Selections -------------------------------------------------------------------------------

    private fun mode(m: Int) = when (m) {
        1 -> Editor.SelectMode.ADD
        2 -> Editor.SelectMode.SUBTRACT
        else -> Editor.SelectMode.REPLACE
    }

    /** A closed outline, x0, y0, x1, y1... in canvas pixels. mode: 0 replace, 1 add, 2 subtract. */
    fun selectPolygon(xy: FloatArray, mode: Int) = apply(editor.selectPolygon(xy, mode(mode)), all = true)
    fun selectSimilar(x: Int, y: Int, tolerance: Float, allLayers: Boolean, mode: Int) =
        apply(editor.selectSimilar(x, y, tolerance, allLayers, mode(mode)), all = true)
    fun selectAll() = apply(editor.selectAll(), all = true)
    fun deselect() = apply(editor.deselect(), all = true)
    fun invertSelection() = apply(editor.invertSelection(), all = true)
    fun clearSelected() = apply(editor.clearSelected(), doc.activeLayerId)
    fun fillSelection(color: Int) = apply(editor.fillSelection(color), doc.activeLayerId)
    fun selectionToNewLayer(cut: Boolean) = apply(editor.selectionToNewLayer(cut), all = true)
    val hasSelection: Boolean get() = doc.selection != null

    /** Bumped whenever the selection changes, so the page knows to fetch a new outline. */
    val selectionStamp: Double get() = (doc.selection?.hashCode() ?: 0).toDouble()

    var gridWidth: Int = 0
        private set
    var gridHeight: Int = 0
        private set
    var gridStep: Int = 1
        private set

    /**
     * The selection's coverage on a grid no bigger than [maxSide] (0..255 per cell, sampled at
     * cell centres), for drawing marching ants. Empty if nothing is selected.
     */
    fun selectionGrid(maxSide: Int): ByteArray {
        val sel = doc.selection ?: return ByteArray(0)
        val step = maxOf(1, (maxOf(doc.width, doc.height) + maxSide - 1) / maxSide)
        val gw = (doc.width + step - 1) / step
        val gh = (doc.height + step - 1) / step
        gridWidth = gw; gridHeight = gh; gridStep = step
        val out = ByteArray(gw * gh)
        val half = step / 2
        for (y in 0 until gh) {
            val sy = minOf(doc.height - 1, y * step + half)
            for (x in 0 until gw) out[y * gw + x] = sel.at(minOf(doc.width - 1, x * step + half), sy).toByte()
        }
        return out
    }

    // ---- Move and transform -----------------------------------------------------------------------

    /** Starts moving the active layer (or the selected part). Returns its bounds, or empty. */
    fun beginTransform(): IntArray {
        val r = editor.beginTransform() ?: return IntArray(0)
        apply(editor.takeSettledChange(), all = true)
        // The session lifts the pixels off the layer; draw them back where they are straight away.
        apply(editor.previewTransform(Affine()), doc.activeLayerId)
        return intArrayOf(r.left, r.top, r.right, r.bottom)
    }

    private fun quality(q: Int) = Resample.entries.getOrElse(q) { Resample.SMOOTH }

    fun previewTransform(a: Float, b: Float, c: Float, d: Float, tx: Float, ty: Float, quality: Int) =
        apply(editor.previewTransform(Affine(a, b, c, d, tx, ty), quality(quality)), doc.activeLayerId)

    fun commitTransform(a: Float, b: Float, c: Float, d: Float, tx: Float, ty: Float, quality: Int) =
        apply(editor.commitTransform(Affine(a, b, c, d, tx, ty), quality(quality)), all = true)

    fun cancelTransform() = apply(editor.cancelTransform(), doc.activeLayerId)
    val isTransforming: Boolean get() = editor.isTransforming

    // ---- Adjustments ------------------------------------------------------------------------------

    /** Starts an adjustment (an [Adjustment] name) on the active layer. False if there's nothing to adjust. */
    fun beginAdjust(kind: String): Boolean {
        val k = Adjustment.entries.firstOrNull { it.name == kind } ?: return false
        val ok = editor.beginAdjust(k)
        apply(editor.takeSettledChange(), all = true)
        return ok
    }

    fun previewAdjust(a: Float, b: Float, c: Float) = apply(editor.previewAdjust(a, b, c), doc.activeLayerId)
    fun commitAdjust() = apply(editor.commitAdjust(), all = true)
    fun cancelAdjust() = apply(editor.cancelAdjust(), doc.activeLayerId)
    val isAdjusting: Boolean get() = editor.isAdjusting

    // ---- Liquify ----------------------------------------------------------------------------------

    fun beginLiquify(mode: String, size: Float, strength: Float, x: Float, y: Float, pressure: Float) {
        val m = LiquifyMode.entries.firstOrNull { it.name == mode } ?: LiquifyMode.PUSH
        apply(editor.beginLiquify(m, size, strength, x, y, pressure), all = true)
        apply(editor.takeSettledChange(), all = true)
    }

    fun liquifyTo(x: Float, y: Float, pressure: Float) = apply(editor.liquifyTo(x, y, pressure), doc.activeLayerId)
    fun endLiquify() = apply(editor.endLiquify(), all = true)

    // ---- Photoshop files --------------------------------------------------------------------------

    /** The drawing as a layered .psd file. */
    fun exportPsd(): ByteArray = PsdWriter.encode(doc)

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
                put("group", l.group)
                put("mask", l.mask != null)
            }
        }))
        put("groups", JsonArray(doc.groups.map { (id, g) ->
            buildJsonObject {
                put("id", id)
                put("name", g.name)
                put("visible", g.visible)
                put("opacity", g.opacity)
            }
        }))
    }.toString()

    /** A layer's painted tiles: [count, then index and 4096 premultiplied ARGB pixels per tile]. */
    fun layerData(id: String): IntArray {
        val layer = doc.layer(id) ?: return IntArray(1)
        return surfaceData(layer.surface)
    }

    /** The layer's mask in the same form as [layerData], or empty if it has none. */
    fun maskData(id: String): IntArray {
        val mask = doc.layer(id)?.mask ?: return IntArray(0)
        return surfaceData(mask)
    }

    private fun surfaceData(s: TiledSurface): IntArray {
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
        alphaLock: Boolean, clipping: Boolean, data: IntArray, group: String, maskData: IntArray,
    ) {
        val surface = surfaceFrom(data)
        val mask = if (maskData.isEmpty()) null else surfaceFrom(maskData)
        doc.layers.add(Layer(id, props(name, visible, opacity, blendMode, alphaLock, clipping).copy(group = group), surface, mask))
    }

    fun restoreGroup(id: String, name: String, visible: Boolean, opacity: Float) {
        doc.groups[id] = GroupProps(name, visible, opacity.coerceIn(0f, 1f))
    }

    private fun surfaceFrom(data: IntArray): TiledSurface {
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
        return surface
    }

    /** Finishes opening a project: picks the active layer and makes sure there is one layer. */
    fun finishRestore(activeLayerId: String) {
        if (doc.layers.isEmpty()) doc.layers.add(doc.createLayer("Layer 1"))
        val used = doc.layers.mapTo(HashSet()) { it.group }
        doc.groups.keys.retainAll(used)
        for (l in doc.layers) if (l.group.isNotEmpty() && !doc.groups.containsKey(l.group)) l.props = l.props.copy(group = "")
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

        /** Last warnings from [fromPsd], joined by new lines. */
        var psdWarnings: String = ""
            private set

        /** Opens a Photoshop file; throws with a readable message if it can't. */
        fun fromPsd(bytes: ByteArray, maxSide: Int): WebEditor {
            val result = PsdReader.read(bytes, maxSide)
            psdWarnings = result.warnings.joinToString("\n")
            return WebEditor(result.doc).also {
                if (it.doc.layers.isEmpty()) it.doc.layers.add(it.doc.createLayer("Layer 1"))
                if (it.doc.layer(it.doc.activeLayerId) == null) it.doc.activeLayerId = it.doc.layers.last().id
                it.dirty = it.doc.bounds
                it.structure = true
                it.markAll()
            }
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
