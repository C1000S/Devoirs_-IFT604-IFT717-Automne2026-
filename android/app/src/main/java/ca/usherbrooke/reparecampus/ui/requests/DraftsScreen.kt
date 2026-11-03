package ca.usherbrooke.reparecampus.ui.requests

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.usherbrooke.reparecampus.ui.AppViewModelProvider
import ca.usherbrooke.reparecampus.ui.components.EmptyState
import ca.usherbrooke.reparecampus.ui.components.OfflineBanner
import ca.usherbrooke.reparecampus.ui.components.PriorityChip
import ca.usherbrooke.reparecampus.util.Labels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    viewModel: DraftsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message, ui.error) {
        val text = ui.message ?: ui.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Brouillons hors ligne") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!ui.online) OfflineBanner()
            if (drafts.isEmpty()) {
                EmptyState("Aucun brouillon. Les demandes créées hors ligne apparaîtront ici.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(drafts, key = { it.localId }) { draft ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    listOf(draft.deviceType, draft.deviceBrand)
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (draft.categoryName.isNotBlank()) {
                                    Text(
                                        draft.categoryName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.size(4.dp))
                                Text(draft.problemDescription, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.size(8.dp))
                                PriorityChip(draft.priority)
                                Spacer(Modifier.size(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.submit(draft) },
                                        enabled = ui.online && ui.submittingId == null
                                    ) {
                                        if (ui.submittingId == draft.localId) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Text("Envoyer")
                                        }
                                    }
                                    OutlinedButton(onClick = { viewModel.delete(draft) }) {
                                        Text("Supprimer")
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
