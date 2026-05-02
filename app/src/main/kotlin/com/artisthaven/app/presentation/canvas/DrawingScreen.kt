package com.artisthaven.app.presentation.canvas

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
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

    Box(modifier = Modifier.fillMaxSize()) {
        DrawingCanvasArea(
            uiState = uiState,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        )

        DrawingToolbar(
            uiState = uiState,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onToggleLayers = { viewModel.toggleLayerDrawer() },
            onToggleBrush = { viewModel.toggleBrushSidebar() },
            onExport = { viewModel.exportAsPng() },
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
                selectedBrushDefinitionId = uiState.selectedBrushDefinition?.id,
                onBrushTypeSelected = { viewModel.selectBrushType(it) },
                onRecentBrushSelected = { viewModel.selectRecentBrush(it) },
                onBrushSizeChanged = { viewModel.updateBrushSize(it) },
                onBrushOpacityChanged = { viewModel.updateBrushOpacity(it) },
                onBrushHardnessChanged = { viewModel.updateBrushHardness(it) },
                onColorSelected = { viewModel.updateColor(it) },
                onOpenBrushLibrary = { viewModel.toggleBrushLibrary() },
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
    }
}

@Composable
private fun DrawingCanvasArea(
    uiState: CanvasUiState,
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            DrawingCanvasView(context).apply {
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
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = uiState.project?.name ?: "Artist Haven",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = onToggleBrush) {
                Icon(Icons.Default.Brush, contentDescription = "Toggle brushes")
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = uiState.canUndo) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = uiState.canRedo) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onToggleLayers) {
                Icon(Icons.Default.Layers, contentDescription = "Toggle layers")
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export PNG")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
        modifier = modifier,
    )
}
