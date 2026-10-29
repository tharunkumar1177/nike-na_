/*
 * Export bottom sheet: settings before an export, then progress, then the result.
 */
package com.framecraft.editor.export

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.framecraft.editor.R
import com.framecraft.editor.domain.export.ExportQuality
import com.framecraft.editor.domain.export.ExportResolution
import com.framecraft.editor.domain.export.ExportSettings
import com.framecraft.editor.domain.time.FrameRate
import com.framecraft.editor.engine.export.ExportState
import com.framecraft.editor.ui.theme.Spacing
import java.util.Locale

/** Test tag of the export sheet. */
const val EXPORT_SHEET_TAG = "export_sheet"

/** Test tag of the start export button. */
const val EXPORT_START_TAG = "export_btn_start"

/** Test tag of the cancel export button. */
const val EXPORT_CANCEL_TAG = "export_btn_cancel"

/** Test tag of the share button. */
const val EXPORT_SHARE_TAG = "export_btn_share"

/** Test tag of the play button. */
const val EXPORT_PLAY_TAG = "export_btn_play"

/** What the export sheet does: change settings, start, cancel, share or play the saved video, or close. */
class ExportSheetActions(
    val onSelect: (ExportSettings) -> Unit,
    val onStart: () -> Unit,
    val onCancel: () -> Unit,
    val onShare: (String) -> Unit,
    val onPlay: (String) -> Unit,
    val onClose: () -> Unit,
)

/** Modal sheet for [state]; dismissing it while exporting leaves the export running. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    form: ExportForm?,
    state: ExportState,
    actions: ExportSheetActions,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = actions.onClose, modifier = modifier.testTag(EXPORT_SHEET_TAG)) {
        ExportSheetContent(form, state, actions)
    }
}

/** The sheet body for [state]: settings when idle, progress while exporting, then the outcome. */
@Composable
internal fun ExportSheetContent(
    form: ExportForm?,
    state: ExportState,
    actions: ExportSheetActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg)) {
        when (state) {
            ExportState.Idle -> form?.let { ExportSettingsContent(it, actions) }
            ExportState.Preparing, is ExportState.Running -> ExportProgressContent(state, actions.onCancel)
            else -> ExportOutcomeContent(state, actions)
        }
    }
}

/** Title [titleRes] on the start edge and a close button on the end edge. */
@Composable
internal fun SheetHeader(
    @StringRes titleRes: Int,
    onClose: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.export_close_description))
        }
    }
}

/** Resolution, frame rate, and quality choices, the size estimate, and the export button. */
@Composable
private fun ExportSettingsContent(
    form: ExportForm,
    actions: ExportSheetActions,
) {
    val settings = form.settings
    val projectRate = form.options.frameRates.first()
    SheetHeader(R.string.export_title, actions.onClose)
    ChoiceRow(
        labelRes = R.string.export_resolution,
        options = form.options.resolutions,
        selected = settings.resolution,
        label = { resolutionLabel(it) },
        onSelect = { actions.onSelect(settings.copy(resolution = it)) },
    )
    ChoiceRow(
        labelRes = R.string.export_frame_rate,
        options = form.options.frameRates,
        selected = settings.frameRate,
        label = { rateLabel(it, projectRate) },
        onSelect = { actions.onSelect(settings.copy(frameRate = it)) },
    )
    ChoiceRow(
        labelRes = R.string.export_quality,
        options = ExportQuality.entries,
        selected = settings.quality,
        label = { qualityLabel(it) },
        onSelect = { actions.onSelect(settings.copy(quality = it)) },
    )
    Spacer(Modifier.height(Spacing.lg))
    val size = Formatter.formatShortFileSize(LocalContext.current, form.estimateBytes)
    val summary =
        if (form.canExport) {
            stringResource(R.string.export_estimate, size)
        } else {
            stringResource(R.string.export_error_nothing)
        }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Spacing.xs))
    Button(
        onClick = actions.onStart,
        enabled = form.canExport,
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.xxl).testTag(EXPORT_START_TAG),
    ) {
        Text(stringResource(R.string.export_button))
    }
}

/** A labelled single-choice segmented row over [options], showing [selected] and reporting taps to [onSelect]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    @StringRes labelRes: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Spacer(Modifier.height(Spacing.lg))
    Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(Spacing.sm))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label(option), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Returns the label of [resolution]. */
@Composable
private fun resolutionLabel(resolution: ExportResolution): String =
    stringResource(
        when (resolution) {
            ExportResolution.HD_720 -> R.string.export_resolution_720
            ExportResolution.FULL_HD_1080 -> R.string.export_resolution_1080
            ExportResolution.UHD_4K -> R.string.export_resolution_4k
        },
    )

/** Returns the label of [rate], marking the [projectRate] as the matching choice. */
@Composable
private fun rateLabel(
    rate: FrameRate,
    projectRate: FrameRate,
): String {
    val number = formatFps(rate)
    return if (rate == projectRate) stringResource(R.string.export_fps_match, number) else number
}

/** Returns the label of [quality]. */
@Composable
private fun qualityLabel(quality: ExportQuality): String =
    stringResource(
        when (quality) {
            ExportQuality.STANDARD -> R.string.export_quality_standard
            ExportQuality.HIGH -> R.string.export_quality_high
        },
    )

/** Formats [rate] in frames per second: whole rates as integers, others with two decimals. */
internal fun formatFps(rate: FrameRate): String =
    if (rate.denominator == 1) {
        rate.numerator.toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", rate.numerator.toDouble() / rate.denominator)
    }
