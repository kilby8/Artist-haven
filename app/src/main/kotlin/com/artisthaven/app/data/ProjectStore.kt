package com.artisthaven.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.io.ProjectCodec
import com.artisthaven.engine.io.ProjectManifest
import java.io.File
import java.util.UUID

/** A saved artwork as the gallery shows it. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val modifiedAt: Long,
    val createdAt: Long,
    val layerCount: Int,
    val thumbnail: File,
)

/**
 * Projects live in app storage as `projects/<id>/` folders (see [ProjectCodec]). Deleted projects
 * move to `trash/` and are purged after 30 days, so a mis-tap never loses work for good.
 */
class ProjectStore(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val trash = File(context.filesDir, "trash").apply { mkdirs() }

    fun dir(id: String) = File(root, id)

    fun thumbnailFile(id: String) = File(dir(id), THUMBNAIL)

    fun list(): List<ProjectSummary> =
        (root.listFiles() ?: emptyArray())
            .filter { File(it, ProjectCodec.MANIFEST).exists() }
            .mapNotNull { d -> runCatching { summary(d.name, ProjectCodec.readManifest(d)) }.getOrNull() }
            .sortedByDescending { it.modifiedAt }

    private fun summary(id: String, m: ProjectManifest) = ProjectSummary(
        id = id,
        name = m.name,
        width = m.width,
        height = m.height,
        modifiedAt = m.modifiedAt,
        createdAt = m.createdAt,
        layerCount = m.layers.size,
        thumbnail = thumbnailFile(id),
    )

    fun create(name: String, width: Int, height: Int, paper: Int): String {
        val id = UUID.randomUUID().toString()
        val doc = Document.blank(width, height, paper)
        val now = System.currentTimeMillis()
        save(id, doc, name, now, now, changedLayers = null)
        return id
    }

    fun load(id: String): Pair<Document, ProjectManifest> = ProjectCodec.load(dir(id))

    /** Saves the project and refreshes its gallery thumbnail. Call on the engine thread. */
    fun save(
        id: String,
        doc: Document,
        name: String,
        createdAt: Long,
        modifiedAt: Long,
        changedLayers: Set<String>?,
    ) {
        val d = dir(id)
        ProjectCodec.save(doc, d, name, createdAt, modifiedAt, changedLayers)
        val (px, w, h) = Compositor.thumbnail(doc, THUMB_SIZE)
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        ProjectCodec.atomicWrite(thumbnailFile(id)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    fun rename(id: String, name: String) {
        val d = dir(id)
        val m = ProjectCodec.readManifest(d)
        val updated = m.copy(name = name, modifiedAt = System.currentTimeMillis())
        ProjectCodec.atomicWrite(File(d, ProjectCodec.MANIFEST)) {
            it.write(ProjectCodec.json.encodeToString(ProjectManifest.serializer(), updated).toByteArray())
        }
    }

    fun duplicate(id: String): String {
        val newId = UUID.randomUUID().toString()
        dir(id).copyRecursively(dir(newId))
        val m = ProjectCodec.readManifest(dir(newId))
        val now = System.currentTimeMillis()
        val updated = m.copy(name = m.name + " copy", createdAt = now, modifiedAt = now)
        ProjectCodec.atomicWrite(File(dir(newId), ProjectCodec.MANIFEST)) {
            it.write(ProjectCodec.json.encodeToString(ProjectManifest.serializer(), updated).toByteArray())
        }
        return newId
    }

    /** Moves a project to the trash; returns false if it couldn't be moved. */
    fun delete(id: String): Boolean {
        val target = File(trash, "$id-${System.currentTimeMillis()}")
        return dir(id).renameTo(target)
    }

    /** Restores the most recently deleted project with this id. */
    fun undelete(id: String): Boolean {
        val latest = trash.listFiles { f -> f.name.startsWith("$id-") }?.maxByOrNull { it.name } ?: return false
        return latest.renameTo(dir(id))
    }

    fun purgeTrash(olderThanMs: Long = 30L * 24 * 3600 * 1000) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        trash.listFiles()?.forEach { f ->
            val stamp = f.name.substringAfterLast('-').toLongOrNull() ?: 0L
            if (stamp < cutoff) f.deleteRecursively()
        }
    }

    fun loadThumbnail(id: String): Bitmap? =
        thumbnailFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    companion object {
        const val THUMBNAIL = "thumbnail.png"
        const val THUMB_SIZE = 512
    }
}
