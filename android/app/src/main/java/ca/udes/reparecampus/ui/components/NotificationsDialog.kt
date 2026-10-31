package ca.udes.reparecampus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.udes.reparecampus.data.model.AppNotification

@Composable
fun NotificationsButton(unread: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
            Icon(Icons.Filled.Notifications, contentDescription = "Notifications" + if (unread > 0) " ($unread non lues)" else "")
        }
    }
}

@Composable
fun NotificationsDialog(
    notifications: List<AppNotification>,
    onOpen: (AppNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notifications") },
        text = {
            if (notifications.isEmpty()) {
                Text("Aucune notification.")
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(notifications, key = { it.id }) { n ->
                        ListItem(
                            leadingContent = {
                                if (!n.read) Icon(Icons.Filled.Circle, contentDescription = "Non lue", tint = MaterialTheme.colorScheme.error, modifier = Modifier.heightIn(max = 10.dp))
                            },
                            headlineContent = { Text(n.title, fontWeight = if (n.read) FontWeight.Normal else FontWeight.SemiBold) },
                            supportingContent = { Text("${n.message}\n${formatDateTime(n.createdAt)}") },
                            modifier = Modifier.clickable { onOpen(n) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        dismissButton = {
            if (notifications.any { !it.read }) TextButton(onClick = onMarkAllRead) { Text("Tout marquer comme lu") }
        },
    )
}
