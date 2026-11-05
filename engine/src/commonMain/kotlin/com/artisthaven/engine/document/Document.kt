package com.artisthaven.engine.document

import com.artisthaven.engine.raster.IRect
import com.artisthaven.engine.raster.TiledSurface

/**
 * An open canvas: its size, paper colour and layers (index 0 is the bottom layer).
 * Only the engine thread touches a Document.
 */
class Document(
    val width: Int,
    val height: Int,
    var paperColor: Int = 0xFFFFFFFF.toInt(),
    var paperVisible: Boolean = true,
) {
    val layers: MutableList<Layer> = mutableListOf()
    var activeLayerId: String = ""

    /** Layer groups by id; a layer joins one through [LayerProps.group]. */
    val groups: MutableMap<String, GroupProps> = LinkedHashMap()

    fun groupOf(layer: Layer): GroupProps? = if (layer.group.isEmpty()) null else groups[layer.group]

    /** Shown, counting its group: a hidden group hides all its layers. */
    fun effectiveVisible(layer: Layer): Boolean = layer.visible && (groupOf(layer)?.visible ?: true)

    /** The layer's opacity times its group's. */
    fun effectiveOpacity(layer: Layer): Float = layer.opacity * (groupOf(layer)?.opacity ?: 1f)

    fun newGroupId(): String {
        var n = groups.size + 1
        while (groups.containsKey("G$n")) n++
        return "G$n"
    }

    fun nextGroupName(): String {
        var n = groups.size + 1
        while (groups.values.any { it.name == "Group $n" }) n++
        return "Group $n"
    }

    /** Limits painting, fills and transforms to part of the canvas; null means everything. */
    var selection: Selection? = null

    /** When true, painting tools work on the active layer's mask (if it has one). */
    var editingMask: Boolean = false

    /** True if edits to [layer] go to its mask rather than its pixels. */
    fun paintsMask(layer: Layer): Boolean = editingMask && layer.mask != null && layer.id == activeLayer?.id

    /** The surface painting tools change on [layer]: its mask while editing the mask. */
    fun paintSurface(layer: Layer): TiledSurface = if (paintsMask(layer)) layer.mask!! else layer.surface

    private var idCounter = 0

    val bounds: IRect get() = IRect(0, 0, width, height)

    val activeLayer: Layer?
        get() = layers.firstOrNull { it.id == activeLayerId } ?: layers.lastOrNull()

    val activeIndex: Int get() = layers.indexOfFirst { it.id == activeLayer?.id }

    fun indexOf(id: String): Int = layers.indexOfFirst { it.id == id }

    fun layer(id: String): Layer? = layers.firstOrNull { it.id == id }

    fun newLayerId(): String {
        var id: String
        do {
            id = "L" + (++idCounter)
        } while (layers.any { it.id == id })
        return id
    }

    fun nextLayerName(): String {
        var n = layers.size + 1
        while (layers.any { it.name == "Layer $n" }) n++
        return "Layer $n"
    }

    /** A new empty layer; it joins the active layer's group, as new layers go just above it. */
    fun createLayer(name: String = nextLayerName(), group: String = activeLayer?.group ?: ""): Layer =
        Layer(newLayerId(), LayerProps(name = name, group = group), TiledSurface(width, height))

    companion object {
        fun blank(width: Int, height: Int, paperColor: Int = 0xFFFFFFFF.toInt()): Document {
            val doc = Document(width, height, paperColor)
            val layer = doc.createLayer("Layer 1")
            doc.layers.add(layer)
            doc.activeLayerId = layer.id
            return doc
        }
    }
}
