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
import com.artisthaven.app.domain.model.BlendBehavior
import com.artisthaven.app.domain.model.BrushDefinition
import com.artisthaven.app.domain.model.BrushLibrary
import com.artisthaven.app.domain.model.BrushPreset
import com.artisthaven.app.domain.model.BrushProfile
import com.artisthaven.app.domain.model.BrushStyle
import com.artisthaven.app.domain.model.BrushType
import com.artisthaven.app.domain.model.CanvasType
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
import kotlinx.coroutines.flow.collect
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
    val isBrushSidebarOpen: Boolean = false,
    val isBrushLibraryOpen: Boolean = false,
    val selectedBrushDefinition: BrushDefinition? = null,
    val recentBrushDefinitions: List<BrushDefinition> = emptyList(),
    val isExporting: Boolean = false,
    val exportedFilePath: String? = null,
    val savedProjects: List<SavedProjectItem> = emptyList(),
    val savedColors: List<Color> = listOf(
        Color.Black, Color.White,
        Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF388E3C),
        Color(0xFFF57F17), Color(0xFF7B1FA2),
    ),
    val styleProfiles: Map<BrushStyle, BrushProfile> = BrushStyle.entries.associateWith { style ->
        BrushProfile.preset(style)
    },
    val brushPresets: List<BrushPreset> = emptyList(),
    // Canvas system properties
    val canvasType: CanvasType = CanvasType.COLD_PRESS_PAPER,
    val enableToothInteraction: Boolean = true,
    val enableLighting: Boolean = false,
    val lightingIntensity: Float = 0.15f,
    val isCanvasTypeSelectorOpen: Boolean = false,
)

data class SavedProjectItem(
    val id: String,
    val name: String,
    val folderName: String,
    val modifiedAt: Long,
)

/**
 * ViewModel for the main drawing canvas.
 * Manages drawing state, layer management, command history, and brush library integration.
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
    private val brushEngine = BrushEngine(context)
    private val canvasRenderingManager = CanvasRenderingManager()

    private var canvasWidth: Int = 0
    private var canvasHeight: Int = 0

    init {
        viewModelScope.launch {
            projectRepository.observeProjects().collect { projects ->
                _uiState.update { state ->
                    state.copy(
                        savedProjects = projects.map { project ->
                            SavedProjectItem(
                                id = project.id,
                                name = project.name,
                                folderName = project.folderName,
                                modifiedAt = project.modifiedAt,
                            )
                        }
                    )
                }
            }
        }
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

        // Initialize canvas rendering system
        canvasRenderingManager.initialize(
            width = width,
            height = height,
            canvasType = _uiState.value.canvasType,
            enableToothInteraction = _uiState.value.enableToothInteraction,
        )

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
            val nextStyle = suggestedStyleForType(brushType, state.activeBrush.style)
            val styleProfile = state.styleProfiles[nextStyle] ?: BrushProfile.preset(nextStyle)
            val resolvedProfile = if (brushType == BrushType.ERASER) {
                styleProfile.copy(blend = BlendBehavior.CLEAR)
            } else {
                styleProfile
            }
            state.copy(
                activeBrush = state.activeBrush.copy(
                    type = brushType,
                    style = nextStyle,
                    profile = resolvedProfile,
                    size = brushType.defaultSize,
                    opacity = brushType.defaultOpacity,
                    hardness = brushType.defaultHardness,
                ),
                selectedBrushDefinition = null,
            )
        }
    }

    fun selectBrushFromLibrary(brushDefinition: BrushDefinition) {
        // Map BrushDefinition to closest BrushType
        val brushType = mapBrushDefinitionToType(brushDefinition)

        val style = _uiState.value.activeBrush.style
        val styleProfile = _uiState.value.styleProfiles[style] ?: BrushProfile.preset(style)
        val drawingBrush = Brush(
            type = brushType,
            style = style,
            profile = styleProfile,
            size = brushDefinition.defaultSize,
            opacity = brushDefinition.defaultOpacity,
            color = _uiState.value.selectedColor,
            hardness = brushDefinition.defaultHardness,
            spacing = brushDefinition.spacing,
            textureStrength = if (brushDefinition.usesShader) brushDefinition.scatter else 0f,
        )

        _uiState.update { state ->
            state.copy(
                activeBrush = drawingBrush,
                selectedBrushDefinition = brushDefinition,
                recentBrushDefinitions = updatedRecentBrushes(state.recentBrushDefinitions, brushDefinition),
                isBrushLibraryOpen = false,
            )
        }
    }

    fun selectRecentBrush(brushDefinition: BrushDefinition) {
        selectBrushFromLibrary(brushDefinition)
    }

    fun saveCurrentBrushPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        _uiState.update { state ->
            val preset = BrushPreset(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                brush = state.activeBrush,
            )
            state.copy(brushPresets = (listOf(preset) + state.brushPresets).take(24))
        }
    }

    fun applyBrushPreset(presetId: String) {
        _uiState.update { state ->
            val preset = state.brushPresets.firstOrNull { it.id == presetId } ?: return@update state
            val updatedProfiles = state.styleProfiles + (preset.brush.style to preset.brush.profile)
            state.copy(
                activeBrush = preset.brush,
                selectedColor = preset.brush.color,
                styleProfiles = updatedProfiles,
            )
        }
    }

    fun deleteBrushPreset(presetId: String) {
        _uiState.update { state ->
            state.copy(brushPresets = state.brushPresets.filterNot { it.id == presetId })
        }
    }

    fun selectBrushStyle(style: BrushStyle) {
        _uiState.update { state ->
            val profile = state.styleProfiles[style] ?: BrushProfile.preset(style)
            state.copy(
                activeBrush = state.activeBrush.copy(
                    style = style,
                    profile = profile,
                )
            )
        }
    }

    fun updateDynamicsPowerCurve(exponent: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(
                dynamics = profile.dynamics.copy(powerCurveExponent = exponent.coerceIn(0.7f, 3.0f))
            )
        }
    }

    fun updateGrainScale(scale: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(grain = profile.grain.copy(scale = scale.coerceIn(0.2f, 3.0f)))
        }
    }

    fun updateGrainStrength(strength: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(grain = profile.grain.copy(strength = strength.coerceIn(0f, 1f), enabled = strength > 0.01f))
        }
    }

    fun updateTipSpacing(spacing: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(spacing = spacing.coerceIn(0.05f, 0.7f)))
        }
    }

    fun updateTipJitter(jitter: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(jitter = jitter.coerceIn(0f, 0.6f)))
        }
    }

    fun updateTipOverlapFactor(overlapFactor: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(overlapFactor = overlapFactor.coerceIn(0f, 1f)))
        }
    }

    fun updateTipAlphaSmoothing(alphaSmoothing: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(alphaSmoothing = alphaSmoothing.coerceIn(0.05f, 0.35f)))
        }
    }

    fun updateTipMicroDabEnabled(enabled: Boolean) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(enableMicroDab = enabled, useMicroDabs = enabled))
        }
    }

    fun updateTipMinGapClamping(minGapPx: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(minGapClamping = minGapPx.coerceIn(0.25f, 4f)))
        }
    }

    fun updateFluidJitterPercent(percent: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(fluidJitterPercent = percent.coerceIn(0f, 0.12f)))
        }
    }

    fun updateFluidAccumulationAlpha(alpha: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(fluidAccumulationAlpha = alpha.coerceIn(0.05f, 0.35f)))
        }
    }

    fun updateFluidVelocitySpacingTightening(tightening: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(tip = profile.tip.copy(fluidVelocitySpacingTightening = tightening.coerceIn(0f, 0.8f)))
        }
    }

    fun updateEdgeSoftness(softness: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(edge = profile.edge.copy(softness = softness.coerceIn(0f, 0.8f)))
        }
    }

    fun updateCornerSmoothing(cornerPx: Float) {
        updateActiveBrushProfile { profile ->
            profile.copy(edge = profile.edge.copy(cornerSmoothingPx = cornerPx.coerceIn(0f, 28f)))
        }
    }

    fun toggleBrushLibrary() {
        _uiState.update { it.copy(isBrushLibraryOpen = !it.isBrushLibraryOpen) }
    }

    private fun mapBrushDefinitionToType(definition: BrushDefinition): BrushType {
        // Map brush library definitions to existing BrushType enum
        return when {
            definition.id.startsWith("sketch") -> BrushType.PENCIL
            definition.id.startsWith("paint") -> BrushType.WATERCOLOR
            definition.id.startsWith("ink") -> BrushType.PEN
            definition.id.startsWith("tex") -> BrushType.CHARCOAL
            definition.id.startsWith("fx") -> BrushType.MARKER
            else -> BrushType.PEN
        }
    }

    private fun suggestedStyleForType(type: BrushType, current: BrushStyle): BrushStyle {
        return when (type) {
            BrushType.CHARCOAL -> BrushStyle.TEXTURED_CHARCOAL
            BrushType.PEN -> if (current == BrushStyle.STANDARD) BrushStyle.CALLIGRAPHY else current
            BrushType.MARKER -> if (current == BrushStyle.STANDARD) BrushStyle.NEON_GLOW else current
            else -> current
        }
    }

    private fun updatedRecentBrushes(
        current: List<BrushDefinition>,
        selected: BrushDefinition,
    ): List<BrushDefinition> {
        val deduped = current.filterNot { it.id == selected.id }
        return listOf(selected) + deduped.take(7)
    }

    private fun updateActiveBrushProfile(transform: (BrushProfile) -> BrushProfile) {
        _uiState.update { state ->
            val style = state.activeBrush.style
            val currentProfile = state.activeBrush.profile
            val updatedProfile = transform(currentProfile).copy(style = style)
            state.copy(
                activeBrush = state.activeBrush.copy(profile = updatedProfile),
                styleProfiles = state.styleProfiles + (style to updatedProfile),
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
            brushEngine = brushEngine,
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

    fun setCanvasType(canvasType: CanvasType) {
        _uiState.update { it.copy(canvasType = canvasType) }
        canvasRenderingManager.setCanvasType(canvasType)
    }

    fun setToothInteraction(enabled: Boolean) {
        _uiState.update { it.copy(enableToothInteraction = enabled) }
        canvasRenderingManager.setToothInteraction(enabled)
    }

    fun setLighting(enabled: Boolean) {
        _uiState.update { it.copy(enableLighting = enabled) }
        canvasRenderingManager.setLighting(enabled)
    }

    fun setLightingIntensity(intensity: Float) {
        _uiState.update { it.copy(lightingIntensity = intensity) }
        canvasRenderingManager.setLightingIntensity(intensity)
    }

    fun toggleCanvasTypeSelector() {
        _uiState.update { it.copy(isCanvasTypeSelectorOpen = !it.isCanvasTypeSelectorOpen) }
    }

    fun closeCanvasTypeSelector() {
        _uiState.update { it.copy(isCanvasTypeSelectorOpen = false) }
    }

    fun getCanvasRenderingManager(): CanvasRenderingManager = canvasRenderingManager

    fun toggleLayerDrawer() {
        _uiState.update { it.copy(isLayerDrawerOpen = !it.isLayerDrawerOpen) }
    }

    fun closeLayerDrawer() {
        _uiState.update { it.copy(isLayerDrawerOpen = false) }
    }

    fun toggleBrushSidebar() {
        _uiState.update { it.copy(isBrushSidebarOpen = !it.isBrushSidebarOpen) }
    }

    fun closeBrushSidebar() {
        _uiState.update { it.copy(isBrushSidebarOpen = false) }
    }

    fun renameProject(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return

        val project = _uiState.value.project ?: return
        val updatedProject = project.copy(
            name = trimmed,
            modifiedAt = System.currentTimeMillis(),
        )
        _uiState.update { it.copy(project = updatedProject) }

        viewModelScope.launch {
            projectRepository.saveProject(updatedProject)
        }
    }

    fun saveProjectNow() {
        saveCurrentProject()
    }

    fun saveColor(color: Color) {
        val current = _uiState.value.savedColors
        if (current.any { it == color }) return
        _uiState.update { it.copy(savedColors = (listOf(color) + current).take(32)) }
    }

    fun removeColor(color: Color) {
        _uiState.update { it.copy(savedColors = it.savedColors.filter { c -> c != color }) }
    }

    fun saveProjectWithOptions(name: String, folderName: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        val project = _uiState.value.project ?: return
        val normalizedFolder = normalizeFolderName(folderName)
        val updatedProject = project.copy(
            name = trimmedName,
            folderName = normalizedFolder,
            modifiedAt = System.currentTimeMillis(),
            layers = _uiState.value.layers,
        )
        _uiState.update { it.copy(project = updatedProject) }

        viewModelScope.launch {
            projectRepository.saveProject(updatedProject)
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val loadedProject = projectRepository.getProject(projectId) ?: return@launch
            val loadedLayers = projectRepository.getLayers(projectId)
                .sortedBy { it.order }
                .ifEmpty {
                    listOf(
                        Layer(
                            id = UUID.randomUUID().toString(),
                            name = "Layer 1",
                            order = 0,
                        )
                    )
                }

            commandHistory.clear()
            layerBitmaps.clear()

            if (canvasWidth > 0 && canvasHeight > 0) {
                loadedLayers.forEach { layer ->
                    layerBitmaps[layer.id] = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                }
            }

            _uiState.update {
                it.copy(
                    project = loadedProject.copy(layers = loadedLayers),
                    layers = loadedLayers,
                    activeLayerIndex = 0,
                )
            }
        }
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

    private fun normalizeFolderName(folderName: String): String {
        val trimmed = folderName.trim()
        return if (trimmed.isEmpty()) "General" else trimmed
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
    private val brushEngine: BrushEngine,
) : DrawingCommand {

    private var undoBitmap: Bitmap? = null

    override val description: String = "Stroke with ${stroke.brushSnapshot.type.displayName}"

    override fun execute() {
        // Memory trade-off: capturing a full-layer snapshot per stroke is simple and
        // correct but costs ~16 MB per undo entry for a 2048×2048 ARGB_8888 canvas.
        // CommandHistory caps history at maxSize=50 to bound peak usage (~800 MB worst
        // case). A future optimisation can store per-stroke bounding-box patches instead.
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
        val canvas = android.graphics.Canvas(targetBitmap)
        brushEngine.renderStroke(
            canvas = canvas,
            points = stroke.points,
            brush = stroke.brushSnapshot,
            isPreview = false,
        )
    }
}
