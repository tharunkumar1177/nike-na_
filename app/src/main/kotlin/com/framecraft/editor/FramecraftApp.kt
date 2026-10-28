/*
 * App root: switches between home and editor and launches the system photo picker.
 */
package com.framecraft.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.framecraft.editor.di.AppContainer
import com.framecraft.editor.editor.EditorRoute
import com.framecraft.editor.editor.EditorState
import com.framecraft.editor.editor.EditorViewModel
import com.framecraft.editor.home.HomeScreen

/** Shows home when no project is open, otherwise the editor. */
@Composable
fun FramecraftApp(container: AppContainer) {
    val viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projectName = stringResource(R.string.editor_default_project_name)
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            viewModel.importMedia(uris.map(Uri::toString), projectName)
        }
    if (state == EditorState.Idle) {
        HomeScreen(
            onNewProject = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
        )
    } else {
        EditorRoute(viewModel = viewModel, state = state)
    }
}
