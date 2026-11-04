package com.artisthaven.engine.brush

/** Runs [block] holding [lock] on the JVM, where brush previews render on other threads. */
internal expect inline fun <T> locked(lock: Any, block: () -> T): T
