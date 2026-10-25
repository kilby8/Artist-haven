package com.artisthaven.engine.io

import com.artisthaven.engine.document.BlendMode
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.Layer
import com.artisthaven.engine.document.LayerProps
import com.artisthaven.engine.raster.TiledSurface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

@Serializable
data class LayerManifest(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: String = "NORMAL",
    val alphaLock: Boolean = false,
    val clipping: Boolean = false,
    val file: String,
)

@Serializable
data class ProjectManifest(
    val format: Int = FORMAT_VERSION,
    val name: String,
    val width: Int,
    val height: Int,
    val paperColor: Int = 0xFFFFFFFF.toInt(),
    val paperVisible: Boolean = true,
    val createdAt: Long,
    val modifiedAt: Long,
    val activeLayerId: String = "",
    val layers: List<LayerManifest>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

/**
 * A project is a folder: `manifest.json` plus one compressed file of tiles per layer. Writes go to
 * temporary files first and are renamed into place, so a crash mid-save never corrupts a project.
 */
object ProjectCodec {
    const val MANIFEST = "manifest.json"
    private const val MAGIC = 0x41484C31 // "AHL1"

    val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    fun manifestFor(doc: Document, name: String, createdAt: Long, modifiedAt: Long) = ProjectManifest(
        name = name,
        width = doc.width,
        height = doc.height,
        paperColor = doc.paperColor,
        paperVisible = doc.paperVisible,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        activeLayerId = doc.activeLayerId,
        layers = doc.layers.map {
            LayerManifest(
                id = it.id,
                name = it.name,
                visible = it.visible,
                opacity = it.opacity,
                blendMode = it.blendMode.name,
                alphaLock = it.alphaLock,
                clipping = it.clipping,
                file = "layer_${it.id}.bin",
            )
        },
    )

    /**
     * Saves [doc] into [dir]. Only layers in [changedLayers] are rewritten (null means all), plus
     * any whose file is missing. Layer files no longer used are deleted.
     */
    fun save(
        doc: Document,
        dir: File,
        name: String,
        createdAt: Long,
        modifiedAt: Long,
        changedLayers: Set<String>? = null,
    ): ProjectManifest {
        dir.mkdirs()
        val manifest = manifestFor(doc, name, createdAt, modifiedAt)
        for ((layer, entry) in doc.layers.zip(manifest.layers)) {
            val file = File(dir, entry.file)
            if (changedLayers != null && layer.id !in changedLayers && file.exists()) continue
            atomicWrite(File(dir, entry.file)) { out -> writeSurface(layer.surface, out) }
        }
        writeManifest(dir, manifest)
        val keep = manifest.layers.map { it.file }.toSet()
        dir.listFiles { f -> f.name.startsWith("layer_") && f.name.endsWith(".bin") && f.name !in keep }
            ?.forEach { it.delete() }
        return manifest
    }

    /** Replaces a project's manifest atomically, e.g. after a rename. */
    fun writeManifest(dir: File, manifest: ProjectManifest) {
        atomicWrite(File(dir, MANIFEST)) { out ->
            out.write(json.encodeToString(ProjectManifest.serializer(), manifest).toByteArray())
        }
    }

    fun readManifest(dir: File): ProjectManifest =
        json.decodeFromString(ProjectManifest.serializer(), File(dir, MANIFEST).readText())

    fun load(dir: File): Pair<Document, ProjectManifest> {
        val m = readManifest(dir)
        if (m.format > ProjectManifest.FORMAT_VERSION) throw IOException("Project was saved by a newer version")
        val doc = Document(m.width, m.height, m.paperColor, m.paperVisible)
        for (entry in m.layers) {
            val surface = TiledSurface(m.width, m.height)
            val f = File(dir, entry.file)
            if (f.exists()) f.inputStream().buffered().use { readSurface(surface, it) }
            doc.layers += Layer(
                entry.id,
                LayerProps(
                    name = entry.name,
                    visible = entry.visible,
                    opacity = entry.opacity,
                    blendMode = BlendMode.fromName(entry.blendMode),
                    alphaLock = entry.alphaLock,
                    clipping = entry.clipping,
                ),
                surface,
            )
        }
        if (doc.layers.isEmpty()) doc.layers += doc.createLayer("Layer 1")
        doc.activeLayerId = m.activeLayerId.takeIf { id -> doc.layers.any { it.id == id } } ?: doc.layers.last().id
        return doc to m
    }

    fun writeSurface(surface: TiledSurface, out: OutputStream) {
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            val deflate = DeflaterOutputStream(out, deflater, 1 shl 16)
            val data = DataOutputStream(deflate)
            val tiles = surface.nonEmptyTileIndices()
            data.writeInt(MAGIC)
            data.writeInt(surface.width)
            data.writeInt(surface.height)
            data.writeInt(TiledSurface.TILE)
            data.writeInt(tiles.size)
            val bytes = ByteArray(TiledSurface.TILE * TiledSurface.TILE * 4)
            for (index in tiles) {
                val t = surface.tile(index) ?: continue
                data.writeInt(index)
                var j = 0
                for (c in t) {
                    bytes[j++] = (c ushr 24).toByte()
                    bytes[j++] = (c ushr 16).toByte()
                    bytes[j++] = (c ushr 8).toByte()
                    bytes[j++] = c.toByte()
                }
                data.write(bytes)
            }
            data.flush()
            // Finish the deflate stream without closing the caller's stream.
            deflate.finish()
        } finally {
            deflater.end()
        }
    }

    fun readSurface(surface: TiledSurface, input: InputStream) {
        val data = DataInputStream(InflaterInputStream(input, java.util.zip.Inflater(), 1 shl 16))
        if (data.readInt() != MAGIC) throw IOException("Not a layer file")
        val w = data.readInt()
        val h = data.readInt()
        val tile = data.readInt()
        if (w != surface.width || h != surface.height || tile != TiledSurface.TILE) {
            throw IOException("Layer size mismatch")
        }
        val count = data.readInt()
        val bytes = ByteArray(TiledSurface.TILE * TiledSurface.TILE * 4)
        repeat(count) {
            val index = data.readInt()
            data.readFully(bytes)
            if (index !in 0 until surface.tileCount) throw IOException("Bad tile index")
            val t = surface.tileOrCreate(index)
            var j = 0
            for (i in t.indices) {
                t[i] = ((bytes[j].toInt() and 0xFF) shl 24) or ((bytes[j + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[j + 2].toInt() and 0xFF) shl 8) or (bytes[j + 3].toInt() and 0xFF)
                j += 4
            }
        }
    }

    fun atomicWrite(target: File, block: (OutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().buffered(1 shl 16).use { block(it) }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not save ${target.name}")
        }
    }
}
