package com.artisthaven.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/** Reads files the user picks with the system file picker. */
object FileImport {
    /** The file's name as the picker shows it, or [fallback]. */
    fun displayName(context: Context, uri: Uri, fallback: String): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: fallback

    /** Reads the whole file, refusing ones bigger than [maxBytes]. */
    fun readBytes(context: Context, uri: Uri, maxBytes: Long): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
        input.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                require(total <= maxBytes) { "The file is too big (over ${maxBytes / (1024 * 1024)} MB)" }
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
