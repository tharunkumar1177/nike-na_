/*
 * Tests for project storage against real files: saving, reopening, undo across restarts, crash recovery,
 * compaction, schema versions, and the project list.
 */
package com.framecraft.editor.data.project

import com.framecraft.editor.domain.command.DeleteClip
import com.framecraft.editor.domain.command.RenameProject
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Exercises [ProjectStore] in a temporary directory. */
class ProjectStoreTest {
    @TempDir
    lateinit var root: File

    private var now = 1_000L
    private val io = Dispatchers.IO.limitedParallelism(1)

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
                        clips =
                            listOf(
                                Clip("a", "asset", 0L, 0L, 1_000_000L),
                                Clip("b", "asset", 1_000_000L, 0L, 1_000_000L),
                            ),
                    ),
                ),
        )

    /** Returns a store over [root] with a clock that advances on each read. */
    private fun store(migrations: SchemaMigrations = SchemaMigrations.SHIPPED) =
        ProjectStore(root, io, clock = { now++ }, migrations = migrations)

    /** Opens [projectId], failing the test unless it opens. */
    private suspend fun ProjectStore.opened(projectId: String = project.id): OpenProjectResult.Opened {
        val result = open(projectId)
        assertTrue(result is OpenProjectResult.Opened, "Expected to open, got $result")
        return result as OpenProjectResult.Opened
    }

    /** Returns the project's directory. */
    private fun dir() = File(root, project.id)

    /** Returns the non-blank journal lines. */
    private fun journalLines() = File(dir(), "journal.jsonl").readLines().filter { it.isNotBlank() }

    /** Edits survive closing and reopening, and the list shows the latest name. */
    @Test
    fun savedEditsReopen() =
        runBlocking {
            val session = store().create(project)
            session.dispatch(RenameProject("Beach"))
            session.dispatch(DeleteClip("a"))
            session.close()
            val reopened = store().opened().session.state
            assertEquals("Beach", reopened.project.name)
            val track = reopened.project.tracks.single()
            assertEquals(listOf("b"), track.clips.map { it.id })
            assertTrue(reopened.canUndo)
            assertEquals(listOf("Beach"), store().list().summaries.map { it.name })
        }

    /** Undo and redo positions survive a restart. */
    @Test
    fun undoSurvivesRestart() =
        runBlocking {
            val session = store().create(project)
            session.dispatch(RenameProject("B"))
            session.dispatch(RenameProject("C"))
            assertEquals("B", session.undo()?.project?.name)
            val reopened = store().opened().session
            assertEquals("B", reopened.state.project.name)
            assertTrue(reopened.state.canRedo)
            assertEquals("C", reopened.redo()?.project?.name)
            reopened.undo()
            assertEquals("Trip", reopened.undo()?.project?.name)
        }

    /** A new edit after undo discards the redo tail on disk. */
    @Test
    fun editAfterUndoTruncatesJournal() =
        runBlocking {
            val session = store().create(project)
            session.dispatch(RenameProject("B"))
            session.dispatch(RenameProject("C"))
            session.undo()
            session.dispatch(RenameProject("D"))
            assertEquals(2, journalLines().size)
            val reopened = store().opened().session.state
            assertEquals("D", reopened.project.name)
            assertFalse(reopened.canRedo)
        }

    /** A torn final line from a crash is dropped and the original journal is kept for diagnosis. */
    @Test
    fun tornTailIsDropped() =
        runBlocking {
            val session = store().create(project)
            session.dispatch(RenameProject("B"))
            File(dir(), "journal.jsonl").appendText("""{"seq":2,"atEpochMillis":5,"comm""")
            val opened = store().opened()
            assertEquals(1, opened.droppedRecordCount)
            assertEquals("B", opened.session.state.project.name)
            assertEquals(1, journalLines().size)
            assertTrue(dir().listFiles().orEmpty().any { it.name.startsWith("journal.corrupt-") })
        }

    /** A sequence gap ends the usable journal. */
    @Test
    fun sequenceGapDropsTail() =
        runBlocking {
            val session = store().create(project)
            session.dispatch(RenameProject("B"))
            val gap = journalLines().single().replace("\"seq\":1", "\"seq\":3").replace("\"B\"", "\"Z\"")
            File(dir(), "journal.jsonl").appendText(gap + "\n")
            val opened = store().opened()
            assertEquals(1, opened.droppedRecordCount)
            assertEquals("B", opened.session.state.project.name)
        }

    /** Long histories compact into the snapshot but keep the guaranteed undo depth. */
    @Test
    fun compactionKeepsUndoFloor() =
        runBlocking {
            val session = store().create(project)
            repeat(401) { session.dispatch(RenameProject("Name $it")) }
            session.close()
            assertEquals(RETAINED_UNDO_RECORDS, journalLines().size)
            val reopened = store().opened().session
            assertEquals("Name 400", reopened.state.project.name)
            var undone = 0
            while (reopened.undo() != null) undone++
            assertEquals(RETAINED_UNDO_RECORDS, undone)
            assertEquals("Name 300", reopened.state.project.name)
        }

    /** Projects from a newer schema are refused without touching their files. */
    @Test
    fun newerSchemaIsRefused() =
        runBlocking {
            store().create(project).close()
            val snapshot = File(dir(), "snapshot.json")
            val newer = snapshot.readText().replace("\"schemaVersion\":3", "\"schemaVersion\":99")
            snapshot.writeText(newer)
            assertEquals(OpenProjectResult.TooNew(99), store().open(project.id))
            assertEquals(newer, snapshot.readText())
        }

    /** Older schemas are upgraded through the migration chain and saved in the current schema. */
    @Test
    fun olderSchemaIsUpgraded() =
        runBlocking {
            store().create(project).close()
            val snapshot = File(dir(), "snapshot.json")
            snapshot.writeText(
                snapshot
                    .readText()
                    .replace("\"schemaVersion\":3", "\"schemaVersion\":0")
                    .replace("\"name\":\"Trip\"", "\"title\":\"Trip\""),
            )
            val renameTitle =
                object : SchemaMigration {
                    override val fromVersion = 0

                    /** Renames the old `title` field to `name`. */
                    override fun migrateProject(project: JsonObject) =
                        JsonObject(project - "title" + ("name" to (project["title"] ?: JsonPrimitive(""))))

                    /** Commands are unchanged between these versions. */
                    override fun migrateCommand(command: JsonObject) = command
                }
            val opened = store(SchemaMigrations(listOf(renameTitle, Migration1To2, Migration2To3))).opened()
            assertEquals("Trip", opened.session.state.project.name)
            assertTrue(snapshot.readText().contains("\"schemaVersion\":3"))
        }

    /** A project and journal written verbatim in schema 1 open through the shipped chain and are saved as schema 3. */
    @Test
    fun schemaOneFixtureOpens() =
        runBlocking {
            dir().mkdirs()
            File(dir(), "snapshot.json").writeText(SCHEMA_ONE_SNAPSHOT)
            File(dir(), "journal.jsonl").writeText(SCHEMA_ONE_JOURNAL)
            val opened = store().opened()
            val project = opened.session.state.project
            assertEquals("Renamed", project.name)
            assertNull(
                project.tracks
                    .single()
                    .clips
                    .single()
                    .anchor,
            )
            opened.session.close()
            assertTrue(File(dir(), "snapshot.json").readText().contains("\"schemaVersion\":3"))
        }

    /** A schema 2 project with an overlay and its journal open, replay, undo, and are saved as schema 3. */
    @Test
    fun schemaTwoFixtureOpens() =
        runBlocking {
            dir().mkdirs()
            File(dir(), "snapshot.json").writeText(SCHEMA_TWO_SNAPSHOT)
            File(dir(), "journal.jsonl").writeText(SCHEMA_TWO_JOURNAL)
            val opened = store().opened()
            val reopened = opened.session.state.project
            assertEquals(listOf("track"), reopened.tracks.map { it.id })
            val restored = requireNotNull(opened.session.undo()).project
            assertEquals(listOf("lane", "track"), restored.tracks.map { it.id })
            val overlayLane = restored.tracks.first()
            val overlay = overlayLane.clips.single()
            assertEquals(0.5f, overlay.overlayTransform?.opacity)
            assertEquals("a", overlay.anchor?.parentClipId)
            assertTrue(overlay.hasDefaultAudio)
            opened.session.close()
            assertTrue(File(dir(), "snapshot.json").readText().contains("\"schemaVersion\":3"))
        }

    /** A missing list summary is rebuilt; an unreadable project is listed as unreadable and refused. */
    @Test
    fun listRebuildsAndFlagsUnreadable() =
        runBlocking {
            store().create(project).close()
            File(dir(), "meta.json").delete()
            assertEquals(listOf("Trip"), store().list().summaries.map { it.name })
            File(dir(), "meta.json").delete()
            File(dir(), "snapshot.json").writeText("{not json")
            val list = store().list()
            assertTrue(list.summaries.isEmpty())
            assertEquals(listOf(project.id), list.unreadableIds)
            assertTrue(store().open(project.id) is OpenProjectResult.Corrupt)
        }

    /** Closed projects can be renamed and deleted. */
    @Test
    fun changeAndDeleteClosedProject() =
        runBlocking {
            store().create(project).close()
            assertTrue(store().change(project.id, RenameProject("Renamed")))
            assertFalse(store().change(project.id, RenameProject(" ")))
            val summaries = store().list().summaries
            assertEquals("Renamed", summaries.single().name)
            assertTrue(store().delete(project.id))
            assertEquals(OpenProjectResult.NotFound, store().open(project.id))
            assertNull(store().list().summaries.firstOrNull())
        }

    /** Ids that are not safe directory names are rejected. */
    @Test
    fun rejectsUnsafeIds() {
        assertThrows<IllegalArgumentException> { runBlocking { store().open("../escape") } }
    }
}

/** A snapshot exactly as schema 1 wrote it. */
private val SCHEMA_ONE_SNAPSHOT =
    """
    {"baseSeq":0,"createdAtEpochMillis":1,"project":{"id":"project-1","name":"Trip",
    "frameRate":{"numerator":30,"denominator":1},"assets":[{"id":"asset","uri":"content://media/asset",
    "kind":"VIDEO","durationMicros":10000000,"width":1920,"height":1080,"rotationDegrees":0,"hasAudio":true,
    "fingerprint":{"sizeBytes":1}}],"tracks":[{"id":"track","kind":"VIDEO","magnetic":true,"clips":[
    {"id":"a","assetId":"asset","startMicros":0,"trimStartMicros":0,"durationMicros":1000000},
    {"id":"b","assetId":"asset","startMicros":1000000,"trimStartMicros":0,"durationMicros":1000000}]}],
    "schemaVersion":1}}
    """.trimIndent()

/** A journal exactly as schema 1 wrote it: a rename and a clip deletion. */
private val SCHEMA_ONE_JOURNAL =
    """
    {"seq":1,"atEpochMillis":2,"command":{"type":"renameProject","name":"Renamed"}}
    {"seq":2,"atEpochMillis":3,"command":{"type":"deleteClip","clipId":"b"}}

    """.trimIndent()

/** A snapshot exactly as schema 2 wrote it: a base track under an anchored overlay lane. */
private val SCHEMA_TWO_SNAPSHOT =
    """
    {"baseSeq":0,"createdAtEpochMillis":1,"project":{"id":"project-1","name":"Trip",
    "frameRate":{"numerator":30,"denominator":1},"assets":[{"id":"asset","uri":"content://media/asset",
    "kind":"VIDEO","durationMicros":10000000,"width":1920,"height":1080,"rotationDegrees":0,"hasAudio":true,
    "fingerprint":{"sizeBytes":1}}],"tracks":[{"id":"lane","kind":"OVERLAY","magnetic":false,"clips":[
    {"id":"o","assetId":"asset","startMicros":0,"trimStartMicros":0,"durationMicros":1000000,
    "anchor":{"parentClipId":"a","offsetMicros":0},"overlayTransform":{"scale":0.4}}]},
    {"id":"track","kind":"VIDEO","magnetic":true,"clips":[
    {"id":"a","assetId":"asset","startMicros":0,"trimStartMicros":0,"durationMicros":2000000}]}],
    "schemaVersion":2}}
    """.trimIndent()

/** A journal exactly as schema 2 wrote it: an opacity change, then removal of the overlay. */
private val SCHEMA_TWO_JOURNAL =
    listOf(
        """{"seq":1,"atEpochMillis":2,"command":{"type":"setOverlayTransform","clipId":"o",""" +
            """"transform":{"scale":0.4,"opacity":0.5}}}""",
        """{"seq":2,"atEpochMillis":3,"command":{"type":"removeOverlayClip","clipId":"o"}}""",
    ).joinToString(separator = "\n", postfix = "\n")
