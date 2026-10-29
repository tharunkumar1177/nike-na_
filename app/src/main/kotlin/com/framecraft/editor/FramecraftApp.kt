/*
 * App root: switches between the home project list and the editor and launches the system photo picker.
 */
package com.framecraft.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.framecraft.editor.di.AppContainer
import com.framecraft.editor.editor.EditorRoute
import com.framecraft.editor.editor.EditorState
import com.framecraft.editor.editor.EditorViewModel
import com.framecraft.editor.export.ExportViewModel
import com.framecraft.editor.home.HomeScreen
import com.framecraft.editor.home.HomeViewModel

/** Shows home when no project is open, otherwise the editor; the list reloads each time home shows. */
@Composable
fun FramecraftApp(container: AppContainer) {
    val viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(container))
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val exportViewModel: ExportViewModel = viewModel(factory = ExportViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val home by homeViewModel.state.collectAsStateWithLifecycle()
    val projectName = stringResource(R.string.editor_default_project_name)
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            viewModel.importMedia(uris.map(Uri::toString), projectName)
        }
    val showingHome = state == EditorState.Idle
    LaunchedEffect(showingHome) {
        if (showingHome) homeViewModel.refresh()
    }
    if (showingHome) {
        HomeScreen(
            projects = home.projects,
            nowMillis = home.loadedAtMillis,
            onOpenProject = viewModel::openProject,
            onNewProject = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onRename = homeViewModel::rename,
            onDelete = homeViewModel::delete,
        )
    } else {
        EditorRoute(viewModel = viewModel, exportViewModel = exportViewModel, state = state)
    }
}
