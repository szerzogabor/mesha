package com.mesha.mobile.ui.screens.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mesha.mobile.ui.components.BadgeTone
import com.mesha.mobile.ui.components.EmptyState
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState
import com.mesha.mobile.ui.components.MeshaCard
import com.mesha.mobile.ui.components.MeshaTopAppBar
import com.mesha.mobile.ui.components.StatusBadge as PillBadge
import com.mesha.mobile.ui.theme.Mesha

@Composable
fun SessionsScreen(
    onOpenSession: (String) -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { MeshaTopAppBar(title = "Sessions") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), viewModel::load)
            state.sessions.isEmpty() -> EmptyState("No AI sessions yet", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    MeshaCard(
                        onClick = { onOpenSession(session.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            session.issueIdentifier ?: session.issueTitle ?: "Session",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        session.issueTitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = Mesha.colors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        StatusBadge(
                            session.status,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        session.prUrl?.let {
                            Text("PR #${session.prNumber ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mesha.colors.accent,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String?, modifier: Modifier = Modifier) {
    val label = status ?: "UNKNOWN"
    val tone = when (label.uppercase(java.util.Locale.US)) {
        "COMPLETED" -> BadgeTone.Success
        "FAILED", "CANCELLED", "CANCELED" -> BadgeTone.Destructive
        "IN_PROGRESS", "RUNNING", "STARTED" -> BadgeTone.Accent
        else -> BadgeTone.Neutral
    }
    PillBadge(text = label.replace('_', ' '), tone = tone, modifier = modifier)
}
