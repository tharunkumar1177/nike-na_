/*
 * The look sheet: a picture clip's filter and color adjustments on two tabs, plus applying the look to every picture
 * clip and resetting it. Every finished change is saved as one edit right away, so the sheet has no Done button.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.ApplyLookToAll
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.SetClipLook
import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.takesLook
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the look sheet. */
const val LOOK_SHEET_TAG = "look_sheet"

/** Test tag of the filters tab. */
const val LOOK_FILTERS_TAB_TAG = "look_filters_tab"

/** Test tag of the adjust tab. */
const val LOOK_ADJUST_TAB_TAG = "look_adjust_tab"

/** Test tag of the reset all button. */
const val LOOK_RESET_ALL_TAG = "look_reset_all"

/** Test tag of the apply to all button. */
const val LOOK_APPLY_ALL_TAG = "look_apply_all"

/** Test tag of the note saying how many clips apply to all reached. */
const val LOOK_APPLIED_TAG = "look_applied_text"

/** Which clip the look sheet shows; null while the sheet is closed. */
internal class LookEditing {
    /** Clip the sheet shows, or null while it is closed. */
    var clipId by mutableStateOf<String?>(null)
        private set

    /** Opens the sheet on clip [id]; a null id leaves it closed. */
    fun open(id: String?) {
        clipId = id
    }

    /** Closes the sheet. */
    fun close() {
        clipId = null
    }
}

/** Returns the look editing state, kept for as long as the editor shows. */
@Composable
internal fun rememberLookEditing(): LookEditing = remember { LookEditing() }

/**
 * Shows the look sheet for the clip [editing] has open in [project]; each change goes to [dispatch] as one edit, a
 * look that changes nothing as none. The sheet closes when its clip is gone or cannot take a look.
 */
@Composable
internal fun LookSheetHost(
    editing: LookEditing,
    project: Project,
    dispatch: (EditCommand) -> Unit,
) {
    val clipId = editing.clipId
    val track = clipId?.let { id -> project.tracks.find { track -> track.clips.any { it.id == id } } }
    val clip = track?.clips?.find { it.id == clipId }
    if (track != null && clip != null && track.kind.takesLook) {
        var applied by remember(clipId) { mutableStateOf<Int?>(null) }
        val stored = clip.look
        LookSheet(
            look = stored ?: ClipLook(),
            applied = applied,
            onChange = { next ->
                val value = next.takeUnless { it.isNeutral }
                if (value != stored) dispatch(SetClipLook(clip.id, value))
            },
            onApplyAll = { look ->
                val value = look.takeUnless { it.isNeutral }
                applied = ApplyLookToAll.valuesFor(project, value).size
                dispatch(ApplyLookToAll(value))
            },
            onDismiss = editing::close,
        )
    } else if (clipId != null) {
        LaunchedEffect(clipId) { editing.close() }
    }
}

/**
 * Bottom sheet for a clip's [look] on a filters tab and an adjust tab. A chip tap, finished slider drag, or reset
 * goes to [onChange] with the whole new look; apply to all goes to [onApplyAll] and the sheet then says how many
 * clips it [applied] to; [onDismiss] closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LookSheet(
    look: ClipLook,
    applied: Int?,
    onChange: (ClipLook) -> Unit,
    onApplyAll: (ClipLook) -> Unit,
    onDismiss: () -> Unit,
) {
    var adjustTab by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(LOOK_SHEET_TAG),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.editor_look_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PrimaryTabRow(selectedTabIndex = if (adjustTab) 1 else 0) {
                Tab(
                    selected = !adjustTab,
                    onClick = { adjustTab = false },
                    text = { Text(stringResource(R.string.editor_look_tab_filters)) },
                    modifier = Modifier.testTag(LOOK_FILTERS_TAB_TAG),
                )
                Tab(
                    selected = adjustTab,
                    onClick = { adjustTab = true },
                    text = { Text(stringResource(R.string.editor_look_tab_adjust)) },
                    modifier = Modifier.testTag(LOOK_ADJUST_TAB_TAG),
                )
            }
            if (adjustTab) {
                LookAdjustTab(look, onChange)
            } else {
                LookFiltersTab(look, onChange)
            }
            LookActions(look, onChange, onApplyAll)
            applied?.let { LookAppliedNote(it) }
        }
    }
}

/** Apply to all and reset all for [look]; both wait until the look changes something. */
@Composable
private fun LookActions(
    look: ClipLook,
    onChange: (ClipLook) -> Unit,
    onApplyAll: (ClipLook) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(
            onClick = { onApplyAll(look) },
            enabled = !look.isNeutral,
            modifier = Modifier.testTag(LOOK_APPLY_ALL_TAG),
        ) {
            Text(stringResource(R.string.editor_look_apply_all))
        }
        TextButton(
            onClick = { onChange(ClipLook()) },
            enabled = !look.isNeutral,
            modifier = Modifier.testTag(LOOK_RESET_ALL_TAG),
        ) {
            Text(stringResource(R.string.editor_look_reset_all))
        }
    }
}

/** Says politely that apply to all reached [count] clips. */
@Composable
private fun LookAppliedNote(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.editor_look_applied_all, count, count),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .testTag(LOOK_APPLIED_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
