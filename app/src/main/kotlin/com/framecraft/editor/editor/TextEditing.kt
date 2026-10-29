/*
 * Opens the text sheet for new or selected text and turns what the sheet returns into one edit.
 */
package com.framecraft.editor.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.RemoveTextClip
import com.framecraft.editor.domain.command.SetTextContent
import com.framecraft.editor.domain.edit.TextPlacement
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TextContent

/** The tabs of the text sheet. */
enum class TextSheetTab {
    /** Typeface choices. */
    FONT,

    /** Size, color, outline, shadow, background, and alignment. */
    STYLE,

    /** Entrance and exit animations. */
    ANIMATION,
}

/** The sheet edits text clip [clipId] and opens on [tab]; [isNew] when the clip was added just for it. */
data class TextEditRequest(
    val clipId: String,
    val tab: TextSheetTab,
    val isNew: Boolean,
)

/** Which text clip the sheet edits, if any; adds text through [viewModel] with [defaultText] as its words. */
@Stable
internal class TextEditing(
    private val viewModel: EditorViewModel,
    private val defaultText: String,
) {
    /** The open sheet, or null when it is closed. */
    var request: TextEditRequest? by mutableStateOf(null)

    /** Adds text to [project] at [positionMicros], selects it, and opens the sheet on it. */
    fun add(
        project: Project,
        positionMicros: Long,
    ) {
        val command = TextPlacement.propose(project, positionMicros, TextContent(defaultText), viewModel.idGenerator)
        if (command != null) {
            viewModel.dispatch(command, command.clip.id)
            request = TextEditRequest(command.clip.id, TextSheetTab.STYLE, isNew = true)
        }
    }

    /** Opens the sheet on [tab] for text clip [clipId]; does nothing without a clip. */
    fun open(
        clipId: String?,
        tab: TextSheetTab,
    ) {
        if (clipId != null) request = TextEditRequest(clipId, tab, isNew = false)
    }

    /**
     * Closes the sheet for [open], whose clip held [original], saving [edited] as one edit when it changed. Blank
     * text ([edited] null) removes a clip added for this sheet and keeps an existing clip's words.
     */
    fun close(
        open: TextEditRequest,
        original: TextContent,
        edited: TextContent?,
    ) {
        request = null
        when {
            edited == null && open.isNew -> viewModel.dispatch(RemoveTextClip(open.clipId))
            edited != null && edited != original -> viewModel.dispatch(SetTextContent(open.clipId, edited))
        }
    }

    /** Closes the sheet without saving, for when its clip is gone. */
    fun dismiss() {
        request = null
    }
}

/** Remembers the text sheet state for [viewModel]. */
@Composable
internal fun rememberTextEditing(viewModel: EditorViewModel): TextEditing {
    val defaultText = stringResource(R.string.editor_text_default)
    return remember(viewModel, defaultText) { TextEditing(viewModel, defaultText) }
}

/**
 * Shows the text sheet [editing] asks for over [project]. A newly added clip may not be saved yet, so the sheet
 * waits for it; a clip that disappears while the sheet is open (an undo) closes it.
 */
@Composable
internal fun TextEditHost(
    editing: TextEditing,
    project: Project,
) {
    val request = editing.request ?: return
    val clip = project.tracks.firstNotNullOfOrNull { track -> track.clips.firstOrNull { it.id == request.clipId } }
    val content = clip?.text
    var seen by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request, content == null) {
        if (content != null) {
            seen = true
        } else if (seen) {
            editing.dismiss()
        }
    }
    if (clip != null && content != null) {
        key(request) {
            TextEditSheet(
                content = content,
                clipDurationMicros = clip.durationMicros,
                startTab = request.tab,
                onClose = { editing.close(request, content, it) },
            )
        }
    }
}
