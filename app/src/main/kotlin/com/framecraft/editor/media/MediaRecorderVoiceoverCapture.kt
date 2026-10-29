/*
 * Voiceover capture on the platform MediaRecorder: mono AAC takes in each project's recordings folder.
 */
package com.framecraft.editor.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import com.framecraft.editor.data.project.ProjectStore
import com.framecraft.editor.domain.model.AUDIO_ASSET_SIDE
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.SourceFingerprint
import java.io.File
import java.io.IOException

/** Free space a take needs before recording starts. */
private const val MIN_FREE_BYTES = 50L * 1024L * 1024L

/** Encoded bit rate of a take. */
private const val BIT_RATE = 128_000

/** Sample rate of a take. */
private const val SAMPLE_RATE = 44_100

/** Largest value [MediaRecorder.getMaxAmplitude] reports. */
private const val FULL_SCALE_AMPLITUDE = 32_767f

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1_000L

/**
 * Records takes with [MediaRecorder] into `recordings/` of each project directory under [projectsDir], naming takes
 * and assets with [idGenerator].
 */
class MediaRecorderVoiceoverCapture(
    private val context: Context,
    private val projectsDir: File,
    private val idGenerator: IdGenerator,
) : VoiceoverCapture {
    /** Opens the microphone into a new `take-<id>.m4a`; reports full storage before trying. */
    override fun begin(projectId: String): CaptureStart {
        val dir = File(File(projectsDir, projectId), ProjectStore.RECORDINGS_DIR_NAME)
        val ready = dir.isDirectory || dir.mkdirs()
        return when {
            !ready -> CaptureStart.Failed
            dir.usableSpace < MIN_FREE_BYTES -> CaptureStart.StorageFull
            else -> start(File(dir, "take-${idGenerator.next()}.m4a"))
        }
    }

    /** Starts a mono AAC recording into [file]; a recorder that fails to start is released and the file deleted. */
    @Suppress("TooGenericExceptionCaught")
    private fun start(file: File): CaptureStart {
        val recorder = newRecorder()
        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(BIT_RATE)
            recorder.setAudioSamplingRate(SAMPLE_RATE)
            recorder.setAudioChannels(1)
            recorder.setOutputFile(file)
            recorder.prepare()
            recorder.start()
            CaptureStart.Started(RecorderTake(recorder, file))
        } catch (ignored: IOException) {
            recorder.release()
            file.delete()
            CaptureStart.Failed
        } catch (ignored: RuntimeException) {
            // The recorder reports a busy or denied microphone as a bare RuntimeException.
            recorder.release()
            file.delete()
            CaptureStart.Failed
        }
    }

    /** Returns a recorder bound to this app's context where the platform supports it. */
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else legacyRecorder()

    /** Returns a recorder through the constructor deprecated in API 31. */
    @Suppress("DEPRECATION")
    private fun legacyRecorder(): MediaRecorder = MediaRecorder()

    /** A take recording into [file] through [recorder]. */
    private inner class RecorderTake(
        private val recorder: MediaRecorder,
        private val file: File,
    ) : CaptureTake {
        private var released = false

        /** Returns the peak amplitude since the previous call as a fraction of full scale. */
        override fun level(): Float {
            if (released) return 0f
            return (recorder.maxAmplitude / FULL_SCALE_AMPLITUDE).coerceIn(0f, 1f)
        }

        /** Stops recording and reads the take's length; a take that cannot be read is deleted. */
        override fun finish(): MediaAsset? {
            val stopped = stopAndRelease()
            val duration = if (stopped) durationOf(file) else null
            if (duration == null) {
                file.delete()
                return null
            }
            return MediaAsset(
                id = idGenerator.next(),
                uri = file.toURI().toString(),
                kind = MediaKind.AUDIO,
                durationMicros = duration,
                width = AUDIO_ASSET_SIDE,
                height = AUDIO_ASSET_SIDE,
                rotationDegrees = 0,
                hasAudio = true,
                fingerprint = SourceFingerprint(sizeBytes = file.length(), lastModifiedMillis = file.lastModified()),
            )
        }

        /** Stops recording if it still runs and deletes the take. */
        override fun discard() {
            stopAndRelease()
            file.delete()
        }

        /** Stops and releases the recorder once; returns whether it stopped with a usable file. */
        @Suppress("TooGenericExceptionCaught")
        private fun stopAndRelease(): Boolean {
            if (released) return file.length() > 0L
            released = true
            val stopped =
                try {
                    recorder.stop()
                    true
                } catch (ignored: RuntimeException) {
                    // stop() throws when no audio was captured yet.
                    false
                }
            recorder.release()
            return stopped
        }
    }
}

/** Returns the length of the sound in [file] in microseconds, or null when it cannot be read. */
@Suppress("TooGenericExceptionCaught")
private fun durationOf(file: File): Long? =
    try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { it * MICROS_PER_MILLI }
        }
    } catch (ignored: RuntimeException) {
        // setDataSource reports an unreadable file as a bare RuntimeException.
        null
    }
