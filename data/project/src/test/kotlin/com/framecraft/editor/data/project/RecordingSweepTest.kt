/*
 * Tests for the voiceover recording sweep against real files: referenced takes stay and unreferenced ones go.
 */
package com.framecraft.editor.data.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Exercises [RecordingSweep] in a temporary project directory. */
class RecordingSweepTest {
    @TempDir
    lateinit var root: File

    /** Returns the project directory under test, creating it. */
    private fun directory(): ProjectDirectory = ProjectDirectory(File(root, "project-1").apply { mkdirs() })

    /** Creates take [name] in [directory]'s recordings. */
    private fun take(
        directory: ProjectDirectory,
        name: String,
    ) {
        directory.recordingsDir.mkdirs()
        File(directory.recordingsDir, name).writeText("audio")
    }

    /** Takes named by the snapshot or by any journal record stay; every other take is deleted. */
    @Test
    fun keepsReferencedTakes() {
        val directory = directory()
        val base = "file://" + directory.recordingsDir.absolutePath.replace('\\', '/')
        directory.snapshotFile.writeText("""{"uri":"$base/take-a.m4a"}""")
        directory.journalFile.writeText("""{"seq":1,"uri":"$base/take-b.m4a"}""" + "\n")
        listOf("take-a.m4a", "take-b.m4a", "take-c.m4a").forEach { take(directory, it) }
        RecordingSweep.sweep(directory)
        assertEquals(listOf("take-a.m4a", "take-b.m4a"), namesIn(directory))
    }

    /** A project without recordings, or without a journal, sweeps without failing. */
    @Test
    fun toleratesMissingFiles() {
        val directory = directory()
        RecordingSweep.sweep(directory)
        assertFalse(directory.recordingsDir.exists())
        take(directory, "take-a.m4a")
        RecordingSweep.sweep(directory)
        assertEquals(emptyList<String>(), namesIn(directory))
    }

    /** Returns the names of the takes left in [directory], sorted. */
    private fun namesIn(directory: ProjectDirectory): List<String> {
        val files = directory.recordingsDir.listFiles().orEmpty()
        return files.map { it.name }.sorted()
    }
}
