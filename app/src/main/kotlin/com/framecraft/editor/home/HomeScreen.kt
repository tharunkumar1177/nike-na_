/*
 * Home screen: the saved project list (newest first) with rename and delete, or the empty state.
 */
package com.framecraft.editor.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.data.project.ProjectList
import com.framecraft.editor.data.project.ProjectSummary
import com.framecraft.editor.domain.time.Timecode
import com.framecraft.editor.ui.spokenDuration
import com.framecraft.editor.ui.theme.Spacing
import java.time.ZoneId

/** Test tag of the primary "New project" action. */
const val NEW_PROJECT_TAG = "home_new_project"

/** Test tag of each project row. */
const val PROJECT_ROW_TAG = "home_project_row"

/** Test tag of each row's options button. */
const val PROJECT_OVERFLOW_TAG = "home_project_overflow"

/** Test tag of the rename dialog's text field. */
const val RENAME_INPUT_TAG = "dialog_rename_input"

/** Test tag of the rename dialog's confirm button. */
const val RENAME_CONFIRM_TAG = "dialog_rename_confirm"

/** Test tag of the delete dialog's confirm button. */
const val DELETE_CONFIRM_TAG = "dialog_delete_confirm"

/** Opacity of the decorative empty-state icon. */
private const val EMPTY_ICON_ALPHA = 0.5f

/** Space below the list so the floating create button never covers the last row. */
private val LIST_BOTTOM_PADDING = 88.dp

/** Minimum height of a project row. */
private val ROW_MIN_HEIGHT = 72.dp

/**
 * Shows saved [projects] newest first, or the empty state when there are none; nothing is shown until
 * the first load finishes ([projects] is null). Relative times are computed against [nowMillis].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projects: ProjectList?,
    nowMillis: Long,
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val actions = RowActions(onOpenProject, onRename = { renamingId = it }, onDelete = { deletingId = it })
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = { NewProjectButton(onNewProject) },
    ) { padding ->
        when {
            projects == null -> Box(Modifier.padding(padding))
            projects.isEmpty -> EmptyProjects(Modifier.padding(padding))
            else -> ProjectRows(projects, nowMillis, actions, Modifier.padding(padding))
        }
    }
    ProjectDialogs(
        renaming = projects?.summaries?.firstOrNull { it.id == renamingId },
        deletingId = deletingId,
        onDismiss = {
            renamingId = null
            deletingId = null
        },
        onRename = onRename,
        onDelete = onDelete,
    )
}

/** True when there are no projects at all, readable or not. */
private val ProjectList.isEmpty: Boolean
    get() = summaries.isEmpty() && unreadableIds.isEmpty()

/** Shows the rename dialog for [renaming] or the delete confirmation for [deletingId]; both close via [onDismiss]. */
@Composable
private fun ProjectDialogs(
    renaming: ProjectSummary?,
    deletingId: String?,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (renaming != null) {
        RenameDialog(
            initialName = renaming.name,
            onDismiss = onDismiss,
            onConfirm = { name ->
                onDismiss()
                onRename(renaming.id, name)
            },
        )
    }
    if (deletingId != null) {
        DeleteDialog(
            onDismiss = onDismiss,
            onConfirm = {
                onDismiss()
                onDelete(deletingId)
            },
        )
    }
}

/** What a row can ask for, each with the project id. */
private class RowActions(
    val onOpen: (String) -> Unit,
    val onRename: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

/** Readable projects, then unreadable ones, with room below for the create button. */
@Composable
private fun ProjectRows(
    projects: ProjectList,
    nowMillis: Long,
    actions: RowActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LIST_BOTTOM_PADDING)) {
        items(projects.summaries, key = { it.id }) { summary -> ProjectRow(summary, nowMillis, actions) }
        items(projects.unreadableIds, key = { it }) { id ->
            val title = stringResource(R.string.home_unreadable_name)
            val subtitle = stringResource(R.string.home_unreadable)
            RowLayout(
                title = title,
                subtitle = subtitle,
                subtitleColor = MaterialTheme.colorScheme.error,
                description = "$title. $subtitle",
                onClick = null,
            ) {
                RowMenu(name = title, onRename = null, onDelete = { actions.onDelete(id) })
            }
        }
    }
}

/** A saved project: name, then when it was edited, its duration, and its clip count. */
@Composable
private fun ProjectRow(
    summary: ProjectSummary,
    nowMillis: Long,
    actions: RowActions,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val ago = EditedTime.classify(nowMillis, summary.updatedAtEpochMillis, ZoneId.systemDefault())
    val edited = editedLabel(resources, locale, ago)
    val clips = pluralStringResource(R.plurals.home_clip_count, summary.clipCount, summary.clipCount)
    val duration = Timecode.format(summary.durationMicros)
    val spoken = spokenDuration(resources, summary.durationMicros)
    RowLayout(
        title = summary.name,
        subtitle = stringResource(R.string.home_row_subtitle, edited, duration, clips),
        subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        description = stringResource(R.string.home_row_description, summary.name, edited, spoken, clips),
        onClick = { actions.onOpen(summary.id) },
    ) {
        RowMenu(
            name = summary.name,
            onRename = { actions.onRename(summary.id) },
            onDelete = { actions.onDelete(summary.id) },
        )
    }
}

/** 72dp row with a title, a one-line subtitle, and a trailing [menu]; opens on tap when [onClick] is set. */
@Composable
private fun RowLayout(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    description: String,
    onClick: (() -> Unit)?,
    menu: @Composable () -> Unit,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .then(clickable)
                .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm)
                .testTag(PROJECT_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description }) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        menu()
    }
}

/** Options button for the project [name] with Rename (when [onRename] is set) and Delete. */
@Composable
private fun RowMenu(
    name: String,
    onRename: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag(PROJECT_OVERFLOW_TAG)) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.home_project_options, name),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_rename)) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Asks for a new project name; Rename stays disabled while the name is blank. */
@Composable
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.home_rename_label)) },
                singleLine = true,
                modifier = Modifier.testTag(RENAME_INPUT_TAG),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(RENAME_CONFIRM_TAG),
            ) {
                Text(stringResource(R.string.home_rename))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) } },
    )
}

/** Confirms permanently deleting a project. */
@Composable
private fun DeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_delete_title)) },
        text = { Text(stringResource(R.string.home_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(DELETE_CONFIRM_TAG)) {
                Text(stringResource(R.string.home_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) } },
    )
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
