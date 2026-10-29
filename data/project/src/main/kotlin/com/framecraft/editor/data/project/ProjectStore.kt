/*
 * Entry point for project storage: create, list, open, change, and delete projects under one root directory.
 */
package com.framecraft.editor.data.project

import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.EditHistory
import com.framecraft.editor.domain.export.ExportSettings
import com.framecraft.editor.domain.model.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Characters allowed in a project id, which names its directory. */
private val SAFE_ID = Regex("[A-Za-z0-9-]+")

/** Result of [ProjectStore.open]. */
sealed interface OpenProjectResult {
    /** The project opened; [droppedRecordCount] unreadable edits at the end of its journal were discarded. */
    class Opened(
        val session: ProjectSession,
        val droppedRecordCount: Int,
    ) : OpenProjectResult

    /** No project has this id. */
    data object NotFound : OpenProjectResult

    /** The project was saved by a newer version of the app using schema [schemaVersion]. */
    data class TooNew(
        val schemaVersion: Int,
    ) : OpenProjectResult

    /** The project's snapshot could not be read; its files were left untouched. */
    data class Corrupt(
        val detail: String,
    ) : OpenProjectResult
}

/**
 * Stores each project in its own directory under [rootDir]. All file access runs on [ioDispatcher],
 * which must run one task at a time so there is a single writer.
 */
class ProjectStore(
    private val rootDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val migrations: SchemaMigrations = SchemaMigrations.SHIPPED,
) {
    /** Saves [project] as a new project and opens it. */
    @Throws(IOException::class)
    suspend fun create(project: Project): ProjectSession =
        withContext(ioDispatcher) {
            val directory = directoryOf(project.id)
            val dir = directory.dir
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
            val snapshot = SnapshotDocument(baseSeq = 0L, createdAtEpochMillis = clock(), project = project)
            directory.writeSnapshot(snapshot)
            directory.writeJournal(emptyList())
            directory.writeCursor(0L)
            val loaded = LoadedProject(snapshot, emptyList(), EditHistory.empty(), project, droppedRecords = 0)
            ProjectSession(directory, ioDispatcher, clock, loaded).also { it.writeMeta() }
        }

    /** Opens project [projectId], recovering from a crash if needed. */
    @Throws(IOException::class)
    suspend fun open(projectId: String): OpenProjectResult =
        withContext(ioDispatcher) {
            val directory = directoryOf(projectId)
            if (directory.exists()) openExisting(directory) else OpenProjectResult.NotFound
        }

    /** Lists saved projects, most recently edited first. */
    suspend fun list(): ProjectList =
        withContext(ioDispatcher) {
            val summaries = mutableListOf<ProjectSummary>()
            val unreadable = mutableListOf<String>()
            rootDir.listFiles { file -> file.isDirectory && file.name.matches(SAFE_ID) }?.forEach { dir ->
                val summary = runCatching { summaryOf(ProjectDirectory(dir)) }.getOrNull()
                if (summary != null) summaries += summary else unreadable += dir.name
            }
            ProjectList(summaries.sortedByDescending { it.updatedAtEpochMillis }, unreadable.sorted())
        }

    /** Applies [command] to the closed project [projectId] and saves it; false if it did not apply. */
    @Throws(IOException::class)
    suspend fun change(
        projectId: String,
        command: EditCommand,
    ): Boolean {
        val session = (open(projectId) as? OpenProjectResult.Opened)?.session
        val applied = session?.dispatch(command) is DispatchResult.Applied
        session?.close()
        return applied
    }

    /** Returns the export settings last saved for [projectId], or null when there are none or they are unreadable. */
    suspend fun readExportSettings(projectId: String): ExportSettings? =
        withContext(ioDispatcher) { ExportSettingsFile(directoryOf(projectId).dir).read() }

    /** Saves [settings] as the export settings of the existing project [projectId]. */
    @Throws(IOException::class)
    suspend fun writeExportSettings(
        projectId: String,
        settings: ExportSettings,
    ) {
        withContext(ioDispatcher) {
            val directory = directoryOf(projectId)
            if (!directory.exists()) throw IOException("No project $projectId")
            ExportSettingsFile(directory.dir).write(settings)
        }
    }

    /** Deletes project [projectId] and all its files; true if nothing remains. */
    suspend fun delete(projectId: String): Boolean =
        withContext(ioDispatcher) { directoryOf(projectId).dir.deleteRecursively() }

    /** Returns the directory of [projectId], rejecting ids that are not safe directory names. */
    private fun directoryOf(projectId: String): ProjectDirectory {
        require(projectId.matches(SAFE_ID)) { "Unsafe project id: $projectId" }
        return ProjectDirectory(File(rootDir, projectId))
    }

    /** Loads [directory] into an open session. */
    private fun openExisting(directory: ProjectDirectory): OpenProjectResult =
        when (val result = directory.load(migrations, clock())) {
            is LoadResult.Loaded -> {
                val session = ProjectSession(directory, ioDispatcher, clock, result.project)
                OpenProjectResult.Opened(session, result.project.droppedRecords)
            }

            is LoadResult.TooNew -> {
                OpenProjectResult.TooNew(result.version)
            }

            is LoadResult.Corrupt -> {
                OpenProjectResult.Corrupt(result.detail)
            }
        }

    /** Returns the stored summary of [directory], rebuilding it when missing; null if the project is unreadable. */
    private fun summaryOf(directory: ProjectDirectory): ProjectSummary? =
        directory.readMeta() ?: (directory.load(migrations, clock()) as? LoadResult.Loaded)?.let { result ->
            val loaded = result.project
            val created = loaded.snapshot.createdAtEpochMillis
            val updated = loaded.records.lastOrNull()?.atEpochMillis ?: created
            val summary = ProjectSummary.of(loaded.project, created, updated)
            directory.writeMeta(summary)
            summary
        }
}
