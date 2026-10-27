package com.artisthaven.app.canvas

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artisthaven.app.data.ExportFormat
import com.artisthaven.app.data.Exporter
import com.artisthaven.app.data.ProjectStore
import com.artisthaven.app.data.Settings
import com.artisthaven.engine.Editor
import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.brush.BrushPreset
import com.artisthaven.engine.document.LayerProps
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.stroke.Symmetry
import com.artisthaven.engine.stroke.Tool
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.tools.Affine
import com.artisthaven.engine.tools.Resample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ToolMode(val slot: String) {
    BRUSH("brush"), SMUDGE("smudge"), ERASER("eraser"), FILL("fill"), EYEDROPPER("eyedropper"), SELECT("select");

    val engineTool: Tool
        get() = when (this) {
            SMUDGE -> Tool.SMUDGE
            ERASER -> Tool.ERASER
            else -> Tool.BRUSH
        }
}

enum class Panel { BRUSHES, COLORS, LAYERS, ACTIONS }

/** How the selection tool picks an area. */
enum class SelectShape(val label: String) {
    FREEHAND("Freehand"), RECTANGLE("Rectangle"), ELLIPSE("Ellipse"), AUTOMATIC("Automatic")
}

/** A move/transform in progress: the layer's original pixel area and where it is going. */
data class TransformUi(
    val source: IRect,
    val matrix: Affine = Affine.IDENTITY,
    val quality: Resample = Resample.SMOOTH,
) {
    val centerX: Float get() = (source.left + source.right) / 2f
    val centerY: Float get() = (source.top + source.bottom) / 2f
}

data class CanvasUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val projectName: String = "",
    val docWidth: Int = 0,
    val docHeight: Int = 0,
    val tool: ToolMode = ToolMode.BRUSH,
    /** The tool to return to after a one-off eyedropper pick. */
    val lastPaintTool: ToolMode = ToolMode.BRUSH,
    val brushIds: Map<String, String> = emptyMap(),
    val size: Float = 10f,
    val opacity: Float = 1f,
    val color: Int = 0xFF1F2A44.toInt(),
    val recentColors: List<Int> = emptyList(),
    val palette: List<Int> = emptyList(),
    val symmetry: Symmetry = Symmetry.NONE,
    val panel: Panel? = null,
    val host: HostState = HostState(),
    val fingerPainting: Boolean = false,
    val stylusDetected: Boolean = false,
    val extraStreamline: Float = 0f,
    val fillTolerance: Float = 0.12f,
    val fillAllLayers: Boolean = true,
    val message: String? = null,
    val busy: Boolean = false,
    val transform: TransformUi? = null,
    val selectShape: SelectShape = SelectShape.FREEHAND,
    val selectMode: Editor.SelectMode = Editor.SelectMode.REPLACE,
) {
    /** Brush used by the current (or last painting) tool. */
    fun brushFor(mode: ToolMode): BrushPreset {
        val slot = if (mode == ToolMode.FILL || mode == ToolMode.EYEDROPPER || mode == ToolMode.SELECT) ToolMode.BRUSH.slot else mode.slot
        return BrushLibrary.byId(brushIds[slot])
    }

    val activeBrush: BrushPreset get() = brushFor(paintingTool)

    val paintingTool: ToolMode
        get() = when (tool) {
            ToolMode.BRUSH, ToolMode.SMUDGE, ToolMode.ERASER -> tool
            else -> lastPaintTool
        }
}

/**
 * State and actions for one open artwork. The document itself lives in [EngineHost] on its own
 * thread; this holds the tool choices and mirrors the document state for the UI.
 */
class CanvasViewModel(app: Application, val projectId: String) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    private val settings = Settings(app)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<CanvasUiState> = _state.asStateFlow()

    var host: EngineHost? = null
        private set

    private val _hostReady = MutableStateFlow<EngineHost?>(null)
    val hostReady: StateFlow<EngineHost?> = _hostReady.asStateFlow()

    /** Saves straight away when the app goes to the background, in case it is killed there. */
    private val backgroundSaver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            host?.saveNow()
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(backgroundSaver)
        BrushPreviews.ensureRendered()
        viewModelScope.launch {
            try {
                val (doc, manifest) = withContext(Dispatchers.IO) { store.load(projectId) }
                val h = EngineHost(store, projectId, doc, manifest.name, manifest.createdAt, historyBudget(app))
                h.onState = { hs ->
                    // The engine finishes a transform by itself when another edit comes in.
                    _state.update { it.copy(host = hs, transform = if (hs.transforming) it.transform else null) }
                }
                host = h
                _hostReady.value = h
                _state.update {
                    it.copy(loading = false, projectName = manifest.name, docWidth = doc.width, docHeight = doc.height)
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = "Couldn't open this artwork: ${t.message}") }
            }
        }
    }

    private fun initialState(): CanvasUiState {
        val brushIds = mapOf(
            ToolMode.BRUSH.slot to settings.brushFor(ToolMode.BRUSH.slot, BrushLibrary.default.id),
            ToolMode.SMUDGE.slot to settings.brushFor(ToolMode.SMUDGE.slot, BrushLibrary.defaultSmudge.id),
            ToolMode.ERASER.slot to settings.brushFor(ToolMode.ERASER.slot, BrushLibrary.defaultEraser.id),
        )
        val base = CanvasUiState(
            brushIds = brushIds,
            color = settings.color,
            recentColors = settings.recentColors,
            palette = settings.palette,
            fingerPainting = settings.fingerPainting,
            extraStreamline = settings.extraStreamline,
        )
        return withSlotValues(base, ToolMode.BRUSH)
    }

    /** Loads the remembered size and opacity for the brush in a tool slot. */
    private fun withSlotValues(s: CanvasUiState, mode: ToolMode): CanvasUiState {
        val brush = s.brushFor(mode)
        val slot = if (mode == ToolMode.FILL || mode == ToolMode.EYEDROPPER || mode == ToolMode.SELECT) ToolMode.BRUSH.slot else mode.slot
        val defaultOpacity = if (mode == ToolMode.SMUDGE) 0.8f else brush.defaultOpacity
        return s.copy(
            size = settings.size(slot, brush.id, brush.defaultSize),
            opacity = settings.opacity(slot, brush.id, defaultOpacity),
        )
    }

    // ---- Tools ----------------------------------------------------------------------------------

    fun selectTool(mode: ToolMode) {
        commitTransform()
        _state.update { s ->
            val reopenLibrary = s.tool == mode && mode in setOf(ToolMode.BRUSH, ToolMode.SMUDGE, ToolMode.ERASER)
            val next = s.copy(
                tool = mode,
                lastPaintTool = if (mode in setOf(ToolMode.BRUSH, ToolMode.SMUDGE, ToolMode.ERASER)) mode else s.lastPaintTool,
                panel = if (reopenLibrary) (if (s.panel == Panel.BRUSHES) null else Panel.BRUSHES) else null,
            )
            if (mode == ToolMode.FILL || mode == ToolMode.EYEDROPPER || mode == ToolMode.SELECT) next else withSlotValues(next, mode)
        }
    }

    fun selectBrush(brush: BrushPreset) {
        _state.update { s ->
            val mode = s.paintingTool
            settings.setBrushFor(mode.slot, brush.id)
            withSlotValues(s.copy(brushIds = s.brushIds + (mode.slot to brush.id)), mode)
        }
    }

    fun setSize(v: Float) {
        _state.update { s ->
            val b = s.activeBrush
            val size = v.coerceIn(b.minSize, b.maxSize)
            settings.setSize(s.paintingTool.slot, b.id, size)
            s.copy(size = size)
        }
    }

    fun setOpacity(v: Float) {
        _state.update { s ->
            val o = v.coerceIn(0.01f, 1f)
            settings.setOpacity(s.paintingTool.slot, s.activeBrush.id, o)
            s.copy(opacity = o)
        }
    }

    fun setColor(color: Int, addToRecent: Boolean = true) {
        settings.color = color
        _state.update { s ->
            val recent = if (addToRecent) (listOf(color) + s.recentColors.filter { it != color }).take(10) else s.recentColors
            if (addToRecent) settings.recentColors = recent
            s.copy(color = color, recentColors = recent)
        }
    }

    fun addToPalette(color: Int) {
        _state.update { s ->
            val p = (s.palette.filter { it != color } + color).take(30)
            settings.palette = p
            s.copy(palette = p)
        }
    }

    fun removeFromPalette(color: Int) {
        _state.update { s ->
            val p = s.palette.filter { it != color }
            settings.palette = p
            s.copy(palette = p)
        }
    }

    fun setSymmetry(sym: Symmetry) = _state.update { it.copy(symmetry = sym) }

    fun setFingerPainting(on: Boolean) {
        settings.fingerPainting = on
        _state.update { it.copy(fingerPainting = on) }
    }

    fun setExtraStreamline(v: Float) {
        settings.extraStreamline = v
        _state.update { it.copy(extraStreamline = v) }
    }

    fun setFillTolerance(v: Float) = _state.update { it.copy(fillTolerance = v) }
    fun setFillAllLayers(v: Boolean) = _state.update { it.copy(fillAllLayers = v) }

    fun onStylusDetected() = _state.update { it.copy(stylusDetected = true) }

    fun togglePanel(panel: Panel) = _state.update { it.copy(panel = if (it.panel == panel) null else panel) }
    fun closePanel() = _state.update { it.copy(panel = null) }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
    fun messageShown() = _state.update { it.copy(message = null) }

    // ---- Canvas input -----------------------------------------------------------------------------

    fun touchAction(): TouchAction = if (_state.value.transform != null) TouchAction.TRANSFORM else when (_state.value.tool) {
        ToolMode.FILL -> TouchAction.FILL
        ToolMode.EYEDROPPER -> TouchAction.EYEDROPPER
        ToolMode.SELECT -> TouchAction.SELECT
        else -> TouchAction.PAINT
    }

    fun strokeParams(isStylus: Boolean, eraserTip: Boolean): StrokeParams {
        val s = _state.value
        val mode = if (eraserTip) ToolMode.ERASER else s.paintingTool
        val brush = s.brushFor(mode)
        val size: Float
        val opacity: Float
        if (eraserTip && s.paintingTool != ToolMode.ERASER) {
            size = settings.size(ToolMode.ERASER.slot, brush.id, brush.defaultSize)
            opacity = settings.opacity(ToolMode.ERASER.slot, brush.id, 1f)
        } else {
            size = s.size
            opacity = s.opacity
        }
        return StrokeParams(
            brush = brush,
            tool = mode.engineTool,
            size = size,
            opacity = opacity,
            color = s.color,
            symmetry = s.symmetry,
            usePressure = isStylus,
            extraStreamline = s.extraStreamline,
        )
    }

    fun beginStroke(first: InputPoint, isStylus: Boolean, eraserTip: Boolean) {
        if (_state.value.panel != null) closePanel()
        host?.beginStroke(strokeParams(isStylus, eraserTip), first)
    }

    fun fill(x: Int, y: Int) {
        val s = _state.value
        host?.fill(x, y, s.color, s.fillTolerance, s.fillAllLayers)
    }

    // ---- Selection --------------------------------------------------------------------------------

    fun setSelectShape(shape: SelectShape) = _state.update { it.copy(selectShape = shape) }
    fun setSelectMode(mode: Editor.SelectMode) = _state.update { it.copy(selectMode = mode) }

    /** A finished lasso, rectangle or ellipse outline in canvas pixels. */
    fun selectOutline(xy: FloatArray) {
        if (xy.size < 6) return
        host?.selectPolygon(xy, _state.value.selectMode)
    }

    /** Automatic selection: tap an area of colour. */
    fun selectAt(x: Int, y: Int) {
        val s = _state.value
        host?.selectSimilar(x, y, s.fillTolerance, s.fillAllLayers, s.selectMode)
    }

    fun selectAll() = host?.selectAll()
    fun deselect() = host?.deselect()
    fun invertSelection() = host?.invertSelection()
    fun clearSelected() = host?.clearSelected()
    fun fillSelection() = host?.fillSelection(_state.value.color)
    fun selectionToNewLayer(cut: Boolean) = host?.selectionToNewLayer(cut)

    // ---- Move and transform -----------------------------------------------------------------------

    /** Starts moving the active layer, or finishes the move if one is already going. */
    fun toggleTransform() {
        if (_state.value.transform != null) {
            commitTransform()
            return
        }
        val h = host ?: return
        closePanel()
        h.beginTransform { source ->
            if (source == null) showMessage("This layer is empty or hidden, so there's nothing to move")
            else _state.update { it.copy(transform = TransformUi(source)) }
        }
    }

    fun updateTransform(m: Affine) {
        val t = _state.value.transform ?: return
        _state.update { it.copy(transform = t.copy(matrix = m)) }
        host?.previewTransform(m, t.quality)
    }

    /** Flips along the box's own axis, so a rotated object flips the way it looks. */
    fun flipTransform(horizontal: Boolean) {
        val t = _state.value.transform ?: return
        val flip = if (horizontal) Affine.scale(-1f, 1f) else Affine.scale(1f, -1f)
        updateTransform(t.matrix * Affine.about(t.centerX, t.centerY, flip))
    }

    fun rotateTransform90() {
        val t = _state.value.transform ?: return
        val cx = t.matrix.mapX(t.centerX, t.centerY)
        val cy = t.matrix.mapY(t.centerX, t.centerY)
        updateTransform(Affine.about(cx, cy, Affine.rotate(90f)) * t.matrix)
    }

    fun resetTransform() = updateTransform(Affine.IDENTITY)

    fun setTransformQuality(q: Resample) {
        val t = _state.value.transform ?: return
        _state.update { it.copy(transform = t.copy(quality = q)) }
        host?.previewTransform(t.matrix, q)
    }

    fun commitTransform() {
        val t = _state.value.transform ?: return
        _state.update { it.copy(transform = null) }
        host?.commitTransform(t.matrix, t.quality)
    }

    fun cancelTransform() {
        if (_state.value.transform == null) return
        _state.update { it.copy(transform = null) }
        host?.cancelTransform()
    }

    // ---- Document actions -------------------------------------------------------------------------

    fun undo() = host?.undo()
    fun redo() = host?.redo()
    fun addLayer() = host?.addLayer()
    fun selectLayer(id: String) = host?.selectLayer(id)
    fun duplicateLayer(id: String) = host?.duplicateLayer(id)
    fun deleteLayer(id: String) = host?.deleteLayer(id)
    fun clearLayer(id: String) = host?.clearLayer(id)
    fun mergeDown(id: String) = host?.mergeDown(id)
    fun setLayerProps(id: String, update: (LayerProps) -> LayerProps) = host?.setLayerProps(id, update)
    fun previewLayerProps(id: String, update: (LayerProps) -> LayerProps) = host?.previewLayerProps(id, update)
    fun commitLayerProps(id: String, before: LayerProps) = host?.commitLayerProps(id, before)

    /** Moves a layer up or down the stack shown in the panel (top of the list is the top layer). */
    fun moveLayer(id: String, up: Boolean) {
        val layers = _state.value.host.layers.asReversed() // bottom first, like the document
        val from = layers.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = if (up) from + 1 else from - 1
        if (to !in layers.indices) return
        host?.moveLayer(from, to)
    }

    fun setPaper(color: Int, visible: Boolean) = host?.setPaper(color, visible)

    fun rename(name: String) {
        val n = name.trim().ifEmpty { return }
        host?.rename(n)
        _state.update { it.copy(projectName = n) }
    }

    fun saveNow() = host?.saveNow()

    /** Encodes the artwork and either saves it to the gallery or hands back a share intent. */
    fun export(context: Context, format: ExportFormat, share: Boolean, onShare: (Intent) -> Unit) {
        val h = host ?: return
        commitTransform()
        _state.update { it.copy(busy = true) }
        val name = _state.value.projectName
        h.withDocument({ doc -> Exporter.encodeToCache(getApplication(), doc, name, format) }) { file ->
            viewModelScope.launch {
                try {
                    if (share) {
                        onShare(Exporter.shareIntent(context, file, format))
                    } else {
                        val uri = withContext(Dispatchers.IO) { Exporter.saveToGallery(getApplication(), file, format) }
                        val where = if (format == ExportFormat.PSD) "Downloads/Artist Haven" else "Pictures/Artist Haven"
                        showMessage(if (uri != null) "Saved to $where" else "Couldn't save the file")
                    }
                } catch (t: Throwable) {
                    showMessage("Export failed: ${t.message}")
                } finally {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun close(done: () -> Unit) {
        val h = host
        if (h == null) done() else h.close(done)
        host = null
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(backgroundSaver)
        host?.close()
        host = null
        super.onCleared()
    }

    class Factory(private val app: Application, private val projectId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CanvasViewModel(app, projectId) as T
    }

    companion object {
        /** Undo memory: a quarter of the app's heap, between 64 and 512 MB. */
        fun historyBudget(context: Context): Long {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val heapMb = am.largeMemoryClass.toLong()
            return (heapMb / 4).coerceIn(64, 512) * 1024 * 1024
        }
    }
}
