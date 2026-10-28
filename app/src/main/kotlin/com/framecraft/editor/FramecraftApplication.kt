/*
 * Application class that creates the dependency container.
 */
package com.framecraft.editor

import android.app.Application
import com.framecraft.editor.di.AppContainer

/** Process-wide entry point holding the [AppContainer]. */
class FramecraftApplication : Application() {
    /** App-wide services, available after [onCreate]. */
    lateinit var container: AppContainer
        private set

    /** Builds the dependency container. */
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
