/*
 * The on-device speech model: a one-time download of a pinned Vosk model, verified by size and SHA-256, unpacked
 * into app storage, and only then made visible with an atomic rename so a broken download is never used.
 */
package com.framecraft.editor.captions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** The pinned speech model: where it downloads from, its exact size and digest, and the folder it unpacks to. */
data class SpeechModelSpec(
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val folderName: String,
) {
    /** The models this build ships with. */
    companion object {
        /** The small US English Vosk model. */
        val ENGLISH =
            SpeechModelSpec(
                url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                sizeBytes = 41_205_931L,
                sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
                folderName = "vosk-model-small-en-us-0.15",
            )
    }
}

/** Why the speech model could not be installed. */
enum class ModelInstallFailure {
    /** The device lacks the free space the download and the unpacked model need. */
    NO_SPACE,

    /** The download could not be completed. */
    NETWORK,

    /** The downloaded file was not the pinned model. */
    CORRUPT,
}

/** Thrown when installing the speech model fails for [failure]. */
class ModelInstallException(
    val failure: ModelInstallFailure,
    cause: Throwable? = null,
) : IOException("Speech model install failed: $failure", cause)

/** How long to wait to connect to the model server. */
private const val CONNECT_TIMEOUT_MILLIS = 15_000

/** How long to wait for more of the model to arrive. */
private const val READ_TIMEOUT_MILLIS = 30_000

/** Bytes read from the network or the archive at a time. */
private const val BUFFER_BYTES = 64 * 1024

/** Free space needed to download and unpack the model side by side. */
const val MODEL_REQUIRED_FREE_BYTES: Long = 150L * 1024 * 1024

/** Marks a model folder whose unpacking finished. */
private const val COMPLETE_MARKER = ".complete"

/** Throws the install failure [failure]. */
private fun installFailure(failure: ModelInstallFailure): Nothing = throw ModelInstallException(failure)

/** Keeps the speech model described by [spec] under [root]. */
class SpeechModelStore(
    private val root: File,
    private val spec: SpeechModelSpec = SpeechModelSpec.ENGLISH,
) {
    /** The unpacked model, which the recognizer loads. */
    val modelDir: File = File(root, spec.folderName)

    /** Size of the download, for telling people before it starts. */
    val downloadBytes: Long = spec.sizeBytes

    /** True when the model is unpacked and ready. */
    fun isInstalled(): Boolean = File(modelDir, COMPLETE_MARKER).isFile

    /**
     * Downloads, verifies, and unpacks the model unless it is already installed, reporting the fraction downloaded
     * to [onProgress]. Cancelling the calling coroutine stops it and removes the partial files. Throws
     * [ModelInstallException] on failure.
     */
    suspend fun install(onProgress: (Float) -> Unit) {
        if (isInstalled()) return
        withContext(Dispatchers.IO) {
            root.mkdirs()
            if (root.usableSpace < MODEL_REQUIRED_FREE_BYTES) installFailure(ModelInstallFailure.NO_SPACE)
            val archive = File(root, "${spec.folderName}.zip.part")
            val staging = File(root, "${spec.folderName}.staging")
            try {
                download(archive, onProgress)
                unpack(archive, staging)
                modelDir.deleteRecursively()
                val unpacked = File(staging, spec.folderName)
                if (!unpacked.renameTo(modelDir)) throw IOException("Could not move the model into place")
            } finally {
                archive.delete()
                staging.deleteRecursively()
            }
        }
    }

    /** Deletes the installed model. */
    fun remove() {
        modelDir.deleteRecursively()
    }

    /** Downloads the archive to [target], checking its size and digest as it arrives. */
    private suspend fun download(
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        val connection = URL(spec.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    copyChunks(input) { buffer, count ->
                        received += count
                        if (received > spec.sizeBytes) installFailure(ModelInstallFailure.CORRUPT)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(received.toFloat() / spec.sizeBytes)
                    }
                }
            }
        } catch (e: IOException) {
            throw e as? ModelInstallException ?: ModelInstallException(ModelInstallFailure.NETWORK, e)
        } finally {
            connection.disconnect()
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (received != spec.sizeBytes || hex != spec.sha256) installFailure(ModelInstallFailure.CORRUPT)
    }

    /** Unpacks [archive] into [staging], refusing entries that would land outside it, and marks it complete. */
    private suspend fun unpack(
        archive: File,
        staging: File,
    ) {
        staging.deleteRecursively()
        val base = staging.canonicalFile
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                extract(zip, File(base, entry.name).canonicalFile, entry.isDirectory, base)
                entry = zip.nextEntry
            }
        }
        val unpacked = File(staging, spec.folderName)
        if (!unpacked.isDirectory) installFailure(ModelInstallFailure.CORRUPT)
        File(unpacked, COMPLETE_MARKER).writeText(spec.sha256)
    }

    /** Writes the current entry of [zip] to [target], a folder when [isDirectory], which must lie inside [base]. */
    private suspend fun extract(
        zip: ZipInputStream,
        target: File,
        isDirectory: Boolean,
        base: File,
    ) {
        if (!target.path.startsWith(base.path + File.separator)) installFailure(ModelInstallFailure.CORRUPT)
        if (isDirectory) {
            target.mkdirs()
        } else {
            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                copyChunks(zip) { buffer, count -> output.write(buffer, 0, count) }
            }
        }
    }

    /** Reads [input] to its end in chunks, handing each to [consume], and stops if the coroutine is cancelled. */
    private suspend fun copyChunks(
        input: InputStream,
        consume: (ByteArray, Int) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var count = input.read(buffer)
        while (count >= 0) {
            currentCoroutineContext().ensureActive()
            if (count > 0) consume(buffer, count)
            count = input.read(buffer)
        }
    }
}
