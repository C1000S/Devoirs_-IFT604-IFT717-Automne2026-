package ca.udes.reparecampus.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.udes.reparecampus.data.model.Priority
import ca.udes.reparecampus.data.model.RequestStatus

@Composable
fun StatusChip(status: RequestStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        RequestStatus.TO_TRIAGE -> scheme.surfaceVariant to scheme.onSurfaceVariant
        RequestStatus.ASSIGNED, RequestStatus.IN_DIAGNOSIS -> scheme.primaryContainer to scheme.onPrimaryContainer
        RequestStatus.WAITING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        RequestStatus.IN_REPAIR -> scheme.secondaryContainer to scheme.onSecondaryContainer
        RequestStatus.READY_FOR_PICKUP -> Color(0xFFC8F0D0) to Color(0xFF0B3D1A)
        RequestStatus.CLOSED -> scheme.surfaceVariant to scheme.onSurfaceVariant
        RequestStatus.CANCELLED, RequestStatus.NOT_REPAIRABLE -> scheme.errorContainer to scheme.onErrorContainer
    }
    Pill(status.label, container, content, modifier)
}

@Composable
fun PriorityChip(priority: Priority, modifier: Modifier = Modifier) {
    if (priority == Priority.NORMAL) return
    val scheme = MaterialTheme.colorScheme
    val (container, content) = if (priority == Priority.HIGH) scheme.error to scheme.onError
    else scheme.surfaceVariant to scheme.onSurfaceVariant
    Pill("Priorité ${priority.label.lowercase()}", container, content, modifier)
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
