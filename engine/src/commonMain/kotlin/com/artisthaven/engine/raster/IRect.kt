package com.artisthaven.engine.raster

/** Integer rectangle, right and bottom exclusive. */
data class IRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun union(other: IRect?): IRect {
        if (other == null || other.isEmpty) return this
        if (isEmpty) return other
        return IRect(
            minOf(left, other.left),
            minOf(top, other.top),
            maxOf(right, other.right),
            maxOf(bottom, other.bottom),
        )
    }

    fun intersect(other: IRect): IRect = IRect(
        maxOf(left, other.left),
        maxOf(top, other.top),
        minOf(right, other.right),
        minOf(bottom, other.bottom),
    )

    fun inset(d: Int): IRect = IRect(left - d, top - d, right + d, bottom + d)

    companion object {
        val EMPTY = IRect(0, 0, 0, 0)

        fun of(x0: Float, y0: Float, x1: Float, y1: Float): IRect = IRect(
            kotlin.math.floor(minOf(x0, x1)).toInt(),
            kotlin.math.floor(minOf(y0, y1)).toInt(),
            kotlin.math.ceil(maxOf(x0, x1)).toInt() + 1,
            kotlin.math.ceil(maxOf(y0, y1)).toInt() + 1,
        )

        fun unionOf(a: IRect?, b: IRect?): IRect? = when {
            a == null || a.isEmpty -> b?.takeUnless { it.isEmpty }
            b == null || b.isEmpty -> a
            else -> a.union(b)
        }
    }
}
