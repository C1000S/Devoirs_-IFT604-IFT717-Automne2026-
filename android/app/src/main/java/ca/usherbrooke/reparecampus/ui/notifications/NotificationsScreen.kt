package ca.usherbrooke.reparecampus.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.usherbrooke.reparecampus.ui.AppViewModelProvider
import ca.usherbrooke.reparecampus.ui.components.EmptyState
import ca.usherbrooke.reparecampus.ui.components.OfflineBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onOpenRequest: (String) -> Unit,
    viewModel: NotificationsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Notifications") },
            actions = {
                IconButton(onClick = viewModel::markAllRead) {
                    Icon(Icons.Filled.DoneAll, contentDescription = "Tout marquer lu")
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualiser")
                }
            }
        )
        if (!ui.online) OfflineBanner()

        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (notifications.isEmpty()) {
                EmptyState("Aucune notification.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { n ->
                        Card(
                            Modifier.fillMaxWidth().clickable {
                                viewModel.markRead(n.id)
                                n.requestId?.let(onOpenRequest)
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (n.read) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                if (!n.read) {
                                    Box(
                                        Modifier.size(8.dp).padding(top = 2.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                    }
                                    Spacer(Modifier.size(8.dp))
                                }
                                Column {
                                    Text(
                                        n.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold
                                    )
                                    Text(n.message, style = MaterialTheme.typography.bodyMedium)
                                    if (n.createdAt.isNotBlank()) {
                                        Text(
                                            n.createdAt,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
