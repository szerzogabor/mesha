package com.mesha.mobile.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onCreateIssueWithAi: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenChat: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unsynced by viewModel.unsyncedDrafts.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Mesha") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.workspaceName.isNotBlank()) {
                Text(
                    state.workspaceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onCreateIssueWithAi,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp))
                Text("Create Issue with AI")
            }

            Button(
                onClick = onOpenChat,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Chat, contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp))
                Text("Chat with Local AI")
            }

            if (unsynced > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "$unsynced draft${if (unsynced == 1) "" else "s"} waiting to sync",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Open issues", state.openIssues, Modifier.weight(1f), onOpenIssues)
                StatCard("Active sessions", state.activeSessions, Modifier.weight(1f), onOpenSessions)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Agents online", state.onlineAgents, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier) { content() }
    } else {
        Card(modifier = modifier) { content() }
    }
}
