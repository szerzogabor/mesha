package com.mesha.mobile.ui.screens.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.ui.components.EmptyState
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(
    onCreateIssueWithAi: () -> Unit,
    onOpenIssue: (projectId: String, issueId: String) -> Unit,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Issues") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateIssueWithAi,
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                text = { Text("AI") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.projects.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.projects.take(8).forEach { project ->
                        FilterChip(
                            selected = project.id == state.selectedProjectId,
                            onClick = { viewModel.selectProject(project.id) },
                            label = { Text(project.key ?: project.name) },
                        )
                    }
                }
            }

            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load)
                state.issues.isEmpty() -> EmptyState("No issues yet. Tap AI to create one.")
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.issues, key = { it.id }) { issue ->
                        IssueRow(issue, onClick = { onOpenIssue(issue.projectId, issue.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: IssueDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                issue.identifier?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                issue.priority?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(issue.title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            issue.status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
