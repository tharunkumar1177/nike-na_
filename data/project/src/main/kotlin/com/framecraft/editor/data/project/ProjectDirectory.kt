/*
 * Layout of one project's directory and the reads and writes of each file in it.
 */
package com.framecraft.editor.data.project

import java.io.File

/**
 * Files of one project:
 * - `snapshot.json`: [SnapshotDocument], replaced atomically.
 * - `journal.jsonl`: one [JournalRecord] per line, appended and synced per edit.
 * - `history.cursor`: [CursorDocument], replaced atomically.
 * - `meta.json`: [ProjectSummary] for the project list, rebuilt from the snapshot when missing.
 * - `export-settings.json`: the last export settings, read and written by [ExportSettingsFile].
 * - `journal.corrupt-<millis>`: copies of journals whose unreadable tail was dropped, kept for diagnosis.
 */
internal class ProjectDirectory(
    val dir: File,
) {
    /** The snapshot file. */
    val snapshotFile = File(dir, "snapshot.json")

    /** The journal file. */
    val journalFile = File(dir, "journal.jsonl")

    private val cursorFile = File(dir, "history.cursor")
    private val metaFile = File(dir, "meta.json")

    /** Whether a project is stored here. */
    fun exists(): Boolean = snapshotFile.isFile

    /** Atomically replaces the snapshot. */
    fun writeSnapshot(document: SnapshotDocument) {
        snapshotFile.writeAtomically(projectJson.encodeToString(SnapshotDocument.serializer(), document))
    }

    /** Atomically replaces the whole journal with [records]. */
    fun writeJournal(records: List<JournalRecord>) {
        journalFile.writeAtomically(records.joinToString(separator = "") { encode(it) + "\n" })
    }

    /** Appends [record] to the journal and syncs it. */
    fun appendRecord(record: JournalRecord) {
        journalFile.appendLineDurably(encode(record))
    }

    /** Atomically records [cursorSeq] as the last applied journal sequence. */
    fun writeCursor(cursorSeq: Long) {
        cursorFile.writeAtomically(projectJson.encodeToString(CursorDocument.serializer(), CursorDocument(cursorSeq)))
    }

    /** Returns the stored cursor, or null when it is missing or unreadable. */
    fun readCursor(): Long? =
        runCatching { projectJson.decodeFromString(CursorDocument.serializer(), cursorFile.readText()).cursorSeq }
            .getOrNull()

    /** Writes the list summary; on failure deletes the stale summary so the list rebuilds it. */
    fun writeMeta(summary: ProjectSummary) {
        runCatching { metaFile.writeAtomically(projectJson.encodeToString(ProjectSummary.serializer(), summary)) }
            .onFailure { metaFile.delete() }
    }

    /** Returns the stored list summary, or null when it is missing or unreadable. */
    fun readMeta(): ProjectSummary? =
        runCatching { projectJson.decodeFromString(ProjectSummary.serializer(), metaFile.readText()) }.getOrNull()

    /** Keeps a copy of the current journal before its unreadable tail is dropped. */
    fun quarantineJournal(nowMillis: Long) {
        if (journalFile.isFile) journalFile.copyTo(File(dir, "journal.corrupt-$nowMillis"), overwrite = true)
    }

    /** Deletes temporary files left by writes interrupted by a crash. */
    fun deleteTemporaryFiles() {
        dir.listFiles { file -> file.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
    }

    /** Encodes [record] as one journal line. */
    private fun encode(record: JournalRecord): String = projectJson.encodeToString(JournalRecord.serializer(), record)
}
