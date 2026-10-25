package com.artisthaven.engine

import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.stroke.InputPoint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.sin

object TestSupport {
    val outputDir: File = File(System.getProperty("testOutputDir") ?: "build/test-output").also { it.mkdirs() }

    /** An S-curve with a natural pressure swell, sampled like a 120 Hz stylus. */
    fun sCurve(x0: Float, y0: Float, width: Float, height: Float, steps: Int = 90): List<InputPoint> =
        (0..steps).map { i ->
            val t = i / steps.toFloat()
            val pressure = (0.15f + 0.85f * sin(t * PI).toFloat()).coerceIn(0f, 1f)
            InputPoint(
                x = x0 + width * t,
                y = y0 + height / 2f + sin(t * 2 * PI).toFloat() * height / 2.4f,
                pressure = pressure,
                tilt = 0.35f,
                azimuth = 0.8f,
                time = i * 8L,
            )
        }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 40, pressure: Float = 1f): List<InputPoint> =
        (0..steps).map { i ->
            val t = i / steps.toFloat()
            InputPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, pressure, time = i * 8L)
        }

    fun savePng(doc: Document, name: String): File {
        val px = Compositor.flatten(doc)
        val img = BufferedImage(doc.width, doc.height, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, doc.width, doc.height, px, 0, doc.width)
        val f = File(outputDir, name)
        ImageIO.write(img, "png", f)
        return f
    }
}
