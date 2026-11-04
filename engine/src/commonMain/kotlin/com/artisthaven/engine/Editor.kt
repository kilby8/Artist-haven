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
import com.artisthaven.engine.document.Selection
import com.artisthaven.engine.document.Command
import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.document.MaskCommand
import com.artisthaven.engine.raster.TiledSurface
import com.artisthaven.engine.document.SelectionCommand
import com.artisthaven.engine.raster.Pixels
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.stroke.StrokeSession
import com.artisthaven.engine.stroke.WetCanvas
import com.artisthaven.engine.stroke.WaterSim
import com.artisthaven.engine.brush.RenderMode
import com.artisthaven.engine.stroke.Tool
import com.artisthaven.engine.tools.FloodFill
import com.artisthaven.engine.tools.QuickShape
import com.artisthaven.engine.tools.Affine
import com.artisthaven.engine.tools.Resample
import com.artisthaven.engine.tools.TransformSession
import com.artisthaven.engine.raster.IRect
import kotlin.math.min

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

    /** Paint on the Mixer Brush, kept between strokes like a real brush. */
    val mixerState = com.artisthaven.engine.stroke.MixerState()

    /** Where recent watercolour is still wet, per layer, for wet-in-wet. */
    private val wetCanvases = HashMap<String, WetCanvas>()

    private fun wetCanvasFor(layer: Layer): WetCanvas {
        val w = wetCanvases[layer.id]
        if (w != null && w.width == doc.width && w.height == doc.height) return w
        return WetCanvas(doc.width, doc.height).also { wetCanvases[layer.id] = it }
    }

    // ---- Watercolour water ------------------------------------------------------------------------

    /** Each layer's watercolour water, which keeps flowing after the pen lifts (see [WaterSim]). */
    private val waterSims = HashMap<String, WaterSim>()
    /** Pixels the water changed since the last undo step was closed, per layer, as they were. */
    private val waterOriginals = HashMap<String, HashMap<Int, IntArray?>>()
    private var waterLabel = "Watercolour"
    private var waterSnapshot: Pair<WaterSim, WaterSim.Snapshot>? = null
    private var waterClock = 0.0
    private var waterFrame = 0

    private fun waterSimFor(layer: Layer): WaterSim {
        val w = waterSims[layer.id]
        if (w != null && w.width == doc.width && w.height == doc.height) return w
        return WaterSim(doc.width, doc.height).also { waterSims[layer.id] = it }
    }

    /** True while any watercolour is wet or damp, so the app should keep calling [tickWater]. */
    val isWaterActive: Boolean get() = waterSims.values.any { it.isActive }

    /** Debug summary of each layer's water. */
    fun waterStats(): String = waterSims.entries.joinToString("; ") { "${it.key}: ${it.value.stats()}" }

    /** True while paint is still moving somewhere. */
    val isWet: Boolean get() = waterSims.values.any { it.isWet }

    private fun touchFor(layer: Layer): (Int) -> Unit {
        val map = waterOriginals.getOrPut(layer.id) { HashMap() }
        val surface = layer.surface
        return { index -> if (!map.containsKey(index)) map[index] = surface.tile(index)?.copyOf() }
    }

    /** Makes everything the water changed so far one undo step. */
    private fun closeWaterStep(): Boolean {
        if (waterOriginals.isEmpty()) return false
        val parts = ArrayList<Command>()
        for ((id, originals) in waterOriginals) {
            val layer = doc.layer(id) ?: continue
            PixelCommand.capture(waterLabel, layer, originals)?.let { parts += it }
        }
        waterOriginals.clear()
        waterLabel = "Watercolour"
        if (parts.isEmpty()) return false
        history.push(if (parts.size == 1) parts[0] else CompoundCommand(parts[0].label, parts))
        revision++
        return true
    }

    private fun renderWater(layer: Layer, sim: WaterSim, rect: IRect?, force: Boolean = false): IRect? {
        if (rect == null) return null
        return sim.render(layer.surface, rect, 1f, layer.alphaLock, force = force, touch = touchFor(layer))
    }

    /**
     * Lets the water flow for [elapsedMillis] (call it every frame while [isWaterActive]). Returns
     * what to redraw. At most [maxSteps] steps are run, so a slow frame doesn't stall the app.
     */
    fun tickWater(elapsedMillis: Double, maxSteps: Int = 3): Change {
        if (waterSims.isEmpty()) return Change.NONE
        waterClock += elapsedMillis.coerceAtLeast(0.0)
        var steps = (waterClock / STEP_MILLIS).toInt()
        waterClock -= steps * STEP_MILLIS
        if (steps > 0) waterFrame++
        if (steps > maxSteps) {
            steps = maxSteps
            waterClock = 0.0
        }
        var dirty: IRect? = null
        for ((id, sim) in waterSims) {
            val layer = doc.layer(id) ?: continue
            if (!sim.isActive) continue
            repeat(steps) { sim.step() }
            // Where the brush is goes on screen at once; the slower creep of the water every
            // other frame (every third while painting, to keep the pen quick).
            val flowNow = !sim.isWet || waterFrame % (if (stroke != null) 3 else 2) == 0
            dirty = IRect.unionOf(dirty, renderWater(layer, sim, sim.takePending(withFlow = flowNow)))
            if (!sim.isWet && stroke == null) {
                // One last full redraw, so nothing skipped along the way is left behind.
                dirty = IRect.unionOf(dirty, renderWater(layer, sim, sim.paintedRect(), force = true))
                sim.bake()
            }
        }
        if (stroke == null && !isWet) closeWaterStep()
        return Change(dirty, false)
    }

    /** Runs the water for [millis] at full speed (tests, and drying in one go). */
    fun advanceWater(millis: Double): Change {
        var c = Change.NONE
        var left = millis
        while (left > 0.0 && isWaterActive) {
            c = c.merge(tickWater(min(left, STEP_MILLIS * 8), maxSteps = 8))
            left -= STEP_MILLIS * 8
        }
        return c
    }

    /** Dries all watercolour where it is now (the Dry button). */
    fun dryWater(): Change {
        var dirty: IRect? = null
        for ((id, sim) in waterSims) {
            val layer = doc.layer(id) ?: continue
            dirty = IRect.unionOf(dirty, sim.freeze(layer.surface, 1f, layer.alphaLock, touchFor(layer)))
        }
        closeWaterStep()
        return Change(dirty, false)
    }

    /** Before something else edits [layer]'s pixels: its water dries where it is. */
    private fun dryLayerWater(layer: Layer): IRect? {
        val sim = waterSims[layer.id] ?: return null
        if (!sim.isActive) return null
        return sim.freeze(layer.surface, 1f, layer.alphaLock, touchFor(layer))
    }

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
        val flowing = params.tool == Tool.BRUSH && params.brush.renderMode == RenderMode.WASH &&
            !params.brush.blendOnly && !doc.paintsMask(layer)
        var dirty: IRect? = null
        val sim = if (flowing) {
            closeWaterStep()
            waterLabel = params.brush.name
            waterSimFor(layer).also {
                it.configure(params.brush.pigmentFlow, params.brush.edgeDarken, params.brush.granulation, doc.selection)
                waterSnapshot = it to it.snapshot()
            }
        } else {
            // Anything else on a wet layer dries it first, so the water can't paint over it.
            dirty = dryLayerWater(layer)
            closeWaterStep()
            waterSnapshot = null
            null
        }
        lastPointTime = first.time
        val s = StrokeSession(doc, layer, params.copy(seed = seed++), mixerState, wetCanvasFor(layer), sim)
        stroke = s
        dirty = IRect.unionOf(dirty, s.add(first))
        if (sim != null) dirty = IRect.unionOf(dirty, renderWater(layer, sim, sim.takePending(withFlow = false)))
        return Change(dirty, false)
    }

    private var lastPointTime = 0L

    fun strokeTo(point: InputPoint): Change {
        val s = stroke ?: return Change.NONE
        var dirty = s.add(point)
        if (waterSnapshot != null) {
            // The water flows while the stroke is painted, in step with the pen.
            val dt = (point.time - lastPointTime).coerceIn(0L, 100L)
            dirty = IRect.unionOf(dirty, tickWater(dt.toDouble()).dirty)
        }
        lastPointTime = point.time
        return Change(dirty, false)
    }

    fun endStroke(): Change {
        val s = stroke ?: return Change.NONE
        stroke = null
        val result = s.finish()
        result.command?.let { history.push(it) }
        var dirty = result.dirty
        waterSnapshot?.let { (sim, _) ->
            doc.activeLayer?.let { dirty = IRect.unionOf(dirty, renderWater(it, sim, sim.takePending(withFlow = false))) }
        }
        waterSnapshot = null
        return changed(Change(dirty, false))
    }

    fun cancelStroke(): Change {
        val s = stroke ?: return Change.NONE
        stroke = null
        var dirty = s.cancel()
        waterSnapshot?.let { (sim, snap) ->
            sim.takePending()
            val layer = doc.activeLayer
            val r = sim.restore(snap)
            if (layer != null) dirty = IRect.unionOf(dirty, renderWater(layer, sim, r, force = true))
        }
        waterSnapshot = null
        return Change(dirty, false)
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
        settlePixels()
        val layer = doc.activeLayer ?: return null
        if (!layer.visible || doc.paintSurface(layer).isBlank()) return null
        val t = TransformSession(layer, doc.selection, doc.paintsMask(layer))
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
        val matrix = m ?: t.matrix
        val (dirty, cmd) = t.finish(matrix, quality)
        if (cmd == null) return Change(dirty, false)
        // The selection travels with the pixels it lifted.
        val sel = t.selection
        if (sel != null && !matrix.isIdentity) {
            val moved = SelectionCommand(sel, sel.transformed(matrix).takeUnless { it.isEmpty })
            moved.apply(doc)
            history.push(CompoundCommand("Transform", listOf(cmd, moved)))
        } else {
            history.push(cmd)
        }
        return changed(Change(dirty, true))
    }

    fun cancelTransform(): Change {
        val t = transform ?: return Change.NONE
        transform = null
        return Change(t.cancel(), false)
    }

    private var settled: Change = Change.NONE

    /** Anything else that edits the document first finishes a transform in progress. */
    private fun settle() {
        // Keep undo in order: what the water did so far comes before this edit.
        closeWaterStep()
        if (transform != null) settled = settled.merge(commitTransform())
    }

    /** Edits that change pixels (fill, clear, merge, transform...) dry the water first. */
    private fun settlePixels() {
        if (waterSims.values.any { it.isActive }) {
            val d = dryWater()
            if (d.dirty != null) settled = settled.merge(d)
        }
        settle()
    }

    /** Redraw needed by a transform that another edit finished implicitly; resets after reading. */
    fun takeSettledChange(): Change = settled.also { settled = Change.NONE }

    // ---- Selection ------------------------------------------------------------------------------

    enum class SelectMode { REPLACE, ADD, SUBTRACT }

    private fun select(shape: Selection, mode: SelectMode): Change {
        settle()
        val current = doc.selection
        val next = when {
            mode == SelectMode.REPLACE || current == null -> if (mode == SelectMode.SUBTRACT) null else shape
            else -> current.combine(shape, add = mode == SelectMode.ADD)
        }?.takeUnless { it.isEmpty }
        if (next == null && current == null) return Change.NONE
        return changed(history.execute(doc, SelectionCommand(current, next)))
    }

    /** Lasso, rectangle or ellipse: a closed outline as x0, y0, x1, y1, ... in canvas pixels. */
    fun selectPolygon(xy: FloatArray, mode: SelectMode = SelectMode.REPLACE): Change =
        select(Selection.polygon(doc.width, doc.height, xy), mode)

    /** Automatic selection: the connected area of similar colour, like the fill tool would fill. */
    fun selectSimilar(x: Int, y: Int, tolerance: Float, allLayers: Boolean, mode: SelectMode = SelectMode.REPLACE): Change {
        val layer = doc.activeLayer ?: return Change.NONE
        val (rect, cover) = FloodFill.region(doc, layer, x, y, tolerance, allLayers) ?: return Change.NONE
        return select(Selection.fromCoverage(doc.width, doc.height, rect, cover), mode)
    }

    fun selectAll(): Change = select(Selection.all(doc.width, doc.height), SelectMode.REPLACE)

    fun deselect(): Change {
        settle()
        val current = doc.selection ?: return Change.NONE
        return changed(history.execute(doc, SelectionCommand(current, null)))
    }

    fun invertSelection(): Change {
        settle()
        val current = doc.selection ?: return Change.NONE
        return changed(history.execute(doc, SelectionCommand(current, current.inverted().takeUnless { it.isEmpty })))
    }

    /** Erases the selected pixels on the active layer. */
    fun clearSelected(): Change {
        settlePixels()
        val sel = doc.selection ?: return Change.NONE
        val layer = doc.activeLayer ?: return Change.NONE
        val target = doc.paintSurface(layer)
        val onMask = doc.paintsMask(layer)
        val before = HashMap<Int, IntArray?>()
        var dirty: IRect? = null
        for (index in target.nonEmptyTileIndices()) {
            if (sel.tileEmpty(index)) continue
            before[index] = target.copyTile(index)
            val tile = target.tile(index) ?: continue
            val tr = target.tileRect(index)
            for (y in tr.top until tr.bottom) for (x in tr.left until tr.right) {
                val k = ((y and 63) shl 6) or (x and 63)
                if (tile[k] != 0) tile[k] = Pixels.scale(tile[k], 1f - sel.coverage(x, y))
            }
            dirty = IRect.unionOf(dirty, tr)
        }
        val cmd = PixelCommand.capture("Clear selection", layer, before, onMask) ?: return Change.NONE
        history.push(cmd)
        return changed(Change(dirty, true))
    }

    /** Fills the selection with a straight colour on the active layer. */
    fun fillSelection(color: Int): Change {
        settlePixels()
        val sel = doc.selection ?: return Change.NONE
        val layer = doc.activeLayer ?: return Change.NONE
        val rect = sel.bounds() ?: return Change.NONE
        val target = doc.paintSurface(layer)
        val onMask = doc.paintsMask(layer)
        val before = HashMap<Int, IntArray?>()
        for (index in target.tilesIn(rect)) before[index] = target.copyTile(index)
        val src = Pixels.premultiply(color)
        for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) {
            val a = sel.coverage(x, y)
            if (a <= 0f) continue
            val d = target.getPixel(x, y)
            val out = if (layer.alphaLock && !onMask) {
                if (d == 0) continue
                Pixels.lerp(d, Pixels.scale(src, Pixels.alpha(d) / 255f), a)
            } else {
                Pixels.srcOver(d, Pixels.scale(src, a))
            }
            target.setPixel(x, y, out)
        }
        val cmd = PixelCommand.capture("Fill selection", layer, before, onMask) ?: return Change.NONE
        history.push(cmd)
        return changed(Change(rect, true))
    }

    /** Copies (or cuts, with [cut]) the selected pixels of the active layer onto a new layer above. */
    fun selectionToNewLayer(cut: Boolean): Change {
        settlePixels()
        val sel = doc.selection ?: return Change.NONE
        val src = doc.activeLayer ?: return Change.NONE
        val layer = doc.createLayer(src.name + if (cut) " cut" else " copy")
        var any = false
        for (index in src.surface.nonEmptyTileIndices()) {
            if (sel.tileEmpty(index)) continue
            val from = src.surface.tile(index) ?: continue
            val tr = src.surface.tileRect(index)
            val out = IntArray(from.size)
            for (y in tr.top until tr.bottom) for (x in tr.left until tr.right) {
                val k = ((y and 63) shl 6) or (x and 63)
                if (from[k] != 0) out[k] = Pixels.scale(from[k], sel.coverage(x, y))
            }
            if (out.any { it != 0 }) {
                layer.surface.setTile(index, out)
                any = true
            }
        }
        if (!any) return Change.NONE
        val parts = ArrayList<Command>()
        var dirty: IRect? = layer.surface.contentBounds()
        if (cut) {
            val before = HashMap<Int, IntArray?>()
            for (index in layer.surface.nonEmptyTileIndices()) {
                before[index] = src.surface.copyTile(index)
                val tile = src.surface.tile(index) ?: continue
                val tr = src.surface.tileRect(index)
                for (y in tr.top until tr.bottom) for (x in tr.left until tr.right) {
                    val k = ((y and 63) shl 6) or (x and 63)
                    if (tile[k] != 0) tile[k] = Pixels.scale(tile[k], 1f - sel.coverage(x, y))
                }
            }
            PixelCommand.capture("Cut", src, before)?.let { parts += it }
        }
        val add = AddLayerCommand(doc.activeIndex + 1, layer, doc.activeLayerId)
        dirty = IRect.unionOf(dirty, add.apply(doc).dirty)
        parts += add
        history.push(CompoundCommand(if (cut) "Cut to layer" else "Copy to layer", parts))
        return changed(Change(dirty, true))
    }

    // ---- Layer masks ----------------------------------------------------------------------------

    /** Gives the active layer an empty mask (everything shows) and starts editing it. */
    fun addMask(id: String): Change {
        settlePixels()
        val layer = doc.layer(id) ?: return Change.NONE
        if (layer.mask != null) return setEditingMask(true)
        doc.activeLayerId = id
        return changed(history.execute(doc, MaskCommand(id, null, TiledSurface(doc.width, doc.height), doc.editingMask, true, "Add mask")))
    }

    fun deleteMask(id: String): Change {
        settlePixels()
        val layer = doc.layer(id) ?: return Change.NONE
        val mask = layer.mask ?: return Change.NONE
        return changed(history.execute(doc, MaskCommand(id, mask, null, doc.editingMask, false, "Delete mask")))
    }

    /** Bakes the mask into the layer's pixels and removes it. */
    fun applyMask(id: String): Change {
        settlePixels()
        val layer = doc.layer(id) ?: return Change.NONE
        val mask = layer.mask ?: return Change.NONE
        val before = HashMap<Int, IntArray?>()
        for (index in mask.nonEmptyTileIndices()) {
            val tile = layer.surface.tile(index) ?: continue
            val m = mask.tile(index) ?: continue
            before[index] = tile.copyOf()
            for (k in tile.indices) if (tile[k] != 0) tile[k] = Pixels.scale(tile[k], Layer.maskVisibility(m[k]))
        }
        val parts = ArrayList<Command>()
        PixelCommand.capture("Apply mask", layer, before)?.let { parts += it }
        val remove = MaskCommand(id, mask, null, doc.editingMask, false)
        remove.apply(doc)
        parts += remove
        history.push(CompoundCommand("Apply mask", parts))
        return changed(Change(doc.bounds, true))
    }

    /** Switches painting between the active layer's mask and its pixels (not an undo step). */
    fun setEditingMask(on: Boolean): Change {
        settle()
        val want = on && doc.activeLayer?.mask != null
        if (doc.editingMask == want) return Change.NONE
        doc.editingMask = want
        return Change(null, true)
    }

    // ---- Fill and colour ----------------------------------------------------------------------

    fun fill(x: Int, y: Int, color: Int, tolerance: Float, allLayers: Boolean): Change {
        settlePixels()
        val layer = doc.activeLayer ?: return Change.NONE
        val (rect, before) = FloodFill.fill(doc, layer, x, y, color, tolerance, allLayers) ?: return Change.NONE
        val cmd = PixelCommand.capture("Fill", layer, before, doc.paintsMask(layer)) ?: return Change.NONE
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

    /**
     * Adds a picture as a new layer above the active one, centred on the canvas. [pixels] are
     * straight ARGB, [width] x [height]; anything past the canvas edge is cropped.
     */
    fun addImageLayer(name: String, pixels: IntArray, width: Int, height: Int): Change {
        settle()
        val layer = doc.createLayer(name)
        val ox = (doc.width - width) / 2
        val oy = (doc.height - height) / 2
        for (y in 0 until height) {
            val dy = y + oy
            if (dy < 0 || dy >= doc.height) continue
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                if (c ushr 24 == 0) continue
                layer.surface.setPixel(x + ox, dy, Pixels.premultiply(c))
            }
        }
        val index = doc.activeIndex + 1
        return changed(history.execute(doc, AddLayerCommand(index, layer, doc.activeLayerId)))
    }

    fun duplicateLayer(id: String): Change {
        settlePixels()
        val src = doc.layer(id) ?: return Change.NONE
        val copy = src.duplicate(doc.newLayerId(), src.name + " copy")
        return changed(history.execute(doc, AddLayerCommand(doc.indexOf(id) + 1, copy, doc.activeLayerId)))
    }

    fun deleteLayer(id: String): Change {
        settlePixels()
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
        if (doc.activeLayerId != id) doc.editingMask = false
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
        settlePixels()
        val layer = doc.layer(id) ?: return Change.NONE
        val target = doc.paintSurface(layer)
        val before = HashMap<Int, IntArray?>()
        for (index in target.nonEmptyTileIndices()) {
            before[index] = target.copyTile(index)
            target.setTile(index, null)
        }
        val cmd = PixelCommand.capture("Clear layer", layer, before, doc.paintsMask(layer)) ?: return Change.NONE
        history.push(cmd)
        return changed(Change(doc.bounds, true))
    }

    /** Merges a layer into the one below it, baking in opacity and blend mode. */
    fun mergeDown(id: String): Change {
        settlePixels()
        val index = doc.indexOf(id)
        if (index <= 0) return Change.NONE
        val upper = doc.layers[index]
        val lower = doc.layers[index - 1]
        val before = HashMap<Int, IntArray?>()
        val indices = upper.surface.nonEmptyTileIndices()
        if (upper.visible) {
            for (ti in indices) {
                before[ti] = lower.surface.copyTile(ti)
                val raw = upper.surface.tile(ti) ?: continue
                val maskTile = upper.mask?.tile(ti)
                val src = if (maskTile == null) raw else IntArray(raw.size) { k -> if (raw[k] == 0) 0 else Pixels.scale(raw[k], Layer.maskVisibility(maskTile[k])) }
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
        wetCanvases.clear()
        // Undo while paint is still wet takes back that stroke and its water.
        closeWaterStep()
        waterSims.values.forEach { it.clear() }
        return changed(history.undo(doc))
    }

    fun redo(): Change {
        cancelStroke()
        if (transform != null) return Change.NONE
        wetCanvases.clear()
        closeWaterStep()
        waterSims.values.forEach { it.clear() }
        return changed(history.redo(doc))
    }

    companion object {
        /** One step of the water simulation, in milliseconds. */
        const val STEP_MILLIS = 1000.0 / 60.0
    }
}
