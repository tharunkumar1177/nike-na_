/*
 * The text sheet: a text field with Done over Font, Style, and Animation tabs, saved as one edit on close.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.MAX_TEXT_LENGTH
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.TextFont
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the text sheet's text field. */
const val TEXT_FIELD_TAG = "text_sheet_field"

/** Test tag of the text sheet's Done button. */
const val TEXT_DONE_TAG = "text_sheet_done"

/** Most lines the text field shows before it scrolls. */
private const val FIELD_MAX_LINES = 3

/** Label of each typeface choice. */
@StringRes
private fun fontLabel(font: TextFont): Int =
    when (font) {
        TextFont.DEFAULT -> R.string.editor_text_font_default
        TextFont.BOLD -> R.string.editor_text_font_bold
        TextFont.SERIF -> R.string.editor_text_font_serif
        TextFont.MONOSPACE -> R.string.editor_text_font_mono
    }

/** Label of each sheet tab. */
@StringRes
private fun tabLabel(tab: TextSheetTab): Int =
    when (tab) {
        TextSheetTab.FONT -> R.string.editor_text_tab_font
        TextSheetTab.STYLE -> R.string.editor_text_tab_style
        TextSheetTab.ANIMATION -> R.string.editor_text_tab_animation
    }

/**
 * Edits [content] of a clip lasting [clipDurationMicros], opening on [startTab] with the words selected and, except
 * on the animation tab, the keyboard up. Changes stay in the sheet until it closes by Done or by dismissal; then
 * [onClose] gets the edited content once, or null when the words are blank. Done is disabled while they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextEditSheet(
    content: TextContent,
    clipDurationMicros: Long,
    startTab: TextSheetTab,
    onClose: (TextContent?) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(content.text, TextRange(0, content.text.length))) }
    var draft by remember { mutableStateOf(content) }
    var tab by remember { mutableStateOf(startTab) }
    val focus = remember { FocusRequester() }
    val blank = field.text.isBlank()
    val finish = { onClose(if (blank) null else draft.copy(text = field.text)) }
    LaunchedEffect(Unit) {
        if (startTab != TextSheetTab.ANIMATION) focus.requestFocus()
    }
    ModalBottomSheet(
        onDismissRequest = finish,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = if (it.text.length <= MAX_TEXT_LENGTH) it else field },
                    label = { Text(stringResource(R.string.editor_text_field)) },
                    isError = blank,
                    supportingText = { if (blank) Text(stringResource(R.string.editor_text_empty)) },
                    maxLines = FIELD_MAX_LINES,
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag(TEXT_FIELD_TAG),
                )
                TextButton(onClick = finish, enabled = !blank, modifier = Modifier.testTag(TEXT_DONE_TAG)) {
                    Text(stringResource(R.string.editor_text_done))
                }
            }
            PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = Spacing.md)) {
                TextSheetTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = { Text(stringResource(tabLabel(entry))) },
                    )
                }
            }
            when (tab) {
                TextSheetTab.FONT -> {
                    FontChoices(draft.style.font) { draft = draft.copy(style = draft.style.copy(font = it)) }
                }

                TextSheetTab.STYLE -> {
                    TextStyleControls(draft.style) { draft = draft.copy(style = it) }
                }

                TextSheetTab.ANIMATION -> {
                    TextAnimationControls(draft, clipDurationMicros) { draft = it }
                }
            }
        }
    }
}

/** One chip per system typeface, with [selected] chosen; picking one goes to [onPick]. */
@Composable
private fun FontChoices(
    selected: TextFont,
    onPick: (TextFont) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TextFont.entries.forEach { font ->
            FilterChip(
                selected = font == selected,
                onClick = { onPick(font) },
                label = { Text(stringResource(fontLabel(font))) },
            )
        }
    }
}
