/*
 * The editor's contextual tools zone: a scrolling row of tools for the selection, the inline opacity and volume
 * panels, and the fade sheet.
 */
package com.framecraft.editor.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.MAX_VOLUME_PERCENT
import com.framecraft.editor.domain.model.UNITY_VOLUME_PERCENT
import com.framecraft.editor.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Test tag of the add audio tool. */
const val TOOL_AUDIO_TAG = "editor_tool_audio"

/** Test tag of the extract audio tool. */
const val TOOL_EXTRACT_TAG = "editor_tool_extract"

/** Test tag of the volume tool. */
const val TOOL_VOLUME_TAG = "editor_tool_volume"

/** Test tag of the fade tool. */
const val TOOL_FADE_TAG = "editor_tool_fade"

/** Test tag of the ducking tool. */
const val TOOL_DUCK_TAG = "editor_tool_duck"

/** Test tag of the voiceover tool. */
const val TOOL_VOICEOVER_TAG = "editor_tool_voiceover"

/** Test tag of the mixer tool. */
const val TOOL_MIXER_TAG = "editor_tool_mixer"

/** Test tag of the volume slider. */
const val VOLUME_SLIDER_TAG = "editor_volume_slider"

/** Test tag of the mute toggle. */
const val MUTE_TOGGLE_TAG = "editor_mute_toggle"

/** Test tag of the fade-in slider. */
const val FADE_IN_SLIDER_TAG = "editor_fade_in_slider"

/** Test tag of the fade-out slider. */
const val FADE_OUT_SLIDER_TAG = "editor_fade_out_slider"

/** Longest fade the sliders offer. */
private const val MAX_FADE_MICROS = 10_000_000L

/** Microseconds in a second. */
private const val MICROS_PER_SECOND = 1_000_000f

/** Divides a percent into a 0 to 1 fraction. */
private const val PERCENT_SCALE = 100f

/** Opacity of a disabled tool. */
private const val DISABLED_ALPHA = 0.38f

/** Size of a tool button in the tools zone. */
private val TOOL_SIZE = 64.dp

/**
 * Sound of the selected clip: [volumePercent], [fadeInMicros], and [fadeOutMicros] within [durationMicros], and the
 * [autoDuckPercent] it drops by under voiceovers unless it is a [voiceover] itself.
 */
data class ClipAudio(
    val volumePercent: Int,
    val fadeInMicros: Long,
    val fadeOutMicros: Long,
    val durationMicros: Long,
    val autoDuckPercent: Int = 0,
    val voiceover: Boolean = false,
)

/** What replaces or covers the tool row. */
private enum class ToolPanel {
    /** The tool row itself. */
    NONE,

    /** The inline volume panel. */
    VOLUME,

    /** The fade sheet over the row. */
    FADE,

    /** The ducking sheet over the row. */
    DUCK,
}

/**
 * Contextual tools for the selection in [availability], doing [tools]. The row scrolls sideways; Volume swaps it
 * for an inline panel and Fade opens a sheet, both closing when the selection changes.
 */
@Composable
internal fun ToolsZone(
    availability: ToolAvailability,
    tools: ToolActions,
    modifier: Modifier = Modifier,
) {
    var panel by rememberSaveable(availability.selectedClipId) { mutableStateOf(ToolPanel.NONE) }
    val audio = availability.audio
    val rowModifier =
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md)
    if (panel == ToolPanel.VOLUME && audio != null) {
        VolumePanel(audio, tools.onAudioCommit, onBack = { panel = ToolPanel.NONE }, modifier = rowModifier)
    } else {
        Row(
            modifier = rowModifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolRow(availability, tools, onOpen = { panel = it })
        }
    }
    if (panel == ToolPanel.FADE && audio != null) {
        FadeSheet(audio, tools.onAudioCommit, onDismiss = { panel = ToolPanel.NONE })
    }
    if (panel == ToolPanel.DUCK && audio != null) {
        DuckingSheet(audio, tools.onAudioCommit, onDismiss = { panel = ToolPanel.NONE })
    }
}

/** The tools that apply to [availability], in order; panel tools call [onOpen]. */
@Composable
private fun ToolRow(
    availability: ToolAvailability,
    tools: ToolActions,
    onOpen: (ToolPanel) -> Unit,
) {
    val canvas = availability.canvas
    if (canvas.isBase) {
        ToolButton(if (canvas.fill) Tool.FILL else Tool.FIT, tools.onToggleFit, selected = canvas.fill)
        ToolButton(Tool.ADJUST, tools.onToggleAdjust, selected = canvas.adjusting)
    }
    if (availability.isText) {
        ToolButton(Tool.EDIT_TEXT, tools.onEditText)
        ToolButton(Tool.ANIMATE_TEXT, tools.onAnimateText)
    }
    val opacity = availability.overlayOpacity
    if (opacity != null) {
        OpacitySlider(opacity, tools.onOpacityCommit, Modifier.width(OPACITY_WIDTH))
    } else if (availability.showAddOverlay) {
        ToolButton(Tool.OVERLAY, tools.onAddOverlay, availability.canAddOverlay)
    }
    if (availability.showAddText) {
        ToolButton(Tool.TEXT, tools.onAddText, availability.canAddText)
    }
    if (!availability.hasSelection) {
        ToolButton(Tool.AUDIO, tools.onAddAudio, availability.canAddAudio)
        ToolButton(Tool.VOICEOVER, tools.onRecordVoiceover, availability.canRecord)
        ToolButton(Tool.MIXER, tools.onOpenMixer)
    }
    if (canvas.show) {
        ToolButton(Tool.CANVAS, tools.onOpenCanvas, canvas.enabled)
    }
    ClipToolButtons(availability, tools, onOpen)
}

/** The tools of the selected clip that apply to [availability], in order; panel tools call [onOpen]. */
@Composable
private fun ClipToolButtons(
    availability: ToolAvailability,
    tools: ToolActions,
    onOpen: (ToolPanel) -> Unit,
) {
    if (availability.showSpeed) {
        ToolButton(Tool.SPEED, tools.onOpenSpeed)
    }
    if (availability.showLook) {
        ToolButton(Tool.LOOK, tools.onOpenLook)
        ToolButton(Tool.MASK, tools.onOpenMask, selected = availability.maskActive)
        ToolButton(Tool.CHROMA, tools.onOpenChromaKey, selected = availability.chromaKeyActive)
    }
    if (availability.showExtract) {
        ToolButton(Tool.EXTRACT, tools.onExtractAudio, availability.canExtract)
    }
    val audio = availability.audio
    if (audio != null) {
        ToolButton(Tool.VOLUME, { onOpen(ToolPanel.VOLUME) })
        ToolButton(Tool.FADE, { onOpen(ToolPanel.FADE) })
        if (!audio.voiceover) ToolButton(Tool.DUCK, { onOpen(ToolPanel.DUCK) }, selected = audio.autoDuckPercent > 0)
    }
    if (availability.hasSelection) {
        ToolButton(Tool.SPLIT, tools.onSplitClip, availability.canSplit)
        ToolButton(Tool.DELETE, tools.onDeleteClip)
    }
}

/** Width of the inline opacity slider inside the scrolling row. */
private val OPACITY_WIDTH = 200.dp

/**
 * Labelled opacity slider for the selected overlay, starting at [opacity]. Dragging moves only the slider; the value
 * goes to [onCommit] once, when the drag ends.
 */
@Composable
private fun OpacitySlider(
    opacity: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(opacity) { mutableFloatStateOf(opacity) }
    val label = stringResource(R.string.editor_tool_opacity)
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.editor_opacity_value, (draft * PERCENT_SCALE).roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) },
            modifier = Modifier.testTag(TOOL_OPACITY_TAG).semantics { contentDescription = label },
        )
    }
}

/**
 * Back, a 0 to 200% volume slider starting at [audio]'s volume, and a mute toggle that switches between silence and
 * the last audible volume. Each finished change goes to [onCommit] once.
 */
@Composable
private fun VolumePanel(
    audio: ClipAudio,
    onCommit: (ClipAudio) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(audio.volumePercent) { mutableIntStateOf(audio.volumePercent) }
    var lastAudible by remember { mutableIntStateOf(audio.volumePercent.takeIf { it > 0 } ?: UNITY_VOLUME_PERCENT) }
    val label = stringResource(R.string.editor_volume_label)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.editor_tools_back))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.editor_volume_value, draft),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = draft.toFloat(),
                onValueChange = { draft = it.roundToInt() },
                onValueChangeFinished = {
                    if (draft > 0) lastAudible = draft
                    onCommit(audio.copy(volumePercent = draft))
                },
                valueRange = 0f..MAX_VOLUME_PERCENT.toFloat(),
                modifier = Modifier.testTag(VOLUME_SLIDER_TAG).semantics { contentDescription = label },
            )
        }
        val muted = draft == 0
        IconButton(
            onClick = {
                if (!muted) lastAudible = draft
                draft = if (muted) lastAudible else 0
                onCommit(audio.copy(volumePercent = draft))
            },
            modifier = Modifier.testTag(MUTE_TOGGLE_TAG),
        ) {
            val icon = if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume
            val description = if (muted) R.string.editor_unmute else R.string.editor_mute
            Icon(painterResource(icon), stringResource(description))
        }
    }
}

/** Sheet with fade-in and fade-out sliders for [audio]; each finished change goes to [onCommit] once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FadeSheet(
    audio: ClipAudio,
    onCommit: (ClipAudio) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Text(
                text = stringResource(R.string.editor_fade_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FadeSlider(
                labelRes = R.string.editor_fade_in,
                valueMicros = audio.fadeInMicros,
                maxMicros = minOf(MAX_FADE_MICROS, audio.durationMicros - audio.fadeOutMicros),
                onCommit = { onCommit(audio.copy(fadeInMicros = it)) },
                modifier = Modifier.testTag(FADE_IN_SLIDER_TAG),
            )
            FadeSlider(
                labelRes = R.string.editor_fade_out,
                valueMicros = audio.fadeOutMicros,
                maxMicros = minOf(MAX_FADE_MICROS, audio.durationMicros - audio.fadeInMicros),
                onCommit = { onCommit(audio.copy(fadeOutMicros = it)) },
                modifier = Modifier.testTag(FADE_OUT_SLIDER_TAG),
            )
        }
    }
}

/**
 * Labelled slider for one fade from 0 to [maxMicros], starting at [valueMicros] and shown in tenths of a second;
 * disabled when no room is left. The finished value goes to [onCommit].
 */
@Composable
private fun FadeSlider(
    @StringRes labelRes: Int,
    valueMicros: Long,
    maxMicros: Long,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxSeconds = maxMicros.coerceAtLeast(0L) / MICROS_PER_SECOND
    var draft by remember(valueMicros) { mutableFloatStateOf(valueMicros / MICROS_PER_SECOND) }
    val label = stringResource(labelRes)
    val locale = LocalConfiguration.current.locales[0]
    Spacer(Modifier.height(Spacing.md))
    Text(
        text = stringResource(R.string.editor_fade_value, label, String.format(locale, "%.1f", draft)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Slider(
        value = draft.coerceIn(0f, maxSeconds),
        onValueChange = { draft = it },
        onValueChangeFinished = {
            onCommit((draft * MICROS_PER_SECOND).roundToLong().coerceIn(0L, maxMicros.coerceAtLeast(0L)))
        },
        valueRange = 0f..maxSeconds.coerceAtLeast(MIN_SLIDER_SECONDS),
        enabled = maxSeconds > 0f,
        modifier = modifier.semantics { contentDescription = label },
    )
}

/** Smallest slider range, so a slider with no room left still has a valid range. */
private const val MIN_SLIDER_SECONDS = 0.1f

/** A tool in the row: its [iconRes], [labelRes], the [descriptionRes] TalkBack reads, and its test [tag]. */
private enum class Tool(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val tag: String,
) {
    /** Switches the selected base clip, which fits the canvas, to fill it. */
    FIT(
        R.drawable.ic_fit_screen,
        R.string.editor_tool_fit_fill,
        R.string.editor_tool_fit_fill_description,
        TOOL_FIT_FILL_TAG,
    ),

    /** Switches the selected base clip, which fills the canvas, to fit inside it. */
    FILL(
        R.drawable.ic_crop_free,
        R.string.editor_tool_fit_fill,
        R.string.editor_tool_fit_fill_description,
        TOOL_FIT_FILL_TAG,
    ),

    /** Shows or hides the selected base clip's placement handles. */
    ADJUST(R.drawable.ic_adjust, R.string.editor_tool_adjust, R.string.editor_tool_adjust_description, TOOL_ADJUST_TAG),

    /** Opens the canvas sheet. */
    CANVAS(R.drawable.ic_canvas, R.string.editor_tool_canvas, R.string.editor_tool_canvas_description, TOOL_CANVAS_TAG),

    /** Adds an overlay at the playhead. */
    OVERLAY(
        R.drawable.ic_overlay,
        R.string.editor_tool_overlay,
        R.string.editor_tool_overlay_description,
        TOOL_OVERLAY_TAG,
    ),

    /** Adds text at the playhead. */
    TEXT(R.drawable.ic_text, R.string.editor_tool_text, R.string.editor_tool_text_description, TOOL_TEXT_TAG),

    /** Opens the text sheet on the style tab. */
    EDIT_TEXT(
        R.drawable.ic_edit,
        R.string.editor_tool_edit_text,
        R.string.editor_text_edit_description,
        TOOL_EDIT_TEXT_TAG,
    ),

    /** Opens the text sheet on the animation tab. */
    ANIMATE_TEXT(
        R.drawable.ic_animation,
        R.string.editor_tool_animate_text,
        R.string.editor_tool_animate_text_description,
        TOOL_ANIMATE_TEXT_TAG,
    ),

    /** Adds music at the playhead. */
    AUDIO(R.drawable.ic_music, R.string.editor_tool_audio, R.string.editor_tool_audio_description, TOOL_AUDIO_TAG),

    /** Opens the speed sheet for the selected video. */
    SPEED(R.drawable.ic_speed, R.string.editor_tool_speed, R.string.editor_tool_speed_description, TOOL_SPEED_TAG),

    /** Opens the look sheet for the selected picture. */
    LOOK(R.drawable.ic_look, R.string.editor_tool_look, R.string.editor_tool_look_description, TOOL_LOOK_TAG),

    /** Opens the mask panel for the selected picture. */
    MASK(R.drawable.ic_mask, R.string.editor_tool_mask, R.string.editor_tool_mask_description, TOOL_MASK_TAG),

    /** Opens the chroma key panel for the selected picture. */
    CHROMA(
        R.drawable.ic_chroma,
        R.string.editor_tool_chroma,
        R.string.editor_tool_chroma_description,
        TOOL_CHROMA_TAG,
    ),

    /** Lifts the selected video's sound onto an audio lane. */
    EXTRACT(
        R.drawable.ic_extract_audio,
        R.string.editor_tool_extract,
        R.string.editor_tool_extract_description,
        TOOL_EXTRACT_TAG,
    ),

    /** Opens the volume panel. */
    VOLUME(
        R.drawable.ic_volume,
        R.string.editor_tool_volume,
        R.string.editor_tool_volume_description,
        TOOL_VOLUME_TAG,
    ),

    /** Opens the fade sheet. */
    FADE(R.drawable.ic_fade, R.string.editor_tool_fade, R.string.editor_tool_fade_description, TOOL_FADE_TAG),

    /** Opens the ducking sheet. */
    DUCK(R.drawable.ic_volume_down, R.string.editor_tool_duck, R.string.editor_tool_duck_description, TOOL_DUCK_TAG),

    /** Opens the voiceover recorder at the playhead. */
    VOICEOVER(
        R.drawable.ic_mic,
        R.string.editor_tool_voiceover,
        R.string.editor_tool_voiceover_description,
        TOOL_VOICEOVER_TAG,
    ),

    /** Opens the mixer sheet. */
    MIXER(R.drawable.ic_tune, R.string.editor_tool_mixer, R.string.editor_tool_mixer_description, TOOL_MIXER_TAG),

    /** Splits the selection at the playhead. */
    SPLIT(
        R.drawable.ic_content_cut,
        R.string.editor_tool_split,
        R.string.editor_tool_split_description,
        TOOL_SPLIT_TAG,
    ),

    /** Deletes the selection. */
    DELETE(
        R.drawable.ic_delete,
        R.string.editor_tool_delete,
        R.string.editor_tool_delete_description,
        TOOL_DELETE_TAG,
    ),
}

/**
 * 64dp button for [tool] with its icon over its label; dimmed and inert when not [enabled], and highlighted when a
 * toggle tool is [selected].
 */
@Composable
private fun ToolButton(
    tool: Tool,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val description = stringResource(tool.descriptionRes)
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .testTag(tool.tag)
                .size(TOOL_SIZE)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .background(if (selected) colors.primaryContainer else Color.Transparent, MaterialTheme.shapes.small)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    this.selected = selected
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) colors.onPrimaryContainer else colors.onSurface
        Icon(painter = painterResource(tool.iconRes), contentDescription = null, tint = color)
        Spacer(Modifier.height(Spacing.xs))
        Text(text = stringResource(tool.labelRes), style = MaterialTheme.typography.labelLarge, color = color)
    }
}
