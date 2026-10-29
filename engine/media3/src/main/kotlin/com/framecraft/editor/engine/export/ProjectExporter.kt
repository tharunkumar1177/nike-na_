/*
 * Exports a project snapshot to an H.264/AAC MP4 with Media3 Transformer and saves it to the gallery.
 */
package com.framecraft.editor.engine.export

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.framecraft.editor.domain.export.ExportPlanner
import com.framecraft.editor.domain.export.ExportSettings
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.engine.CompositionFactory
import com.framecraft.editor.engine.plan.PlaybackPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

/** How often encoding progress is sampled. */
private const val PROGRESS_INTERVAL_MS = 500L

/** Highest percent shown while encoding; 100 is reserved for saving to the gallery. */
private const val MAX_ENCODING_PERCENT = 99

/** Percent shown while the finished video is copied to the gallery. */
private const val SAVING_PERCENT = 100

/** Cache subdirectory holding the file being encoded. */
private const val WORK_DIR = "exports"

/** The file being encoded and the gallery name it will get. */
private class PendingOutput(
    val file: File,
    val displayName: String,
)

/**
 * Runs one export at a time from an immutable project snapshot, so editing can continue meanwhile. The video
 * is encoded to a cache file and then copied into the gallery. Must be created, used, and released on the
 * main thread.
 */
@OptIn(markerClass = [UnstableApi::class])
class ProjectExporter(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saver = GallerySaver(appContext)
    private val mutableState = MutableStateFlow<ExportState>(ExportState.Idle)
    private var transformer: Transformer? = null
    private var pending: PendingOutput? = null
    private var job: Job? = null

    /** Progress of the current or last export. */
    val state: StateFlow<ExportState> = mutableState.asStateFlow()

    private val listener =
        object : Transformer.Listener {
            /** Saves the encoded file to the gallery. */
            override fun onCompleted(
                composition: Composition,
                exportResult: ExportResult,
            ) {
                save()
            }

            /** Reports why encoding failed. */
            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                val reason = ExportFailureMapper.map(exportException.errorCode, exportException.cause)
                finish(ExportState.Failed(reason))
            }
        }

    /** Starts exporting [project] with [settings]; does nothing while another export is in progress. */
    fun export(
        project: Project,
        settings: ExportSettings,
    ) {
        if (mutableState.value.isInProgress) return
        mutableState.value = ExportState.Preparing
        val size = ExportPlanner.outputSize(project, settings.resolution)
        val estimate = ExportPlanner.estimateBytes(project, settings, project.durationMicros)
        val failure = ExportPrecheck.check(appContext, project.durationMicros, size, estimate)
        if (failure != null) {
            mutableState.value = ExportState.Failed(failure)
        } else {
            start(project, settings)
        }
    }

    /** Stops the current export and discards its output; does nothing when no export is in progress. */
    fun cancel() {
        if (mutableState.value.isInProgress) finish(ExportState.Cancelled)
    }

    /** Cancels any export and frees the encoder; the instance must not be used afterwards. */
    fun release() {
        cancel()
        scope.cancel()
    }

    /** Builds the composition and starts encoding it to a fresh cache file. */
    private fun start(
        project: Project,
        settings: ExportSettings,
    ) {
        val dir = File(appContext.cacheDir, WORK_DIR).apply { deleteRecursively() }
        dir.mkdirs()
        val displayName = GallerySaver.displayNameFor(project.name, LocalDateTime.now())
        val output = PendingOutput(File(dir, "export.mp4"), displayName)
        val size = ExportPlanner.outputSize(project, settings.resolution)
        val composition =
            CompositionFactory.create(PlaybackPlanner.plan(project), ExportEffects.create(size, settings.frameRate))
        val bitrate = ExportPlanner.targetBitrateBps(size, settings.frameRate, settings.quality)
        val next = buildTransformer(bitrate)
        pending = output
        transformer = next
        mutableState.value = ExportState.Running(0)
        next.start(composition, output.file.absolutePath)
        job = scope.launch { pollProgress(next) }
    }

    /** Returns a transformer writing H.264 at [bitrate] bits per second and AAC, falling back if unsupported. */
    private fun buildTransformer(bitrate: Int): Transformer {
        val videoSettings = VideoEncoderSettings.Builder().setBitrate(bitrate).build()
        val encoders =
            DefaultEncoderFactory
                .Builder(appContext)
                .setRequestedVideoEncoderSettings(videoSettings)
                .setEnableFallback(true)
                .build()
        return Transformer
            .Builder(appContext)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoders)
            .addListener(listener)
            .build()
    }

    /** Publishes encoding progress from [transformer] until the job is cancelled. */
    private suspend fun pollProgress(transformer: Transformer) {
        val holder = ProgressHolder()
        while (currentCoroutineContext().isActive) {
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                mutableState.value = ExportState.Running(holder.progress.coerceIn(0, MAX_ENCODING_PERCENT))
            }
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    /** Copies the encoded file into the gallery off the main thread and reports the result. */
    private fun save() {
        val output = pending ?: return
        job?.cancel()
        transformer = null
        mutableState.value = ExportState.Running(SAVING_PERCENT)
        job =
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { saveTo(output) } }
                finish(
                    result.fold(
                        onSuccess = { (uri, bytes) -> ExportState.Succeeded(uri.toString(), bytes) },
                        onFailure = { ExportState.Failed(ExportFailureMapper.mapSaveFailure(it)) },
                    ),
                )
            }
    }

    /** Saves [output] to the gallery and returns its URI and size, or removes it if cancelled meanwhile. */
    @Throws(IOException::class)
    private suspend fun saveTo(output: PendingOutput): Pair<Uri, Long> {
        val bytes = output.file.length()
        val uri = saver.save(output.file, output.displayName)
        if (!currentCoroutineContext().isActive) {
            saver.delete(uri)
            throw IOException("Export cancelled while saving")
        }
        return uri to bytes
    }

    /** Ends the export with [state], stopping the encoder and deleting the work file. */
    private fun finish(state: ExportState) {
        job?.cancel()
        job = null
        transformer?.cancel()
        transformer = null
        pending?.file?.delete()
        pending = null
        mutableState.value = state
    }
}
