/*
 * Serialized documents stored in a project directory, and the JSON configuration used to read and write them.
 */
package com.framecraft.editor.data.project

import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.model.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON settings for every stored document: tolerant of unknown keys, always writing defaults such as schemaVersion. */
internal val projectJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

/** The project as of journal sequence [baseSeq]; records after it are replayed on top. */
@Serializable
data class SnapshotDocument(
    val baseSeq: Long,
    val createdAtEpochMillis: Long,
    val project: Project,
)

/** One journaled edit, numbered [seq] contiguously after the snapshot's base. */
@Serializable
data class JournalRecord(
    val seq: Long,
    val atEpochMillis: Long,
    val command: EditCommand,
)

/** Sequence number of the last applied journal record; later records are undone and can be redone. */
@Serializable
internal data class CursorDocument(
    val cursorSeq: Long,
)

/** What the project list shows for a project, readable without opening it. */
@Serializable
data class ProjectSummary(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val durationMicros: Long,
    val clipCount: Int,
    val schemaVersion: Int,
) {
    /** Factories. */
    companion object {
        /** Summarizes [project]. */
        fun of(
            project: Project,
            createdAtEpochMillis: Long,
            updatedAtEpochMillis: Long,
        ): ProjectSummary =
            ProjectSummary(
                id = project.id,
                name = project.name,
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
                durationMicros = project.durationMicros,
                clipCount = project.tracks.sumOf { it.clips.size },
                schemaVersion = project.schemaVersion,
            )
    }
}

/** Saved projects, newest first, plus the ids of project directories that could not be read. */
data class ProjectList(
    val summaries: List<ProjectSummary>,
    val unreadableIds: List<String>,
)
