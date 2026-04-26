package com.artisthaven.app.domain.command

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandHistoryTest {

    private lateinit var history: CommandHistory

    @Before
    fun setup() {
        history = CommandHistory(maxSize = 5)
    }

    @Test
    fun `initial state has no undo or redo`() = runTest {
        assertFalse(history.canUndo.first())
        assertFalse(history.canRedo.first())
        assertEquals(0, history.undoCount)
        assertEquals(0, history.redoCount)
    }

    @Test
    fun `executing command enables undo`() = runTest {
        val command = TestCommand("cmd1")
        history.execute(command)

        assertTrue(history.canUndo.first())
        assertFalse(history.canRedo.first())
        assertTrue(command.wasExecuted)
        assertEquals(1, history.undoCount)
    }

    @Test
    fun `undo reverts command and enables redo`() = runTest {
        val command = TestCommand("cmd1")
        history.execute(command)

        val result = history.undo()

        assertTrue(result)
        assertTrue(command.wasUndone)
        assertFalse(history.canUndo.first())
        assertTrue(history.canRedo.first())
        assertEquals(0, history.undoCount)
        assertEquals(1, history.redoCount)
    }

    @Test
    fun `redo re-executes undone command`() = runTest {
        val command = TestCommand("cmd1")
        history.execute(command)
        history.undo()

        val result = history.redo()

        assertTrue(result)
        assertEquals(2, command.executeCount)
        assertTrue(history.canUndo.first())
        assertFalse(history.canRedo.first())
    }

    @Test
    fun `new command clears redo stack`() = runTest {
        val cmd1 = TestCommand("cmd1")
        val cmd2 = TestCommand("cmd2")
        history.execute(cmd1)
        history.undo()
        assertTrue(history.canRedo.first())

        history.execute(cmd2)
        assertFalse(history.canRedo.first())
        assertEquals(0, history.redoCount)
    }

    @Test
    fun `max size evicts oldest command`() = runTest {
        val commands = (1..6).map { TestCommand("cmd$it") }
        commands.forEach { history.execute(it) }

        assertEquals(5, history.undoCount)
    }

    @Test
    fun `undo on empty history returns false`() = runTest {
        val result = history.undo()
        assertFalse(result)
    }

    @Test
    fun `redo on empty redo stack returns false`() = runTest {
        val result = history.redo()
        assertFalse(result)
    }

    @Test
    fun `clear resets all state`() = runTest {
        val command = TestCommand("cmd1")
        history.execute(command)
        history.clear()

        assertFalse(history.canUndo.first())
        assertFalse(history.canRedo.first())
        assertEquals(0, history.undoCount)
        assertEquals(0, history.redoCount)
    }

    @Test
    fun `multiple undo redo cycles work correctly`() = runTest {
        val cmd1 = TestCommand("cmd1")
        val cmd2 = TestCommand("cmd2")
        history.execute(cmd1)
        history.execute(cmd2)

        history.undo()
        history.undo()

        assertFalse(history.canUndo.first())
        assertEquals(2, history.redoCount)

        history.redo()
        history.redo()

        assertTrue(history.canUndo.first())
        assertFalse(history.canRedo.first())
    }
}

private class TestCommand(override val description: String) : DrawingCommand {
    var wasExecuted = false
    var wasUndone = false
    var executeCount = 0

    override fun execute() {
        wasExecuted = true
        executeCount++
    }

    override fun undo() {
        wasUndone = true
    }
}
