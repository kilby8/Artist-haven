package com.artisthaven.app.data

import android.content.Context
import com.artisthaven.engine.brush.BrushCodec
import com.artisthaven.engine.brush.BrushPreset
import java.io.File

/** The brushes made in the Brush Studio, kept in one JSON file in app storage. */
class BrushStore(context: Context) {
    private val file = File(context.filesDir, "brushes.json")

    fun load(): List<BrushPreset> =
        if (file.exists()) BrushCodec.decode(file.readText()) else emptyList()

    fun save(brushes: List<BrushPreset>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(BrushCodec.encode(brushes))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
