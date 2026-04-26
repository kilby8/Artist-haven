package com.artisthaven.app.domain.command

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages undo/redo history using the Command Pattern.
 *
 * Memory management:
 * - Bounded history stack prevents unbounded memory growth.
 * - When capacity is exceeded, the oldest command is evicted.
 * - Commands store lightweight descriptors, not full bitmap copies.
 */
class CommandHistory(private val maxSize: Int = 50) {

    private val undoStack = ArrayDeque<DrawingCommand>()
    private val redoStack = ArrayDeque<DrawingCommand>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /**
     * Executes a command and adds it to the undo stack.
     * Clears the redo stack to maintain linear history.
     */
    fun execute(command: DrawingCommand) {
        command.execute()
        undoStack.addLast(command)
        if (undoStack.size > maxSize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        updateState()
    }

    /**
     * Undoes the most recent command.
     * @return true if an undo was performed, false if history is empty.
     */
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val command = undoStack.removeLast()
        command.undo()
        redoStack.addLast(command)
        updateState()
        return true
    }

    /**
     * Redoes the most recently undone command.
     * @return true if a redo was performed, false if redo stack is empty.
     */
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val command = redoStack.removeLast()
        command.execute()
        undoStack.addLast(command)
        updateState()
        return true
    }

    /** Clears all history. Call when opening a new project. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    /** Returns the number of commands in the undo stack. */
    val undoCount: Int get() = undoStack.size

    /** Returns the number of commands in the redo stack. */
    val redoCount: Int get() = redoStack.size

    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
