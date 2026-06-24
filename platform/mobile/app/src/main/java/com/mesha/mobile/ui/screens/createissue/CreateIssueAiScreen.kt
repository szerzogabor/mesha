package com.mesha.mobile.ui.screens.createissue

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import com.mesha.mobile.domain.ai.IssuePriority

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIssueAiScreen(
    onClose: () -> Unit,
    viewModel: CreateIssueAiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleListening() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Issue with AI") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Close") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                CreateStep.INPUT -> InputStep(
                    state = state,
                    onPromptChange = viewModel::onPromptChange,
                    onMic = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.toggleListening()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onGenerate = viewModel::generate,
                    onSelectProject = viewModel::selectProject,
                )

                CreateStep.GENERATING -> GeneratingStep()

                CreateStep.REVIEW -> ReviewStep(
                    state = state,
                    viewModel = viewModel,
                )

                CreateStep.DONE -> DoneStep(message = state.resultMessage.orEmpty(), onClose = onClose)
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InputStep(
    state: CreateIssueUiState,
    onPromptChange: (String) -> Unit,
    onMic: () -> Unit,
    onGenerate: () -> Unit,
    onSelectProject: (String) -> Unit,
) {
    Text(
        "Describe the task in plain language. An on-device Gemma model will draft the issue.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (!state.modelAvailable) {
        Text(
            "⚠ On-device model not installed — drafts can't be generated until you add it in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    OutlinedTextField(
        value = state.prompt,
        onValueChange = onPromptChange,
        label = { Text("e.g. Need GitHub App uninstall handling.") },
        modifier = Modifier.fillMaxWidth().height(140.dp),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMic, enabled = state.speechAvailable) {
            Icon(
                if (state.listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (state.listening) "Stop listening" else "Speak",
            )
        }
        Text(
            if (state.listening) "Listening…" else "Tap to dictate",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (state.projects.isNotEmpty()) {
        Text("Project", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.projects.take(6).forEach { project ->
                FilterChip(
                    selected = project.id == state.selectedProjectId,
                    onClick = { onSelectProject(project.id) },
                    label = { Text(project.key ?: project.name) },
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Button(
        onClick = onGenerate,
        enabled = state.modelAvailable && state.prompt.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Generate draft") }
}

@Composable
private fun GeneratingStep() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text("Generating on-device…", style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewStep(
    state: CreateIssueUiState,
    viewModel: CreateIssueAiViewModel,
) {
    OutlinedTextField(
        value = state.title,
        onValueChange = viewModel::onTitleChange,
        label = { Text("Title") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = viewModel::onDescriptionChange,
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )

    Text("Priority", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IssuePriority.entries.forEach { p ->
            FilterChip(
                selected = p == state.priority,
                onClick = { viewModel.onPriorityChange(p) },
                label = { Text(p.name) },
            )
        }
    }

    Text("Acceptance criteria", style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold)
    state.acceptanceCriteria.forEachIndexed { index, criterion ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = criterion,
                onValueChange = { viewModel.updateCriterion(index, it) },
                modifier = Modifier.fillMaxWidth(0.85f),
            )
            TextButton(onClick = { viewModel.removeCriterion(index) }) { Text("✕") }
        }
    }
    TextButton(onClick = viewModel::addCriterion) { Text("+ Add criterion") }

    if (state.labels.isNotEmpty()) {
        Text("Labels", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.labels.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
        }
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::backToInput) { Text("Back") }
        Button(onClick = viewModel::createIssue, modifier = Modifier.fillMaxWidth()) {
            Text("Create issue")
        }
    }
}

@Composable
private fun DoneStep(message: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("✓", style = MaterialTheme.typography.displayMedium)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onClose) { Text("Done") }
    }
}
