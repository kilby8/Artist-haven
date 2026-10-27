package com.artisthaven.engine.document

import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.TiledSurface

/** What an edit, undo or redo changed, so the UI can redraw only what it must. */
data class Change(val dirty: IRect?, val layersChanged: Boolean) {
    fun merge(other: Change?): Change =
        if (other == null) this else Change(IRect.unionOf(dirty, other.dirty), layersChanged || other.layersChanged)

    companion object {
        val NONE = Change(null, false)
    }
}

/** A reversible edit. [apply] redoes it; [revert] undoes it. */
interface Command {
    val label: String
    val bytes: Long
    fun apply(doc: Document): Change
    fun revert(doc: Document): Change
}

/** Pixel change on one layer, stored as the tiles before and after the edit. */
class PixelCommand(
    override val label: String,
    private val layerId: String,
    private val indices: IntArray,
    private val before: Array<IntArray?>,
    private val after: Array<IntArray?>,
) : Command {
    override val bytes: Long =
        (before.count { it != null } + after.count { it != null }) * TiledSurface.TILE_BYTES + 64

    private fun put(doc: Document, tiles: Array<IntArray?>): Change {
        val layer = doc.layer(layerId) ?: return Change.NONE
        var dirty: IRect? = null
        for (k in indices.indices) {
            layer.surface.setTile(indices[k], tiles[k]?.copyOf())
            dirty = IRect.unionOf(dirty, layer.surface.tileRect(indices[k]))
        }
        return Change(dirty, false)
    }

    override fun apply(doc: Document) = put(doc, after)
    override fun revert(doc: Document) = put(doc, before)

    companion object {
        /**
         * Builds a command from saved "before" tiles and the layer's current tiles. Returns null if
         * nothing actually changed.
         */
        fun capture(label: String, layer: Layer, before: Map<Int, IntArray?>): PixelCommand? {
            val idx = ArrayList<Int>()
            val b = ArrayList<IntArray?>()
            val a = ArrayList<IntArray?>()
            for ((index, old) in before) {
                val now = layer.surface.tile(index)
                if (now != null && now.all { it == 0 }) layer.surface.setTile(index, null)
                val current = layer.surface.tile(index)
                if (old == null && current == null) continue
                if (old != null && current != null && old.contentEquals(current)) continue
                idx += index
                b += old
                a += current?.copyOf()
            }
            if (idx.isEmpty()) return null
            return PixelCommand(label, layer.id, idx.toIntArray(), b.toTypedArray(), a.toTypedArray())
        }
    }
}

class AddLayerCommand(private val index: Int, private val layer: Layer, private val previousActive: String) : Command {
    override val label = "Add layer"
    override val bytes: Long = layer.surface.nonEmptyTileIndices().size * TiledSurface.TILE_BYTES + 128
    override fun apply(doc: Document): Change {
        doc.layers.add(index.coerceIn(0, doc.layers.size), layer)
        doc.activeLayerId = layer.id
        return Change(layer.surface.contentBounds(), true)
    }

    override fun revert(doc: Document): Change {
        doc.layers.remove(layer)
        doc.activeLayerId = previousActive
        return Change(layer.surface.contentBounds(), true)
    }
}

class DeleteLayerCommand(private val index: Int, private val layer: Layer, private val nextActive: String) : Command {
    override val label = "Delete layer"
    override val bytes: Long = layer.surface.nonEmptyTileIndices().size * TiledSurface.TILE_BYTES + 128
    override fun apply(doc: Document): Change {
        doc.layers.remove(layer)
        doc.activeLayerId = nextActive
        return Change(layer.surface.contentBounds(), true)
    }

    override fun revert(doc: Document): Change {
        doc.layers.add(index.coerceIn(0, doc.layers.size), layer)
        doc.activeLayerId = layer.id
        return Change(layer.surface.contentBounds(), true)
    }
}

class MoveLayerCommand(private val from: Int, private val to: Int) : Command {
    override val label = "Move layer"
    override val bytes = 32L
    private fun move(doc: Document, a: Int, b: Int): Change {
        val layer = doc.layers.removeAt(a)
        doc.layers.add(b, layer)
        return Change(doc.bounds, true)
    }

    override fun apply(doc: Document) = move(doc, from, to)
    override fun revert(doc: Document) = move(doc, to, from)
}

class LayerPropsCommand(
    private val layerId: String,
    private val before: LayerProps,
    private val after: LayerProps,
    override val label: String = "Layer settings",
) : Command {
    override val bytes = 128L
    private fun set(doc: Document, p: LayerProps): Change {
        val layer = doc.layer(layerId) ?: return Change.NONE
        val pixelsMatter = layer.props.copy(name = p.name) != p
        layer.props = p
        return Change(if (pixelsMatter) layer.surface.contentBounds() ?: doc.bounds else null, true)
    }

    override fun apply(doc: Document) = set(doc, after)
    override fun revert(doc: Document) = set(doc, before)
}

class PaperCommand(
    private val beforeColor: Int,
    private val beforeVisible: Boolean,
    private val afterColor: Int,
    private val afterVisible: Boolean,
) : Command {
    override val label = "Background"
    override val bytes = 32L
    override fun apply(doc: Document): Change {
        doc.paperColor = afterColor
        doc.paperVisible = afterVisible
        return Change(doc.bounds, true)
    }

    override fun revert(doc: Document): Change {
        doc.paperColor = beforeColor
        doc.paperVisible = beforeVisible
        return Change(doc.bounds, true)
    }
}

/** Several commands undone and redone as one step. */
class CompoundCommand(override val label: String, private val parts: List<Command>) : Command {
    override val bytes: Long = parts.sumOf { it.bytes }
    override fun apply(doc: Document): Change =
        parts.fold(Change.NONE) { acc, c -> acc.merge(c.apply(doc)) }

    override fun revert(doc: Document): Change =
        parts.asReversed().fold(Change.NONE) { acc, c -> acc.merge(c.revert(doc)) }
}

/**
 * Undo and redo stacks. Old steps are dropped when the history exceeds [maxBytes] or [maxSteps],
 * so long sessions can't run out of memory.
 */
class History(var maxBytes: Long = 192L * 1024 * 1024, var maxSteps: Int = 200) {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()
    private var totalBytes = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size
    val sizeBytes: Long get() = totalBytes

    /** Records a command that has already been applied to the document. */
    fun push(command: Command) {
        undoStack.addLast(command)
        totalBytes += command.bytes
        for (c in redoStack) totalBytes -= c.bytes
        redoStack.clear()
        trim()
    }

    /** Applies [command] and records it. */
    fun execute(doc: Document, command: Command): Change {
        val change = command.apply(doc)
        push(command)
        return change
    }

    fun undo(doc: Document): Change? {
        val c = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(c)
        return c.revert(doc)
    }

    fun redo(doc: Document): Change? {
        val c = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(c)
        return c.apply(doc)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        totalBytes = 0
    }

    private fun trim() {
        while (undoStack.size > 1 && (totalBytes > maxBytes || undoStack.size > maxSteps)) {
            totalBytes -= undoStack.removeFirst().bytes
        }
    }
}

/** A change of selection, so lasso steps can be undone like Procreate's. */
class SelectionCommand(private val before: Selection?, private val after: Selection?) : Command {
    override val label = "Selection"
    override val bytes: Long = (before?.byteSize ?: 0) + (after?.byteSize ?: 0) + 32
    override fun apply(doc: Document): Change {
        doc.selection = after
        return Change(null, true)
    }

    override fun revert(doc: Document): Change {
        doc.selection = before
        return Change(null, true)
    }
}
