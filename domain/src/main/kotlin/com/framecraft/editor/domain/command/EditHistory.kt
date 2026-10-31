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

/** Result of [EditHistory.undo] and [EditHistory.redo]. */
sealed interface HistoryOutcome {
    /** The entry was undone or redone, giving [step]. */
    data class Moved(
        val step: HistoryStep,
    ) : HistoryOutcome

    /** There was no entry to undo or redo. */
    data object Empty : HistoryOutcome

    /** The entry's command or inverse was rejected for [reason]; history and project are unchanged. */
    data class Rejected(
        val reason: CommandRejection,
    ) : HistoryOutcome
}

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

    /** Undoes the entry before the cursor on [project]. */
    fun undo(project: Project): HistoryOutcome = step(entries.getOrNull(cursor - 1)?.inverse, project, cursor - 1)

    /** Redoes the entry at the cursor on [project]. */
    fun redo(project: Project): HistoryOutcome = step(entries.getOrNull(cursor)?.command, project, cursor + 1)

    /** Applies [command] to [project] and moves the cursor to [nextCursor]; [HistoryOutcome.Empty] when null. */
    private fun step(
        command: EditCommand?,
        project: Project,
        nextCursor: Int,
    ): HistoryOutcome =
        when (val outcome = command?.applyTo(project)) {
            null -> {
                HistoryOutcome.Empty
            }

            is CommandOutcome.Rejected -> {
                HistoryOutcome.Rejected(outcome.reason)
            }

            is CommandOutcome.Applied -> {
                HistoryOutcome.Moved(HistoryStep(EditHistory(entries, nextCursor), outcome.project))
            }
        }

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
