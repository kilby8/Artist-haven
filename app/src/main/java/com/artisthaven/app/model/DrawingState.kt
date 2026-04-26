package com.artisthaven.app.model

import androidx.compose.ui.graphics.Color

data class DrawingState(
    val currentTool: DrawingTool = DrawingTool.BRUSH,
    val brushSize: Float = 0.3f,
    val opacity: Float = 1.0f,
    val currentColor: Color = Color(0xFF2196F3),
    val layers: List<Layer> = listOf(Layer(name = "Layer 1")),
    val activeLayerIndex: Int = 0,
    val isToolbarVisible: Boolean = true,
    val isColorPickerVisible: Boolean = false,
    val isLayerDrawerVisible: Boolean = false,
    val isEyeDropperActive: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)
