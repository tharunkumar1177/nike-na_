/*
 * Removes voiceover recordings that neither the snapshot nor the journal names any more.
 */
package com.framecraft.editor.data.project

import java.io.File

/** A recording file name as it appears inside a stored file URI. */
private val RECORDING_REFERENCE = Regex("""recordings/([A-Za-z0-9._-]+\.m4a)""")

/**
 * Keeps a project's `recordings/` directory to the takes its history can still reach. A take stays while the
 * snapshot or any journal record names it, so undo and redo across restarts always find their files; it becomes
 * removable once compaction folds its last mention away, or when a crash left it unsaved.
 */
internal object RecordingSweep {
    /** Deletes every recording in [directory] that its snapshot and journal do not name; failures are ignored. */
    fun sweep(directory: ProjectDirectory) {
        val listed = directory.recordingsDir.listFiles()
        val files = listed?.filter { it.isFile }.orEmpty()
        if (files.isNotEmpty()) {
            val referenced = referencedNames(listOf(directory.snapshotFile, directory.journalFile))
            files.filter { it.name !in referenced }.forEach { file -> runCatching { file.delete() } }
        }
    }

    /** Returns every recording file name mentioned in [sources], skipping files that cannot be read. */
    private fun referencedNames(sources: List<File>): Set<String> =
        sources
            .mapNotNull { source -> runCatching { source.readText() }.getOrNull() }
            .flatMap { text -> RECORDING_REFERENCE.findAll(text).map { it.groupValues[1] }.toList() }
            .toSet()
}
