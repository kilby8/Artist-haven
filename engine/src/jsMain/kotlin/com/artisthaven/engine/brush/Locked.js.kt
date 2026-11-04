package com.artisthaven.engine.brush

// JavaScript is single-threaded; the web app runs the engine in one worker.
internal actual inline fun <T> locked(lock: Any, block: () -> T): T = block()
