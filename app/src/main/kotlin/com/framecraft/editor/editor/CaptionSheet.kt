/*
 * The auto captions sheet: what to caption, the one-time speech model download, progress while captions are made,
 * and the result, with removing or regenerating the captions a project already has.
 */
package com.framecraft.editor.editor

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framecraft.editor.R
import com.framecraft.editor.domain.edit.CaptionPlanner
import com.framecraft.editor.domain.edit.CaptionScope
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToInt

/** Test tag of the caption sheet. */
const val CAPTION_SHEET_TAG = "caption_sheet"

/** Test tag of the generate or regenerate button. */
const val CAPTION_GENERATE_TAG = "caption_generate"

/** Test tag of the remove all captions button. */
const val CAPTION_REMOVE_TAG = "caption_remove"

/** Test tag of the cancel button shown while captions are made. */
const val CAPTION_CANCEL_TAG = "caption_cancel"

/** Returns the test tag of the source choice for [scope]. */
fun captionScopeTag(scope: CaptionScope): String = "caption_scope_${scope.name.lowercase()}"

/** Turns a 0 to 1 fraction into a percent. */
private const val PERCENT = 100f

/** What the caption sheet knows about the project: [hasCaptions], and [canSelect] when the selection has sound. */
data class CaptionSheetProject(
    val hasCaptions: Boolean,
    val canSelect: Boolean,
)

/** What the caption sheet does: [onGenerate] a run, [onCancel] it, [onRemoveAll] captions, and [onDismiss]. */
class CaptionSheetActions(
    val onGenerate: (CaptionRequest) -> Unit,
    val onCancel: () -> Unit,
    val onRemoveAll: () -> Unit,
    val onDismiss: () -> Unit,
)

/** Shows the caption sheet of [captioning] over [project], with clip [selectedClipId] selected, while it is open. */
@Composable
internal fun CaptionSheetHost(
    captioning: Captioning,
    project: Project,
    selectedClipId: String?,
) {
    val open by captioning.sheetOpen.collectAsStateWithLifecycle()
    val phase by captioning.phase.collectAsStateWithLifecycle()
    if (!open) return
    val selectable =
        selectedClipId != null &&
            CaptionPlanner.sources(project, CaptionScope.SELECTED, selectedClipId, includeMuted = true).isNotEmpty()
    val context = LocalContext.current
    val downloadSize = Formatter.formatShortFileSize(context, captioning.downloadBytes)
    CaptionSheet(
        phase = phase,
        project = CaptionSheetProject(CaptionPlanner.hasCaptions(project), selectable),
        downloadSize = downloadSize.takeUnless { captioning.modelInstalled },
        selectedClipId = selectedClipId,
        actions =
            CaptionSheetActions(
                onGenerate = captioning::generate,
                onCancel = captioning::cancel,
                onRemoveAll = captioning::removeAll,
                onDismiss = captioning::closeSheet,
            ),
    )
}

/**
 * Bottom sheet for [phase] over [project]: the settings before a run, with the [downloadSize] still to fetch when the
 * speech model is missing, the progress of a run, or its result. Generating covers [selectedClipId] when chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptionSheet(
    phase: CaptionPhase,
    project: CaptionSheetProject,
    downloadSize: String?,
    selectedClipId: String?,
    actions: CaptionSheetActions,
) {
    ModalBottomSheet(onDismissRequest = actions.onDismiss) {
        Column(
            modifier =
                Modifier
                    .testTag(CAPTION_SHEET_TAG)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.editor_captions_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (phase) {
                CaptionPhase.Idle -> {
                    CaptionSettings(project, downloadSize, selectedClipId, actions)
                }

                is CaptionPhase.Downloading -> {
                    CaptionProgress(R.string.editor_captions_downloading, phase.progress, actions.onCancel)
                }

                is CaptionPhase.Generating -> {
                    CaptionProgress(R.string.editor_captions_generating, phase.progress, actions.onCancel)
                }

                is CaptionPhase.Done -> {
                    CaptionResult(resultText(phase.count), actions.onDismiss, null)
                }

                is CaptionPhase.Failed -> {
                    CaptionResult(stringResource(failureText(phase.failure)), actions.onDismiss, actions.onCancel)
                }
            }
        }
    }
}

/**
 * The choices before a run: which clips, whether muted ones count, the language, and the [downloadSize] still to
 * fetch, then generate (or regenerate and remove all when the [project] has captions).
 */
@Composable
private fun CaptionSettings(
    project: CaptionSheetProject,
    downloadSize: String?,
    selectedClipId: String?,
    actions: CaptionSheetActions,
) {
    var picked by rememberSaveable { mutableStateOf(CaptionScope.ALL) }
    var includeMuted by rememberSaveable { mutableStateOf(false) }
    val scope = if (picked == CaptionScope.SELECTED && !project.canSelect) CaptionScope.ALL else picked
    Column(Modifier.selectableGroup()) {
        Text(stringResource(R.string.editor_captions_source), style = MaterialTheme.typography.labelLarge)
        CaptionScope.entries.forEach { option ->
            val enabled = option != CaptionScope.SELECTED || project.canSelect
            ScopeRow(option, selected = scope == option, enabled = enabled) { picked = option }
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = includeMuted, role = Role.Switch, onValueChange = { includeMuted = it }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.editor_captions_include_muted), Modifier.weight(1f))
        Switch(checked = includeMuted, onCheckedChange = null)
    }
    NoteText(stringResource(R.string.editor_captions_language))
    downloadSize?.let { NoteText(stringResource(R.string.editor_captions_download_note, it)) }
    if (project.hasCaptions) NoteText(stringResource(R.string.editor_captions_replace_note))
    val generate = if (project.hasCaptions) R.string.editor_captions_regenerate else R.string.editor_captions_generate
    Button(
        onClick = { actions.onGenerate(CaptionRequest(scope, selectedClipId, includeMuted)) },
        modifier = Modifier.fillMaxWidth().testTag(CAPTION_GENERATE_TAG),
    ) { Text(stringResource(generate)) }
    if (project.hasCaptions) {
        OutlinedButton(onClick = actions.onRemoveAll, modifier = Modifier.fillMaxWidth().testTag(CAPTION_REMOVE_TAG)) {
            Text(stringResource(R.string.editor_captions_remove_all))
        }
    }
}

/** A radio row for source [option], [selected] or not, dimmed when not [enabled]; tapping it calls [onSelect]. */
@Composable
private fun ScopeRow(
    option: CaptionScope,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val label =
        when (option) {
            CaptionScope.ALL -> R.string.editor_captions_source_all
            CaptionScope.VOICEOVERS -> R.string.editor_captions_source_voiceovers
            CaptionScope.SELECTED -> R.string.editor_captions_source_selected
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(captionScopeTag(option))
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(stringResource(label), Modifier.padding(start = Spacing.sm))
    }
}

/** The progress of a download or transcription, described by [labelRes] at [progress], with [onCancel]. */
@Composable
private fun CaptionProgress(
    @StringRes labelRes: Int,
    progress: Float,
    onCancel: () -> Unit,
) {
    val percent = (progress.coerceIn(0f, 1f) * PERCENT).roundToInt()
    val label = stringResource(labelRes, percent)
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    )
    NoteText(stringResource(R.string.editor_captions_wait_note))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().testTag(CAPTION_CANCEL_TAG)) {
        Text(stringResource(R.string.editor_captions_cancel))
    }
}

/** A finished run's [message], with done, and try again when [onRetry] is given. */
@Composable
private fun CaptionResult(
    message: String,
    onDone: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        onRetry?.let { TextButton(onClick = it) { Text(stringResource(R.string.editor_captions_retry)) } }
        Button(onClick = onDone) { Text(stringResource(R.string.editor_captions_done)) }
    }
}

/** Secondary explanatory [text]. */
@Composable
private fun NoteText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Returns how a finished run that added [count] captions reads. */
@Composable
private fun resultText(count: Int): String =
    if (count > 0) {
        pluralStringResource(R.plurals.editor_captions_added, count, count)
    } else {
        stringResource(R.string.editor_captions_no_speech)
    }

/** Returns the explanation of [failure]. */
private fun failureText(failure: CaptionFailure): Int =
    when (failure) {
        CaptionFailure.NO_SPACE -> R.string.editor_captions_failed_space
        CaptionFailure.NETWORK -> R.string.editor_captions_failed_network
        CaptionFailure.CORRUPT -> R.string.editor_captions_failed_corrupt
        CaptionFailure.ENGINE -> R.string.editor_captions_failed_engine
    }
