package ca.udes.reparecampus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.udes.reparecampus.data.local.PendingActionEntity

/** Bandeau affiché hors ligne : on consulte le cache Room. */
@Composable
fun OfflineBanner(lastSync: Long?, pendingCount: Int) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null)
            Column {
                Text("Hors ligne — données enregistrées sur l'appareil", style = MaterialTheme.typography.titleSmall)
                Text(
                    (lastSync?.let { "Dernière synchronisation : ${formatDateTime(it)}." } ?: "Jamais synchronisé.") +
                        if (pendingCount > 0) " $pendingCount action(s) seront envoyées au retour du réseau." else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Icône d'état de synchronisation, avec le nombre d'actions en file. */
@Composable
fun SyncButton(isOnline: Boolean, pendingCount: Int, hasFailures: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = {
            if (pendingCount > 0) {
                Badge(containerColor = if (hasFailures) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) {
                    Text("$pendingCount")
                }
            }
        }) {
            Icon(
                when {
                    !isOnline -> Icons.Filled.CloudOff
                    pendingCount > 0 -> Icons.Filled.CloudSync
                    else -> Icons.Filled.CloudDone
                },
                contentDescription = "Synchronisation" + if (pendingCount > 0) " ($pendingCount en attente)" else "",
            )
        }
    }
}

@Composable
fun SyncDialog(
    pending: List<PendingActionEntity>,
    isOnline: Boolean,
    lastSync: Long?,
    onSyncNow: () -> Unit,
    onDiscardFailed: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Synchronisation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isOnline) "En ligne." else "Hors ligne : les actions seront envoyées automatiquement au retour du réseau.")
                Text(lastSync?.let { "Dernière synchronisation : ${formatDateTime(it)}" } ?: "Jamais synchronisé.", style = MaterialTheme.typography.bodySmall)
                if (pending.isEmpty()) {
                    Text("Aucune action en attente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(pending, key = { it.id }) { a ->
                            val failed = a.state == PendingActionEntity.STATE_FAILED
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        if (failed) Icons.Filled.ErrorOutline else Icons.Filled.Schedule,
                                        contentDescription = if (failed) "Refusée" else "En attente",
                                        tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                    )
                                },
                                headlineContent = { Text(a.label) },
                                supportingContent = {
                                    Text(if (failed) "Refusée par le serveur : ${a.error}" else "En attente · ${formatDateTime(a.createdAt)}")
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSyncNow) { Text("Synchroniser maintenant") } },
        dismissButton = {
            Row {
                if (pending.any { it.state == PendingActionEntity.STATE_FAILED }) {
                    TextButton(onClick = onDiscardFailed) { Text("Effacer les échecs") }
                }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
    )
}
