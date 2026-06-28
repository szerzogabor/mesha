package com.mesha.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
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
import com.mesha.mobile.update.UpdateStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAgents: () -> Unit,
    onOpenLocalAi: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // On-device AI model status
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("On-device AI", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.modelInstalled) "✓ Model installed — ready for offline drafts"
                        else "No on-device model installed. Open Local AI to download a supported " +
                            "model directly from Mesha.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.modelInstalled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenLocalAi, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage Local AI models")
                    }
                    OutlinedButton(onClick = viewModel::refreshModelStatus) { Text("Refresh status") }
                }
            }

            // App updates
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App updates", fontWeight = FontWeight.SemiBold)
                    Text("Version ${state.versionName} (${state.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val update = state.updateStatus
                    if (update is UpdateStatus.UpdateAvailable) {
                        Text("Update available: v${update.release.versionName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                        update.release.releaseNotes?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = viewModel::downloadAndInstallUpdate,
                            enabled = !state.downloadingUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.downloadingUpdate) "Downloading…" else "Download & install")
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::checkForUpdate,
                            enabled = !state.checkingUpdate,
                        ) {
                            Text(if (state.checkingUpdate) "Checking…" else "Check for updates")
                        }
                    }
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()

            OutlinedButton(onClick = onOpenAgents, modifier = Modifier.fillMaxWidth()) {
                Text("Agent monitoring")
            }
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
        }
    }
}
