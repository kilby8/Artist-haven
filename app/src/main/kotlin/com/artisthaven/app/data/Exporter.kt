package com.artisthaven.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.io.PsdWriter
import java.io.File
import java.io.OutputStream

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    PNG("PNG", "png", "image/png"),
    PNG_TRANSPARENT("PNG (no background)", "png", "image/png"),
    JPEG("JPEG", "jpg", "image/jpeg"),
    PSD("PSD (layers)", "psd", "image/vnd.adobe.photoshop"),
}

/** Saves artwork to the phone's Pictures folder and shares it with other apps. */
object Exporter {
    private const val FOLDER = "Artist Haven"

    /** Encodes the document; call on the engine thread because it reads the document. */
    fun encode(doc: Document, format: ExportFormat, out: OutputStream) {
        when (format) {
            ExportFormat.PSD -> PsdWriter.write(doc, out)
            else -> {
                val px = Compositor.flatten(doc, withPaper = format != ExportFormat.PNG_TRANSPARENT)
                val bmp = Bitmap.createBitmap(px, doc.width, doc.height, Bitmap.Config.ARGB_8888)
                val (fmt, quality) = if (format == ExportFormat.JPEG) Bitmap.CompressFormat.JPEG to 95 else Bitmap.CompressFormat.PNG to 100
                bmp.compress(fmt, quality, out)
                bmp.recycle()
            }
        }
    }

    /** Writes the file into a temporary share folder and returns it. */
    fun encodeToCache(context: Context, doc: Document, name: String, format: ExportFormat): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "${safeName(name)}.${format.extension}")
        f.outputStream().buffered(1 shl 16).use { encode(doc, format, it) }
        return f
    }

    /** Copies an encoded file into Pictures/Artist Haven; returns its content Uri. */
    fun saveToGallery(context: Context, file: File, format: ExportFormat): Uri? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (format == ExportFormat.PSD) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val dir = if (format == ExportFormat.PSD) Environment.DIRECTORY_DOWNLOADS else Environment.DIRECTORY_PICTURES
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }
        @Suppress("DEPRECATION")
        val base = Environment.getExternalStoragePublicDirectory(
            if (format == ExportFormat.PSD) Environment.DIRECTORY_DOWNLOADS else Environment.DIRECTORY_PICTURES,
        )
        val dir = File(base, FOLDER).apply { mkdirs() }
        val target = File(dir, file.name)
        file.copyTo(target, overwrite = true)
        return Uri.fromFile(target)
    }

    fun shareIntent(context: Context, file: File, format: ExportFormat): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Share artwork") }
    }

    fun safeName(name: String): String =
        name.trim().ifEmpty { "Artwork" }.replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(60)
}
