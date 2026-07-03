package com.mesha.mobile.ui.screens.createissue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState

private val PRIORITIES = listOf("LOW", "MEDIUM", "HIGH", "URGENT")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateIssueManualScreen(
    onClose: () -> Unit,
    viewModel: CreateIssueManualViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Issue") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Cancel") } },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null && state.projects.isEmpty() ->
                ErrorState(state.error!!, Modifier.padding(padding), onRetry = viewModel::load)
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Project
                if (state.projects.size > 1) {
                    Text("Project", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.projects.forEach { project ->
                            FilterChip(
                                selected = project.id == state.selectedProjectId,
                                onClick = { viewModel.selectProject(project.id) },
                                label = { Text(project.key ?: project.name) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = state.error != null && state.title.isBlank(),
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                )

                // Status
                if (state.statuses.isNotEmpty()) {
                    LabeledDropdown(
                        label = "Status",
                        value = state.status ?: "None",
                        options = state.statuses.map { it.name },
                        onSelect = viewModel::setStatus,
                    )
                }

                // Priority
                LabeledDropdown(
                    label = "Priority",
                    value = state.priority,
                    options = PRIORITIES,
                    onSelect = viewModel::setPriority,
                )

                // Assignee
                if (state.members.isNotEmpty()) {
                    val assigneeName = state.members.firstOrNull { it.userId == state.assigneeId }
                        ?.let { it.name ?: it.email } ?: "Unassigned"
                    AssigneeDropdown(
                        value = assigneeName,
                        members = state.members,
                        onSelect = viewModel::setAssignee,
                    )
                }

                // Labels
                if (state.labels.isNotEmpty()) {
                    Text("Labels", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.labels.forEach { label ->
                            FilterChip(
                                selected = state.selectedLabelIds.contains(label.id),
                                onClick = { viewModel.toggleLabel(label.id) },
                                label = { Text(label.name) },
                            )
                        }
                    }
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = viewModel::submit,
                    enabled = !state.submitting && state.title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Create issue")
                }
            }
        }
    }
}

@Composable
private fun LabeledDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value, modifier = Modifier.fillMaxWidth())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssigneeDropdown(
    value: String,
    members: List<com.mesha.mobile.data.remote.dto.WorkspaceMemberDto>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Assignee", fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value, modifier = Modifier.fillMaxWidth())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Unassigned") },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    },
                )
                members.forEach { member ->
                    DropdownMenuItem(
                        text = { Text(member.name ?: member.email ?: "Unknown") },
                        onClick = {
                            expanded = false
                            onSelect(member.userId)
                        },
                    )
                }
            }
        }
    }
}
