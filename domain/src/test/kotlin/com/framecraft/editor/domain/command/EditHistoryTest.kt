/*
 * Unit tests for the linear undo history.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Verifies undo, redo, branch truncation, and restoration. */
class EditHistoryTest {
    private val start =
        Project(
            id = "project",
            name = "A",
            frameRate = FrameRate.FPS_30,
            assets = emptyList(),
            tracks = listOf(Track("track", TrackKind.VIDEO, magnetic = true, clips = emptyList())),
        )

    /** Applies [command] to [project] and records it, returning both. */
    private fun record(
        history: EditHistory,
        project: Project,
        command: EditCommand,
    ): HistoryStep {
        val outcome = command.applyTo(project) as CommandOutcome.Applied
        return HistoryStep(history.pushed(HistoryEntry(command, outcome.inverse)), outcome.project)
    }

    /** Undo and redo walk the history and stop at its ends. */
    @Test
    fun undoAndRedoWalkTheHistory() {
        val first = record(EditHistory.empty(), start, RenameProject("B"))
        val second = record(first.history, first.project, RenameProject("C"))
        val undone = requireNotNull(second.history.undo(second.project))
        assertEquals("B", undone.project.name)
        val undoneTwice = requireNotNull(undone.history.undo(undone.project))
        assertEquals(start, undoneTwice.project)
        assertFalse(undoneTwice.history.canUndo)
        assertNull(undoneTwice.history.undo(undoneTwice.project))
        val redone = requireNotNull(undoneTwice.history.redo(undoneTwice.project))
        assertEquals("B", redone.project.name)
        assertTrue(redone.history.canRedo)
    }

    /** Recording after an undo discards the redo entries. */
    @Test
    fun pushAfterUndoTruncatesRedo() {
        val first = record(EditHistory.empty(), start, RenameProject("B"))
        val second = record(first.history, first.project, RenameProject("C"))
        val undone = requireNotNull(second.history.undo(second.project))
        val branched = record(undone.history, undone.project, RenameProject("D"))
        assertEquals(2, branched.history.entries.size)
        assertFalse(branched.history.canRedo)
        assertNull(branched.history.redo(branched.project))
    }

    /** Restored histories validate their cursor. */
    @Test
    fun restoredValidatesCursor() {
        val entry = HistoryEntry(RenameProject("B"), RenameProject("A"))
        assertTrue(EditHistory.restored(listOf(entry), 1).canUndo)
        assertThrows<IllegalArgumentException> { EditHistory.restored(listOf(entry), 2) }
    }
}
