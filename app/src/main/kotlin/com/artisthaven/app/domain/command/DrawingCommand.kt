package com.artisthaven.app.domain.command

/**
 * Command pattern interface for all drawing operations.
 * Enables undo/redo functionality without memory leaks by using
 * lightweight operation descriptors rather than storing full bitmaps.
 */
interface DrawingCommand {
    /** Executes the drawing operation. */
    fun execute()

    /** Reverses the drawing operation. */
    fun undo()

    /** Human-readable description for debugging. */
    val description: String
}
