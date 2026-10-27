package com.artisthaven.app.canvas

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.artisthaven.app.data.ProjectStore
import com.artisthaven.engine.Editor
import com.artisthaven.engine.document.Change
import com.artisthaven.engine.document.Compositor
import com.artisthaven.engine.document.Document
import com.artisthaven.engine.document.History
import com.artisthaven.engine.document.LayerProps
import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.stroke.InputPoint
import com.artisthaven.engine.stroke.StrokeParams
import com.artisthaven.engine.tools.Affine
import com.artisthaven.engine.tools.Resample
import java.util.concurrent.atomic.AtomicReference

/** A layer as the layers panel shows it. */
data class LayerInfo(val id: String, val props: LayerProps, val thumbnail: Bitmap?)

/** Snapshot of the document for the UI. */
data class HostState(
    val layers: List<LayerInfo> = emptyList(),
    val activeLayerId: String = "",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val paperColor: Int = -1,
    val paperVisible: Boolean = true,
    val saving: Boolean = false,
    val lastSavedAt: Long = 0L,
    /** A move/transform is in progress on the engine side. */
    val transforming: Boolean = false,
)

/**
 * Owns the open document on a dedicated thread. The canvas view sends input here; edits run in
 * order on the engine thread, and the changed area of the picture is composited into [display],
 * which the view copies into its bitmap when it draws. Saving also runs here, a moment after the
 * last edit, so it never races a stroke.
 */
class EngineHost(
    private val store: ProjectStore,
    val projectId: String,
    private val doc: Document,
    var projectName: String,
    private val createdAt: Long,
    historyBytes: Long,
) {
    val width = doc.width
    val height = doc.height

    /** Straight ARGB composite of the whole picture. Guard reads and writes with [displayLock]. */
    val display = IntArray(width * height)
    val displayLock = Any()
    private var pendingDirty: IRect? = null

    private val thread = HandlerThread("ArtistHavenEngine", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val engine = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    private val editor = Editor(doc, History(maxBytes = historyBytes))

    @Volatile var onFrame: (() -> Unit)? = null
    @Volatile var onState: ((HostState) -> Unit)? = null

    private var savedRevision = 0L
    private var changedLayers: MutableSet<String>? = HashSet()
    private var lastSavedAt = 0L
    private val saveToken = Any()
    private val thumbCache = HashMap<String, Pair<Long, Bitmap>>()
    private var closed = false

    init {
        engine.post {
            synchronized(displayLock) {
                Compositor.composite(doc, doc.bounds, display, withPaper = true)
                pendingDirty = doc.bounds
            }
            frame()
            publish()
        }
    }

    // ---- Drawing --------------------------------------------------------------------------------

    fun beginStroke(params: StrokeParams, first: InputPoint) = run { apply(editor.beginStroke(params, first), publish = false) }

    fun strokeTo(points: List<InputPoint>) = run {
        var dirty: IRect? = null
        for (p in points) dirty = IRect.unionOf(dirty, editor.strokeTo(p).dirty)
        apply(Change(dirty, false), publish = false)
    }

    fun endStroke() = run {
        val layer = doc.activeLayerId
        val c = editor.endStroke()
        if (c.dirty != null) markLayer(layer)
        apply(c, publish = true)
    }

    fun cancelStroke() = run { apply(editor.cancelStroke(), publish = false) }

    /** Snaps the stroke in progress to a shape; [done] gets true on the main thread if it did. */
    fun quickShape(done: (Boolean) -> Unit) = run {
        val c = editor.quickShape()
        if (c != null) apply(c, publish = false)
        main.post { done(c != null) }
    }

    fun fill(x: Int, y: Int, color: Int, tolerance: Float, allLayers: Boolean) = run {
        val layer = doc.activeLayerId
        val c = editor.fill(x, y, color, tolerance, allLayers)
        if (c.dirty != null) markLayer(layer)
        apply(c, publish = true)
    }

    fun sample(x: Int, y: Int, result: (Int) -> Unit) = run {
        val color = editor.sampleColor(x, y)
        main.post { result(color) }
    }

    // ---- Move and transform ---------------------------------------------------------------------

    /** Starts transforming the active layer; [done] gets the area of its pixels, or null if empty. */
    fun beginTransform(done: (IRect?) -> Unit) = run {
        val layer = doc.activeLayerId
        val r = editor.beginTransform()
        if (r != null) markLayer(layer)
        apply(Change(null, false), publish = true)
        main.post { done(r) }
    }

    private val pendingTransform = AtomicReference<Pair<Affine, Resample>?>(null)

    /** Live preview while dragging. Only the latest matrix is drawn if the engine falls behind. */
    fun previewTransform(m: Affine, quality: Resample) {
        if (pendingTransform.getAndSet(m to quality) != null) return
        run {
            val (latest, q) = pendingTransform.getAndSet(null) ?: return@run
            apply(editor.previewTransform(latest, q), publish = false)
        }
    }

    fun commitTransform(m: Affine, quality: Resample) = run {
        pendingTransform.set(null)
        markLayer(doc.activeLayerId)
        apply(editor.commitTransform(m, quality), publish = true)
    }

    fun cancelTransform() = run {
        pendingTransform.set(null)
        apply(editor.cancelTransform(), publish = true)
    }

    fun undo() = run { markAll(); apply(editor.undo(), publish = true) }

    fun redo() = run { markAll(); apply(editor.redo(), publish = true) }

    // ---- Layers ---------------------------------------------------------------------------------

    fun addLayer() = run { apply(editor.addLayer(), publish = true) }
    fun duplicateLayer(id: String) = run { markAll(); apply(editor.duplicateLayer(id), publish = true) }
    fun deleteLayer(id: String) = run { apply(editor.deleteLayer(id), publish = true) }
    fun selectLayer(id: String) = run { apply(editor.selectLayer(id), publish = true) }
    fun moveLayer(from: Int, to: Int) = run { apply(editor.moveLayer(from, to), publish = true) }
    fun clearLayer(id: String) = run { markLayer(id); apply(editor.clearLayer(id), publish = true) }
    fun mergeDown(id: String) = run { markAll(); apply(editor.mergeDown(id), publish = true) }
    fun setLayerProps(id: String, update: (LayerProps) -> LayerProps) =
        run { apply(editor.setLayerProps(id, update), publish = true) }

    /** Live preview while a slider is dragged; call [commitLayerProps] when it is released. */
    fun previewLayerProps(id: String, update: (LayerProps) -> LayerProps) =
        run { apply(editor.previewLayerProps(id, update), publish = false) }

    fun commitLayerProps(id: String, before: LayerProps) = run { apply(editor.commitLayerProps(id, before), publish = true) }

    fun setPaper(color: Int, visible: Boolean) = run { apply(editor.setPaper(color, visible), publish = true) }

    // ---- Saving and export ----------------------------------------------------------------------

    /** Saves now if anything changed since the last save. */
    fun saveNow(done: (() -> Unit)? = null) = run {
        saveIfNeeded()
        done?.let { main.post(it) }
    }

    fun rename(name: String) = run {
        projectName = name
        savedRevision = -1
        scheduleSave()
    }

    /** Runs [block] on the engine thread with the document, then [done] on the main thread. */
    fun <T> withDocument(block: (Document) -> T, done: (T) -> Unit) = run {
        val result = block(doc)
        main.post { done(result) }
    }

    /** Saves, then stops the engine thread. */
    fun close(done: (() -> Unit)? = null) {
        engine.post {
            saveIfNeeded()
            closed = true
            thread.quitSafely()
            done?.let { main.post(it) }
        }
    }

    // ---- Internals ------------------------------------------------------------------------------

    private inline fun run(crossinline block: () -> Unit) {
        engine.post {
            if (closed) return@post
            try {
                block()
            } catch (t: Throwable) {
                Log.e(TAG, "Engine operation failed", t)
            }
        }
    }

    private fun markLayer(id: String) {
        changedLayers?.add(id)
    }

    private fun markAll() {
        changedLayers = null
    }

    private fun apply(op: Change, publish: Boolean) {
        val settled = editor.takeSettledChange()
        if (settled.dirty != null) markAll()
        val change = op.merge(settled)
        val dirty = change.dirty
        if (dirty != null) {
            val r = if (change.layersChanged && dirty == doc.bounds) dirty else dirty.inset(1)
            synchronized(displayLock) {
                Compositor.composite(doc, r, display, withPaper = true)
                pendingDirty = IRect.unionOf(pendingDirty, r.intersect(doc.bounds))
            }
            frame()
        } else if (change.layersChanged) {
            frame()
        }
        if (publish || change.layersChanged) {
            publish()
            if (editor.revision != savedRevision) scheduleSave()
        }
    }

    /** Takes the area of [display] that changed since the last call (on the main thread). */
    fun takeDirty(): IRect? = synchronized(displayLock) { pendingDirty.also { pendingDirty = null } }

    private var framePosted = false

    private fun frame() {
        synchronized(this) {
            if (framePosted) return
            framePosted = true
        }
        main.post {
            synchronized(this) { framePosted = false }
            onFrame?.invoke()
        }
    }

    private fun publish() {
        val layers = doc.layers.asReversed().map { layer ->
            val key = layer.id
            val cached = thumbCache[key]
            val thumb = if (cached != null && cached.first == editor.revision) cached.second else {
                val (px, w, h) = Compositor.layerThumbnail(doc, layer.id, THUMB)
                Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).also { thumbCache[key] = editor.revision to it }
            }
            LayerInfo(layer.id, layer.props, thumb)
        }
        thumbCache.keys.retainAll(doc.layers.map { it.id }.toSet())
        val state = HostState(
            layers = layers,
            activeLayerId = doc.activeLayer?.id ?: "",
            canUndo = editor.history.canUndo,
            canRedo = editor.history.canRedo,
            paperColor = doc.paperColor,
            paperVisible = doc.paperVisible,
            saving = false,
            lastSavedAt = lastSavedAt,
            transforming = editor.isTransforming,
        )
        main.post { onState?.invoke(state) }
    }

    private fun scheduleSave() {
        engine.removeCallbacksAndMessages(saveToken)
        engine.postAtTime({ if (!editor.isStroking && !editor.isTransforming) saveIfNeeded() else scheduleSave() }, saveToken, SystemClock.uptimeMillis() + AUTOSAVE_DELAY_MS)
    }

    private fun saveIfNeeded() {
        if (editor.isStroking) editor.endStroke()
        // Never save a half-finished transform; finish it first (the app is closing or going away).
        if (editor.isTransforming) {
            markAll()
            apply(editor.commitTransform(), publish = true)
        }
        if (editor.revision == savedRevision) return
        try {
            val t0 = SystemClock.uptimeMillis()
            store.save(projectId, doc, projectName, createdAt, System.currentTimeMillis(), changedLayers)
            savedRevision = editor.revision
            changedLayers = HashSet()
            lastSavedAt = System.currentTimeMillis()
            Log.i(TAG, "Saved $projectId in ${SystemClock.uptimeMillis() - t0} ms")
            publish()
        } catch (t: Throwable) {
            Log.e(TAG, "Save failed", t)
        }
    }

    companion object {
        private const val TAG = "EngineHost"
        private const val THUMB = 96
        const val AUTOSAVE_DELAY_MS = 1500L
    }
}
