package ca.usherbrooke.reparecampus.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.usherbrooke.reparecampus.ui.AppViewModelProvider
import ca.usherbrooke.reparecampus.ui.components.OfflineBanner
import ca.usherbrooke.reparecampus.util.Labels
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRequestScreen(
    onDone: () -> Unit,
    viewModel: CreateRequestViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    // Ferme l'écran après un envoi réussi ou l'enregistrement d'un brouillon.
    LaunchedEffect(Unit) {
        viewModel.events.collect { onDone() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        TopAppBar(
            title = { Text("Nouvelle demande") },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
            }
        )
        if (!state.online) OfflineBanner()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Catégorie.
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = state.categoryName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Catégorie") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) },
                            onClick = { viewModel.onCategory(c.id, c.name); expanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.deviceType,
                onValueChange = viewModel::onDeviceType,
                label = { Text("Type d'appareil *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.deviceBrand,
                onValueChange = viewModel::onDeviceBrand,
                label = { Text("Marque") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.deviceModel,
                onValueChange = viewModel::onDeviceModel,
                label = { Text("Modèle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Priorité", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low", "normal", "high").forEach { p ->
                    FilterChip(
                        selected = state.priority == p,
                        onClick = { viewModel.onPriority(p) },
                        label = { Text(Labels.priority(p)) }
                    )
                }
            }

            OutlinedTextField(
                value = state.problemDescription,
                onValueChange = viewModel::onProblem,
                label = { Text("Description du problème *") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.contactValue,
                onValueChange = viewModel::onContact,
                label = { Text("Contact (courriel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.size(4.dp))
            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit && !state.loading && state.online,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Envoyer la demande")
                }
            }
            OutlinedButton(
                onClick = viewModel::saveDraft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.online) "Enregistrer comme brouillon" else "Enregistrer (hors ligne)")
            }
        }
    }
}
