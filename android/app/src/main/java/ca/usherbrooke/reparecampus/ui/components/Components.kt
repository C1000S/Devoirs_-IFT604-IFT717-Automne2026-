package ca.usherbrooke.reparecampus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.usherbrooke.reparecampus.data.local.entity.RequestEntity
import ca.usherbrooke.reparecampus.ui.theme.Amber
import ca.usherbrooke.reparecampus.ui.theme.Green
import ca.usherbrooke.reparecampus.ui.theme.Grey
import ca.usherbrooke.reparecampus.ui.theme.NavyLight
import ca.usherbrooke.reparecampus.ui.theme.Red
import ca.usherbrooke.reparecampus.ui.theme.Teal
import ca.usherbrooke.reparecampus.util.Constants
import ca.usherbrooke.reparecampus.util.Labels

// Couleur associée à chaque état du cycle de vie.
fun statusColor(status: String): Color = when (status) {
    Constants.STATUS_TO_TRIAGE -> Grey
    Constants.STATUS_ASSIGNED -> NavyLight
    Constants.STATUS_IN_DIAGNOSIS -> Teal
    Constants.STATUS_WAITING -> Amber
    Constants.STATUS_IN_REPAIR -> NavyLight
    Constants.STATUS_READY -> Green
    Constants.STATUS_CLOSED -> Grey
    Constants.STATUS_CANCELLED -> Red
    Constants.STATUS_NOT_REPAIRABLE -> Red
    else -> Grey
}

// Petite pastille colorée affichant le libellé d'un code.
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) =
    Pill(Labels.status(status), statusColor(status), modifier)

@Composable
fun PriorityChip(priority: String, modifier: Modifier = Modifier) {
    val color = when (priority) {
        "high" -> Red
        "low" -> Grey
        else -> Teal
    }
    Pill(Labels.priority(priority), color, modifier)
}

// Carte résumant une demande dans la liste.
@Composable
fun RequestCard(request: RequestEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = listOf(request.deviceType, request.deviceBrand)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "Appareil" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(request.status)
            }
            if (request.categoryName.isNotBlank()) {
                Text(
                    text = request.categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = request.problemDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriorityChip(request.priority)
                Spacer(Modifier.width(8.dp))
                if (request.hasEstimate) {
                    Pill("Devis", Amber)
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = request.assignedToName.ifBlank { "Non assignée" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Carte-section titrée, pour l'écran de détail.
@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

// Ligne « libellé : valeur ».
@Composable
fun LabeledValue(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label : ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            if (onRetry != null) {
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Réessayer")
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Inbox,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Bandeau « hors ligne » affiché en haut des listes lorsque le réseau est absent.
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(color = Amber.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Mode hors ligne — données en cache",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
