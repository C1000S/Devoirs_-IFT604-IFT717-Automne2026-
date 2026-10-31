package ca.udes.reparecampus.ui.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.udes.reparecampus.data.model.Category
import ca.udes.reparecampus.data.model.Priority
import ca.udes.reparecampus.ui.components.formatDateTime
import ca.udes.reparecampus.ui.detail.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRequestScreen(viewModel: CreateRequestViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    val f = state.form
    val err = state.showErrors

    LaunchedEffect(state.createdRequestId) { state.createdRequestId?.let(onCreated) }
    LaunchedEffect(state.queuedOffline) { if (state.queuedOffline) onBack() }
    val leave = { viewModel.saveDraft(); onBack() }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.draftId != null) "Brouillon de demande" else "Nouvelle demande") },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour (brouillon conservé)") } },
                actions = {
                    if (state.draftId != null) {
                        IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Filled.Delete, "Supprimer le brouillon") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryField(categories, f.category, isError = err && f.category.isBlank()) { c -> viewModel.update { it.copy(category = c) } }
                OutlinedTextField(
                    value = f.deviceType,
                    onValueChange = { v -> viewModel.update { it.copy(deviceType = v) } },
                    label = { Text("Type d'appareil *") },
                    placeholder = { Text("ex. téléphone, vélo, portable") },
                    isError = err && f.deviceType.isBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = f.brand,
                        onValueChange = { v -> viewModel.update { it.copy(brand = v) } },
                        label = { Text("Marque") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = f.model,
                        onValueChange = { v -> viewModel.update { it.copy(model = v) } },
                        label = { Text("Modèle") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = f.problemDescription,
                    onValueChange = { v -> viewModel.update { it.copy(problemDescription = v) } },
                    label = { Text("Description du problème *") },
                    isError = err && f.problemDescription.isBlank(),
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Priorité", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = f.priority == p,
                            onClick = { viewModel.update { it.copy(priority = p) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size),
                        ) { Text(p.label) }
                    }
                }
                OutlinedTextField(
                    value = f.contactMethod,
                    onValueChange = { v -> viewModel.update { it.copy(contactMethod = v) } },
                    label = { Text("Moyen de contact préféré") },
                    placeholder = { Text("ex. courriel, téléphone, Teams") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    state.draftSavedAt?.let { "Brouillon enregistré sur l'appareil · ${formatDateTime(it)}" }
                        ?: "La saisie est enregistrée automatiquement comme brouillon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = leave, enabled = !state.isSubmitting) { Text("Garder en brouillon") }
                    Button(onClick = viewModel::submit, enabled = !state.isSubmitting) {
                        if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Envoyer la demande")
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            "Supprimer le brouillon ?",
            "Le contenu saisi sera perdu.",
            "Supprimer",
            onConfirm = { confirmDiscard = false; viewModel.discardDraft(onBack) },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(categories: List<Category>, selected: String, isError: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = categories.firstOrNull { it.id == selected }?.name ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Catégorie *") },
            isError = isError,
            supportingText = if (categories.isEmpty()) ({ Text("Catégories indisponibles pour le moment.") }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Column { Text(c.name); if (c.description.isNotBlank()) Text(c.description, style = MaterialTheme.typography.bodySmall) } },
                    onClick = { onSelect(c.id); expanded = false },
                )
            }
        }
    }
}
