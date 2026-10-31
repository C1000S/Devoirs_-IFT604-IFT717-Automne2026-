package ca.udes.reparecampus.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ca.udes.reparecampus.data.model.RequestStatus
import ca.udes.reparecampus.data.remote.dto.UserDto

@Composable
fun ChangeStatusDialog(
    current: RequestStatus,
    targets: List<RequestStatus>,
    onConfirm: (RequestStatus, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(targets.first()) }
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Changer l'état") },
        text = {
            Column {
                Text("État actuel : ${current.label}", style = MaterialTheme.typography.bodyMedium)
                targets.forEach { status ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = selected == status, onClick = { selected = status }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == status, onClick = { selected = status })
                        Text(status.label)
                    }
                }
                if (selected.isFinal) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motif (facultatif)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, reason.takeIf { selected.isFinal && it.isNotBlank() }) }) {
                Text("Confirmer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun TextInputDialog(title: String, label: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun EstimateDialog(onConfirm: (Double, String) -> Unit, onDismiss: () -> Unit) {
    var cost by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val parsed = cost.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Proposer une estimation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Coût estimé ($)") },
                    singleLine = true,
                    isError = cost.isNotEmpty() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Détail (pièces, main-d'œuvre…)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "La demande passera à « En attente » jusqu'à la réponse de la personne demandeuse.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onConfirm(it, description.trim()) } }, enabled = parsed != null) { Text("Proposer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun AssignDialog(repairers: List<UserDto>, category: String, currentId: String?, onConfirm: (UserDto) -> Unit, onDismiss: () -> Unit) {
    // Les personnes compétentes pour la catégorie sont proposées en premier.
    val sorted = repairers.sortedByDescending { category in it.skills }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Affecter à une personne réparatrice") },
        text = {
            if (repairers.isEmpty()) {
                CircularProgressIndicator()
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sorted, key = { it.id }) { user ->
                        ListItem(
                            headlineContent = { Text(user.name + if (user.id == currentId) " (actuel)" else "") },
                            supportingContent = {
                                Text(
                                    (if (category in user.skills) "Compétent·e · " else "") +
                                        user.skills.joinToString().ifEmpty { "Aucune compétence déclarée" },
                                )
                            },
                            modifier = Modifier.clickable { onConfirm(user) }.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
