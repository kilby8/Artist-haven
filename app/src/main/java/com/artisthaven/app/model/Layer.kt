package com.artisthaven.app.model

import androidx.compose.ui.graphics.ImageBitmap
import java.util.UUID

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Layer",
    val isVisible: Boolean = true,
    val thumbnail: ImageBitmap? = null
)
