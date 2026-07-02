package com.mesha.mobile.ui.screens.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    projectId: String,
    issueId: String,
    onBack: () -> Unit,
    viewModel: IssueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(projectId, issueId) { viewModel.load(projectId, issueId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.issue?.identifier ?: "Issue") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
                    issue?.title?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        issue?.status?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                        issue?.priority?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                    }

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

                    issue?.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Description", fontWeight = FontWeight.SemiBold)
                                Text(description, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("Comments", fontWeight = FontWeight.SemiBold)
                    if (state.comments.isEmpty()) {
                        Text("No comments yet", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.comments.forEach { comment ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                comment.author?.name?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                Text(comment.body, style = MaterialTheme.typography.bodyMedium)
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
        Text("$label:", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
