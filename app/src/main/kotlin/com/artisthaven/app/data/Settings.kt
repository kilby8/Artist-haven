package com.artisthaven.app.data

import android.content.Context
import androidx.core.content.edit

/** Small preferences that should survive restarts: colours, brush choices and sizes, options. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("artist_haven", Context.MODE_PRIVATE)

    var recentColors: List<Int>
        get() = ints("recent_colors")
        set(v) = putInts("recent_colors", v)

    var palette: List<Int>
        get() = ints("palette").ifEmpty { DEFAULT_PALETTE }
        set(v) = putInts("palette", v)

    var color: Int
        get() = prefs.getInt("color", 0xFF1F2A44.toInt())
        set(v) = prefs.edit { putInt("color", v) }

    var fingerPainting: Boolean
        get() = prefs.getBoolean("finger_painting", false)
        set(v) = prefs.edit { putBoolean("finger_painting", v) }

    var extraStreamline: Float
        get() = prefs.getFloat("extra_streamline", 0f)
        set(v) = prefs.edit { putFloat("extra_streamline", v) }

    fun brushFor(slot: String, default: String): String = prefs.getString("brush_$slot", default) ?: default
    fun setBrushFor(slot: String, id: String) = prefs.edit { putString("brush_$slot", id) }

    fun size(slot: String, brushId: String, default: Float): Float = prefs.getFloat("size_${slot}_$brushId", default)
    fun setSize(slot: String, brushId: String, v: Float) = prefs.edit { putFloat("size_${slot}_$brushId", v) }

    fun opacity(slot: String, brushId: String, default: Float): Float = prefs.getFloat("opacity_${slot}_$brushId", default)
    fun setOpacity(slot: String, brushId: String, v: Float) = prefs.edit { putFloat("opacity_${slot}_$brushId", v) }

    var seenTips: Boolean
        get() = prefs.getBoolean("seen_tips", false)
        set(v) = prefs.edit { putBoolean("seen_tips", v) }

    private fun ints(key: String): List<Int> =
        prefs.getString(key, "")!!.split(',').mapNotNull { it.toLongOrNull()?.toInt() }

    private fun putInts(key: String, v: List<Int>) = prefs.edit {
        putString(key, v.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })
    }

    companion object {
        val DEFAULT_PALETTE: List<Int> = listOf(
            0xFF1F2A44, 0xFF3D5A80, 0xFF98C1D9, 0xFFE0FBFC, 0xFFEE6C4D,
            0xFFF4A261, 0xFFE9C46A, 0xFF2A9D8F, 0xFF264653, 0xFF8E7DBE,
            0xFFD4A5A5, 0xFF6B4226, 0xFF000000, 0xFF7F7F7F, 0xFFFFFFFF,
        ).map { it.toInt() }
    }
}
