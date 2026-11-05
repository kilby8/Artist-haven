package com.artisthaven.engine.io

internal actual fun inflateInto(bytes: ByteArray, offset: Int, length: Int, outs: List<ByteArray>) {
    val inflater = java.util.zip.Inflater()
    try {
        inflater.setInput(bytes, offset, length)
        for (p in outs) inflater.inflate(p)
    } finally {
        inflater.end()
    }
}
