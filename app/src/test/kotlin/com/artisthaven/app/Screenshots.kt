package com.artisthaven.app

import android.graphics.Bitmap
import java.io.File

/** Writes a screenshot to app/build/test-output so CI can upload it for review. */
fun Bitmap.saveScreenshot(name: String): File {
    val dir = File("build/test-output").apply { mkdirs() }
    val f = File(dir, "$name.png")
    f.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
    return f
}
