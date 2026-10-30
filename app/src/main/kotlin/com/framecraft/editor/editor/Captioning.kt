/*
 * Runs auto-captioning for the open project: the one-time speech model download, transcribing the chosen clips on
 * the device, and placing the captions as one undoable edit.
 */
package com.framecraft.editor.editor

import android.util.Log
import com.framecraft.editor.captions.CaptionTranscriber
import com.framecraft.editor.captions.ModelInstallException
import com.framecraft.editor.captions.ModelInstallFailure
import com.framecraft.editor.captions.SpeechModelStore
import com.framecraft.editor.captions.SpeechStretch
import com.framecraft.editor.domain.command.EditCommand
import com.framecraft.editor.domain.edit.CaptionPlanner
import com.framecraft.editor.domain.edit.CaptionScope
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Log tag for captioning failures. */
private const val TAG = "Captioning"

/** The speech model on the device and the transcriber that reads clips with it. */
class CaptionServices(
    val modelStore: SpeechModelStore,
    val transcriber: CaptionTranscriber,
)

/** Why captions could not be made. */
enum class CaptionFailure {
    /** Not enough free storage for the speech model. */
    NO_SPACE,

    /** The speech model could not be downloaded. */
    NETWORK,

    /** The downloaded speech model was damaged. */
    CORRUPT,

    /** The speech engine could not run on this device. */
    ENGINE,
}

/** Where a caption run stands. */
sealed interface CaptionPhase {
    /** Nothing is running. */
    data object Idle : CaptionPhase

    /** The speech model is downloading, [progress] of the way from 0 to 1. */
    data class Downloading(
        val progress: Float,
    ) : CaptionPhase

    /** Clips are being transcribed, [progress] of the way from 0 to 1. */
    data class Generating(
        val progress: Float,
    ) : CaptionPhase

    /** The run finished and added [count] captions; zero means no speech was found. */
    data class Done(
        val count: Int,
    ) : CaptionPhase

    /** The run stopped because of [failure]. */
    data class Failed(
        val failure: CaptionFailure,
    ) : CaptionPhase
}

/** What a caption run covers: clips in [scope], with [selectedClipId] selected, and muted ones if [includeMuted]. */
data class CaptionRequest(
    val scope: CaptionScope,
    val selectedClipId: String?,
    val includeMuted: Boolean,
)

/**
 * Makes captions in [scope] with [services] for the project [currentProject] returns, sending the placing edit to
 * [dispatch] with ids from [idGenerator]. Must be used on the main thread.
 */
class Captioning(
    private val scope: CoroutineScope,
    private val services: CaptionServices,
    private val idGenerator: IdGenerator,
    private val currentProject: () -> Project?,
    private val dispatch: (EditCommand) -> Unit,
) {
    private val mutablePhase = MutableStateFlow<CaptionPhase>(CaptionPhase.Idle)
    private val mutableSheetOpen = MutableStateFlow(false)
    private var job: Job? = null

    /** Where the current run stands. */
    val phase: StateFlow<CaptionPhase> = mutablePhase.asStateFlow()

    /** True while the caption sheet shows. */
    val sheetOpen: StateFlow<Boolean> = mutableSheetOpen.asStateFlow()

    /** True when the speech model is already on the device. */
    val modelInstalled: Boolean
        get() = services.modelStore.isInstalled()

    /** Size of the one-time speech model download. */
    val downloadBytes: Long
        get() = services.modelStore.downloadBytes

    /** Shows the caption sheet, clearing the result of an earlier run. */
    fun openSheet() {
        if (job?.isActive != true) mutablePhase.value = CaptionPhase.Idle
        mutableSheetOpen.value = true
    }

    /** Hides the caption sheet; a running caption job is cancelled. */
    fun closeSheet() {
        cancel()
        mutableSheetOpen.value = false
    }

    /**
     * Downloads the speech model if needed, transcribes the clips [request] covers, and places their captions,
     * replacing any captions the project has. Ignored while a run is going.
     */
    @Suppress("TooGenericExceptionCaught")
    fun generate(request: CaptionRequest) {
        if (job?.isActive == true) return
        job =
            scope.launch {
                try {
                    ensureModel()
                    mutablePhase.value = CaptionPhase.Generating(0f)
                    mutablePhase.value = CaptionPhase.Done(captionsFor(request))
                } catch (e: CancellationException) {
                    mutablePhase.value = CaptionPhase.Idle
                    throw e
                } catch (e: ModelInstallException) {
                    mutablePhase.value = CaptionPhase.Failed(failureOf(e.failure))
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Speech engine is not available", e)
                    mutablePhase.value = CaptionPhase.Failed(CaptionFailure.ENGINE)
                } catch (e: Exception) {
                    // The native recognizer reports a model it cannot load or run through many exception types.
                    Log.w(TAG, "Captioning failed", e)
                    mutablePhase.value = CaptionPhase.Failed(CaptionFailure.ENGINE)
                }
            }
    }

    /** Removes every caption from the project as one edit. */
    fun removeAll() {
        currentProject()?.let(CaptionPlanner::removeAll)?.let(dispatch)
    }

    /** Stops a running download or transcription; nothing is placed. */
    fun cancel() {
        job?.cancel()
        job = null
        mutablePhase.value = CaptionPhase.Idle
    }

    /** Downloads the speech model when it is missing, reporting its progress. */
    private suspend fun ensureModel() {
        val store = services.modelStore
        if (!store.isInstalled()) {
            mutablePhase.value = CaptionPhase.Downloading(0f)
            store.install { mutablePhase.value = CaptionPhase.Downloading(it) }
        }
    }

    /**
     * Transcribes the clips [request] covers in the open project, places their captions over the project as it is
     * by then, and returns how many were placed.
     */
    private suspend fun captionsFor(request: CaptionRequest): Int {
        val project = currentProject() ?: return 0
        val stretches = stretchesOf(project, request)
        val words = services.transcriber.transcribe(stretches) { mutablePhase.value = CaptionPhase.Generating(it) }
        val plan = currentProject()?.let { CaptionPlanner.plan(it, words, CaptionPlanner.DEFAULT_STYLE, idGenerator) }
        plan?.takeIf { it.captionCount > 0 }?.let { dispatch(it.command) }
        return plan?.captionCount ?: 0
    }

    /** Returns the stretch of sound each clip [request] covers in [project] plays. */
    private fun stretchesOf(
        project: Project,
        request: CaptionRequest,
    ): List<SpeechStretch> =
        CaptionPlanner.sources(project, request.scope, request.selectedClipId, request.includeMuted).map { source ->
            val clip = source.clip
            SpeechStretch(clip.id, source.asset.uri, clip.trimStartMicros, clip.trimStartMicros + clip.sourceSpanMicros)
        }

    /** Maps an install [failure] to what the sheet explains. */
    private fun failureOf(failure: ModelInstallFailure): CaptionFailure =
        when (failure) {
            ModelInstallFailure.NO_SPACE -> CaptionFailure.NO_SPACE
            ModelInstallFailure.NETWORK -> CaptionFailure.NETWORK
            ModelInstallFailure.CORRUPT -> CaptionFailure.CORRUPT
        }
}
