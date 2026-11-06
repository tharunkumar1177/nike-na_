/*
 * Device-test harness: writes still sources drawn at runtime, exports a project through the production plan and
 * composition path, and reads frames back from the exported file.
 */
package com.framecraft.editor.engine.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.frame.FrameExtractor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.test.platform.app.InstrumentationRegistry
import com.framecraft.editor.domain.export.ExportPlanner
import com.framecraft.editor.domain.export.ExportQuality
import com.framecraft.editor.domain.export.ExportResolution
import com.framecraft.editor.domain.export.ExportSize
import com.framecraft.editor.domain.model.MediaAsset
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.domain.model.SourceFingerprint
import com.framecraft.editor.engine.CompositionFactory
import com.framecraft.editor.engine.export.ExportEffects
import com.framecraft.editor.engine.plan.PlaybackPlanner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Longest an export of a test project may take on the emulator. */
private const val EXPORT_TIMEOUT_SECONDS = 180L

/** Longest one frame extraction may take. */
private const val FRAME_TIMEOUT_SECONDS = 60L

/** PNG quality argument; PNG is lossless, so the value is ignored but required. */
private const val PNG_QUALITY = 100

/** An exported video and the size it was encoded at. */
internal class RenderedVideo(
    val file: File,
    val size: ExportSize,
)

/**
 * Exports test projects to files under the app cache and extracts their frames. Media3 objects are created and
 * started on the main thread, as the production exporter and thumbnails do; the calling test thread only waits.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class RenderHarness(
    private val context: Context,
) {
    private val dir =
        File(context.cacheDir, "render-test").apply {
            deleteRecursively()
            mkdirs()
        }

    /** Writes a [width] by [height] PNG of one opaque [argb] color named [id] and returns it as an image asset. */
    fun solidImage(
        id: String,
        width: Int,
        height: Int,
        argb: Int,
    ): MediaAsset {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }
        val file = File(dir, "$id.png")
        file.outputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) }
        bitmap.recycle()
        return MediaAsset(
            id = id,
            uri = Uri.fromFile(file).toString(),
            kind = MediaKind.IMAGE,
            durationMicros = null,
            width = width,
            height = height,
            rotationDegrees = 0,
            hasAudio = false,
            fingerprint = SourceFingerprint(file.length(), file.lastModified()),
        )
    }

    /**
     * Exports [project] at [resolution] exactly as the editor does: plan, composition with export effects, then
     * H.264 through Transformer. Throws when the export fails or does not finish in time.
     */
    fun export(
        project: Project,
        resolution: ExportResolution = ExportResolution.HD_720,
    ): RenderedVideo {
        val size = ExportPlanner.outputSize(project, resolution)
        val composition =
            CompositionFactory.create(PlaybackPlanner.plan(project), ExportEffects.create(size, project.frameRate))
        val output = File(dir, "${project.id}.mp4").apply { delete() }
        val bitrate = ExportPlanner.targetBitrateBps(size, project.frameRate, ExportQuality.HIGH)
        val done = CountDownLatch(1)
        val failure = AtomicReference<ExportException?>(null)
        val transformer = AtomicReference<Transformer?>(null)
        onMain {
            val next = buildTransformer(bitrate, done, failure)
            transformer.set(next)
            next.start(composition, output.absolutePath)
        }
        if (!done.await(EXPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            onMain { transformer.get()?.cancel() }
            error("Export of ${project.id} did not finish within $EXPORT_TIMEOUT_SECONDS s")
        }
        failure.get()?.let { throw AssertionError("Export of ${project.id} failed: ${it.errorCodeName}", it) }
        check(output.length() > 0) { "Export of ${project.id} wrote an empty file" }
        return RenderedVideo(output, size)
    }

    /** Returns software bitmaps of [video] at each of [timesMillis], decoded with exact seeking. */
    fun framesAt(
        video: RenderedVideo,
        timesMillis: List<Long>,
    ): List<Bitmap> {
        val extractor = AtomicReference<FrameExtractor?>(null)
        onMain {
            extractor.set(FrameExtractor.Builder(context, MediaItem.fromUri(Uri.fromFile(video.file))).build())
        }
        try {
            return timesMillis.map { frameAt(checkNotNull(extractor.get()), it) }
        } finally {
            onMain { extractor.get()?.close() }
        }
    }

    /** Requests the frame at [timeMillis] from [extractor] on the main thread and returns it as a software bitmap. */
    private fun frameAt(
        extractor: FrameExtractor,
        timeMillis: Long,
    ): Bitmap {
        val future = AtomicReference<ListenableFuture<FrameExtractor.Frame>>()
        onMain { future.set(extractor.getFrame(timeMillis)) }
        val bitmap = future.get().get(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS).bitmap
        return if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    }

    /** Returns an H.264 transformer at [bitrate] that counts down [done] and records any [failure]. */
    private fun buildTransformer(
        bitrate: Int,
        done: CountDownLatch,
        failure: AtomicReference<ExportException?>,
    ): Transformer {
        val videoSettings = VideoEncoderSettings.Builder().setBitrate(bitrate).build()
        val encoders =
            DefaultEncoderFactory
                .Builder(context)
                .setRequestedVideoEncoderSettings(videoSettings)
                .setEnableFallback(true)
                .build()
        val listener =
            object : Transformer.Listener {
                /** Releases the waiting test. */
                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult,
                ) {
                    done.countDown()
                }

                /** Records why the export failed and releases the waiting test. */
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    failure.set(exportException)
                    done.countDown()
                }
            }
        return Transformer
            .Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoders)
            .addListener(listener)
            .build()
    }

    /** Runs [block] on the main thread and waits for it. */
    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
