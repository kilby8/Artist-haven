package com.artisthaven.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artisthaven.app.ui.canvas.DrawingCanvas
import com.artisthaven.app.ui.components.BrushSidebar
import com.artisthaven.app.ui.components.ColorPickerDisc
import com.artisthaven.app.ui.components.LayerDrawer
import com.artisthaven.app.ui.components.QuickActionSidebar
import com.artisthaven.app.ui.components.TopToolbar

/**
 * Root composable for Artist Haven.
 *
 * Uses a [Box] as the full-screen immersive canvas root. Components are layered:
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  DrawingCanvas  (fills entire Box — bottom layer)         │
 *  │  ┌───┐                                          ┌───┐    │
 *  │  │ B │  ← BrushSidebar (left)                  │ Q │    │
 *  │  │ r │                      QuickActionSidebar→ │ A │    │
 *  │  │ u │                                          │ B │    │
 *  │  └───┘                                          └───┘    │
 *  │  ══ TopToolbar (top, auto-hides) ══════════════════════   │
 *  │                                          LayerDrawer →   │
 *  │                  ColorPickerDisc (centred popup)          │
 *  └──────────────────────────────────────────────────────────┘
 *
 * The canvas is rendered on its own layer; UI overlays use [Alignment] pins
 * to stay in their designated corners without obstructing the canvas.
 *
 * Design note: [DrawingCanvas] uses [PointerEventPass.Initial] so that stylus
 * strokes are captured before any UI overlay processes the event.
 */
@Composable
fun ArtistHavenScreen(
    modifier: Modifier = Modifier,
    viewModel: ArtistHavenViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {

        // ── 1. Drawing canvas (bottom layer, full-screen) ────────────────────
        DrawingCanvas(
            drawingState = state,
            modifier = Modifier.fillMaxSize(),
            onStrokeAdded = { viewModel.pushUndoSnapshot() }
        )

        // ── 2. Left floating sidebar — Brush Size + Opacity sliders ──────────
        BrushSidebar(
            brushSize = state.brushSize,
            opacity = state.opacity,
            onBrushSizeChange = viewModel::setBrushSize,
            onOpacityChange = viewModel::setOpacity,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        )

        // ── 3. Right floating sidebar — Undo / Redo / Eye-dropper ────────────
        QuickActionSidebar(
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            isEyeDropperActive = state.isEyeDropperActive,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onEyeDropper = viewModel::toggleEyeDropper,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )

        // ── 4. Top toolbar (auto-hiding) ──────────────────────────────────────
        TopToolbar(
            visible = state.isToolbarVisible,
            currentTool = state.currentTool,
            currentColor = state.currentColor,
            onGalleryClick = { /* TODO: navigate to gallery */ },
            onToolSelected = viewModel::selectTool,
            onColorPreviewClick = viewModel::showColorPicker,
            onLayersClick = viewModel::showLayerDrawer,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ── 5. Layer drawer (right-edge sliding panel) ────────────────────────
        LayerDrawer(
            visible = state.isLayerDrawerVisible,
            layers = state.layers,
            activeLayerIndex = state.activeLayerIndex,
            onDismiss = viewModel::dismissLayerDrawer,
            onAddLayer = viewModel::addLayer,
            onDeleteLayer = viewModel::deleteLayer,
            onToggleVisibility = viewModel::toggleLayerVisibility,
            onSelectLayer = viewModel::selectLayer,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // ── 6. HSV colour picker popup (centred) ──────────────────────────────
        ColorPickerDisc(
            visible = state.isColorPickerVisible,
            initialColor = state.currentColor,
            onColorSelected = viewModel::setColor,
            onDismiss = viewModel::dismissColorPicker,
        )
    }
}
