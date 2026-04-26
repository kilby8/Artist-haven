package com.artisthaven.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "layers",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("projectId")]
)
data class LayerEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val isVisible: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: String = "NORMAL",
    val isLocked: Boolean = false,
    val bitmapPath: String? = null,
    val order: Int = 0,
)
