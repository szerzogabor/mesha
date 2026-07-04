package com.mesha.mobile.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.ui.components.MeshaCard
import com.mesha.mobile.ui.components.MeshaTopAppBar
import com.mesha.mobile.ui.theme.Mesha
import com.mesha.mobile.update.UpdateStatus

@Composable
fun SettingsScreen(
    onOpenAgents: () -> Unit,
    onOpenAgentConfig: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenLocalAi: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Requesting POST_NOTIFICATIONS when the user turns notifications on (Android 13+).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* preference already persisted; nothing to do on the result */ }

    Scaffold(
        topBar = { MeshaTopAppBar(title = "Settings") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Push notifications
            MeshaCard(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Push notifications", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Get notified the moment a ticket's status changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mesha.colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = state.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setNotificationsEnabled(enabled)
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
            }

            // On-device AI model status
            MeshaCard(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("On-device AI", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.modelInstalled) "✓ Model installed — ready for offline drafts"
                        else "No on-device model installed. Open Local AI to download a supported " +
                            "model directly from Mesha.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.modelInstalled) Mesha.colors.success
                        else Mesha.colors.textSecondary,
                    )
                    Button(onClick = onOpenLocalAi, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage Local AI models")
                    }
                    OutlinedButton(onClick = viewModel::refreshModelStatus) { Text("Refresh status") }
                }
            }

            // App updates
            MeshaCard(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App updates", fontWeight = FontWeight.SemiBold)
                    Text("Version ${state.versionName} (${state.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mesha.colors.textSecondary)

                    val update = state.updateStatus
                    if (update is UpdateStatus.UpdateAvailable) {
                        Text("Update available: v${update.release.versionName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mesha.colors.accent)
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
                            color = Mesha.colors.textSecondary)
                    }
                }
            }

            HorizontalDivider(color = Mesha.colors.border)

            Text("Workspace", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onOpenAgentConfig, modifier = Modifier.fillMaxWidth()) {
                Text("Custom AI agents")
            }
            OutlinedButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth()) {
                Text("Automations & ticket rules")
            }
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
