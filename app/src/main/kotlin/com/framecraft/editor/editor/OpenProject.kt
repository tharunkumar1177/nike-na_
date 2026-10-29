/*
 * One open project: its saving session, preview player, and timeline media, edited one change at a time.
 */
package com.framecraft.editor.editor

import com.framecraft.editor.data.project.ProjectSession
import com.framecraft.editor.data.project.SessionState
import com.framecraft.editor.engine.ProjectPreview
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Owns [session] together with the [preview] and timeline [media] built for it. */
internal class OpenProject(
    private val session: ProjectSession,
    val preview: ProjectPreview,
    val media: TimelineMedia,
) {
    private val editLock = Mutex()

    /** The session's current state. */
    val state: SessionState
        get() = session.state

    /** Runs [change] after earlier changes finish; returns the new state, or null if nothing changed. */
    @Throws(IOException::class)
    suspend fun change(change: suspend (ProjectSession) -> SessionState?): SessionState? =
        editLock.withLock { change(session) }

    /** Closes the session; storage errors are ignored because every edit is already saved. */
    suspend fun closeSession() {
        try {
            session.close()
        } catch (ignored: IOException) {
        }
    }

    /** Releases the preview player and timeline media. */
    fun release() {
        preview.release()
        media.close()
    }
}
