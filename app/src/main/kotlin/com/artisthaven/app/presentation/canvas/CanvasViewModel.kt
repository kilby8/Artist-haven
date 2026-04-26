package com.artisthaven.app.presentation.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artisthaven.app.domain.command.CommandHistory
import com.artisthaven.app.domain.command.DrawingCommand
import com.artisthaven.app.domain.model.Brush
import com.artisthaven.app.domain.model.BrushType
import com.artisthaven.app.domain.model.DrawingStroke
import com.artisthaven.app.domain.model.Layer
import com.artisthaven.app.domain.model.LayerBlendMode
import com.artisthaven.app.domain.model.Project
import com.artisthaven.app.domain.model.StrokePoint
import com.artisthaven.app.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

data class CanvasUiState(
    val project: Project? = null,
    val layers: List<Layer> = emptyList(),
    val activeLayerIndex: Int = 0,
    val activeBrush: Brush = Brush(),
    val selectedColor: Color = Color.Black,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isLayerDrawerOpen: Boolean = false,
    val isBrushSidebarOpen: Boolean = true,
    val isExporting: Boolean = false,
    val exportedFilePath: String? = null,
)

/**
 * ViewModel for the main drawing canvas.
 * Manages drawing state, layer management, and command history.
 * Follows Clean Architecture - communicates with domain layer only.
 */
@HiltViewModel
class CanvasViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val commandHistory: CommandHistory,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private val layerBitmaps = mutableMapOf<String, Bitmap>()

    private var canvasWidth: Int = 0
    private var canvasHeight: Int = 0

    init {
        viewModelScope.launch {
            commandHistory.canUndo.collect { canUndo ->
                _uiState.update { it.copy(canUndo = canUndo) }
            }
        }
        viewModelScope.launch {
            commandHistory.canRedo.collect { canRedo ->
                _uiState.update { it.copy(canRedo = canRedo) }
            }
        }
        createNewProject()
    }

    fun onCanvasSizeAvailable(width: Int, height: Int) {
        if (canvasWidth == width && canvasHeight == height) return
        canvasWidth = width
        canvasHeight = height
        _uiState.value.layers.forEach { layer ->
            if (!layerBitmaps.containsKey(layer.id)) {
                layerBitmaps[layer.id] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
        }
    }

    fun createNewProject(name: String = "Untitled", widthPx: Int = 2048, heightPx: Int = 2048) {
        val projectId = UUID.randomUUID().toString()
        val firstLayer = Layer(
            id = UUID.randomUUID().toString(),
            name = "Layer 1",
            order = 0,
        )
        val project = Project(
            id = projectId,
            name = name,
            widthPx = widthPx,
            heightPx = heightPx,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            layers = listOf(firstLayer),
        )
        commandHistory.clear()
        layerBitmaps.clear()

        _uiState.update {
            it.copy(
                project = project,
                layers = project.layers,
                activeLayerIndex = 0,
            )
        }

        viewModelScope.launch {
            projectRepository.saveProject(project)
        }
    }

    fun selectBrushType(brushType: BrushType) {
        _uiState.update { state ->
            state.copy(
                activeBrush = state.activeBrush.copy(
                    type = brushType,
                    size = brushType.defaultSize,
                    opacity = brushType.defaultOpacity,
                    hardness = brushType.defaultHardness,
                )
            )
        }
    }

    fun updateBrushSize(size: Float) {
        _uiState.update { it.copy(activeBrush = it.activeBrush.copy(size = size)) }
    }

    fun updateBrushOpacity(opacity: Float) {
        _uiState.update { it.copy(activeBrush = it.activeBrush.copy(opacity = opacity)) }
    }

    fun updateBrushHardness(hardness: Float) {
        _uiState.update { it.copy(activeBrush = it.activeBrush.copy(hardness = hardness)) }
    }

    fun updateColor(color: Color) {
        _uiState.update { it.copy(selectedColor = color, activeBrush = it.activeBrush.copy(color = color)) }
    }

    fun commitStroke(stroke: DrawingStroke) {
        val activeLayer = _uiState.value.layers.getOrNull(_uiState.value.activeLayerIndex)
            ?: return
        if (activeLayer.isLocked) return

        val bitmap = layerBitmaps[activeLayer.id] ?: return

        val command = StrokeCommand(
            stroke = stroke,
            targetBitmap = bitmap,
        )
        commandHistory.execute(command)
    }

    fun addLayer() {
        val currentLayers = _uiState.value.layers
        val newLayer = Layer(
            id = UUID.randomUUID().toString(),
            name = "Layer ${currentLayers.size + 1}",
            order = currentLayers.size,
        )
        if (canvasWidth > 0 && canvasHeight > 0) {
            layerBitmaps[newLayer.id] = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        }
        val updatedLayers = currentLayers + newLayer
        _uiState.update { it.copy(layers = updatedLayers, activeLayerIndex = updatedLayers.lastIndex) }
        saveCurrentProject()
    }

    fun deleteLayer(layerId: String) {
        val currentLayers = _uiState.value.layers
        if (currentLayers.size <= 1) return
        layerBitmaps.remove(layerId)
        val updatedLayers = currentLayers.filter { it.id != layerId }
        val newActiveIndex = _uiState.value.activeLayerIndex.coerceAtMost(updatedLayers.lastIndex)
        _uiState.update { it.copy(layers = updatedLayers, activeLayerIndex = newActiveIndex) }
        saveCurrentProject()
        viewModelScope.launch {
            projectRepository.deleteLayer(layerId)
        }
    }

    fun selectLayer(index: Int) {
        if (index in _uiState.value.layers.indices) {
            _uiState.update { it.copy(activeLayerIndex = index) }
        }
    }

    fun toggleLayerVisibility(layerId: String) {
        val updatedLayers = _uiState.value.layers.map { layer ->
            if (layer.id == layerId) layer.copy(isVisible = !layer.isVisible) else layer
        }
        _uiState.update { it.copy(layers = updatedLayers) }
        saveCurrentProject()
    }

    fun updateLayerOpacity(layerId: String, opacity: Float) {
        val updatedLayers = _uiState.value.layers.map { layer ->
            if (layer.id == layerId) layer.copy(opacity = opacity) else layer
        }
        _uiState.update { it.copy(layers = updatedLayers) }
    }

    fun updateLayerBlendMode(layerId: String, blendMode: LayerBlendMode) {
        val updatedLayers = _uiState.value.layers.map { layer ->
            if (layer.id == layerId) layer.copy(blendMode = blendMode) else layer
        }
        _uiState.update { it.copy(layers = updatedLayers) }
        saveCurrentProject()
    }

    fun reorderLayers(fromIndex: Int, toIndex: Int) {
        val layers = _uiState.value.layers.toMutableList()
        if (fromIndex !in layers.indices || toIndex !in layers.indices) return
        val layer = layers.removeAt(fromIndex)
        layers.add(toIndex, layer)
        val reordered = layers.mapIndexed { i, l -> l.copy(order = i) }
        _uiState.update { it.copy(layers = reordered) }
        saveCurrentProject()
    }

    fun undo() {
        commandHistory.undo()
    }

    fun redo() {
        commandHistory.redo()
    }

    fun toggleLayerDrawer() {
        _uiState.update { it.copy(isLayerDrawerOpen = !it.isLayerDrawerOpen) }
    }

    fun toggleBrushSidebar() {
        _uiState.update { it.copy(isBrushSidebarOpen = !it.isBrushSidebarOpen) }
    }

    fun exportAsPng() {
        val projectId = _uiState.value.project?.id ?: return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val merged = mergeLayers() ?: return@launch
            val stream = ByteArrayOutputStream()
            merged.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val path = projectRepository.exportAsPng(projectId, stream.toByteArray())
            _uiState.update { it.copy(isExporting = false, exportedFilePath = path) }
        }
    }

    fun getLayerBitmap(layerId: String): Bitmap? = layerBitmaps[layerId]

    fun ensureLayerBitmap(layerId: String): Bitmap {
        return layerBitmaps.getOrPut(layerId) {
            Bitmap.createBitmap(
                canvasWidth.coerceAtLeast(1),
                canvasHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
        }
    }

    private fun mergeLayers(): Bitmap? {
        if (canvasWidth <= 0 || canvasHeight <= 0) return null
        val result = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
        _uiState.value.layers
            .filter { it.isVisible }
            .sortedBy { it.order }
            .forEach { layer ->
                val bitmap = layerBitmaps[layer.id] ?: return@forEach
                paint.alpha = (layer.opacity * 255).toInt()
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
        return result
    }

    private fun saveCurrentProject() {
        val project = _uiState.value.project ?: return
        val updatedProject = project.copy(
            layers = _uiState.value.layers,
            modifiedAt = System.currentTimeMillis(),
        )
        _uiState.update { it.copy(project = updatedProject) }
        viewModelScope.launch {
            projectRepository.saveProject(updatedProject)
        }
    }

    override fun onCleared() {
        super.onCleared()
        layerBitmaps.values.forEach { it.recycle() }
        layerBitmaps.clear()
    }
}

/**
 * Command for drawing a stroke onto a layer bitmap.
 * Captures a snapshot of the affected pixels for undo support.
 */
private class StrokeCommand(
    private val stroke: DrawingStroke,
    private val targetBitmap: Bitmap,
) : DrawingCommand {

    private var undoBitmap: Bitmap? = null

    override val description: String = "Stroke with ${stroke.brushSnapshot.type.displayName}"

    override fun execute() {
        if (undoBitmap == null) {
            undoBitmap = targetBitmap.copy(targetBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
        renderStroke()
    }

    override fun undo() {
        undoBitmap?.let { snapshot ->
            val canvas = android.graphics.Canvas(targetBitmap)
            val paint = AndroidPaint()
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            canvas.drawBitmap(snapshot, 0f, 0f, paint)
        }
    }

    private fun renderStroke() {
        if (stroke.points.size < 2) {
            stroke.points.firstOrNull()?.let { point ->
                drawDot(point)
            }
            return
        }

        val canvas = android.graphics.Canvas(targetBitmap)
        val brush = stroke.brushSnapshot
        val paint = createPaint(brush)

        val path = android.graphics.Path()
        val points = stroke.points

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val cp1x = curr.x - (next.x - prev.x) / 6f
            val cp1y = curr.y - (next.y - prev.y) / 6f
            val cp2x = curr.x + (next.x - prev.x) / 6f
            val cp2y = curr.y + (next.y - prev.y) / 6f

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, curr.x, curr.y)
        }

        val last = points.last()
        path.lineTo(last.x, last.y)

        canvas.drawPath(path, paint)
    }

    private fun drawDot(point: StrokePoint) {
        val canvas = android.graphics.Canvas(targetBitmap)
        val brush = stroke.brushSnapshot
        val paint = createPaint(brush)
        paint.style = AndroidPaint.Style.FILL
        val radius = brush.size * point.pressure / 2f
        canvas.drawCircle(point.x, point.y, radius, paint)
    }

    private fun createPaint(brush: Brush): AndroidPaint {
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

        when (brush.type) {
            BrushType.ERASER -> {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            else -> {
                paint.color = brush.color.toArgb()
                paint.alpha = (brush.opacity * 255).toInt()
            }
        }

        val strokeWidth = brush.size
        paint.strokeWidth = strokeWidth
        paint.style = AndroidPaint.Style.STROKE
        paint.strokeCap = AndroidPaint.Cap.ROUND
        paint.strokeJoin = AndroidPaint.Join.ROUND

        val blurRadius = strokeWidth * (1f - brush.hardness) * 0.5f
        if (blurRadius > 0.5f) {
            paint.maskFilter = android.graphics.BlurMaskFilter(
                blurRadius,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }

        return paint
    }
}
