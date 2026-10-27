package com.artisthaven.app

import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.artisthaven.app.data.ProjectStore
import com.artisthaven.app.ui.theme.ArtistHavenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app: new canvas, paint with touch, switch tools and brushes, open every panel,
 * undo, add a layer, go back to the gallery and check the artwork was saved. Screenshots of each
 * step go to app/build/test-output.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun settle(ms: Long = 400) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            // Advance the main looper's clock too, so Choreographer frames (and invalidates) run.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            rule.mainClock.advanceTimeBy(16)
            Thread.sleep(10)
        }
        rule.waitForIdle()
    }

    /** Draws the activity window in software; captureToImage waits for frames Robolectric never sends. */
    private fun shot(name: String): Bitmap {
        settle(150)
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        bmp.saveScreenshot(name)
        return bmp
    }

    /** Pixels in the middle band of the screen that are clearly not white paper or dark chrome. */
    private fun paintedPixels(bmp: Bitmap): Int {
        var n = 0
        for (y in bmp.height / 4 until bmp.height * 3 / 4 step 2) {
            for (x in bmp.width / 5 until bmp.width * 4 / 5 step 2) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r + g + b in 60..690 && maxOf(r, g, b) > 45) n++
            }
        }
        return n
    }

    private fun stroke(points: List<Offset>) {
        rule.onNodeWithTag("canvas").performTouchInput {
            down(points.first())
            points.drop(1).forEach { moveTo(it, delayMillis = 12) }
            up()
        }
        settle()
    }

    private fun wave(y: Float, w: Float, amp: Float) = (0..40).map { i ->
        Offset(w * (0.12f + 0.76f * i / 40f), y + amp * kotlin.math.sin(i / 40f * 6.28f))
    }

    @Test
    fun drawSaveAndReturn() {
        val store = ProjectStore(ApplicationProvider.getApplicationContext())
        rule.setContent { ArtistHavenTheme { App() } }
        settle()
        shot("01-gallery-empty")

        rule.onNodeWithTag("new_canvas").performClick()
        settle()
        rule.onNodeWithTag("preset_Square").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("canvas")).fetchSemanticsNodes().isNotEmpty() }
        settle(800)
        shot("02-new-canvas")

        val size = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        val w = size.width.toFloat()
        val h = size.height.toFloat()

        // Ink stroke with the default Studio Pen.
        stroke(wave(h * 0.35f, w, h * 0.06f))
        val afterInk = paintedPixels(shot("02b-first-stroke"))
        assertTrue("the first stroke shows on the canvas ($afterInk px)", afterInk > 100)

        // Pick a watercolour brush from the library (tap the active brush tool to open it).
        rule.onNodeWithTag("tool_paint").performClick()
        settle()
        rule.onNodeWithTag("panel_brushes").assertExists()
        rule.onNodeWithTag("category_watercolor").performClick()
        settle(1500)
        shot("03-brush-library")
        rule.onNodeWithTag("brush_watercolor_round").performClick()
        settle()

        // Colours panel.
        rule.onNodeWithTag("tool_colors").performClick()
        settle()
        shot("04-colours")
        rule.onNodeWithTag("palette_add").assertExists()
        rule.onNodeWithTag("tool_colors").performClick()
        settle()

        stroke(wave(h * 0.5f, w, h * 0.05f))

        // Smudge across both strokes.
        rule.onNodeWithTag("tool_smudge").performClick()
        settle()
        stroke((0..30).map { i -> Offset(w * 0.5f, h * (0.25f + 0.35f * i / 30f)) })
        shot("05-painted")

        // Undo the smudge with the button, then redo.
        rule.onNodeWithTag("tool_undo").performClick()
        settle()
        rule.onNodeWithTag("tool_redo").performClick()
        settle()

        // Layers: add one, paint on it, open its options.
        rule.onNodeWithTag("tool_layers").performClick()
        settle()
        rule.onNodeWithTag("layer_add").performClick()
        settle()
        shot("06-layers")
        rule.onNodeWithTag("tool_layers").performClick()
        settle()
        rule.onNodeWithTag("tool_paint").performClick()
        settle()
        stroke(wave(h * 0.65f, w, h * 0.04f))

        // Actions panel.
        rule.onNodeWithTag("tool_actions").performClick()
        settle()
        rule.onNodeWithText("Save and share").assertExists()
        shot("07-actions")
        rule.onNodeWithTag("symmetry_vertical").performClick()
        rule.onNodeWithTag("tool_actions").performClick()
        settle()
        stroke((0..20).map { i -> Offset(w * (0.2f + 0.2f * i / 20f), h * (0.8f - 0.1f * i / 20f)) })
        shot("08-symmetry")

        // Move and transform the layer: drag it, turn it, flip it, then Done.
        rule.onNodeWithTag("tool_transform").performClick()
        settle()
        rule.onNodeWithTag("transform_bar").assertExists()
        shot("08b-transform-start")
        stroke((0..10).map { i -> Offset(w * 0.3f + w * 0.15f * i / 10f, h * 0.3f + h * 0.05f * i / 10f) })
        rule.onNodeWithTag("transform_rotate").performClick()
        settle()
        rule.onNodeWithTag("transform_flip_h").performClick()
        settle()
        shot("08c-transform-moved")
        rule.onNodeWithTag("transform_done").performClick()
        settle()
        assertTrue(rule.onAllNodes(hasTestTag("transform_bar")).fetchSemanticsNodes().isEmpty())
        shot("08d-transform-done")

        // Back to the gallery: the artwork must be saved with both layers.
        rule.onNodeWithTag("tool_gallery").performClick()
        rule.waitUntil(15_000) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.onAllNodes(hasTestTag("gallery_grid")).fetchSemanticsNodes().isNotEmpty()
        }
        settle(1500)
        shot("09-gallery")

        val saved = store.list()
        assertEquals(1, saved.size)
        assertEquals(2, saved.first().layerCount)
        val (doc, _) = store.load(saved.first().id)
        val painted = doc.layers.sumOf { l -> l.surface.nonEmptyTileIndices().size }
        assertTrue("strokes were saved ($painted tiles)", painted > 10)
    }
}
