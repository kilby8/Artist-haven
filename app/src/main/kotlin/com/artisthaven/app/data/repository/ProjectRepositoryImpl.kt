package com.artisthaven.app.data.repository

import android.content.Context
import com.artisthaven.app.data.local.dao.LayerDao
import com.artisthaven.app.data.local.dao.ProjectDao
import com.artisthaven.app.data.local.entity.LayerEntity
import com.artisthaven.app.data.local.entity.ProjectEntity
import com.artisthaven.app.domain.model.Layer
import com.artisthaven.app.domain.model.LayerBlendMode
import com.artisthaven.app.domain.model.Project
import com.artisthaven.app.domain.repository.ProjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
    private val layerDao: LayerDao,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> =
        projectDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getProject(id: String): Project? =
        projectDao.getById(id)?.toDomain()

    override suspend fun saveProject(project: Project) {
        projectDao.insert(project.toEntity())
        project.layers.forEach { layer ->
            layerDao.insert(layer.toEntity(project.id))
        }
    }

    override suspend fun deleteProject(id: String) {
        projectDao.deleteById(id)
    }

    override suspend fun getLayers(projectId: String): List<Layer> =
        layerDao.getByProject(projectId).map { it.toDomain() }

    override suspend fun saveLayer(projectId: String, layer: Layer) {
        layerDao.insert(layer.toEntity(projectId))
    }

    override suspend fun deleteLayer(layerId: String) {
        layerDao.deleteById(layerId)
    }

    override suspend fun reorderLayers(projectId: String, orderedLayerIds: List<String>) {
        val layers = layerDao.getByProject(projectId)
        val orderMap = orderedLayerIds.withIndex().associate { (index, id) -> id to index }
        layers.forEach { layer ->
            orderMap[layer.id]?.let { newOrder ->
                layerDao.update(layer.copy(order = newOrder))
            }
        }
    }

    override suspend fun exportAsPng(projectId: String, bitmapByteArray: ByteArray): String {
        val dir = File(context.filesDir, "exports/$projectId")
        dir.mkdirs()
        val file = File(dir, "canvas_export_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { it.write(bitmapByteArray) }
        return file.absolutePath
    }

    private fun ProjectEntity.toDomain(): Project = Project(
        id = id,
        name = name,
        folderName = folderName,
        widthPx = widthPx,
        heightPx = heightPx,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        thumbnailPath = thumbnailPath,
    )

    private fun Project.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        folderName = folderName,
        widthPx = widthPx,
        heightPx = heightPx,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        thumbnailPath = thumbnailPath,
    )

    private fun LayerEntity.toDomain(): Layer = Layer(
        id = id,
        name = name,
        isVisible = isVisible,
        opacity = opacity,
        blendMode = LayerBlendMode.entries.find { it.name == blendMode } ?: LayerBlendMode.NORMAL,
        isLocked = isLocked,
        bitmapPath = bitmapPath,
        order = order,
    )

    private fun Layer.toEntity(projectId: String): LayerEntity = LayerEntity(
        id = id,
        projectId = projectId,
        name = name,
        isVisible = isVisible,
        opacity = opacity,
        blendMode = blendMode.name,
        isLocked = isLocked,
        bitmapPath = bitmapPath,
        order = order,
    )
}
