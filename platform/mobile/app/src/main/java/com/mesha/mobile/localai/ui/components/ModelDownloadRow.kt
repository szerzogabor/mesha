package com.mesha.mobile.localai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mesha.mobile.localai.model.CatalogEntry
import com.mesha.mobile.localai.model.DownloadState
import com.mesha.mobile.localai.model.ModelStatus
import com.mesha.mobile.localai.util.formatBytes

/**
 * One model row: title, size/version, requirements, and a state-dependent action area
 * (download / progress + cancel / installed actions / error). An in-flight [download] takes
 * precedence over the static install [CatalogEntry.status].
 */
@Composable
fun ModelDownloadRow(
    entry: CatalogEntry,
    download: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDismissError: () -> Unit,
) {
    val model = entry.model
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(model.name, fontWeight = FontWeight.SemiBold)
                if (model.recommended && download == null && entry.status is ModelStatus.NotInstalled) {
                    Text("Recommended", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "${model.provider} · ${formatBytes(model.sizeBytes)} · ${model.engine}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (download) {
                is DownloadState.Connecting -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Connecting…", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                is DownloadState.Downloading -> {
                    val fraction = download.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "Downloading… ${(fraction * 100).toInt()}%  " +
                                "(${formatBytes(download.bytesDownloaded)} / ${formatBytes(download.totalBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Downloading… ${formatBytes(download.bytesDownloaded)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                is DownloadState.Verifying -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Verifying integrity…", style = MaterialTheme.typography.bodySmall)
                }
                is DownloadState.Failed -> {
                    Text(download.message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload) { Text("Retry") }
                        TextButton(onClick = onDismissError) { Text("Dismiss") }
                    }
                }
                is DownloadState.Completed, null -> InstallActions(
                    entry = entry,
                    onDownload = onDownload,
                    onDelete = onDelete,
                    onCheckUpdates = onCheckUpdates,
                )
            }
        }
    }
}

@Composable
private fun InstallActions(
    entry: CatalogEntry,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    when (val status = entry.status) {
        is ModelStatus.NotInstalled -> {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download") }
        }
        is ModelStatus.Installed -> {
            Text("✓ Installed · v${status.installed.version} · ${formatBytes(status.installed.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onCheckUpdates) { Text("Check for Updates") }
            }
        }
        is ModelStatus.UpdateAvailable -> {
            Text("Update available · installed v${status.installed.version} → v${entry.model.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDownload) { Text("Update") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
