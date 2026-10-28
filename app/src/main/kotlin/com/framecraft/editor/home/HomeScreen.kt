/*
 * Home screen: the project list, currently shown in its empty state.
 */
package com.framecraft.editor.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the primary "New project" action. */
const val NEW_PROJECT_TAG = "home_new_project"

/** Opacity of the decorative empty-state icon. */
private const val EMPTY_ICON_ALPHA = 0.5f

/** Shows the project list; with no projects it shows the empty state and a bottom-centered create action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = { NewProjectButton(onNewProject) },
    ) { padding ->
        EmptyProjects(Modifier.padding(padding))
    }
}

/** Centered message explaining that no projects exist yet. */
@Composable
private fun EmptyProjects(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_movie),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = EMPTY_ICON_ALPHA),
            modifier = Modifier.size(Spacing.xxxl),
        )
        Text(
            text = stringResource(R.string.home_empty_headline),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** Primary create action that opens the system photo picker. */
@Composable
private fun NewProjectButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.heightIn(min = 56.dp).testTag(NEW_PROJECT_TAG),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
        Text(
            text = stringResource(R.string.home_new_project),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}
