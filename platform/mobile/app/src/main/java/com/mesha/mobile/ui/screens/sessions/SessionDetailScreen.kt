package com.mesha.mobile.ui.screens.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.session?.issueIdentifier ?: "Session") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null && state.session == null ->
                ErrorState(state.error!!, Modifier.padding(padding)) { viewModel.load(sessionId) }
            else -> {
                val session = state.session
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    session?.issueTitle?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                    StatusBadge(session?.status)

                    // Status / logs
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Details", fontWeight = FontWeight.SemiBold)
                            session?.branchName?.let { Detail("Branch", it) }
                            session?.instructions?.let { Detail("Instructions", it) }
                            session?.errorMessage?.let { Detail("Error", it) }
                        }
                    }

                    // Pull request
                    session?.prUrl?.let { url ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Pull request", fontWeight = FontWeight.SemiBold)
                                Text(session.prTitle ?: "PR #${session.prNumber ?: ""}",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(url, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("Conversation", fontWeight = FontWeight.SemiBold)
                    if (state.messages.isEmpty()) {
                        Text("No messages yet", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.messages.forEach { msg ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(msg.role.uppercase(), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // Follow-up — continue the session from mobile.
                    OutlinedTextField(
                        value = state.followUp,
                        onValueChange = viewModel::onFollowUpChange,
                        label = { Text("Send a follow-up…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::sendFollowUp,
                        enabled = !state.sending && state.followUp.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
