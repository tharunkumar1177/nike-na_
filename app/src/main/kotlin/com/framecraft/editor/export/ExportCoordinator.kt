/*
 * App-wide owner of the single running export: hands work to the export service and mirrors its state for the UI.
 */
package com.framecraft.editor.export

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.framecraft.editor.domain.export.ExportSettings
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.engine.export.ExportFailure
import com.framecraft.editor.engine.export.ExportState
import com.framecraft.editor.engine.export.isInProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Log tag for export problems. */
private const val LOG_TAG = "Export"

/** A project snapshot and the settings to export it with. */
class ExportRequest(
    val project: Project,
    val settings: ExportSettings,
)

/**
 * Runs at most one export in the whole app. The UI calls [start] with the snapshot it shows; [ExportService]
 * takes that request, runs it in the foreground, and reports progress back. All calls happen on the main thread.
 */
class ExportCoordinator(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow<ExportState>(ExportState.Idle)
    private var request: ExportRequest? = null
    private var cancelRunning: (() -> Unit)? = null

    /** State of the current or last export. */
    val state: StateFlow<ExportState> = mutableState.asStateFlow()

    /** Id of the project being exported or last exported, or null before the first export. */
    var projectId: String? = null
        private set

    /** Starts exporting [project] with [settings]; returns false if another export is in progress. */
    fun start(
        project: Project,
        settings: ExportSettings,
    ): Boolean {
        val free = !mutableState.value.isInProgress
        if (free) {
            request = ExportRequest(project, settings)
            projectId = project.id
            mutableState.value = ExportState.Preparing
            launchService()
        }
        return free
    }

    /** Stops the export in progress, if any. */
    fun cancel() {
        val running = cancelRunning
        when {
            running != null -> running()
            mutableState.value.isInProgress -> finishWith(ExportState.Cancelled)
        }
    }

    /** Clears a finished export's result so the next one starts fresh; ignored while one is in progress. */
    fun acknowledge() {
        if (!mutableState.value.isInProgress) mutableState.value = ExportState.Idle
    }

    /** Hands the waiting request to the service, which stops it through [cancel]; null if there is none. */
    internal fun takeRequest(cancel: () -> Unit): ExportRequest? {
        val taken = request
        request = null
        if (taken != null) cancelRunning = cancel
        return taken
    }

    /** Records [state] reported by the service. */
    internal fun report(state: ExportState) {
        if (!state.isInProgress) cancelRunning = null
        mutableState.value = state
    }

    /** Starts the foreground export service; a refused start fails the export instead of crashing. */
    private fun launchService() {
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, ExportService::class.java))
        } catch (refused: IllegalStateException) {
            Log.w(LOG_TAG, "Export service could not start", refused)
            finishWith(ExportState.Failed(ExportFailure.UNKNOWN))
        }
    }

    /** Ends the export with [state] without the service. */
    private fun finishWith(state: ExportState) {
        request = null
        report(state)
    }
}
