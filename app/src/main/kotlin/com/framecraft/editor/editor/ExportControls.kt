/*
 * Connects the editor to the export sheet: opening it, asking for notifications, and sharing or playing the result.
 */
package com.framecraft.editor.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.engine.export.ExportState
import com.framecraft.editor.engine.export.isInProgress
import com.framecraft.editor.export.ExportIntents
import com.framecraft.editor.export.ExportSheet
import com.framecraft.editor.export.ExportSheetActions
import com.framecraft.editor.export.ExportViewModel

/** Log tag for export hand-off problems. */
private const val LOG_TAG = "EditorExport"

/** What the editor needs from the export sheet: the top bar [indicator] and the action that [open]s the sheet. */
class ExportControls(
    val indicator: ExportIndicator,
    val open: () -> Unit,
)

/**
 * Shows the export sheet for [project] when opened. The sheet also opens by itself when an export finishes or
 * fails, so the result is never missed; closing it while exporting leaves the export running.
 */
@Composable
fun rememberExportControls(
    exportViewModel: ExportViewModel,
    project: Project,
): ExportControls {
    val exportState by exportViewModel.exportState.collectAsStateWithLifecycle()
    val form by exportViewModel.form.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.export_share_chooser)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(exportState) {
        if (exportState is ExportState.Succeeded || exportState is ExportState.Failed) showSheet = true
    }
    if (showSheet) {
        val actions =
            ExportSheetActions(
                onSelect = exportViewModel::select,
                onStart = {
                    requestNotifications(context, permission)
                    exportViewModel.start()
                },
                onCancel = exportViewModel::cancel,
                onShare = { uri -> context.startActivity(ExportIntents.share(uri.toUri(), shareTitle)) },
                onPlay = { uri -> startSafely(context, ExportIntents.play(uri.toUri())) },
                onClose = {
                    showSheet = false
                    exportViewModel.acknowledge()
                },
            )
        ExportSheet(form, exportState, actions)
    }
    val running = exportState.isInProgress
    return ExportControls(
        indicator = ExportIndicator(exporting = running, percent = (exportState as? ExportState.Running)?.percent),
        open = {
            if (!running) exportViewModel.open(project)
            showSheet = true
        },
    )
}

/** Asks for permission to show export notifications on Android 13 and later; the export starts either way. */
private fun requestNotifications(
    context: Context,
    launcher: ManagedActivityResultLauncher<String, Boolean>,
) {
    val needed =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    if (needed) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
}

/** Starts [intent], doing nothing when no installed app can handle it. */
private fun startSafely(
    context: Context,
    intent: Intent,
) {
    try {
        context.startActivity(intent)
    } catch (missing: ActivityNotFoundException) {
        Log.w(LOG_TAG, "No app can play the export", missing)
    }
}
