package ca.udes.reparecampus.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.udes.reparecampus.data.model.Priority
import ca.udes.reparecampus.data.model.RepairRequest
import ca.udes.reparecampus.data.sync.RequestAction
import ca.udes.reparecampus.ui.components.OfflineBanner
import ca.udes.reparecampus.ui.components.Pill
import ca.udes.reparecampus.ui.components.PriorityChip
import ca.udes.reparecampus.ui.components.StatusChip
import ca.udes.reparecampus.ui.components.formatDateTime
import ca.udes.reparecampus.ui.components.formatMoney

private enum class DialogKind { STATUS, DIAGNOSIS, ESTIMATE, INTERVENTION, ASSIGN, ACCEPT, REFUSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailScreen(viewModel: RequestDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val repairers by viewModel.repairers.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSync.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<DialogKind?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.request?.device?.label?.ifBlank { null } ?: "Demande") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rafraîchir")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val request = state.request
        Column(Modifier.padding(padding).fillMaxSize()) {
        if (!isOnline) OfflineBanner(lastSync, pendingCount = 0)
        Box(Modifier.fillMaxSize()) {
            if (state.isLoading || state.isSubmitting) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                request == null && state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                request == null -> Text("Demande introuvable.", Modifier.align(Alignment.Center))
                else -> DetailContent(state, request, onAction = { dialog = it })
            }
        }
        }
    }

    val request = state.request ?: return
    val close = { dialog = null }
    fun run(action: RequestAction, msg: String) { viewModel.perform(action, msg); close() }
    when (dialog) {
        DialogKind.STATUS -> ChangeStatusDialog(
            current = request.statusEnum,
            targets = state.actions.statusTargets,
            onConfirm = { s, reason -> run(RequestAction.ChangeStatus(s.apiValue, reason), "État modifié : ${s.label}.") },
            onDismiss = close,
        )
        DialogKind.DIAGNOSIS -> TextInputDialog("Consigner un diagnostic", "Diagnostic", { run(RequestAction.AddDiagnosis(it), "Diagnostic enregistré.") }, close)
        DialogKind.INTERVENTION -> TextInputDialog("Consigner une intervention", "Travail effectué", { run(RequestAction.AddIntervention(it), "Intervention enregistrée.") }, close)
        DialogKind.ESTIMATE -> EstimateDialog({ cost, d -> run(RequestAction.ProposeEstimate(cost, d), "Estimation envoyée.") }, close)
        DialogKind.ASSIGN -> {
            LaunchedEffect(Unit) { viewModel.loadRepairers() }
            AssignDialog(repairers, request.category, request.assignedTo?.id, { u -> run(RequestAction.Assign(u.id, u.name), "Demande affectée à ${u.name}.") }, close)
        }
        DialogKind.ACCEPT -> ConfirmDialog(
            "Accepter l'estimation ?",
            "La réparation de ${formatMoney(request.estimate?.cost ?: 0.0)} sera lancée.",
            "Accepter",
            { run(RequestAction.RespondEstimate(true), "Estimation acceptée.") },
            close,
        )
        DialogKind.REFUSE -> ConfirmDialog(
            "Refuser l'estimation ?",
            "La demande sera marquée « Non réparable » et fermée.",
            "Refuser",
            { run(RequestAction.RespondEstimate(false), "Estimation refusée.") },
            close,
        )
        null -> Unit
    }
}

@Composable
private fun DetailContent(state: RequestDetailUiState, request: RepairRequest, onAction: (DialogKind) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(state, request)
                    WorkCard(request)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.actions.any) ActionsCard(state, onAction)
                    HistoryCard(request)
                    NotificationsCard(state)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoCard(state, request)
                if (state.actions.any) ActionsCard(state, onAction)
                WorkCard(request)
                HistoryCard(request)
                NotificationsCard(state)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text("$label : ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(state: RequestDetailUiState, r: RepairRequest) {
    Section("Demande") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(r.statusEnum)
            PriorityChip(Priority.fromApi(r.priority))
            if (r.isLocal || r.pendingSync) {
                Pill(
                    if (r.isLocal) "En attente d'envoi au serveur" else "Modifications non synchronisées",
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Text(r.problemDescription, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider()
        Field("Appareil", r.device.label.ifBlank { "—" })
        Field("Catégorie", state.categoryName)
        Field("Priorité", Priority.fromApi(r.priority).label)
        Field("Demandeur·euse", r.requester?.name ?: "—")
        Field("Réparateur·rice", r.assignedTo?.name ?: "Non affectée")
        if (r.contactMethod.isNotBlank()) Field("Contact", r.contactMethod)
        Field("Créée le", formatDateTime(r.createdAt))
        Field("Mise à jour", formatDateTime(r.updatedAt))
        r.closedReason?.let { Field("Motif de fermeture", it) }
    }
}

@Composable
private fun WorkCard(r: RepairRequest) {
    if (r.diagnosis?.text == null && !r.hasEstimate && r.interventions.isEmpty()) return
    Section("Suivi technique") {
        r.diagnosis?.text?.let {
            Text("Diagnostic", fontWeight = FontWeight.SemiBold)
            Text(it)
        }
        if (r.hasEstimate) {
            val e = r.estimate!!
            Text("Estimation : ${formatMoney(e.cost!!)}", fontWeight = FontWeight.SemiBold)
            e.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Text(
                when (e.status) {
                    "accepted" -> "Acceptée le ${formatDateTime(e.respondedAt)}"
                    "refused" -> "Refusée le ${formatDateTime(e.respondedAt)}"
                    else -> "En attente de réponse"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (r.interventions.isNotEmpty()) {
            Text("Interventions", fontWeight = FontWeight.SemiBold)
            r.interventions.forEach {
                Text("• ${it.description} — ${it.performedBy?.name ?: ""} ${formatDateTime(it.performedAt)}")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsCard(state: RequestDetailUiState, onAction: (DialogKind) -> Unit) {
    val a = state.actions
    val r = state.request ?: return
    val enabled = !state.isSubmitting
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Actions", style = MaterialTheme.typography.titleMedium)
            if (a.respondToEstimate) {
                Text("Estimation proposée : ${formatMoney(r.estimate?.cost ?: 0.0)}", fontWeight = FontWeight.SemiBold)
                r.estimate?.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.respondToEstimate) {
                    Button(onClick = { onAction(DialogKind.ACCEPT) }, enabled = enabled) { Text("Accepter l'estimation") }
                    OutlinedButton(onClick = { onAction(DialogKind.REFUSE) }, enabled = enabled) { Text("Refuser") }
                }
                if (a.assign) Button(onClick = { onAction(DialogKind.ASSIGN) }, enabled = enabled) { Text(if (r.assignedTo == null) "Affecter" else "Réaffecter") }
                if (a.diagnosis) Button(onClick = { onAction(DialogKind.DIAGNOSIS) }, enabled = enabled) { Text("Diagnostic") }
                if (a.estimate) Button(onClick = { onAction(DialogKind.ESTIMATE) }, enabled = enabled) { Text("Proposer une estimation") }
                if (a.intervention) Button(onClick = { onAction(DialogKind.INTERVENTION) }, enabled = enabled) { Text("Intervention") }
                if (a.statusTargets.isNotEmpty()) OutlinedButton(onClick = { onAction(DialogKind.STATUS) }, enabled = enabled) { Text("Changer l'état") }
            }
        }
    }
}

@Composable
private fun HistoryCard(r: RepairRequest) {
    Section("Historique") {
        if (r.events.isEmpty()) Text("Aucun événement chargé.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        r.events.forEach { e ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Circle, contentDescription = null, modifier = Modifier.size(10.dp).padding(top = 6.dp), tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(e.message, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatDateTime(e.createdAt) + (e.actor?.name?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationsCard(state: RequestDetailUiState) {
    Section("Notifications liées") {
        if (state.notifications.isEmpty()) Text("Aucune notification pour cette demande.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.notifications.forEach { n ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(10.dp).padding(top = 6.dp)) {
                    if (!n.read) Icon(Icons.Filled.Circle, contentDescription = "Non lue", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.error)
                }
                Column {
                    Text(n.title, fontWeight = FontWeight.SemiBold)
                    Text(n.message, style = MaterialTheme.typography.bodyMedium)
                    Text(formatDateTime(n.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
