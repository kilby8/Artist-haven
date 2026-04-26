package com.artisthaven.app.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.artisthaven.app.model.DrawingState
import com.artisthaven.app.model.DrawingTool
import com.artisthaven.app.model.Layer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel that owns all mutable UI + drawing state for Artist Haven.
 * Undo/redo are managed as separate history stacks of [DrawingState] snapshots.
 */
class ArtistHavenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DrawingState())
    val uiState: StateFlow<DrawingState> = _uiState.asStateFlow()

    // Undo / Redo stacks hold previous DrawingState snapshots
    private val undoStack = ArrayDeque<DrawingState>()
    private val redoStack = ArrayDeque<DrawingState>()
    private val maxHistorySize = 50

    // ── Tool & Brush ────────────────────────────────────────────────────────────

    fun selectTool(tool: DrawingTool) {
        _uiState.update { it.copy(currentTool = tool, isEyeDropperActive = false) }
    }

    fun setBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size.coerceIn(0f, 1f)) }
    }

    fun setOpacity(opacity: Float) {
        _uiState.update { it.copy(opacity = opacity.coerceIn(0f, 1f)) }
    }

    fun setColor(color: Color) {
        _uiState.update { it.copy(currentColor = color, isColorPickerVisible = false) }
    }

    // ── Undo / Redo ─────────────────────────────────────────────────────────────

    /** Call before each destructive stroke is committed. */
    fun pushUndoSnapshot() {
        val current = _uiState.value
        undoStack.addLast(current)
        if (undoStack.size > maxHistorySize) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        redoStack.addLast(_uiState.value)
        _uiState.value = previous.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(_uiState.value)
        _uiState.value = next.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    // ── Eye Dropper ─────────────────────────────────────────────────────────────

    fun toggleEyeDropper() {
        _uiState.update { it.copy(isEyeDropperActive = !it.isEyeDropperActive) }
    }

    fun pickColor(color: Color) {
        _uiState.update {
            it.copy(
                currentColor = color,
                isEyeDropperActive = false
            )
        }
    }

    // ── Toolbar visibility ───────────────────────────────────────────────────────

    fun toggleToolbar() {
        _uiState.update { it.copy(isToolbarVisible = !it.isToolbarVisible) }
    }

    fun showToolbar() {
        _uiState.update { it.copy(isToolbarVisible = true) }
    }

    // ── Color Picker ────────────────────────────────────────────────────────────

    fun showColorPicker() {
        _uiState.update { it.copy(isColorPickerVisible = true) }
    }

    fun dismissColorPicker() {
        _uiState.update { it.copy(isColorPickerVisible = false) }
    }

    // ── Layer Drawer ────────────────────────────────────────────────────────────

    fun showLayerDrawer() {
        _uiState.update { it.copy(isLayerDrawerVisible = true) }
    }

    fun dismissLayerDrawer() {
        _uiState.update { it.copy(isLayerDrawerVisible = false) }
    }

    fun addLayer() {
        _uiState.update { state ->
            val newLayer = Layer(name = "Layer ${state.layers.size + 1}")
            val newLayers = state.layers + newLayer
            state.copy(
                layers = newLayers,
                activeLayerIndex = newLayers.lastIndex
            )
        }
    }

    fun deleteLayer(index: Int) {
        _uiState.update { state ->
            if (state.layers.size <= 1) return@update state // Always keep at least one layer
            val newLayers = state.layers.toMutableList().also { it.removeAt(index) }
            val newActiveIndex = when {
                state.activeLayerIndex >= newLayers.size -> newLayers.lastIndex
                else -> state.activeLayerIndex
            }
            state.copy(layers = newLayers, activeLayerIndex = newActiveIndex)
        }
    }

    fun toggleLayerVisibility(index: Int) {
        _uiState.update { state ->
            val updated = state.layers.mapIndexed { i, layer ->
                if (i == index) layer.copy(isVisible = !layer.isVisible) else layer
            }
            state.copy(layers = updated)
        }
    }

    fun selectLayer(index: Int) {
        _uiState.update { it.copy(activeLayerIndex = index) }
    }

    fun reorderLayer(from: Int, to: Int) {
        _uiState.update { state ->
            if (from == to || from !in state.layers.indices || to !in state.layers.indices) {
                return@update state
            }
            val reordered = state.layers.toMutableList()
            val moved = reordered.removeAt(from)
            reordered.add(to, moved)
            val newActive = when (state.activeLayerIndex) {
                from -> to
                in (minOf(from, to)..maxOf(from, to)) ->
                    if (from < to) state.activeLayerIndex - 1 else state.activeLayerIndex + 1
                else -> state.activeLayerIndex
            }
            state.copy(layers = reordered, activeLayerIndex = newActive)
        }
    }
}
