package com.artisthaven.engine.brush

internal actual inline fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)
