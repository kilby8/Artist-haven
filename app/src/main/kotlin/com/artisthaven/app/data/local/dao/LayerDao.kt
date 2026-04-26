package com.artisthaven.app.data.local.dao

import androidx.room.*
import com.artisthaven.app.data.local.entity.LayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LayerDao {
    @Query("SELECT * FROM layers WHERE projectId = :projectId ORDER BY `order` ASC")
    suspend fun getByProject(projectId: String): List<LayerEntity>

    @Query("SELECT * FROM layers WHERE projectId = :projectId ORDER BY `order` ASC")
    fun observeByProject(projectId: String): Flow<List<LayerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(layer: LayerEntity)

    @Update
    suspend fun update(layer: LayerEntity)

    @Query("DELETE FROM layers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM layers WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
}
