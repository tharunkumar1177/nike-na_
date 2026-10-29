/*
 * JVM tests (Robolectric) for the app-wide export coordinator and the intents that hand a saved export to other apps.
 */
package com.framecraft.editor.export

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.framecraft.editor.domain.export.ExportPlanner
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.ProjectFactory
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.engine.export.ExportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Drives [ExportCoordinator] the way the editor and [ExportService] do. */
@RunWith(AndroidJUnit4::class)
class ExportCoordinatorTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val coordinator = ExportCoordinator(app)
    private var counter = 0
    private val project =
        ProjectFactory.create(
            name = "Trip",
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
            idGenerator = { "id-${counter++}" },
        )
    private val settings = ExportPlanner.defaultsFor(project)

    /** Starting launches the export service once; a second start is refused while the first runs. */
    @Test
    fun startsServiceOnce() {
        assertTrue(coordinator.start(project, settings))
        assertEquals(ExportState.Preparing, coordinator.state.value)
        val started = shadowOf(app).nextStartedService
        assertEquals(ExportService::class.java.name, started.component?.className)
        assertFalse(coordinator.start(project, settings))
        assertEquals(project.id, coordinator.projectId)
    }

    /** The service takes the request once and cancelling goes through it. */
    @Test
    fun serviceTakesRequestAndCancels() {
        coordinator.start(project, settings)
        var cancelled = false
        val request = coordinator.takeRequest { cancelled = true }
        assertSame(project, request?.project)
        assertEquals(settings, request?.settings)
        assertNull(coordinator.takeRequest {})
        coordinator.cancel()
        assertTrue(cancelled)
    }

    /** Cancelling before the service starts ends the export and drops the request. */
    @Test
    fun cancelBeforeServiceStarts() {
        coordinator.start(project, settings)
        coordinator.cancel()
        assertEquals(ExportState.Cancelled, coordinator.state.value)
        assertNull(coordinator.takeRequest {})
    }

    /** Results stay until acknowledged; acknowledging is ignored while an export runs. */
    @Test
    fun acknowledgeClearsOnlyFinishedExports() {
        coordinator.start(project, settings)
        coordinator.takeRequest {}
        coordinator.report(ExportState.Running(40))
        coordinator.acknowledge()
        assertEquals(ExportState.Running(40), coordinator.state.value)
        val done = ExportState.Succeeded("content://media/video/1", 10L)
        coordinator.report(done)
        assertEquals(done, coordinator.state.value)
        coordinator.acknowledge()
        assertEquals(ExportState.Idle, coordinator.state.value)
        assertTrue(coordinator.start(project, settings))
    }

    /** Sharing sends the video with read access through the chooser; playing views it with read access. */
    @Test
    fun intentsGrantReadAccess() {
        val uri = Uri.parse("content://media/video/1")
        val chooser = ExportIntents.share(uri, "Share video")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("video/mp4", send?.type)
        assertTrue((send?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val play = ExportIntents.play(uri)
        assertEquals(Intent.ACTION_VIEW, play.action)
        assertEquals(uri, play.data)
        assertEquals("video/mp4", play.type)
        assertTrue(play.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
