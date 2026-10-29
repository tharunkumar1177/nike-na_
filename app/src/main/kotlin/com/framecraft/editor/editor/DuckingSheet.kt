/*
 * Sheet that turns auto-ducking on for the selected clip and sets how far it drops while a voiceover plays.
 */
package com.framecraft.editor.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.MAX_DUCK_PERCENT
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToInt

/** Test tag of the ducking switch. */
const val DUCK_SWITCH_TAG = "editor_duck_switch"

/** Test tag of the ducking depth slider. */
const val DUCK_SLIDER_TAG = "editor_duck_slider"

/** How far a clip drops when ducking is first turned on. */
const val DEFAULT_DUCK_PERCENT = 60

/** Smallest drop the depth slider offers. */
private const val MIN_DUCK_PERCENT = 10

/** Step of the depth slider in percent. */
private const val DUCK_STEP_PERCENT = 10

/**
 * Sheet with a switch that ducks [audio] under voiceovers and a slider for how far it drops; each finished change
 * goes to [onCommit] once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuckingSheet(
    audio: ClipAudio,
    onCommit: (ClipAudio) -> Unit,
    onDismiss: () -> Unit,
) {
    val on = audio.autoDuckPercent > 0
    var draft by remember(audio.autoDuckPercent) {
        mutableIntStateOf(if (on) audio.autoDuckPercent else DEFAULT_DUCK_PERCENT)
    }
    val label = stringResource(R.string.editor_duck_depth_label)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.editor_duck_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.editor_duck_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { checked -> onCommit(audio.copy(autoDuckPercent = if (checked) draft else 0)) },
                    modifier = Modifier.testTag(DUCK_SWITCH_TAG),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.editor_duck_depth_value, draft),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = draft.toFloat(),
                onValueChange = { draft = it.roundToInt() },
                onValueChangeFinished = { onCommit(audio.copy(autoDuckPercent = draft)) },
                valueRange = MIN_DUCK_PERCENT.toFloat()..MAX_DUCK_PERCENT.toFloat(),
                steps = (MAX_DUCK_PERCENT - MIN_DUCK_PERCENT) / DUCK_STEP_PERCENT - 1,
                enabled = on,
                modifier = Modifier.testTag(DUCK_SLIDER_TAG).semantics { contentDescription = label },
            )
        }
    }
}
