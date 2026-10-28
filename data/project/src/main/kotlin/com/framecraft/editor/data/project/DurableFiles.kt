/*
 * Crash-safe file writes: atomic replacement and synced appends.
 */
package com.framecraft.editor.data.project

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Suffix of temporary files that are renamed over their target once fully written. */
internal const val TEMP_SUFFIX = ".tmp"

/** Replaces this file with [text]: writes a temporary sibling, syncs it to disk, then renames it into place. */
internal fun File.writeAtomically(text: String) {
    val temp = File(parentFile, name + TEMP_SUFFIX)
    FileOutputStream(temp).use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
        out.fd.sync()
    }
    Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

/** Appends [line] and a newline to this file and syncs it to disk before returning. */
internal fun File.appendLineDurably(line: String) {
    FileOutputStream(this, true).use { out ->
        out.write((line + "\n").toByteArray(Charsets.UTF_8))
        out.fd.sync()
    }
}
