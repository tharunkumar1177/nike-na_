/*
 * Export sheet bodies after the export starts: progress with cancel, then success, failure, or cancellation.
 */
package com.framecraft.editor.export

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.engine.export.ExportFailure
import com.framecraft.editor.engine.export.ExportState
import com.framecraft.editor.ui.theme.Spacing

/** Diameter of the progress ring. */
private val RING_SIZE = 120.dp

/** Stroke width of the progress ring. */
private val RING_STROKE = 8.dp

/** Percent at which the encoded video is being copied to the gallery. */
private const val SAVING_PERCENT = 100

/** Title, a ring with the percent (spinning while preparing), and a cancel button. */
@Composable
internal fun ExportProgressContent(
    state: ExportState,
    onCancel: () -> Unit,
) {
    val percent = (state as? ExportState.Running)?.percent
    val title =
        when (percent) {
            null -> R.string.export_preparing
            SAVING_PERCENT -> R.string.export_saving
            else -> R.string.export_running
        }
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (percent == null) {
            CircularProgressIndicator(Modifier.size(RING_SIZE), strokeWidth = RING_STROKE)
        } else {
            CircularProgressIndicator(
                progress = { percent / SAVING_PERCENT.toFloat() },
                modifier = Modifier.size(RING_SIZE),
                strokeWidth = RING_STROKE,
            )
            val spoken = stringResource(R.string.export_progress_description, percent)
            Text(
                text = stringResource(R.string.export_percent, percent),
                style = MaterialTheme.typography.headlineLarge,
                modifier =
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = spoken
                    },
            )
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.xxl).testTag(EXPORT_CANCEL_TAG),
    ) {
        Text(stringResource(R.string.export_cancel))
    }
}

/** The result of a finished export: share and play after success, or the reason and close otherwise. */
@Composable
internal fun ExportOutcomeContent(
    state: ExportState,
    actions: ExportSheetActions,
) {
    val colors = MaterialTheme.colorScheme
    if (state is ExportState.Succeeded) {
        SheetHeader(R.string.export_succeeded, actions.onClose)
        OutcomeMessage(R.drawable.ic_check_circle, colors.primary, R.string.export_succeeded_body)
        SuccessButtons(state.contentUri, actions)
    } else {
        val failed = state as? ExportState.Failed
        SheetHeader(if (failed != null) R.string.export_failed else R.string.export_cancelled, actions.onClose)
        val message = failed?.let { failureMessage(it.reason) } ?: R.string.export_cancelled_body
        OutcomeMessage(R.drawable.ic_error_outline, colors.error, message)
        CloseButton(actions.onClose)
    }
}

/** An icon tinted [tint] beside the message [messageRes]. */
@Composable
private fun OutcomeMessage(
    @DrawableRes iconRes: Int,
    tint: Color,
    @StringRes messageRes: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint)
        Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Share, play, and done for the video saved at [contentUri]. */
@Composable
private fun SuccessButtons(
    contentUri: String,
    actions: ExportSheetActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { actions.onShare(contentUri) },
            modifier = Modifier.weight(1f).heightIn(min = Spacing.xxl).testTag(EXPORT_SHARE_TAG),
        ) {
            Text(stringResource(R.string.export_share))
        }
        FilledTonalButton(
            onClick = { actions.onPlay(contentUri) },
            modifier = Modifier.weight(1f).heightIn(min = Spacing.xxl).testTag(EXPORT_PLAY_TAG),
        ) {
            Text(stringResource(R.string.export_play))
        }
        TextButton(onClick = actions.onClose, modifier = Modifier.heightIn(min = Spacing.xxl)) {
            Text(stringResource(R.string.export_done))
        }
    }
}

/** A close button on the end edge. */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = Spacing.xxl)) {
            Text(stringResource(R.string.export_close), textAlign = TextAlign.End)
        }
    }
}

/** Returns the message explaining [reason]. */
@StringRes
private fun failureMessage(reason: ExportFailure): Int =
    when (reason) {
        ExportFailure.NOTHING_TO_EXPORT -> R.string.export_error_nothing
        ExportFailure.UNSUPPORTED_CODEC -> R.string.export_error_unsupported_codec
        ExportFailure.SOURCE_MISSING -> R.string.export_error_source_missing
        ExportFailure.DECODE_FAILED -> R.string.export_error_decode_failed
        ExportFailure.ENCODE_FAILED -> R.string.export_error_encode_failed
        ExportFailure.STORAGE_FULL -> R.string.export_error_storage_full
        ExportFailure.UNKNOWN -> R.string.export_error_unknown
    }
