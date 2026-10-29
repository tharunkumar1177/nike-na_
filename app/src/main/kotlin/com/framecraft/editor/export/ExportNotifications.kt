/*
 * Notifications for exports: the ongoing progress notification and the finished or failed result.
 */
package com.framecraft.editor.export

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.framecraft.editor.MainActivity
import com.framecraft.editor.R
import com.framecraft.editor.engine.export.ExportState

/** Channel for every export notification. */
private const val CHANNEL_ID = "exports"

/** Id of the ongoing progress notification, owned by the foreground service. */
const val EXPORT_PROGRESS_NOTIFICATION_ID = 1

/** Id of the result notification, which outlives the service. */
private const val EXPORT_RESULT_NOTIFICATION_ID = 2

/** Highest progress value. */
private const val MAX_PERCENT = 100

/** Request code of the open-app intent. */
private const val OPEN_APP_REQUEST = 0

/** Request code of the cancel intent. */
private const val CANCEL_REQUEST = 1

/** Request code of the play-video intent. */
private const val PLAY_REQUEST = 2

/** Builds and posts export notifications on a low-importance channel. */
internal class ExportNotifications(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val channel =
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.export_channel_name))
                .build()
        manager.createNotificationChannel(channel)
    }

    /**
     * Returns the ongoing notification for exporting [projectName], or a generic title when it is not known yet,
     * at [percent], or indeterminate when null.
     */
    fun progress(
        projectName: String?,
        percent: Int?,
    ): Notification {
        val title =
            projectName?.let { context.getString(R.string.export_notification_running, it) }
                ?: context.getString(R.string.export_running)
        val cancel =
            PendingIntent.getService(
                context,
                CANCEL_REQUEST,
                Intent(context, ExportService::class.java).setAction(ExportService.ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return base(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(MAX_PERCENT, percent ?: 0, percent == null)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.export_cancel), cancel)
            .build()
    }

    /** Updates the ongoing notification to [state], or replaces it with the result once the export ends. */
    fun show(
        projectName: String,
        state: ExportState,
    ) {
        when (state) {
            is ExportState.Running -> post(EXPORT_PROGRESS_NOTIFICATION_ID, progress(projectName, state.percent))
            is ExportState.Succeeded -> post(EXPORT_RESULT_NOTIFICATION_ID, succeeded(projectName, state.contentUri))
            is ExportState.Failed -> post(EXPORT_RESULT_NOTIFICATION_ID, failed(projectName))
            else -> Unit
        }
    }

    /** Returns the notification for a saved export whose tap plays the video at [contentUri]. */
    private fun succeeded(
        projectName: String,
        contentUri: String,
    ): Notification {
        val play =
            PendingIntent.getActivity(
                context,
                PLAY_REQUEST,
                ExportIntents.play(contentUri.toUri()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return base(context.getString(R.string.export_notification_succeeded))
            .setContentText(context.getString(R.string.export_notification_succeeded_body, projectName))
            .setContentIntent(play)
            .setAutoCancel(true)
            .build()
    }

    /** Returns the notification for a failed export that opens the app for the reason. */
    private fun failed(projectName: String): Notification =
        base(context.getString(R.string.export_failed))
            .setContentText(projectName)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()

    /** Returns a builder with the channel, icon, and [title] every export notification shares. */
    private fun base(title: String): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_export)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    /** Returns an intent that brings the app to the front. */
    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    /** Posts [notification] as [id] when the user allows notifications; the in-app sheet shows progress anyway. */
    private fun post(
        id: Int,
        notification: Notification,
    ) {
        val allowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (allowed) manager.notify(id, notification)
    }
}
