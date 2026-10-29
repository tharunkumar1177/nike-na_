/*
 * A panel docked to the bottom of the editor that leaves the preview above it touchable, for tools whose handles
 * or eyedropper work on the preview while their settings stay open, plus the percent slider those panels share.
 */
package com.framecraft.editor.editor

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.framecraft.editor.R
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToInt

/** Tallest a docked panel grows before its content scrolls. */
private val PANEL_MAX_HEIGHT = 360.dp

/** Lift of a docked panel above the editor. */
private val PANEL_ELEVATION = 3.dp

/** Shadow under a docked panel. */
private val PANEL_SHADOW = 8.dp

/** Largest value of a percent slider. */
private const val PERCENT_MAX = 100f

/**
 * Shows [content] in a panel tagged [tag] along the bottom of the window under a [title] and a Done button, without
 * blocking touches above it. Done and back both call [onDone].
 */
@Composable
internal fun DockedPanel(
    tag: String,
    title: String,
    onDone: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDone)
    Popup(
        alignment = Alignment.BottomCenter,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag(tag),
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
            tonalElevation = PANEL_ELEVATION,
            shadowElevation = PANEL_SHADOW,
        ) {
            Column(
                modifier =
                    Modifier
                        .navigationBarsPadding()
                        .heightIn(max = PANEL_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.sm, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDone) { Text(stringResource(R.string.editor_panel_done)) }
                }
                content()
            }
        }
    }
}

/**
 * A slider tagged [tag] from 0 to 100 starting at [value], read by TalkBack as [label] and shown through [valueRes]
 * with the dragged value. Dragging moves only the slider; the value goes to [onCommit] once, when the drag ends, if
 * it changed.
 */
@Composable
internal fun PercentSlider(
    value: Int,
    label: String,
    @StringRes valueRes: Int,
    onCommit: (Int) -> Unit,
    tag: String,
    enabled: Boolean = true,
) {
    var draft by remember(value) { mutableIntStateOf(value) }
    Column {
        Text(
            text = stringResource(valueRes, draft),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = draft.toFloat(),
            onValueChange = { draft = it.roundToInt() },
            onValueChangeFinished = { if (draft != value) onCommit(draft) },
            valueRange = 0f..PERCENT_MAX,
            enabled = enabled,
            modifier = Modifier.testTag(tag).semantics { contentDescription = label },
        )
    }
}
