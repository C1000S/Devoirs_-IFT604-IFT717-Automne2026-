package ca.usherbrooke.reparecampus.data.remote.dto

// DTOs correspondant aux réponses JSON de l'API RépareCampus.
// Les champs optionnels sont nullables car certaines réponses ne peuplent pas tout.

data class UserDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val role: String = "requester",
    val phone: String? = null
)

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class CategoryDto(
    val id: String,
    val name: String,
    val slug: String,
    val description: String? = null,
    val active: Boolean = true
)

data class DeviceDto(
    val type: String,
    val brand: String? = null,
    val model: String? = null
)

data class ContactDto(
    val method: String? = "email",
    val value: String? = ""
)

data class DiagnosisDto(
    val notes: String,
    val createdAt: String? = null
)

data class EstimateDto(
    val amount: Double,
    val currency: String? = "CAD",
    val details: String? = null,
    val status: String = "pending",
    val createdAt: String? = null,
    val respondedAt: String? = null
)

data class PartDto(
    val id: String? = null,
    val name: String,
    val quantity: Int = 1,
    val unitCost: Double = 0.0,
    val status: String = "needed"
)

data class InterventionDto(
    val id: String? = null,
    val notes: String,
    val minutesSpent: Int = 0,
    val performedAt: String? = null
)

data class EventDto(
    val id: String,
    val type: String,
    val message: String,
    val actor: UserDto? = null,
    val createdAt: String? = null
)

// Une demande de réparation, avec ses références éventuellement peuplées.
data class RequestDto(
    val id: String,
    val requester: UserDto? = null,
    val category: CategoryDto? = null,
    val device: DeviceDto,
    val problemDescription: String,
    val photos: List<String> = emptyList(),
    val contact: ContactDto? = null,
    val priority: String = "normal",
    val status: String = "to_triage",
    val assignedTo: UserDto? = null,
    val diagnosis: DiagnosisDto? = null,
    val estimate: EstimateDto? = null,
    val parts: List<PartDto> = emptyList(),
    val interventions: List<InterventionDto> = emptyList(),
    val closureReason: String? = null,
    val lastActivityAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // Présent uniquement dans la réponse de détail.
    val events: List<EventDto>? = null
)

data class RequestPage(
    val items: List<RequestDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 0
)

data class NotificationDto(
    val id: String,
    val request: String? = null,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean = false,
    val createdAt: String? = null
)

data class NotificationPage(
    val items: List<NotificationDto> = emptyList(),
    val unreadCount: Int = 0
)

// Créneau horaire de la clinique (le réparateur peut être peuplé).
data class SlotDto(
    val id: String,
    val start: String? = null,
    val end: String? = null,
    val capacity: Int? = null,
    val repairer: UserDto? = null,
    val notes: String? = null
)
