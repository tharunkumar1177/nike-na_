/*
 * The export settings last used for a project, kept beside its snapshot and outside its edit history.
 */
package com.framecraft.editor.data.project

import com.framecraft.editor.domain.export.ExportSettings
import java.io.File

/**
 * `export-settings.json` in a project directory. It is a preference, not an edit, so it is never journaled;
 * a missing or unreadable file simply means no saved settings.
 */
internal class ExportSettingsFile(
    dir: File,
) {
    private val file = File(dir, "export-settings.json")

    /** Returns the saved settings, or null when none are saved or the file cannot be read. */
    fun read(): ExportSettings? =
        runCatching { projectJson.decodeFromString(ExportSettings.serializer(), file.readText()) }.getOrNull()

    /** Atomically replaces the saved settings with [settings]. */
    fun write(settings: ExportSettings) {
        file.writeAtomically(projectJson.encodeToString(ExportSettings.serializer(), settings))
    }
}
