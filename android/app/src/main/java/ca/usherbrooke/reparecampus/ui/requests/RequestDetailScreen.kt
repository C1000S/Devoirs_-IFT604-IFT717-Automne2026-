package ca.usherbrooke.reparecampus.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.usherbrooke.reparecampus.data.remote.dto.RequestDto
import ca.usherbrooke.reparecampus.ui.AppViewModelProvider
import ca.usherbrooke.reparecampus.ui.components.ErrorState
import ca.usherbrooke.reparecampus.ui.components.LabeledValue
import ca.usherbrooke.reparecampus.ui.components.LoadingState
import ca.usherbrooke.reparecampus.ui.components.OfflineBanner
import ca.usherbrooke.reparecampus.ui.components.PriorityChip
import ca.usherbrooke.reparecampus.ui.components.SectionCard
import ca.usherbrooke.reparecampus.ui.components.StatusChip
import ca.usherbrooke.reparecampus.util.Constants
import ca.usherbrooke.reparecampus.util.Labels

// Dialogues d'action possibles depuis le détail.
private enum class Dialog { NONE, DIAGNOSIS, ESTIMATE, INTERVENTION, PART, ASSIGN }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RequestDetailScreen(
    onBack: () -> Unit,
    viewModel: RequestDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var statusTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Demande") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualiser")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.online) OfflineBanner()
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())

            val req = state.request
            when {
                state.loading && req == null -> LoadingState()
                req == null -> ErrorState(state.error ?: "Demande introuvable.", onRetry = viewModel::load)
                else -> DetailContent(
                    req = req,
                    state = state,
                    viewModel = viewModel,
                    onOpenDialog = { d -> if (d == Dialog.ASSIGN) viewModel.loadDirectory(); dialog = d },
                    onStatus = { target -> statusTarget = target }
                )
            }
        }
    }

    // --- Dialogues ---
    when (dialog) {
        Dialog.DIAGNOSIS -> SingleFieldDialog(
            title = "Diagnostic", label = "Constats", confirm = "Enregistrer",
            onConfirm = { viewModel.addDiagnosis(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE }
        )
        Dialog.ESTIMATE -> EstimateDialog(
            onConfirm = { amount, details -> viewModel.addEstimate(amount, details); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE }
        )
        Dialog.INTERVENTION -> InterventionDialog(
            onConfirm = { notes, min -> viewModel.addIntervention(notes, min); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE }
        )
        Dialog.PART -> PartDialog(
            onConfirm = { n, q, c -> viewModel.addPart(n, q, c); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE }
        )
        Dialog.ASSIGN -> AssignDialog(
            state = state,
            onConfirm = { repairerId, slotId -> viewModel.assign(repairerId, slotId); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE }
        )
        Dialog.NONE -> Unit
    }

    // Dialogue de changement d'état (raison facultative).
    statusTarget?.let { target ->
        SingleFieldDialog(
            title = "Passer à « ${Labels.status(target)} »",
            label = "Motif (facultatif)",
            confirm = "Confirmer",
            required = false,
            onConfirm = { reason -> viewModel.changeStatus(target, reason.ifBlank { null }); statusTarget = null },
            onDismiss = { statusTarget = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    req: RequestDto,
    state: DetailUiState,
    viewModel: RequestDetailViewModel,
    onOpenDialog: (Dialog) -> Unit,
    onStatus: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Appareil & problème.
        SectionCard("Appareil") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                StatusChip(req.status)
                Spacer(Modifier.size(8.dp))
                PriorityChip(req.priority)
            }
            Spacer(Modifier.size(8.dp))
            LabeledValue("Type", req.device.type)
            req.device.brand?.let { LabeledValue("Marque", it) }
            req.device.model?.let { LabeledValue("Modèle", it) }
            req.category?.let { LabeledValue("Catégorie", it.name) }
            req.requester?.let { LabeledValue("Demandeur·euse", it.name) }
            LabeledValue("Assignée à", req.assignedTo?.name ?: "—")
            Spacer(Modifier.size(6.dp))
            Text(req.problemDescription, style = MaterialTheme.typography.bodyMedium)
        }

        // Diagnostic.
        if (req.diagnosis != null || viewModel.isStaff) {
            SectionCard("Diagnostic") {
                if (req.diagnosis != null) {
                    Text(req.diagnosis.notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Aucun diagnostic.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (viewModel.isStaff && !isTerminal(req.status)) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { onOpenDialog(Dialog.DIAGNOSIS) }) {
                        Text(if (req.diagnosis == null) "Ajouter un diagnostic" else "Mettre à jour")
                    }
                }
            }
        }

        // Estimation.
        if (req.estimate != null || viewModel.isStaff) {
            SectionCard("Estimation") {
                val est = req.estimate
                if (est != null) {
                    LabeledValue("Montant", "%.2f %s".format(est.amount, est.currency ?: "CAD"))
                    LabeledValue("État", estimateLabel(est.status))
                    est.details?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    // Le demandeur répond à une estimation en attente.
                    if (state.role == Constants.ROLE_REQUESTER && est.status == "pending") {
                        Spacer(Modifier.size(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.respondEstimate("accepted") }) { Text("Accepter") }
                            OutlinedButton(onClick = { viewModel.respondEstimate("refused") }) { Text("Refuser") }
                        }
                    }
                } else {
                    Text("Aucune estimation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (viewModel.isStaff && !isTerminal(req.status)) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { onOpenDialog(Dialog.ESTIMATE) }) {
                        Text(if (est == null) "Ajouter une estimation" else "Nouvelle estimation")
                    }
                }
            }
        }

        // Pièces.
        if (req.parts.isNotEmpty() || viewModel.isStaff) {
            SectionCard("Pièces") {
                if (req.parts.isEmpty()) {
                    Text("Aucune pièce.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    req.parts.forEach { p ->
                        LabeledValue(p.name, "x${p.quantity} · %.2f \$".format(p.unitCost))
                    }
                }
                if (viewModel.isStaff && !isTerminal(req.status)) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { onOpenDialog(Dialog.PART) }) { Text("Ajouter une pièce") }
                }
            }
        }

        // Interventions.
        if (req.interventions.isNotEmpty() || viewModel.isStaff) {
            SectionCard("Interventions") {
                if (req.interventions.isEmpty()) {
                    Text("Aucune intervention.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    req.interventions.forEach { i ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(i.notes, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${i.minutesSpent} min",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (viewModel.isStaff && !isTerminal(req.status)) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { onOpenDialog(Dialog.INTERVENTION) }) {
                        Text("Consigner une intervention")
                    }
                }
            }
        }

        // Actions de cycle de vie.
        if (viewModel.isStaff && !isTerminal(req.status)) {
            SectionCard("Actions") {
                // Prise en charge si non assignée.
                if (req.assignedTo == null) {
                    Button(onClick = viewModel::claim, enabled = !state.working) {
                        Text("Prendre en charge")
                    }
                    Spacer(Modifier.size(8.dp))
                }
                if (viewModel.canAssign) {
                    OutlinedButton(onClick = { onOpenDialog(Dialog.ASSIGN) }, enabled = !state.working) {
                        Text("Affecter à un·e réparateur·rice")
                    }
                    Spacer(Modifier.size(8.dp))
                }
                val next = viewModel.allowedNext(req.status)
                if (next.isNotEmpty()) {
                    Text("Changer l'état :", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.size(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        next.forEach { target ->
                            AssistChip(
                                onClick = { onStatus(target) },
                                label = { Text(Labels.status(target)) }
                            )
                        }
                    }
                }
            }
        }

        // Historique.
        val events = req.events
        if (!events.isNullOrEmpty()) {
            SectionCard("Historique") {
                events.forEach { e ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(e.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(e.actor?.name, e.createdAt).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}

private fun isTerminal(status: String): Boolean =
    status == Constants.STATUS_CLOSED || status == Constants.STATUS_CANCELLED

private fun estimateLabel(status: String): String = when (status) {
    "pending" -> "En attente"
    "accepted" -> "Acceptée"
    "refused" -> "Refusée"
    else -> status
}

// --- Dialogues réutilisables ---

@Composable
private fun SingleFieldDialog(
    title: String,
    label: String,
    confirm: String,
    required: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = !required || text.isNotBlank()) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun EstimateDialog(onConfirm: (Double, String?) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    val value = amount.replace(",", ".").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle estimation") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Montant (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = details, onValueChange = { details = it },
                    label = { Text("Détails (facultatif)") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let { onConfirm(it, details.ifBlank { null }) } },
                enabled = value != null && value > 0
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun InterventionDialog(onConfirm: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Intervention") },
        text = {
            Column {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(notes, minutes.toIntOrNull() ?: 0) },
                enabled = notes.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun PartDialog(onConfirm: (String, Int, Double) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var cost by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pièce") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it.filter(Char::isDigit) },
                    label = { Text("Quantité") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it },
                    label = { Text("Coût unitaire") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, qty.toIntOrNull() ?: 1, cost.replace(",", ".").toDoubleOrNull() ?: 0.0)
                },
                enabled = name.isNotBlank()
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun AssignDialog(
    state: DetailUiState,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Affecter la demande") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.repairers.isEmpty()) {
                    Text("Aucun·e réparateur·rice disponible pour cette catégorie.")
                } else {
                    state.repairers.forEach { r ->
                        FilterChip(
                            selected = selected == r.id,
                            onClick = { selected = r.id },
                            label = { Text(r.name) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onConfirm(it, null) } },
                enabled = selected != null
            ) { Text("Affecter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
