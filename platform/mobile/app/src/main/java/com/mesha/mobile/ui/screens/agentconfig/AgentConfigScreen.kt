package com.mesha.mobile.ui.screens.agentconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.data.remote.dto.AgentDefinitionDto
import com.mesha.mobile.ui.components.EmptyState
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState
import com.mesha.mobile.ui.components.MeshaCard
import com.mesha.mobile.ui.components.MeshaTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentConfigScreen(
    onBack: () -> Unit,
    viewModel: AgentConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state.editing

    Scaffold(
        topBar = {
            MeshaTopAppBar(
                title = if (editing == null) "Custom AI agents" else if (editing.editingId == null) "New agent" else "Edit agent",
                navigationIcon = {
                    TextButton(onClick = { if (editing != null) viewModel.dismissEditor() else onBack() }) {
                        Text(if (editing != null) "Cancel" else "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (editing == null && !state.loading && state.error == null) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::startCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New agent") },
                )
            }
        },
    ) { padding ->
        when {
            editing != null -> AgentEditor(
                form = editing,
                saving = state.saving,
                formError = state.formError,
                onChange = viewModel::updateForm,
                onSave = viewModel::save,
                modifier = Modifier.padding(padding),
            )
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), viewModel::load)
            state.agents.isEmpty() -> EmptyState(
                "No custom agents yet. Tap New agent to create one.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.agents, key = { it.id }) { agent ->
                    AgentCard(
                        agent = agent,
                        deleting = state.deletingId == agent.id,
                        onEdit = { viewModel.startEdit(agent) },
                        onDelete = { viewModel.delete(agent.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentCard(
    agent: AgentDefinitionDto,
    deleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    MeshaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(agent.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, enabled = false, label = { Text(if (agent.active) "Active" else "Inactive") })
            }
            Text(
                "${agent.name} · ${agent.providerType}" + (agent.blocksAgentName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            agent.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (agent.startupCommands.isNotEmpty()) {
                Text(
                    "Startup: ${agent.startupCommands.joinToString(" ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, enabled = !deleting) { Text("Edit") }
                TextButton(onClick = { confirmDelete = true }, enabled = !deleting) {
                    Text(if (deleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete agent?") },
            text = { Text("Delete \"${agent.title}\"? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentEditor(
    form: AgentForm,
    saving: Boolean,
    formError: String?,
    onChange: ((AgentForm) -> AgentForm) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { v -> onChange { it.copy(title = v) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> onChange { it.copy(name = v) } },
            label = { Text("Name (lowercase-kebab-case)") },
            supportingText = { Text("Unique id, e.g. code-reviewer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> onChange { it.copy(description = v) } },
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Provider", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AGENT_PROVIDER_TYPES.forEach { provider ->
                FilterChip(
                    selected = form.providerType == provider,
                    onClick = { onChange { it.copy(providerType = provider) } },
                    label = { Text(provider) },
                )
            }
        }

        OutlinedTextField(
            value = form.blocksAgentName,
            onValueChange = { v -> onChange { it.copy(blocksAgentName = v) } },
            label = { Text("Blocks agent name (optional)") },
            supportingText = { Text("e.g. claude, codex") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.systemPrompt,
            onValueChange = { v -> onChange { it.copy(systemPrompt = v) } },
            label = { Text("System prompt") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.startupCommandsText,
            onValueChange = { v -> onChange { it.copy(startupCommandsText = v) } },
            label = { Text("Startup commands (one per line)") },
            supportingText = { Text("e.g. /sonnet, /ultrathink") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = form.active, onCheckedChange = { v -> onChange { it.copy(active = v) } })
            Text("Active (assignable to issues)")
        }

        formError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (saving) "Saving…" else "Save agent") }
    }
}
