package com.artisthaven.engine.io

internal actual fun inflateInto(bytes: ByteArray, offset: Int, length: Int, outs: List<ByteArray>) {
    throw FormatException("this file uses ZIP-compressed layers, which the browser version can't open yet; save it from Photoshop with RLE (the default) and try again")
}
