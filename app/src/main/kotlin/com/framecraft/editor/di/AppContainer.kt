/*
 * Manual dependency container holding app-wide services.
 */
package com.framecraft.editor.di

import android.content.Context
import com.framecraft.editor.data.project.ProjectStore
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.UuidIdGenerator
import com.framecraft.editor.engine.ClipThumbnails
import com.framecraft.editor.engine.ProjectPreview
import com.framecraft.editor.engine.export.ProjectExporter
import com.framecraft.editor.export.ExportCoordinator
import com.framecraft.editor.media.AndroidMediaImporter
import com.framecraft.editor.media.MediaImporter
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.math.roundToInt

/** Creates and owns app-wide services for the lifetime of the process. */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** Identifier source for new projects, tracks, clips, and assets. */
    val idGenerator: IdGenerator = UuidIdGenerator

    /** Imports picked media into assets. */
    val mediaImporter: MediaImporter = AndroidMediaImporter(appContext, idGenerator)

    /** Saved projects, one directory each under the app's private files; all writes go through one thread. */
    val projectStore: ProjectStore =
        ProjectStore(File(appContext.filesDir, PROJECTS_DIRECTORY), Dispatchers.IO.limitedParallelism(1))

    /** The single app-wide export, shared by the editor and the export service. */
    val exportCoordinator: ExportCoordinator = ExportCoordinator(appContext)

    /** Creates a new preview player; the caller owns and must release it. */
    fun createPreview(): ProjectPreview = ProjectPreview(appContext)

    /** Creates an exporter; the caller owns and must release it. */
    fun createExporter(): ProjectExporter = ProjectExporter(appContext)

    /** Creates a thumbnail cache sized for timeline rows; the caller owns and must close it. */
    fun createThumbnails(): ClipThumbnails {
        val heightPx = (TIMELINE_ROW_HEIGHT_DP * appContext.resources.displayMetrics.density).roundToInt()
        return ClipThumbnails(appContext, heightPx)
    }

    private companion object {
        /** Timeline row height in dp; thumbnails are decoded at this height. */
        const val TIMELINE_ROW_HEIGHT_DP = 64f

        /** Directory under the app's files that holds saved projects. */
        const val PROJECTS_DIRECTORY = "projects"
    }
}
