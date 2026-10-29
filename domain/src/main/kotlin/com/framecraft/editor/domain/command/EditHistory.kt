/*
 * Immutable linear undo history of applied commands and their inverses.
 */
package com.framecraft.editor.domain.command

import com.framecraft.editor.domain.model.Project

/** An applied [command] and the [inverse] that undoes it. */
data class HistoryEntry(
    val command: EditCommand,
    val inverse: EditCommand,
)

/** The project and history after an undo or redo. */
data class HistoryStep(
    val history: EditHistory,
    val project: Project,
)

/**
 * Linear history: entries before [cursor] are applied and can be undone; entries at or after it
 * were undone and can be redone. Pushing a new entry discards the redo entries.
 */
class EditHistory private constructor(
    val entries: List<HistoryEntry>,
    val cursor: Int,
) {
    /** Whether an applied entry can be undone. */
    val canUndo: Boolean
        get() = cursor > 0

    /** Whether an undone entry can be redone. */
    val canRedo: Boolean
        get() = cursor < entries.size

    /** Returns the history with [entry] applied after the cursor, discarding redo entries. */
    fun pushed(entry: HistoryEntry): EditHistory = EditHistory(entries.take(cursor) + entry, cursor + 1)

    /** Undoes the entry before the cursor on [project]; null when there is none or its inverse is rejected. */
    fun undo(project: Project): HistoryStep? =
        entries
            .getOrNull(cursor - 1)
            ?.let { it.inverse.applyTo(project) as? CommandOutcome.Applied }
            ?.let { HistoryStep(EditHistory(entries, cursor - 1), it.project) }

    /** Redoes the entry at the cursor on [project]; null when there is none or its command is rejected. */
    fun redo(project: Project): HistoryStep? =
        entries
            .getOrNull(cursor)
            ?.let { it.command.applyTo(project) as? CommandOutcome.Applied }
            ?.let { HistoryStep(EditHistory(entries, cursor + 1), it.project) }

    /** Factories. */
    companion object {
        /** Returns a history with no entries. */
        fun empty(): EditHistory = EditHistory(emptyList(), 0)

        /** Returns a history of [entries] with [cursor] applied entries; [cursor] must lie within the entries. */
        fun restored(
            entries: List<HistoryEntry>,
            cursor: Int,
        ): EditHistory {
            require(cursor in 0..entries.size) { "History cursor $cursor outside 0..${entries.size}" }
            return EditHistory(entries, cursor)
        }
    }
}
