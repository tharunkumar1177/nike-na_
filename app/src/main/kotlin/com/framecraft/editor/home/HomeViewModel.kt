/*
 * Home state holder: loads the saved project list and renames or deletes projects that are not open.
 */
package com.framecraft.editor.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.framecraft.editor.data.project.ProjectList
import com.framecraft.editor.data.project.ProjectStore
import com.framecraft.editor.di.AppContainer
import com.framecraft.editor.domain.command.RenameProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** The project list as loaded at [loadedAtMillis]; [projects] is null until the first load finishes. */
data class HomeState(
    val projects: ProjectList? = null,
    val loadedAtMillis: Long = 0L,
)

/** Holds the project list shown on the home screen. */
class HomeViewModel(
    private val store: ProjectStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())

    /** Current list state. */
    val state: StateFlow<HomeState> = mutableState.asStateFlow()

    /** Reloads the project list. */
    fun refresh() {
        viewModelScope.launch { reload() }
    }

    /** Renames the closed project [projectId] to [name]; blank names are ignored by the command. */
    fun rename(
        projectId: String,
        name: String,
    ) {
        viewModelScope.launch {
            try {
                store.change(projectId, RenameProject(name))
            } catch (ignored: IOException) {
            }
            reload()
        }
    }

    /** Deletes the project [projectId] and all its files. */
    fun delete(projectId: String) {
        viewModelScope.launch {
            store.delete(projectId)
            reload()
        }
    }

    /** Loads the list from the store. */
    private suspend fun reload() {
        val projects = store.list()
        mutableState.value = HomeState(projects, clock())
    }

    /** Factory wiring. */
    companion object {
        /** Returns a factory that builds the view model from [container]. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { HomeViewModel(container.projectStore) } }
    }
}
