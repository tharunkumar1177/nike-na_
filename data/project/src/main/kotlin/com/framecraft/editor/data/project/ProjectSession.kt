/*
 * An open project: applies edits, persists each one before reporting it, and owns the undo history.
 */
package com.framecraft.editor.data.project

import com.framecraft.editor.domain.command.CommandOutcome
import com.framecraft.editor.domain.command.CommandRejection
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.EditHistory
import com.framecraft.editor.domain.command.HistoryEntry
import com.framecraft.editor.domain.command.HistoryStep
import com.framecraft.editor.domain.model.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

/** Undo depth guaranteed to survive closing and reopening a project. */
const val RETAINED_UNDO_RECORDS = 100

/** Journal length, in records, after which an edit compacts the journal into the snapshot. */
private const val COMPACT_AFTER_RECORDS = 400

/** Journal size, in bytes, after which an edit compacts the journal into the snapshot. */
private const val COMPACT_AFTER_BYTES = 1_000_000L

/** The open project and what the undo history allows. */
data class SessionState(
    val project: Project,
    val canUndo: Boolean,
    val canRedo: Boolean,
)

/** Result of [ProjectSession.dispatch]. */
sealed interface DispatchResult {
    /** The edit was applied and saved. */
    data class Applied(
        val state: SessionState,
    ) : DispatchResult

    /** The edit was rejected; nothing changed. */
    data class Rejected(
        val reason: CommandRejection,
    ) : DispatchResult
}

/**
 * An open project. Every change is written to disk before it is reported, so a crash loses at most the
 * edit in flight. When a write fails the session keeps its last saved state and the call throws
 * [IOException]. All work runs on the store's single-writer dispatcher.
 */
class ProjectSession internal constructor(
    private val directory: ProjectDirectory,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
    loaded: LoadedProject,
) {
    private val createdAtEpochMillis = loaded.snapshot.createdAtEpochMillis
    private var snapshot = loaded.snapshot
    private var records = loaded.records
    private var history = loaded.history
    private var project = loaded.project
    private var journalInSync = true

    /** The current state. */
    val state: SessionState
        get() = SessionState(project, history.canUndo, history.canRedo)

    /** Applies [command] and saves it, discarding any undone edits. */
    suspend fun dispatch(command: EditCommand): DispatchResult =
        withContext(ioDispatcher) {
            when (val outcome = command.applyTo(project)) {
                is CommandOutcome.Rejected -> DispatchResult.Rejected(outcome.reason)
                is CommandOutcome.Applied -> {
                    persist(command, outcome)
                    DispatchResult.Applied(state)
                }
            }
        }

    /** Undoes the last applied edit; null when there is nothing to undo. */
    suspend fun undo(): SessionState? = withContext(ioDispatcher) { history.undo(project)?.let(::moveTo) }

    /** Redoes the last undone edit; null when there is nothing to redo. */
    suspend fun redo(): SessionState? = withContext(ioDispatcher) { history.redo(project)?.let(::moveTo) }

    /** Compacts the journal when it allows and refreshes the list summary; the session stays usable. */
    suspend fun close() {
        withContext(ioDispatcher) {
            compactQuietly()
            writeMeta()
        }
    }

    /** Writes the summary shown in the project list. */
    internal fun writeMeta() {
        directory.writeMeta(ProjectSummary.of(project, createdAtEpochMillis, clock()))
    }

    /** Journals [command], advances the cursor, then publishes [outcome] in memory. */
    private fun persist(
        command: EditCommand,
        outcome: CommandOutcome.Applied,
    ) {
        val kept = records.take(history.cursor)
        val record = JournalRecord(snapshot.baseSeq + kept.size + 1, clock(), command)
        val canAppend = journalInSync && kept.size == records.size
        journalInSync = false
        if (canAppend) directory.appendRecord(record) else directory.writeJournal(kept + record)
        directory.writeCursor(record.seq)
        journalInSync = true
        records = kept + record
        history = history.pushed(HistoryEntry(command, outcome.inverse))
        project = outcome.project
        writeMeta()
        if (records.size > COMPACT_AFTER_RECORDS || directory.journalFile.length() > COMPACT_AFTER_BYTES) {
            compactQuietly()
        }
    }

    /** Saves the cursor of [step], then publishes it in memory. */
    private fun moveTo(step: HistoryStep): SessionState {
        directory.writeCursor(snapshot.baseSeq + step.history.cursor)
        history = step.history
        project = step.project
        writeMeta()
        return state
    }

    /** Compacts; a failure leaves the previous snapshot and journal, which stay consistent. */
    private fun compactQuietly() {
        try {
            compact()
        } catch (ignored: IOException) {
        }
    }

    /**
     * Folds records more than [RETAINED_UNDO_RECORDS] before the cursor into a new snapshot. The snapshot
     * is replaced before the journal, and loading skips records at or before its base, so a crash between
     * the two writes is harmless.
     */
    private fun compact() {
        val folded = (history.cursor - RETAINED_UNDO_RECORDS).coerceAtLeast(0)
        val base = fold(snapshot.project, records.take(folded))
        if (folded == 0 || base == null) return
        val next = SnapshotDocument(snapshot.baseSeq + folded, createdAtEpochMillis, base)
        directory.writeSnapshot(next)
        directory.writeJournal(records.drop(folded))
        snapshot = next
        records = records.drop(folded)
        history = EditHistory.restored(history.entries.drop(folded), history.cursor - folded)
    }

    /** Applies [toApply] to [start]; null if any command is rejected. */
    private fun fold(
        start: Project,
        toApply: List<JournalRecord>,
    ): Project? {
        var current: Project? = start
        for (record in toApply) {
            current = current?.let { (record.command.applyTo(it) as? CommandOutcome.Applied)?.project }
        }
        return current
    }
}
