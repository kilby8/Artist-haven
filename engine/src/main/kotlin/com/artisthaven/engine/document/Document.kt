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

    /** Limits painting, fills and transforms to part of the canvas; null means everything. */
    var selection: Selection? = null

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

    fun createLayer(name: String = nextLayerName()): Layer =
        Layer(newLayerId(), LayerProps(name = name), TiledSurface(width, height))

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
