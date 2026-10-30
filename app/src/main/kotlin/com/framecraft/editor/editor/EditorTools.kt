/*
 * Wires the editor's tools zone and preview overlay and text handles to the view model: availability, actions, and
 * pickers.
 */
package com.framecraft.editor.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import com.framecraft.editor.R
import com.framecraft.editor.domain.command.DeleteClip
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.command.RemoveAudioClip
import com.framecraft.editor.domain.command.RemoveOverlayClip
import com.framecraft.editor.domain.command.RemoveTextClip
import com.framecraft.editor.domain.command.SetClipAudio
import com.framecraft.editor.domain.command.SetOverlayTransform
import com.framecraft.editor.domain.command.SplitClip
import com.framecraft.editor.domain.edit.AudioPlacement
import com.framecraft.editor.domain.edit.CaptionPlanner
import com.framecraft.editor.domain.edit.CaptionScope
import com.framecraft.editor.domain.edit.OverlayPlacement
import com.framecraft.editor.domain.edit.TextPlacement
import com.framecraft.editor.domain.model.Clip
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.OverlayTransform
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.TextContent
import com.framecraft.editor.domain.model.TrackKind
import com.framecraft.editor.domain.model.takesLook
import com.framecraft.editor.engine.ProjectPreview
import com.framecraft.editor.engine.text.TextBox
import com.framecraft.editor.engine.text.TextLayouter
import kotlin.math.roundToInt

/** MIME types the music picker offers. */
private const val AUDIO_MIME_TYPES = "audio/*"

/** Canvas height text is measured at for its handles; only the proportions matter. */
private const val MEASURE_HEIGHT = 1080

/** Preview aspect ratio used when a project has no visual clip. */
private const val DEFAULT_ASPECT_RATIO = 16f / 9f

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

/**
 * What the add, text, speed, look, mask, chroma key, mixer, and voiceover tools and the cut badges open: [overlay]
 * picks photos and videos, [audio] picks sound files, [text] holds the text sheet, [speed] the speed sheet,
 * [transition] the transition sheet, [picture] the look sheet and the mask and chroma key panels, [mixer] the mixer
 * sheet, and [voiceover] starts the recorder.
 */
internal class MediaPickers(
    val overlay: () -> Unit,
    val audio: () -> Unit,
    val text: TextEditing,
    val speed: SpeedEditing,
    val transition: TransitionEditing,
    val picture: PictureEditing,
    val mixer: MixerEditing,
    val voiceover: VoiceoverStarter,
)

/**
 * Returns the overlay and music pickers, the text, speed, transition, look, and mixer sheets, the mask and chroma key
 * panels, and the voiceover recorder of [viewModel], adding at [preview]'s playhead.
 */
@Composable
internal fun rememberMediaPickers(
    viewModel: EditorViewModel,
    preview: ProjectPreview,
): MediaPickers =
    MediaPickers(
        overlay = rememberOverlayPicker(viewModel.overlays, preview),
        audio = rememberAudioPicker(viewModel.audio, preview),
        text = rememberTextEditing(viewModel),
        speed = rememberSpeedEditing(),
        transition = rememberTransitionEditing(),
        picture = rememberPictureEditing(),
        mixer = rememberMixerEditing(),
        voiceover = rememberVoiceoverStarter(viewModel.voiceover),
    )

/**
 * Returns which tools apply to [selected], which sits on a track of [selectedKind], at [positionMicros]. Overlays
 * can be added when the project has a base clip, text when a text lane is free at the playhead too, a voiceover
 * when an audio lane is, and captions when a clip has sound or captions exist; the picker, recorder, and caption
 * tools wait while a pick is still [importing].
 */
internal fun toolAvailability(
    project: Project,
    selected: Clip?,
    selectedKind: TrackKind?,
    positionMicros: Long,
    importing: Boolean,
): ToolAvailability {
    val asset = selected?.let(project::assetOf)
    val placed = selected?.takeIf { selectedKind?.isPlaced == true }
    val isVideo = asset?.kind == MediaKind.VIDEO && selectedKind != TrackKind.AUDIO
    val captionable = CaptionPlanner.sources(project, CaptionScope.ALL, null, includeMuted = true).isNotEmpty()
    val splittable = selected != null && SplitClip.isSplittable(selected, positionMicros, project.frameRate)
    return ToolAvailability(
        hasSelection = selected != null,
        canSplit = splittable && selected?.text?.caption == null,
        canAddOverlay = !importing && OverlayPlacement.baseTrack(project) != null,
        overlayOpacity = placed?.let { (it.overlayTransform ?: OverlayTransform()).opacity },
        selectedClipId = selected?.id,
        showAddOverlay = selectedKind != TrackKind.AUDIO,
        showAddText = selectedKind == null || selectedKind == TrackKind.VIDEO,
        canAddText = !importing && TextPlacement.canPlace(project, positionMicros),
        isText = selectedKind == TrackKind.TEXT,
        canAddAudio = !importing,
        showExtract = isVideo,
        showSpeed = isVideo,
        showLook = selectedKind?.takesLook == true,
        maskActive = selected?.mask != null,
        chromaKeyActive = selected?.chromaKey != null,
        canExtract = isVideo && selected != null && AudioPlacement.canExtract(project, selected),
        audio = selected?.takeIf { asset?.hasAudio == true }?.let(::clipAudioOf),
        canRecord = !importing && AudioPlacement.canRecord(project, positionMicros),
        canCaption = !importing && (CaptionPlanner.hasCaptions(project) || captionable),
    )
}

/** Returns the sound settings of [clip]. */
private fun clipAudioOf(clip: Clip): ClipAudio =
    ClipAudio(
        volumePercent = clip.volumePercent,
        fadeInMicros = clip.fadeInMicros,
        fadeOutMicros = clip.fadeOutMicros,
        durationMicros = clip.durationMicros,
        autoDuckPercent = clip.autoDuckPercent,
        voiceover = clip.voiceover,
    )

/**
 * Tools for the [selected] clip on a track of [selectedKind]: split at [positionMicros], delete, opacity, extract,
 * sound, the text and speed sheets, and the look sheet and mask and chroma key panels, plus the add tools that open
 * [pickers] or add text at
 * [positionMicros]. The clip tools do nothing without a selection.
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
                val command =
                    SetClipAudio(
                        clipId = it.id,
                        volumePercent = audio.volumePercent,
                        fadeInMicros = audio.fadeInMicros,
                        fadeOutMicros = audio.fadeOutMicros,
                        autoDuckPercent = audio.autoDuckPercent,
                    )
                viewModel.dispatch(command)
            }
        },
        onAddText = {
            (viewModel.state.value as? EditorState.Ready)?.project?.let { pickers.text.add(it, positionMicros) }
        },
        onEditText = { pickers.text.open(selected?.id, TextSheetTab.STYLE) },
        onAnimateText = { pickers.text.open(selected?.id, TextSheetTab.ANIMATION) },
        onOpenSpeed = { pickers.speed.open(selected?.id) },
        onOpenLook = { pickers.picture.look.open(selected?.id) },
        onOpenMask = { pickers.picture.openMask(selected?.id) },
        onOpenChromaKey = { pickers.picture.openChromaKey(selected?.id) },
        onOpenMixer = pickers.mixer::open,
        onRecordVoiceover = { pickers.voiceover.start() },
        onOpenCaptions = viewModel.captions::openSheet,
    )

/** Returns the command that removes clip [clipId] from a track of [kind]. */
private fun deleteCommand(
    clipId: String,
    kind: TrackKind?,
): EditCommand =
    when (kind) {
        TrackKind.OVERLAY -> RemoveOverlayClip(clipId)
        TrackKind.AUDIO -> RemoveAudioClip(clipId)
        TrackKind.TEXT -> RemoveTextClip(clipId)
        TrackKind.VIDEO, null -> DeleteClip(clipId)
    }

/**
 * Returns the handles layer for overlay or text [clip] of [project]; a finished gesture goes to [dispatch] as one
 * placement change, and [haptics] ticks when rotation snaps. A text box matches the text's laid-out size on the
 * project canvas, and double-tapping it calls [onEditText].
 */
internal fun overlayLayer(
    clip: Clip,
    project: Project,
    dispatch: (EditCommand, String?) -> Unit,
    haptics: HapticFeedback,
    onEditText: () -> Unit,
): @Composable (Modifier) -> Unit =
    { layerModifier ->
        key(clip.id) {
            val text = clip.text
            val aspect = project.assetOf(clip).displayAspectRatio
            val canvasAspect = canvasAspectRatio(project)
            val box = remember(text, canvasAspect) { text?.let { measureText(it, canvasAspect) } }
            val description = if (text == null) R.string.editor_overlay_box else R.string.editor_text_box
            OverlayTransformLayer(
                transform = clip.overlayTransform ?: OverlayTransform(),
                boxSize = { canvas, scale ->
                    box?.let { OverlayGeometry.textBoxSize(canvas, it.widthFraction, it.heightFraction, scale) }
                        ?: OverlayGeometry.boxSize(canvas, aspect, scale)
                },
                onCommit = { dispatch(SetOverlayTransform(clip.id, it), null) },
                onSnap = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                modifier = layerModifier,
                description = stringResource(description),
                onDoubleTap = onEditText.takeIf { text != null },
                doubleTapLabel = stringResource(R.string.editor_text_edit_description),
            )
        }
    }

/** Returns the share of a canvas of [canvasAspect] (width over height) that [content] takes at scale 1. */
private fun measureText(
    content: TextContent,
    canvasAspect: Float,
): TextBox = TextLayouter.measure(content, (MEASURE_HEIGHT * canvasAspect).roundToInt(), MEASURE_HEIGHT)

/**
 * Returns the preview aspect ratio: the chosen canvas shape, or for the original shape the first base clip's, else the
 * first overlay's, else the default.
 */
internal fun canvasAspectRatio(project: Project): Float {
    val first =
        project.tracks
            .filter { it.kind == TrackKind.VIDEO || it.kind == TrackKind.OVERLAY }
            .sortedBy { it.kind != TrackKind.VIDEO }
            .firstNotNullOfOrNull { it.clips.firstOrNull() }
    return project.canvas.aspect.ratio ?: first?.let { project.assetOf(it).displayAspectRatio } ?: DEFAULT_ASPECT_RATIO
}
