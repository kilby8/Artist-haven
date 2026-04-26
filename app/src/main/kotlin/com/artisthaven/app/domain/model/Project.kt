package com.artisthaven.app.domain.model

/**
 * Represents a drawing project with metadata.
 * Project metadata is stored in Room DB; layer bitmaps in internal storage.
 */
data class Project(
    val id: String,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val createdAt: Long,
    val modifiedAt: Long,
    val layers: List<Layer> = emptyList(),
    val thumbnailPath: String? = null,
)
