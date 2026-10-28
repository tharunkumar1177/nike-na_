/*
 * Manual dependency container holding app-wide services.
 */
package com.framecraft.editor.di

import android.content.Context
import com.framecraft.editor.domain.model.IdGenerator
import com.framecraft.editor.domain.model.UuidIdGenerator
import com.framecraft.editor.engine.ProjectPreview
import com.framecraft.editor.media.AndroidMediaImporter
import com.framecraft.editor.media.MediaImporter

/** Creates and owns app-wide services for the lifetime of the process. */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** Identifier source for new projects, tracks, clips, and assets. */
    val idGenerator: IdGenerator = UuidIdGenerator

    /** Imports picked media into assets. */
    val mediaImporter: MediaImporter = AndroidMediaImporter(appContext, idGenerator)

    /** Creates a new preview player; the caller owns and must release it. */
    fun createPreview(): ProjectPreview = ProjectPreview(appContext)
}
