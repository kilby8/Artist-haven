package com.artisthaven.app

import androidx.test.core.app.ApplicationProvider
import com.artisthaven.app.data.ProjectStore
import com.artisthaven.engine.Editor
import com.artisthaven.engine.brush.BrushLibrary
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ProjectStoreTest {
    private val store = ProjectStore(ApplicationProvider.getApplicationContext())

    @Test
    fun createPaintSaveReload() {
        val id = store.create("Test", 400, 300, 0xFFFFFFFF.toInt())
        val (doc, m) = store.load(id)
        assertEquals("Test", m.name)
        val editor = Editor(doc)
        editor.beginStroke(StrokeParams(BrushLibrary.default, size = 20f, color = 0xFFFF0000.toInt()), InputPoint(20f, 20f, time = 0))
        for (i in 1..40) editor.strokeTo(InputPoint(20f + i * 8f, 20f + i * 6f, time = i * 8L))
        editor.endStroke()
        store.save(id, doc, "Test", m.createdAt, System.currentTimeMillis(), changedLayers = null)

        val (back, _) = store.load(id)
        val layer = back.activeLayer!!
        assertTrue("stroke survives a reload", layer.surface.getPixel(180, 140) ushr 24 > 200)
        assertNotNull(store.loadThumbnail(id))
        assertEquals(1, store.list().count { it.id == id })
    }

    @Test
    fun renameDuplicateDeleteUndelete() {
        val id = store.create("Original", 200, 200, -1)
        store.rename(id, "Renamed")
        assertEquals("Renamed", store.list().first { it.id == id }.name)
        val copy = store.duplicate(id)
        assertEquals("Renamed copy", store.list().first { it.id == copy }.name)
        assertTrue(store.delete(id))
        assertTrue(store.list().none { it.id == id })
        assertTrue(store.undelete(id))
        assertTrue(store.list().any { it.id == id })
    }
}
