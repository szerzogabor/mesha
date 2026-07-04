package com.mesha.mobile.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.ui.components.MeshaCard
import com.mesha.mobile.ui.components.MeshaTopAppBar
import com.mesha.mobile.ui.theme.Mesha

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

    Scaffold(
        topBar = { MeshaTopAppBar(title = "Dashboard") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (state.workspaceName.isNotBlank()) state.workspaceName else "Overview",
                    style = MaterialTheme.typography.titleLarge,
                    color = Mesha.colors.textPrimary,
                )
                Text(
                    "Overview of your agents, sessions, and work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesha.colors.textTertiary,
                )
            }

            // Stat grid — two columns, accent numbers when non-zero, like the web dashboard.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Open issues", state.openIssues, Modifier.weight(1f), onOpenIssues)
                    StatCard("Active sessions", state.activeSessions, Modifier.weight(1f), onOpenSessions)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Agents online", state.onlineAgents, Modifier.weight(1f))
                    if (unsynced > 0) {
                        StatCard("Drafts to sync", unsynced, Modifier.weight(1f))
                    } else {
                        // Keep the grid balanced when there are no pending drafts.
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCreateIssueWithAi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mesha.colors.accent,
                        contentColor = Mesha.materialColors.onPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Create Issue with AI", fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onOpenChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mesha.colors.surfaceHover,
                        contentColor = Mesha.colors.textPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Chat with AI Agent", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    MeshaCard(modifier = modifier, onClick = onClick) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) Mesha.colors.accent else Mesha.colors.textPrimary,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Mesha.colors.textTertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
