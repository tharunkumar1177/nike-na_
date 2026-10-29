/*
 * Opens a stored project: upgrades the snapshot, replays the journal, drops a corrupt tail, and restores undo.
 */
package com.framecraft.editor.data.project

import com.framecraft.editor.domain.command.CommandOutcome
import com.framecraft.editor.domain.command.EditHistory
import com.framecraft.editor.domain.command.HistoryEntry
import com.framecraft.editor.domain.model.CURRENT_SCHEMA_VERSION
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** A project restored from disk, positioned at its undo cursor. */
internal class LoadedProject(
    val snapshot: SnapshotDocument,
    val records: List<JournalRecord>,
    val history: EditHistory,
    val project: Project,
    val droppedRecords: Int,
)

/** Outcome of loading a project directory. */
internal sealed interface LoadResult {
    /** The project was restored. */
    class Loaded(
        val project: LoadedProject,
    ) : LoadResult

    /** The project was written by a newer schema [version]. */
    class TooNew(
        val version: Int,
    ) : LoadResult

    /** The snapshot could not be read; [detail] describes why. */
    class Corrupt(
        val detail: String,
    ) : LoadResult
}

/** Result of reading a snapshot file that parsed. */
private sealed interface SnapshotRead {
    /** A decoded snapshot, upgraded from the schema [fromVersion] it was written with. */
    class Current(
        val document: SnapshotDocument,
        val fromVersion: Int,
    ) : SnapshotRead

    /** A snapshot written by a newer schema [version] that this build cannot read. */
    class Newer(
        val version: Int,
    ) : SnapshotRead
}

/** Journal records that continue the snapshot, how many trailing lines were unusable, and whether to rewrite. */
private class JournalRead(
    val records: List<JournalRecord>,
    val droppedLines: Int,
    val needsRewrite: Boolean,
)

/** Records that replayed cleanly, with the history entries and the project after each one. */
private class Replay(
    val records: List<JournalRecord>,
    val entries: List<HistoryEntry>,
    val projects: List<Project>,
)

/** Loads this directory's project; repairs a corrupt journal tail and upgrades older schemas in place. */
internal fun ProjectDirectory.load(
    migrations: SchemaMigrations,
    nowMillis: Long,
): LoadResult =
    runCatching { readSnapshot(snapshotFile, migrations) }.fold(
        onSuccess = { read ->
            when (read) {
                is SnapshotRead.Current -> LoadResult.Loaded(restore(read, migrations, nowMillis))
                is SnapshotRead.Newer -> LoadResult.TooNew(read.version)
            }
        },
        onFailure = { LoadResult.Corrupt(it.message ?: "Unreadable snapshot") },
    )

/** Replays the journal on [read] and positions the result at the stored cursor. */
private fun ProjectDirectory.restore(
    read: SnapshotRead.Current,
    migrations: SchemaMigrations,
    nowMillis: Long,
): LoadedProject {
    val base = read.document.baseSeq
    val upgraded = read.fromVersion < CURRENT_SCHEMA_VERSION
    val journal = readJournal(journalFile, base, read.fromVersion, migrations)
    val replay = replay(read.document.project, journal.records)
    val dropped = journal.droppedLines + journal.records.size - replay.records.size
    if (dropped > 0) quarantineJournal(nowMillis)
    if (upgraded) writeSnapshot(read.document)
    if (journal.needsRewrite || dropped > 0 || upgraded) writeJournal(replay.records)
    deleteTemporaryFiles()
    val cursor = (readCursor() ?: (base + replay.records.size)).coerceIn(base, base + replay.records.size)
    val index = (cursor - base).toInt()
    return LoadedProject(
        snapshot = read.document,
        records = replay.records,
        history = EditHistory.restored(replay.entries, index),
        project = replay.projects[index],
        droppedRecords = dropped,
    )
}

/** Reads [file] as a snapshot; throws when it is unreadable or no migration exists from its schema. */
private fun readSnapshot(
    file: File,
    migrations: SchemaMigrations,
): SnapshotRead {
    val root = projectJson.parseToJsonElement(file.readText()).jsonObject
    val project = root.getValue("project").jsonObject
    val version =
        project[SchemaMigrations.SCHEMA_VERSION_KEY]?.jsonPrimitive?.intOrNull ?: CURRENT_SCHEMA_VERSION
    require(version > CURRENT_SCHEMA_VERSION || migrations.canUpgrade(version)) {
        "No migration from schema $version"
    }
    return if (version > CURRENT_SCHEMA_VERSION) {
        SnapshotRead.Newer(version)
    } else {
        val upgraded = JsonObject(root + ("project" to migrations.upgradeProject(project, version)))
        SnapshotRead.Current(projectJson.decodeFromJsonElement(SnapshotDocument.serializer(), upgraded), version)
    }
}

/**
 * Reads the journal records that continue sequence [baseSeq]. Records at or before the base (left by an
 * interrupted compaction) are skipped; the first unreadable or out-of-sequence line ends the usable journal.
 */
private fun readJournal(
    file: File,
    baseSeq: Long,
    fromVersion: Int,
    migrations: SchemaMigrations,
): JournalRead {
    val text = if (file.isFile) file.readText() else ""
    val lines = text.lines().filter { it.isNotBlank() }
    val records = mutableListOf<JournalRecord>()
    var index = 0
    var skipped = false
    while (index < lines.size) {
        val record = parseRecord(lines[index], fromVersion, migrations)
        if (record == null || (record.seq > baseSeq && record.seq != baseSeq + records.size + 1)) break
        if (record.seq <= baseSeq) skipped = true else records += record
        index++
    }
    val dropped = lines.size - index
    val unterminated = text.isNotEmpty() && !text.endsWith("\n")
    return JournalRead(records, dropped, skipped || unterminated)
}

/** Decodes one journal line, upgrading its command from [fromVersion]; null when it is unreadable. */
private fun parseRecord(
    line: String,
    fromVersion: Int,
    migrations: SchemaMigrations,
): JournalRecord? =
    runCatching {
        val json = projectJson.parseToJsonElement(line).jsonObject
        val command = migrations.upgradeCommand(json.getValue("command").jsonObject, fromVersion)
        projectJson.decodeFromJsonElement(JournalRecord.serializer(), JsonObject(json + ("command" to command)))
    }.getOrNull()

/** Applies [records] in order from [start], stopping at the first command the project rejects. */
private fun replay(
    start: Project,
    records: List<JournalRecord>,
): Replay {
    val applied = mutableListOf<JournalRecord>()
    val entries = mutableListOf<HistoryEntry>()
    val projects = mutableListOf(start)
    for (record in records) {
        val outcome = record.command.applyTo(projects.last()) as? CommandOutcome.Applied ?: break
        applied += record
        entries += HistoryEntry(record.command, outcome.inverse)
        projects += outcome.project
    }
    return Replay(applied, entries, projects)
}
