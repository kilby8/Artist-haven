package com.artisthaven.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val createdAt: Long,
    val modifiedAt: Long,
    val thumbnailPath: String? = null,
)
