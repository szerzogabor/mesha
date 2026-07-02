package com.mesha.mobile.ui.screens.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState

private val STATUSES = listOf("BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE")
private val PRIORITIES = listOf("LOW", "MEDIUM", "HIGH", "URGENT")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    projectId: String,
    issueId: String,
    onBack: () -> Unit,
    viewModel: IssueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(projectId, issueId) { viewModel.load(projectId, issueId) }

    LaunchedEffect(state.updateError) {
        state.updateError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUpdateError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.issue?.identifier ?: "Issue") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (!state.editMode && state.issue != null) {
                        IconButton(onClick = { viewModel.startEdit() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit issue")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null && state.issue == null ->
                ErrorState(state.error!!, Modifier.padding(padding)) {
                    viewModel.load(projectId, issueId)
                }
            else -> {
                val issue = state.issue
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.editMode) {
                        // --- Edit mode ---
                        OutlinedTextField(
                            value = state.editTitle,
                            onValueChange = { viewModel.setEditTitle(it) },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = state.editTitle.isBlank(),
                        )
                        OutlinedTextField(
                            value = state.editDescription,
                            onValueChange = { viewModel.setEditDescription(it) },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 8,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(
                                onClick = { viewModel.cancelEdit() },
                                enabled = !state.submittingEdit,
                            ) { Text("Cancel") }
                            Button(
                                onClick = { viewModel.submitEdit() },
                                enabled = !state.submittingEdit && state.editTitle.isNotBlank(),
                            ) {
                                if (state.submittingEdit) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(end = 8.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Text("Save")
                            }
                        }
                    } else {
                        // --- Read mode: title ---
                        issue?.title?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // --- Status & Priority chips (always visible, interactive in read mode) ---
                    if (!state.editMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Status chip with dropdown
                            Box {
                                AssistChip(
                                    onClick = { viewModel.showStatusPicker() },
                                    label = {
                                        if (state.updatingStatus) {
                                            CircularProgressIndicator(strokeWidth = 2.dp)
                                        } else {
                                            Text(issue?.status ?: "No status")
                                        }
                                    },
                                )
                                DropdownMenu(
                                    expanded = state.showStatusPicker,
                                    onDismissRequest = { viewModel.dismissStatusPicker() },
                                ) {
                                    STATUSES.forEach { status ->
                                        DropdownMenuItem(
                                            text = { Text(status) },
                                            onClick = { viewModel.updateStatus(status) },
                                        )
                                    }
                                }
                            }

                            // Priority chip with dropdown
                            Box {
                                AssistChip(
                                    onClick = { viewModel.showPriorityPicker() },
                                    label = {
                                        if (state.updatingPriority) {
                                            CircularProgressIndicator(strokeWidth = 2.dp)
                                        } else {
                                            Text(issue?.priority ?: "No priority")
                                        }
                                    },
                                )
                                DropdownMenu(
                                    expanded = state.showPriorityPicker,
                                    onDismissRequest = { viewModel.dismissPriorityPicker() },
                                ) {
                                    PRIORITIES.forEach { priority ->
                                        DropdownMenuItem(
                                            text = { Text(priority) },
                                            onClick = { viewModel.updatePriority(priority) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- Details card ---
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Details", fontWeight = FontWeight.SemiBold)
                            issue?.assignee?.name?.let { IssueDetailRow("Assignee", it) }
                            issue?.labels?.takeIf { it.isNotEmpty() }?.let { labels ->
                                IssueDetailRow("Labels", labels.joinToString { it.name })
                            }
                            issue?.createdAt?.let { IssueDetailRow("Created", it) }
                        }
                    }

                    // --- Description card (read mode only) ---
                    if (!state.editMode) {
                        issue?.description?.takeIf { it.isNotBlank() }?.let { description ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Description", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("Comments", fontWeight = FontWeight.SemiBold)

                    if (state.comments.isEmpty()) {
                        Text(
                            "No comments yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.comments.forEach { comment ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                comment.author?.name?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // --- Add comment ---
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.commentInput,
                            onValueChange = { viewModel.setCommentInput(it) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Add a comment") },
                            enabled = !state.sendingComment,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.sendComment() }),
                        )
                        IconButton(
                            onClick = { viewModel.sendComment() },
                            enabled = state.commentInput.isNotBlank() && !state.sendingComment,
                        ) {
                            if (state.sendingComment) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "Send comment")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueDetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
