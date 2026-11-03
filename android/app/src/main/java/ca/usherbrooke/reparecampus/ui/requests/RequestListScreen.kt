package ca.usherbrooke.reparecampus.ui.requests

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.usherbrooke.reparecampus.ui.AppViewModelProvider
import ca.usherbrooke.reparecampus.ui.components.EmptyState
import ca.usherbrooke.reparecampus.ui.components.OfflineBanner
import ca.usherbrooke.reparecampus.ui.components.RequestCard
import ca.usherbrooke.reparecampus.util.Labels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestListScreen(
    onOpenRequest: (String) -> Unit,
    viewModel: RequestListViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val requests by viewModel.requests.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Demandes") },
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualiser")
                }
            }
        )

        if (!ui.online) OfflineBanner()

        OutlinedTextField(
            value = ui.filters.query,
            onValueChange = viewModel::onQuery,
            label = { Text("Rechercher (appareil, description…)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Filtres par priorité.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("high", "normal", "low").forEach { p ->
                FilterChip(
                    selected = ui.filters.priority == p,
                    onClick = { viewModel.onPriority(if (ui.filters.priority == p) null else p) },
                    label = { Text(Labels.priority(p)) }
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        // Filtres par état.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Labels.allStatuses.forEach { s ->
                FilterChip(
                    selected = ui.filters.status == s,
                    onClick = { viewModel.onStatus(if (ui.filters.status == s) null else s) },
                    label = { Text(Labels.status(s)) }
                )
            }
        }

        Spacer(Modifier.size(6.dp))

        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (requests.isEmpty()) {
                EmptyState("Aucune demande à afficher.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(requests, key = { it.id }) { req ->
                        RequestCard(req) { onOpenRequest(req.id) }
                    }
                }
            }
        }
    }
}
