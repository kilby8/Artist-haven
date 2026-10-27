package com.artisthaven.engine

import com.artisthaven.engine.document.AddLayerCommand
import com.artisthaven.engine.document.Change
import com.artisthaven.engine.document.CompoundCommand
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.DeleteLayerCommand
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.History
import com.artisthaven.engine.document.LayerProps
import com.artisthaven.engine.document.LayerPropsCommand
import com.artisthaven.engine.document.MoveLayerCommand
import com.artisthaven.engine.document.PaperCommand
import com.artisthaven.engine.document.PixelCommand
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.stroke.StrokeSession
import com.artisthaven.engine.tools.FloodFill
import com.artisthaven.engine.tools.QuickShape
import com.artisthaven.engine.tools.Affine
import com.artisthaven.engine.tools.Resample
import com.artisthaven.engine.tools.TransformSession
import com.artisthaven.engine.raster.IRect

/**
 * Every change to a document goes through here, so each one is undoable and reports what needs
 * redrawing. Not thread-safe: call it from one thread.
 */
class Editor(val doc: Document, val history: History = History()) {

    /** Bumped on every change; the app autosaves when it moves. */
    var revision: Long = 0
        private set

    private var stroke: StrokeSession? = null
    private var transform: TransformSession? = null
    private var transformQuality = Resample.SMOOTH
    private var seed = 1

    val isStroking: Boolean get() = stroke != null
    val isTransforming: Boolean get() = transform != null

    private fun changed(c: Change?): Change {
        if (c != null && (c.dirty != null || c.layersChanged)) revision++
        return c ?: Change.NONE
    }

    // ---- Strokes ------------------------------------------------------------------------------

    /** Starts a stroke on the active layer. Returns false if the layer can't be painted. */
    fun beginStroke(params: StrokeParams, first: InputPoint): Change {
        cancelStroke()
        if (transform != null) return Change.NONE
        val layer = doc.activeLayer ?: return Change.NONE
        if (!layer.visible) return Change.NONE
        val s = StrokeSession(doc, layer, params.copy(seed = seed++))
        stroke = s
        return Change(s.add(first), false)
    }

    fun strokeTo(point: InputPoint): Change = Change(stroke?.add(point), false)

    fun endStroke(): Change {
        val s = stroke ?: return Change.NONE
        stroke = null
        val result = s.finish()
        result.command?.let { history.push(it) }
        return changed(Change(result.dirty, false))
    }

    fun cancelStroke(): Change {
        val s = stroke ?: return Change.NONE
        stroke = null
        return Change(s.cancel(), false)
    }

    /** Snaps the stroke in progress to a line, ellipse or rectangle. Returns null if no shape fits. */
    fun quickShape(): Change? {
        val s = stroke ?: return null
        val shape = QuickShape.recognise(s.rawPoints) ?: return null
        return Change(s.replacePath(shape.points), false)
    }

    // ---- Move and transform ----------------------------------------------------------------------

    /**
     * Starts moving the active layer's pixels. Returns the area they cover, or null if the layer is
     * empty or hidden. Call [previewTransform] while dragging, then [commitTransform] or
     * [cancelTransform].
     */
    fun beginTransform(): IRect? {
        cancelStroke()
        settle()
        val layer = doc.activeLayer ?: return null
        if (!layer.visible || layer.surface.isBlank()) return null
        val t = TransformSession(layer)
        if (t.isEmpty) {
            t.cancel()
            return null
        }
        transform = t
        return t.source
    }

    fun previewTransform(m: Affine, quality: Resample = transformQuality): Change {
        val t = transform ?: return Change.NONE
        transformQuality = quality
        // Previews are always quick bilinear, except pixel mode, which is cheaper still and shows
        // pixel artists exactly what they will get.
        return Change(t.render(m, quality.takeIf { it == Resample.PIXEL }), false)
    }

    /** Finishes the transform in progress (with [m], or the last previewed one) as one undo step. */
    fun commitTransform(m: Affine? = null, quality: Resample = transformQuality): Change {
        val t = transform ?: return Change.NONE
        transform = null
        val (dirty, cmd) = t.finish(m ?: t.matrix, quality)
        cmd?.let { history.push(it) }
        return changed(Change(dirty, cmd != null))
    }

    fun cancelTransform(): Change {
        val t = transform ?: return Change.NONE
        transform = null
        return Change(t.cancel(), false)
    }

    private var settled: Change = Change.NONE

    /** Anything else that edits the document first finishes a transform in progress. */
    private fun settle() {
        if (transform != null) settled = settled.merge(commitTransform())
    }

    /** Redraw needed by a transform that another edit finished implicitly; resets after reading. */
    fun takeSettledChange(): Change = settled.also { settled = Change.NONE }

    // ---- Fill and colour ----------------------------------------------------------------------

    fun fill(x: Int, y: Int, color: Int, tolerance: Float, allLayers: Boolean): Change {
        settle()
        val layer = doc.activeLayer ?: return Change.NONE
        val (rect, before) = FloodFill.fill(doc, layer, x, y, color, tolerance, allLayers) ?: return Change.NONE
        val cmd = PixelCommand.capture("Fill", layer, before) ?: return Change.NONE
        history.push(cmd)
        return changed(Change(rect, false))
    }

    fun sampleColor(x: Int, y: Int): Int = Compositor.sample(doc, x, y)

    // ---- Layers -------------------------------------------------------------------------------

    fun addLayer(): Change {
        settle()
        val layer = doc.createLayer()
        val index = doc.activeIndex + 1
        return changed(history.execute(doc, AddLayerCommand(index, layer, doc.activeLayerId)))
    }

    fun duplicateLayer(id: String): Change {
        settle()
        val src = doc.layer(id) ?: return Change.NONE
        val copy = src.duplicate(doc.newLayerId(), src.name + " copy")
        return changed(history.execute(doc, AddLayerCommand(doc.indexOf(id) + 1, copy, doc.activeLayerId)))
    }

    fun deleteLayer(id: String): Change {
        settle()
        if (doc.layers.size <= 1) return clearLayer(id)
        val index = doc.indexOf(id)
        if (index < 0) return Change.NONE
        val layer = doc.layers[index]
        val next = doc.layers.getOrNull(index - 1) ?: doc.layers[index + 1]
        val nextActive = if (doc.activeLayerId == id) next.id else doc.activeLayerId
        return changed(history.execute(doc, DeleteLayerCommand(index, layer, nextActive)))
    }

    fun selectLayer(id: String): Change {
        settle()
        if (doc.layer(id) == null) return Change.NONE
        doc.activeLayerId = id
        return Change(null, true)
    }

    fun moveLayer(from: Int, to: Int): Change {
        settle()
        if (from !in doc.layers.indices || to !in doc.layers.indices || from == to) return Change.NONE
        return changed(history.execute(doc, MoveLayerCommand(from, to)))
    }

    fun setLayerProps(id: String, update: (LayerProps) -> LayerProps): Change {
        settle()
        val layer = doc.layer(id) ?: return Change.NONE
        val before = layer.props
        val after = update(before)
        if (after == before) return Change.NONE
        return changed(history.execute(doc, LayerPropsCommand(id, before, after)))
    }

    /** Sets a property without an undo step, for live slider drags; finish with [commitLayerProps]. */
    fun previewLayerProps(id: String, update: (LayerProps) -> LayerProps): Change {
        val layer = doc.layer(id) ?: return Change.NONE
        layer.props = update(layer.props)
        return Change(layer.surface.contentBounds(), true)
    }

    fun commitLayerProps(id: String, before: LayerProps): Change {
        val layer = doc.layer(id) ?: return Change.NONE
        if (layer.props == before) return Change.NONE
        history.push(LayerPropsCommand(id, before, layer.props))
        return changed(Change(null, true))
    }

    fun clearLayer(id: String): Change {
        settle()
        val layer = doc.layer(id) ?: return Change.NONE
        val before = HashMap<Int, IntArray?>()
        for (index in layer.surface.nonEmptyTileIndices()) {
            before[index] = layer.surface.copyTile(index)
            layer.surface.setTile(index, null)
        }
        val cmd = PixelCommand.capture("Clear layer", layer, before) ?: return Change.NONE
        history.push(cmd)
        return changed(Change(doc.bounds, true))
    }

    /** Merges a layer into the one below it, baking in opacity and blend mode. */
    fun mergeDown(id: String): Change {
        settle()
        val index = doc.indexOf(id)
        if (index <= 0) return Change.NONE
        val upper = doc.layers[index]
        val lower = doc.layers[index - 1]
        val before = HashMap<Int, IntArray?>()
        val indices = upper.surface.nonEmptyTileIndices()
        if (upper.visible) {
            for (ti in indices) {
                before[ti] = lower.surface.copyTile(ti)
                val src = upper.surface.tile(ti) ?: continue
                val dst = lower.surface.tileOrCreate(ti)
                val base = if (upper.clipping) before[ti] else null
                for (i in src.indices) {
                    var s = src[i]
                    if (s == 0) continue
                    if (upper.clipping) {
                        val ba = (base?.get(i) ?: 0) ushr 24
                        if (ba == 0) continue
                        s = Pixels.scale(s, ba / 255f)
                    }
                    dst[i] = upper.blendMode.blend(dst[i], s, upper.opacity)
                }
            }
        }
        val pixels = PixelCommand.capture("Merge", lower, before)
        val delete = DeleteLayerCommand(index, upper, lower.id)
        delete.apply(doc)
        val cmd = CompoundCommand("Merge down", listOfNotNull(pixels, delete))
        history.push(cmd)
        return changed(Change(doc.bounds, true))
    }

    fun setPaper(color: Int, visible: Boolean): Change {
        settle()
        if (color == doc.paperColor && visible == doc.paperVisible) return Change.NONE
        return changed(history.execute(doc, PaperCommand(doc.paperColor, doc.paperVisible, color, visible)))
    }

    // ---- History ------------------------------------------------------------------------------

    fun undo(): Change {
        cancelStroke()
        if (transform != null) return cancelTransform()
        return changed(history.undo(doc))
    }

    fun redo(): Change {
        cancelStroke()
        if (transform != null) return Change.NONE
        return changed(history.redo(doc))
    }
}
