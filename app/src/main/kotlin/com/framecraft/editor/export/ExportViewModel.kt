/*
 * Export sheet state holder: offered options, the current choice and its size estimate, and starting the export.
 */
package com.framecraft.editor.export

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.framecraft.editor.data.project.ProjectStore
import com.framecraft.editor.di.AppContainer
import com.framecraft.editor.domain.export.ExportOptions
import com.framecraft.editor.domain.export.ExportPlanner
import com.framecraft.editor.domain.export.ExportSettings
import com.framecraft.editor.domain.model.Project
import com.framecraft.editor.engine.export.ExportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Log tag for export settings problems. */
private const val LOG_TAG = "ExportSettings"

/** The settings form for one project: [options] offered, [settings] chosen, and the [estimateBytes] they give. */
data class ExportForm(
    val options: ExportOptions,
    val settings: ExportSettings,
    val estimateBytes: Long,
    val canExport: Boolean,
) {
    /** Factories. */
    companion object {
        /** Returns the form for exporting [project] with [settings]. */
        fun of(
            project: Project,
            settings: ExportSettings,
        ): ExportForm =
            ExportForm(
                options = ExportPlanner.optionsFor(project),
                settings = settings,
                estimateBytes = ExportPlanner.estimateBytes(project, settings, project.durationMicros),
                canExport = project.durationMicros > 0L,
            )
    }
}

/**
 * Holds the export sheet's form. The sheet opens on the project snapshot the editor shows; the settings last used
 * for that project are restored, and saved again when an export starts.
 */
class ExportViewModel(
    private val store: ProjectStore,
    private val coordinator: ExportCoordinator,
) : ViewModel() {
    private val mutableForm = MutableStateFlow<ExportForm?>(null)
    private var project: Project? = null

    /** The form, or null while the saved settings load. */
    val form: StateFlow<ExportForm?> = mutableForm.asStateFlow()

    /** State of the app-wide export. */
    val exportState: StateFlow<ExportState> = coordinator.state

    /** Opens the form for [project], restoring the settings last used for it. */
    fun open(project: Project) {
        this.project = project
        mutableForm.value = null
        viewModelScope.launch {
            val saved = store.readExportSettings(project.id)
            if (this@ExportViewModel.project === project) {
                mutableForm.value = ExportForm.of(project, ExportPlanner.settingsFor(project, saved))
            }
        }
    }

    /** Changes the choice to [settings] and updates the estimate. */
    fun select(settings: ExportSettings) {
        project?.let { mutableForm.value = ExportForm.of(it, settings) }
    }

    /** Starts exporting the open project with the chosen settings and remembers them for next time. */
    fun start() {
        val current = project
        val chosen = mutableForm.value?.takeIf { it.canExport }?.settings
        if (current != null && chosen != null && coordinator.start(current, chosen)) {
            viewModelScope.launch {
                try {
                    store.writeExportSettings(current.id, chosen)
                } catch (error: IOException) {
                    Log.w(LOG_TAG, "Export settings were not saved", error)
                }
            }
        }
    }

    /** Stops the running export. */
    fun cancel() {
        coordinator.cancel()
    }

    /** Clears a finished export's result. */
    fun acknowledge() {
        coordinator.acknowledge()
    }

    /** Factories. */
    companion object {
        /** Returns a factory that builds the view model from [container]. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ExportViewModel(container.projectStore, container.exportCoordinator) }
            }
    }
}
