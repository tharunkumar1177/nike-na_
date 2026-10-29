/*
 * Wires the editor's tools zone and preview overlay handles to the view model: availability, actions, and picker.
 */
package com.framecraft.editor.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.framecraft.editor.domain.command.DeleteClip
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.RemoveAudioClip
import com.framecraft.editor.domain.command.RemoveOverlayClip
import com.framecraft.editor.domain.command.SetClipAudio
import com.framecraft.editor.domain.command.SetOverlayTransform
import com.framecraft.editor.domain.command.SplitClip
import com.framecraft.editor.domain.edit.AudioPlacement
import com.framecraft.editor.domain.edit.OverlayPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.engine.ProjectPreview

/** MIME types the music picker offers. */
private const val AUDIO_MIME_TYPES = "audio/*"

/**
 * Returns an action that opens the photo and video picker and adds the picked item through [overlays] at
 * [preview]'s playhead when it returns.
 */
@Composable
internal fun rememberOverlayPicker(
    overlays: OverlayImport,
    preview: ProjectPreview,
): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { overlays.add(it.toString(), preview.state.value.positionMicros) }
        }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}

/**
 * Returns an action that opens the document picker for sound files and adds the picked file through [audio] at
 * [preview]'s playhead when it returns. The importer keeps a persistable read grant so the project can reopen it.
 */
@Composable
internal fun rememberAudioPicker(
    audio: AudioImport,
    preview: ProjectPreview,
): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { audio.add(it.toString(), preview.state.value.positionMicros) }
        }
    return { launcher.launch(arrayOf(AUDIO_MIME_TYPES)) }
}

/** The media pickers the add tools open: [overlay] for photos and videos and [audio] for sound files. */
internal class MediaPickers(
    val overlay: () -> Unit,
    val audio: () -> Unit,
)

/**
 * Returns which tools apply to [selected], which sits on a track of [selectedKind], at [positionMicros]. Overlays
 * can be added when the project has a base clip, and both add tools wait while a pick is still [importing].
 */
internal fun toolAvailability(
    project: Project,
    selected: Clip?,
    selectedKind: TrackKind?,
    positionMicros: Long,
    importing: Boolean,
): ToolAvailability {
    val asset = selected?.let(project::assetOf)
    val overlay = selected?.takeIf { selectedKind == TrackKind.OVERLAY }
    val isVideo = asset?.kind == MediaKind.VIDEO && selectedKind != TrackKind.AUDIO
    return ToolAvailability(
        hasSelection = selected != null,
        canSplit = selected != null && SplitClip.isSplittable(selected, positionMicros, project.frameRate),
        canAddOverlay = !importing && OverlayPlacement.baseTrack(project) != null,
        overlayOpacity = overlay?.let { (it.overlayTransform ?: OverlayTransform()).opacity },
        selectedClipId = selected?.id,
        showAddOverlay = selectedKind != TrackKind.AUDIO,
        canAddAudio = !importing,
        showExtract = isVideo,
        canExtract = isVideo && selected != null && AudioPlacement.canExtract(project, selected),
        audio =
            selected
                ?.takeIf { asset?.hasAudio == true }
                ?.let { ClipAudio(it.volumePercent, it.fadeInMicros, it.fadeOutMicros, it.durationMicros) },
    )
}

/**
 * Tools for the [selected] clip on a track of [selectedKind]: split at [positionMicros], delete, opacity, extract,
 * and sound, plus the add tools that open [pickers]. The clip tools do nothing without a selection.
 */
internal fun toolActions(
    viewModel: EditorViewModel,
    selected: Clip?,
    selectedKind: TrackKind?,
    positionMicros: Long,
    pickers: MediaPickers,
): ToolActions =
    ToolActions(
        onSplitClip = {
            selected?.let { viewModel.dispatch(SplitClip(it.id, positionMicros, viewModel.idGenerator.next())) }
        },
        onDeleteClip = { selected?.let { viewModel.dispatch(deleteCommand(it.id, selectedKind)) } },
        onAddOverlay = pickers.overlay,
        onOpacityCommit = { opacity ->
            selected?.let { clip ->
                val transform = (clip.overlayTransform ?: OverlayTransform()).copy(opacity = opacity.coerceIn(0f, 1f))
                viewModel.dispatch(SetOverlayTransform(clip.id, transform))
            }
        },
        onAddAudio = pickers.audio,
        onExtractAudio = {
            val project = (viewModel.state.value as? EditorState.Ready)?.project
            if (project != null && selected != null) {
                val command = AudioPlacement.proposeExtract(project, selected.id, viewModel.idGenerator)
                command?.let { viewModel.dispatch(it, it.clip.id) }
            }
        },
        onAudioCommit = { audio ->
            selected?.let {
                viewModel.dispatch(SetClipAudio(it.id, audio.volumePercent, audio.fadeInMicros, audio.fadeOutMicros))
            }
        },
    )

/** Returns the command that removes clip [clipId] from a track of [kind]. */
private fun deleteCommand(
    clipId: String,
    kind: TrackKind?,
): EditCommand =
    when (kind) {
        TrackKind.OVERLAY -> RemoveOverlayClip(clipId)
        TrackKind.AUDIO -> RemoveAudioClip(clipId)
        TrackKind.VIDEO, null -> DeleteClip(clipId)
    }

/**
 * Returns the handles layer for overlay [clip] of [project]; a finished gesture goes to [dispatch] as one placement
 * change, and [haptics] ticks when rotation snaps.
 */
internal fun overlayLayer(
    clip: Clip,
    project: Project,
    dispatch: (EditCommand, String?) -> Unit,
    haptics: HapticFeedback,
): @Composable (Modifier) -> Unit =
    { layerModifier ->
        key(clip.id) {
            OverlayTransformLayer(
                transform = clip.overlayTransform ?: OverlayTransform(),
                overlayAspect = project.assetOf(clip).displayAspectRatio,
                onCommit = { dispatch(SetOverlayTransform(clip.id, it), null) },
                onSnap = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                modifier = layerModifier,
            )
        }
    }
