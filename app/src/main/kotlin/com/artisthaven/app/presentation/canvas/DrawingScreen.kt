package com.artisthaven.app.presentation.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.artisthaven.app.BuildConfig
import com.artisthaven.app.presentation.brush.BrushSidebar
import com.artisthaven.app.presentation.brush.library.BrushPaletteDialog
import com.artisthaven.app.presentation.layer.LayerDrawer

/**
 * Main drawing screen composable.
 * Provides a full-screen canvas with brush sidebar, brush library, and layer drawer.
 */
@Composable
fun DrawingScreen(
    viewModel: CanvasViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var canvasView by remember { mutableStateOf<DrawingCanvasView?>(null) }
    var isRenameDialogVisible by remember { mutableStateOf(false) }
    var isSaveDialogVisible by remember { mutableStateOf(false) }
    var isLoadDialogVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        DrawingCanvasArea(
            uiState = uiState,
            viewModel = viewModel,
            onViewReady = { canvasView = it },
            modifier = Modifier.fillMaxSize(),
        )

        if (uiState.isBrushSidebarOpen || uiState.isLayerDrawerOpen) {
            val dismissInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.10f))
                    .clickable(
                        interactionSource = dismissInteractionSource,
                        indication = null,
                    ) {
                        viewModel.closeBrushSidebar()
                        viewModel.closeLayerDrawer()
                    },
            )
        }

        DrawingToolbar(
            uiState = uiState,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onToggleLayers = { viewModel.toggleLayerDrawer() },
            onToggleBrush = { viewModel.toggleBrushSidebar() },
            onSave = { isSaveDialogVisible = true },
            onLoad = { isLoadDialogVisible = true },
            onEditProjectName = { isRenameDialogVisible = true },
            onZoomIn = { canvasView?.zoomIn() },
            onZoomOut = { canvasView?.zoomOut() },
            onResetZoom = { canvasView?.resetZoom() },
            onResetCanvas = {
                viewModel.clearActiveLayer()
                canvasView?.invalidate()
            },
            onExport = { viewModel.exportAsPng() },
            onToggleCanvasSelector = { viewModel.toggleCanvasTypeSelector() },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )

        AnimatedVisibility(
            visible = uiState.isBrushSidebarOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            BrushSidebar(
                activeBrush = uiState.activeBrush,
                selectedColor = uiState.selectedColor,
                recentBrushes = uiState.recentBrushDefinitions,
                selectedBrushDefinition = uiState.selectedBrushDefinition,
                selectedBrushDefinitionId = uiState.selectedBrushDefinition?.id,
                savedColors = uiState.savedColors,
                brushPresets = uiState.brushPresets,
                activeBrushStyle = uiState.activeBrush.style,
                onBrushTypeSelected = { viewModel.selectBrushType(it) },
                onBrushStyleSelected = { viewModel.selectBrushStyle(it) },
                onRecentBrushSelected = { viewModel.selectRecentBrush(it) },
                onBrushSizeChanged = { viewModel.updateBrushSize(it) },
                onBrushOpacityChanged = { viewModel.updateBrushOpacity(it) },
                onBrushHardnessChanged = { viewModel.updateBrushHardness(it) },
                onColorSelected = { viewModel.updateColor(it) },
                onDynamicsPowerCurveChanged = { viewModel.updateDynamicsPowerCurve(it) },
                onGrainScaleChanged = { viewModel.updateGrainScale(it) },
                onGrainStrengthChanged = { viewModel.updateGrainStrength(it) },
                onTipSpacingChanged = { viewModel.updateTipSpacing(it) },
                onTipJitterChanged = { viewModel.updateTipJitter(it) },
                onTipOverlapFactorChanged = { viewModel.updateTipOverlapFactor(it) },
                onTipAlphaSmoothingChanged = { viewModel.updateTipAlphaSmoothing(it) },
                onTipMicroDabToggled = { viewModel.updateTipMicroDabEnabled(it) },
                onTipMinGapClampingChanged = { viewModel.updateTipMinGapClamping(it) },
                onFluidJitterPercentChanged = { viewModel.updateFluidJitterPercent(it) },
                onFluidAccumulationAlphaChanged = { viewModel.updateFluidAccumulationAlpha(it) },
                onFluidVelocitySpacingTighteningChanged = { viewModel.updateFluidVelocitySpacingTightening(it) },
                onEdgeSoftnessChanged = { viewModel.updateEdgeSoftness(it) },
                onCornerSmoothingChanged = { viewModel.updateCornerSmoothing(it) },
                onSaveColor = { viewModel.saveColor(it) },
                onRemoveColor = { viewModel.removeColor(it) },
                onSavePreset = { viewModel.saveCurrentBrushPreset(it) },
                onApplyPreset = { viewModel.applyBrushPreset(it) },
                onDeletePreset = { viewModel.deleteBrushPreset(it) },
                onOpenBrushLibrary = { viewModel.toggleBrushLibrary() },
                onClose = { viewModel.toggleBrushSidebar() },
            )
        }

        if (uiState.isCanvasTypeSelectorOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.closeCanvasTypeSelector() },
                title = { Text("Canvas Type") },
                text = {
                    CanvasTypeSelector(
                        selectedCanvasType = uiState.canvasType,
                        onCanvasTypeSelected = { canvasType ->
                            viewModel.setCanvasType(canvasType)
                            viewModel.closeCanvasTypeSelector()
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.closeCanvasTypeSelector() }) {
                        Text("Done")
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = uiState.isLayerDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            LayerDrawer(
                layers = uiState.layers,
                activeLayerIndex = uiState.activeLayerIndex,
                onLayerSelected = { viewModel.selectLayer(it) },
                onLayerVisibilityToggled = { viewModel.toggleLayerVisibility(it) },
                onLayerAdded = { viewModel.addLayer() },
                onLayerDeleted = { viewModel.deleteLayer(it) },
                onLayerOpacityChanged = { layerId, opacity -> viewModel.updateLayerOpacity(layerId, opacity) },
                onClose = { viewModel.closeLayerDrawer() },
            )
        }

        // Brush Library Dialog
        BrushPaletteDialog(
            visible = uiState.isBrushLibraryOpen,
            selectedBrushId = uiState.selectedBrushDefinition?.id,
            onBrushSelected = { brushDef -> viewModel.selectBrushFromLibrary(brushDef) },
            onDismiss = { viewModel.toggleBrushLibrary() },
        )

        if (uiState.isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        ProjectRenameDialog(
            visible = isRenameDialogVisible,
            currentName = uiState.project?.name ?: "",
            onDismiss = { isRenameDialogVisible = false },
            onConfirm = { newName ->
                viewModel.renameProject(newName)
                isRenameDialogVisible = false
            },
        )

        SaveProjectDialog(
            visible = isSaveDialogVisible,
            currentName = uiState.project?.name ?: "",
            currentFolder = uiState.project?.folderName ?: "General",
            onDismiss = { isSaveDialogVisible = false },
            onConfirm = { name, folder ->
                viewModel.saveProjectWithOptions(name = name, folderName = folder)
                isSaveDialogVisible = false
            },
        )

        LoadProjectDialog(
            visible = isLoadDialogVisible,
            projects = uiState.savedProjects,
            currentProjectId = uiState.project?.id,
            onDismiss = { isLoadDialogVisible = false },
            onProjectSelected = { projectId ->
                viewModel.loadProject(projectId)
                isLoadDialogVisible = false
            },
        )
    }
}

@Composable
private fun DrawingCanvasArea(
    uiState: CanvasUiState,
    viewModel: CanvasViewModel,
    onViewReady: (DrawingCanvasView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            DrawingCanvasView(context).apply {
                onViewReady(this)
                getActiveBrush = { uiState.activeBrush }
                getLayerBitmaps = {
                    uiState.layers
                        .filter { it.isVisible }
                        .sortedBy { it.order }
                        .mapNotNull { layer ->
                            val bitmap = viewModel.getLayerBitmap(layer.id)
                            if (bitmap != null) Pair(bitmap, layer.opacity) else null
                        }
                }
                onSizeAvailable = { w, h ->
                    viewModel.onCanvasSizeAvailable(w, h)
                }
                onStrokeCommitted = { stroke ->
                    viewModel.commitStroke(stroke)
                }
                getCanvasRenderingManager = { viewModel.getCanvasRenderingManager() }
            }
        },
        update = { view ->

            view.getActiveBrush = { uiState.activeBrush }
            view.getLayerBitmaps = {
                uiState.layers
                    .filter { it.isVisible }
                    .sortedBy { it.order }
                    .mapNotNull { layer ->
                        val bitmap = viewModel.getLayerBitmap(layer.id)
                        if (bitmap != null) Pair(bitmap, layer.opacity) else null
                    }
            }
            view.invalidate()
        },
        modifier = modifier.background(Color(0xFFF5F5F0)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawingToolbar(
    uiState: CanvasUiState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleLayers: () -> Unit,
    onToggleBrush: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onEditProjectName: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onResetCanvas: () -> Unit,
    onExport: () -> Unit,
    onToggleCanvasSelector: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showOverflow by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = uiState.project?.name ?: "Artist Haven",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 14.sp,
                )
                Text(
                    text = "Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            // Brush icon — opens brush sidebar
            IconButton(onClick = onToggleBrush) {
                Icon(Icons.Default.Brush, contentDescription = "Brushes")
            }
        },
        actions = {
            // Primary actions always visible
            FilledTonalButton(
                onClick = onToggleCanvasSelector,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onUndo, enabled = uiState.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = uiState.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onToggleLayers) {
                Icon(Icons.Default.Layers, contentDescription = "Layers")
            }
            // Overflow menu for secondary actions
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
                        enabled = false,
                        onClick = {},
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Rename project") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showOverflow = false; onEditProjectName() },
                    )
                    DropdownMenuItem(
                        text = { Text("Save") },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = { showOverflow = false; onSave() },
                    )
                    DropdownMenuItem(
                        text = { Text("Load project") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = { showOverflow = false; onLoad() },
                    )
                    DropdownMenuItem(
                        text = { Text("Canvas Type") },
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        onClick = { showOverflow = false; onToggleCanvasSelector() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Zoom in") },
                        leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                        onClick = { showOverflow = false; onZoomIn() },
                    )
                    DropdownMenuItem(
                        text = { Text("Zoom out") },
                        leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = null) },
                        onClick = { showOverflow = false; onZoomOut() },
                    )
                    DropdownMenuItem(
                        text = { Text("Reset zoom") },
                        leadingIcon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null) },
                        onClick = { showOverflow = false; onResetZoom() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Clear Canvas") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { showOverflow = false; onResetCanvas() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Export PNG") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = { showOverflow = false; onExport() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
        modifier = modifier,
    )
}

@Composable
private fun SaveProjectDialog(
    visible: Boolean,
    currentName: String,
    currentFolder: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    if (!visible) return

    var pendingName by remember(currentName) { mutableStateOf(currentName) }
    var pendingFolder by remember(currentFolder) { mutableStateOf(currentFolder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pendingName,
                    onValueChange = { pendingName = it },
                    singleLine = true,
                    label = { Text("Project name") },
                )
                OutlinedTextField(
                    value = pendingFolder,
                    onValueChange = { pendingFolder = it },
                    singleLine = true,
                    label = { Text("Folder") },
                    supportingText = { Text("Use folders to organize multiple saves of the same project") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pendingName, pendingFolder) },
                enabled = pendingName.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun LoadProjectDialog(
    visible: Boolean,
    projects: List<SavedProjectItem>,
    currentProjectId: String?,
    onDismiss: () -> Unit,
    onProjectSelected: (String) -> Unit,
) {
    if (!visible) return

    val groupedProjects = remember(projects) {
        projects
            .sortedByDescending { it.modifiedAt }
            .groupBy { it.folderName.ifBlank { "General" } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Project") },
        text = {
            if (projects.isEmpty()) {
                Text("No saved projects found.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groupedProjects.forEach { (folderName, folderProjects) ->
                        item {
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(folderProjects, key = { it.id }) { project ->
                            TextButton(
                                onClick = { onProjectSelected(project.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val isCurrent = project.id == currentProjectId
                                val label = if (isCurrent) "${project.name} (current)" else project.name
                                Text(label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ProjectRenameDialog(
    visible: Boolean,
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!visible) return

    var pendingName by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Project") },
        text = {
            OutlinedTextField(
                value = pendingName,
                onValueChange = { pendingName = it },
                singleLine = true,
                label = { Text("Project name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pendingName) },
                enabled = pendingName.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

