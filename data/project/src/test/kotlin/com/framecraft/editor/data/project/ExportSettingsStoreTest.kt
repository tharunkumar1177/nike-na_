/*
 * Tests for per-project export settings against real files: round trip, missing and unreadable files, and
 * isolation from the edit journal.
 */
package com.framecraft.editor.data.project

import com.framecraft.editor.domain.export.ExportQuality
import com.framecraft.editor.domain.export.ExportResolution
import com.framecraft.editor.domain.export.ExportSettings
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.domain.model.Track
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.time.FrameRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** Exercises the export settings methods of [ProjectStore] in a temporary directory. */
class ExportSettingsStoreTest {
    @TempDir
    lateinit var root: File

    private val io = Dispatchers.IO.limitedParallelism(1)
    private val store by lazy { ProjectStore(root, io) }

    private val project =
        Project(
            id = "project-1",
            name = "Trip",
            frameRate = FrameRate.FPS_30,
            assets =
                listOf(
                    MediaAsset(
                        id = "asset",
                        uri = "content://media/asset",
                        kind = MediaKind.VIDEO,
                        durationMicros = 10_000_000L,
                        width = 1920,
                        height = 1080,
                        rotationDegrees = 0,
                        hasAudio = true,
                        fingerprint = SourceFingerprint(sizeBytes = 1L, lastModifiedMillis = null),
                    ),
                ),
            tracks =
                listOf(
                    Track(
                        id = "track",
                        kind = TrackKind.VIDEO,
                        magnetic = true,
                        clips = listOf(Clip("a", "asset", 0L, 0L, 1_000_000L)),
                    ),
                ),
        )

    private val settings = ExportSettings(ExportResolution.HD_720, FrameRate.FPS_24, ExportQuality.HIGH)

    /** Saved settings read back unchanged and leave the journal and undo history alone. */
    @Test
    fun settingsRoundTripOutsideHistory() =
        runBlocking {
            store.create(project).close()
            store.writeExportSettings(project.id, settings)
            assertEquals(settings, store.readExportSettings(project.id))
            val reopened = (store.open(project.id) as OpenProjectResult.Opened).session
            assertFalse(reopened.state.canUndo)
            reopened.close()
            assertEquals("", File(root, "${project.id}/journal.jsonl").readText())
        }

    /** A project with no saved settings reads null. */
    @Test
    fun missingSettingsReadNull() =
        runBlocking {
            store.create(project).close()
            assertNull(store.readExportSettings(project.id))
        }

    /** An unreadable settings file reads null instead of failing. */
    @Test
    fun corruptSettingsReadNull() =
        runBlocking {
            store.create(project).close()
            File(root, "${project.id}/export-settings.json").writeText("{ not json")
            assertNull(store.readExportSettings(project.id))
        }

    /** Settings cannot be saved for a project that does not exist. */
    @Test
    fun writingForUnknownProjectFails() {
        assertThrows<IOException> { runBlocking { store.writeExportSettings("missing", settings) } }
    }
}
