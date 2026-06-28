package com.mesha.mobile.localai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.localai.model.CatalogEntry
import com.mesha.mobile.localai.model.DownloadState
import com.mesha.mobile.localai.model.ModelStatus
import com.mesha.mobile.localai.ui.components.ModelDownloadRow
import com.mesha.mobile.localai.util.formatBytes

/**
 * Local AI screen: discover, download, update and delete on-device models.
 *
 * Reached from Settings → Local AI. Renders the backend catalog split into installed and
 * available sections, with live download progress, cancellation, update detection and a
 * device-storage summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAiScreen(
    onBack: () -> Unit,
    viewModel: LocalAiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local AI") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(
                        onClick = { viewModel.load(forceRefresh = true) },
                        enabled = !state.refreshing,
                    ) { Text(if (state.refreshing) "Refreshing…" else "Refresh") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StorageSummary(
                    availableBytes = state.availableStorageBytes,
                    usageBytes = state.totalUsageBytes,
                )
            }

            if (state.loading) {
                item { Text("Loading catalog…", style = MaterialTheme.typography.bodyMedium) }
            }
            state.error?.let { error ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { viewModel.load(forceRefresh = true) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            val installed = state.entries.filter { it.status !is ModelStatus.NotInstalled }
            val available = state.entries.filter { it.status is ModelStatus.NotInstalled }

            if (installed.isNotEmpty()) {
                item { SectionHeader("Installed") }
                items(installed, key = { it.model.id }) { entry ->
                    ModelCard(entry, state.downloads[entry.model.id], viewModel)
                }
            }

            if (available.isNotEmpty()) {
                item { SectionHeader("Available") }
                items(available, key = { it.model.id }) { entry ->
                    ModelCard(entry, state.downloads[entry.model.id], viewModel)
                }
            }

            if (!state.loading && state.entries.isEmpty() && state.error == null) {
                item { Text("No models available yet.", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StorageSummary(availableBytes: Long, usageBytes: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Device storage", fontWeight = FontWeight.SemiBold)
            Text(
                "Models using ${formatBytes(usageBytes)} · ${formatBytes(availableBytes)} free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelCard(
    entry: CatalogEntry,
    download: DownloadState?,
    viewModel: LocalAiViewModel,
) {
    ModelDownloadRow(
        entry = entry,
        download = download,
        onDownload = { viewModel.download(entry.model) },
        onCancel = { viewModel.cancel(entry.model.id) },
        onDelete = { viewModel.delete(entry.model.id) },
        onCheckUpdates = viewModel::checkForUpdates,
        onDismissError = { viewModel.dismissDownload(entry.model.id) },
    )
}
