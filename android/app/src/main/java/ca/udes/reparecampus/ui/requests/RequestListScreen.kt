package ca.udes.reparecampus.ui.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.udes.reparecampus.data.local.DraftEntity
import ca.udes.reparecampus.data.local.PendingActionEntity
import ca.udes.reparecampus.ui.components.NotificationsButton
import ca.udes.reparecampus.ui.components.NotificationsDialog
import ca.udes.reparecampus.ui.components.OfflineBanner
import ca.udes.reparecampus.ui.components.SyncButton
import ca.udes.reparecampus.ui.components.SyncDialog
import ca.udes.reparecampus.ui.detail.ConfirmDialog
import ca.udes.reparecampus.data.model.Priority
import ca.udes.reparecampus.data.model.RepairRequest
import ca.udes.reparecampus.data.model.RequestStatus
import ca.udes.reparecampus.domain.Role
import ca.udes.reparecampus.domain.Session
import ca.udes.reparecampus.ui.components.PriorityChip
import ca.udes.reparecampus.ui.components.StatusChip
import ca.udes.reparecampus.ui.components.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestListScreen(
    session: Session,
    viewModel: RequestListViewModel,
    onOpenRequest: (String) -> Unit,
    onCreateRequest: (draftId: Long) -> Unit,
    topBarActions: @Composable () -> Unit = {},
    banner: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSync.collectAsStateWithLifecycle()
    val pending by viewModel.pendingActions.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var showSync by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Rafraîchissement à l'ouverture et à chaque retour au premier plan.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.dismissError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(titleFor(session.role))
                        Text(
                            "${session.name} · ${session.role.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    NotificationsButton(notifications.count { !it.read }) { showNotifications = true }
                    SyncButton(isOnline, pending.size, pending.any { it.state == PendingActionEntity.STATE_FAILED }) { showSync = true }
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rafraîchir")
                    }
                    topBarActions()
                    IconButton(onClick = { if (pending.any { it.state == PendingActionEntity.STATE_PENDING }) confirmLogout = true else viewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Se déconnecter")
                    }
                },
            )
        },
        floatingActionButton = {
            if (viewModel.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = { onCreateRequest(0L) },
                    // Le libellé du FAB étendu n'est pas exposé à l'accessibilité : on le fixe explicitement.
                    modifier = Modifier.semantics { contentDescription = "Nouvelle demande" },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nouvelle demande") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!isOnline) OfflineBanner(lastSync, pending.count { it.state == PendingActionEntity.STATE_PENDING })
            banner()
            FiltersBar(session.role, state, viewModel)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (viewModel.canCreate && drafts.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Brouillons (${drafts.size})", style = MaterialTheme.typography.titleSmall)
                        }
                        items(drafts, key = { "draft-${it.id}" }) { draft ->
                            DraftCard(draft, state.categoryName(draft.category)) { onCreateRequest(draft.id) }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "${state.requests.size} demande(s) affichée(s) sur ${state.totalCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.requests.isEmpty() && !state.isRefreshing) {
                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(state.totalCount > 0) }
                    }
                    items(state.requests, key = { it.id }) { request ->
                        RequestCard(request, state.categoryName(request.category), session.role) { onOpenRequest(request.id) }
                    }
                }
            }
        }
    }
    if (showNotifications) {
        NotificationsDialog(
            notifications = notifications,
            onOpen = { n ->
                showNotifications = false
                viewModel.markNotificationsRead(listOf(n.id))
                n.request?.let(onOpenRequest)
            },
            onMarkAllRead = { viewModel.markNotificationsRead(notifications.filter { !it.read }.map { it.id }) },
            onDismiss = { showNotifications = false },
        )
    }
    SyncDialogs(
        showSync = showSync,
        confirmLogout = confirmLogout,
        pending = pending,
        isOnline = isOnline,
        lastSync = lastSync,
        onSyncNow = { showSync = false; viewModel.refresh() },
        onDiscardFailed = viewModel::discardFailedActions,
        onDismissSync = { showSync = false },
        onLogout = { confirmLogout = false; viewModel.logout() },
        onDismissLogout = { confirmLogout = false },
    )
}

@Composable
private fun SyncDialogs(
    showSync: Boolean,
    confirmLogout: Boolean,
    pending: List<PendingActionEntity>,
    isOnline: Boolean,
    lastSync: Long?,
    onSyncNow: () -> Unit,
    onDiscardFailed: () -> Unit,
    onDismissSync: () -> Unit,
    onLogout: () -> Unit,
    onDismissLogout: () -> Unit,
) {
    if (showSync) SyncDialog(pending, isOnline, lastSync, onSyncNow, onDiscardFailed, onDismissSync)
    if (confirmLogout) {
        ConfirmDialog(
            "Se déconnecter ?",
            "${pending.count { it.state == PendingActionEntity.STATE_PENDING }} action(s) hors ligne n'ont pas encore été envoyées et seront perdues.",
            "Se déconnecter",
            onConfirm = onLogout,
            onDismiss = onDismissLogout,
        )
    }
}

private fun titleFor(role: Role) = when (role) {
    Role.REQUESTER -> "Mes demandes"
    Role.REPAIRER -> "Demandes de réparation"
    else -> "Toutes les demandes"
}

@Composable
private fun FiltersBar(role: Role, state: RequestListUiState, viewModel: RequestListViewModel) {
    val f = state.filters
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = f.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Rechercher (appareil, problème, personne…)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (f.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Effacer la recherche")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (role == Role.REPAIRER) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RepairerScope.entries.forEachIndexed { i, scope ->
                    SegmentedButton(
                        selected = f.repairerScope == scope,
                        onClick = { viewModel.onRepairerScope(scope) },
                        shape = SegmentedButtonDefaults.itemShape(i, RepairerScope.entries.size),
                    ) { Text(scope.label) }
                }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DropdownFilter(
                label = "État",
                selectedLabel = f.status?.label,
                options = RequestStatus.entries.map { it.label to it },
                onSelect = viewModel::onStatusFilter,
            )
            DropdownFilter(
                label = "Catégorie",
                selectedLabel = f.category?.let(state::categoryName),
                options = state.categories.map { it.name to it.id },
                onSelect = viewModel::onCategoryFilter,
            )
            DropdownFilter(
                label = "Priorité",
                selectedLabel = f.priority?.label,
                options = Priority.entries.map { it.label to it },
                onSelect = viewModel::onPriorityFilter,
            )
            FilterChip(
                selected = f.hideFinal,
                onClick = { viewModel.onHideFinal(!f.hideFinal) },
                label = { Text("Masquer terminées") },
            )
            if (f.activeCount > 0) {
                TextButton(onClick = viewModel::clearFilters) { Text("Réinitialiser") }
            }
        }
    }
}

@Composable
private fun <T> DropdownFilter(label: String, selectedLabel: String?, options: List<Pair<String, T>>, onSelect: (T?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedLabel != null,
            onClick = { expanded = true },
            label = { Text(if (selectedLabel != null) "$label : $selectedLabel" else label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Tous") }, onClick = { onSelect(null); expanded = false })
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun RequestCard(request: RepairRequest, categoryName: String, role: Role, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(request.statusEnum)
                PriorityChip(Priority.fromApi(request.priority))
                if (request.isLocal || request.pendingSync) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Text(
                        if (request.isLocal) "En attente d'envoi" else "Non synchronisée",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (request.hasPendingEstimate) {
                    Text("Estimation à répondre", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Text(
                request.device.label.ifBlank { "Appareil" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                request.problemDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val who = when (role) {
                Role.REQUESTER -> request.assignedTo?.name?.let { "Réparateur·rice : $it" } ?: "Pas encore affectée"
                else -> "Par ${request.requester?.name ?: "—"}" + (request.assignedTo?.name?.let { " · Affectée à $it" } ?: " · Non affectée")
            }
            Text(
                "$categoryName · ${formatDateTime(request.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DraftCard(draft: DraftEntity, categoryName: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Brouillon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOf(draft.deviceType, draft.brand, draft.model).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sans titre" },
                style = MaterialTheme.typography.titleMedium,
            )
            if (draft.problemDescription.isNotBlank()) {
                Text(draft.problemDescription, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                (if (draft.category.isNotBlank()) "$categoryName · " else "") + "Modifié ${formatDateTime(draft.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(hasHiddenResults: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(
            if (hasHiddenResults) "Aucune demande ne correspond à la recherche ou aux filtres." else "Aucune demande pour le moment.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
