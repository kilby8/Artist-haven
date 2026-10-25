package com.artisthaven.engine.document

import com.artisthaven.engine.raster.TiledSurface

/** Properties of a layer that can change without touching its pixels. */
data class LayerProps(
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val alphaLock: Boolean = false,
    val clipping: Boolean = false,
)

class Layer(
    val id: String,
    var props: LayerProps,
    val surface: TiledSurface,
) {
    val name: String get() = props.name
    val visible: Boolean get() = props.visible
    val opacity: Float get() = props.opacity
    val blendMode: BlendMode get() = props.blendMode
    val alphaLock: Boolean get() = props.alphaLock
    val clipping: Boolean get() = props.clipping

    fun duplicate(newId: String, newName: String): Layer =
        Layer(newId, props.copy(name = newName), surface.copy())
}
