package ca.usherbrooke.reparecampus.data.remote.dto

// Corps de requêtes envoyés à l'API.

data class LoginBody(val email: String, val password: String)

data class RegisterBody(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val phone: String? = null,
    val skills: List<String>? = null
)

data class CreateRequestBody(
    val categoryId: String,
    val device: DeviceDto,
    val problemDescription: String,
    val priority: String? = "normal",
    val contact: ContactDto? = null,
    val photos: List<String>? = null
)

data class AssignBody(val repairerId: String, val slotId: String? = null)

data class StatusBody(val status: String, val reason: String? = null)

data class DiagnosisBody(val notes: String)

data class EstimateBody(val amount: Double, val currency: String? = "CAD", val details: String? = null)

data class EstimateResponseBody(val decision: String)

data class InterventionBody(val notes: String, val minutesSpent: Int? = 0)

data class PartBody(
    val name: String,
    val quantity: Int? = 1,
    val unitCost: Double? = 0.0,
    val status: String? = "needed"
)
