package com.artisthaven.app.domain.repository

import com.artisthaven.app.domain.model.Layer
import com.artisthaven.app.domain.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for project persistence.
 * Follows the Repository pattern for clean separation of concerns.
 */
interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun getProject(id: String): Project?
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(id: String)

    suspend fun getLayers(projectId: String): List<Layer>
    suspend fun saveLayer(projectId: String, layer: Layer)
    suspend fun deleteLayer(layerId: String)
    suspend fun reorderLayers(projectId: String, orderedLayerIds: List<String>)

    /** Exports the merged canvas as a PNG to internal storage and returns the file path. */
    suspend fun exportAsPng(projectId: String, bitmapByteArray: ByteArray): String
}
