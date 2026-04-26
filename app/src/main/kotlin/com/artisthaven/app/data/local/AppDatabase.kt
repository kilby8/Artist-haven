package com.artisthaven.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.artisthaven.app.data.local.dao.LayerDao
import com.artisthaven.app.data.local.dao.ProjectDao
import com.artisthaven.app.data.local.entity.LayerEntity
import com.artisthaven.app.data.local.entity.ProjectEntity

@Database(
    entities = [ProjectEntity::class, LayerEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun layerDao(): LayerDao
}
