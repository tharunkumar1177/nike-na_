/*
 * Foreground service that keeps an export running with the screen off or the app in the background.
 */
package com.framecraft.editor.export

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.framecraft.editor.FramecraftApplication
import com.framecraft.editor.engine.export.ExportState
import com.framecraft.editor.engine.export.ProjectExporter
import com.framecraft.editor.engine.export.isInProgress
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the request waiting in [ExportCoordinator] with a [ProjectExporter], shows progress in a notification,
 * reports every state back to the coordinator, and stops itself when the export ends. It is not restarted after
 * the process dies; the half-written file lives only in the cache.
 */
class ExportService : Service() {
    private val scope = MainScope()
    private var exporter: ProjectExporter? = null
    private lateinit var notifications: ExportNotifications

    private val container
        get() = (application as FramecraftApplication).container

    /** Creates the notification channel. */
    override fun onCreate() {
        super.onCreate()
        notifications = ExportNotifications(this)
    }

    /** Starts the waiting export, or cancels the running one for [ACTION_CANCEL]. */
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_CANCEL) container.exportCoordinator.cancel() else begin()
        return START_NOT_STICKY
    }

    /** Not bound; the coordinator is the only interface. */
    override fun onBind(intent: Intent?): IBinder? = null

    /** Cancels the export when Android's daily limit for media processing runs out. */
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        exporter?.cancel()
    }

    /** Releases the exporter; an export still running at this point is reported as cancelled. */
    override fun onDestroy() {
        exporter?.release()
        exporter = null
        scope.cancel()
        val coordinator = container.exportCoordinator
        if (coordinator.state.value.isInProgress) coordinator.report(ExportState.Cancelled)
        super.onDestroy()
    }

    /** Enters the foreground, then runs the waiting request or stops when there is none. */
    private fun begin() {
        val running = exporter != null
        val request = if (running) null else container.exportCoordinator.takeRequest { exporter?.cancel() }
        ServiceCompat.startForeground(
            this,
            EXPORT_PROGRESS_NOTIFICATION_ID,
            notifications.progress(request?.project?.name, percent = null),
            foregroundType(),
        )
        when {
            request != null -> run(request)
            !running -> stop()
        }
    }

    /** Exports [request], mirroring each state to the coordinator and the notification. */
    private fun run(request: ExportRequest) {
        val next = container.createExporter()
        exporter = next
        val name = request.project.name
        notifications.show(name, ExportState.Running(0))
        scope.launch {
            next.state.collect { state ->
                if (state != ExportState.Idle) {
                    container.exportCoordinator.report(state)
                    notifications.show(name, state)
                    if (!state.isInProgress) stop()
                }
            }
        }
        next.export(request.project, request.settings)
    }

    /** Leaves the foreground, removing the progress notification, and stops the service. */
    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Returns the foreground type: media processing where Android has it, otherwise data sync. */
    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }

    /** Intent actions. */
    companion object {
        /** Action that cancels the running export, sent from the notification. */
        const val ACTION_CANCEL = "com.framecraft.editor.export.CANCEL"
    }
}
