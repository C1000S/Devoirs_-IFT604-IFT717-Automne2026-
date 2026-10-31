package ca.udes.reparecampus.data.remote.dto

import ca.udes.reparecampus.data.model.Device
import kotlinx.serialization.Serializable

@Serializable
data class CreateRequestBody(
    val category: String,
    val device: Device,
    val problemDescription: String,
    val priority: String = "normal",
    val contactMethod: String = "",
)

@Serializable data class EstimateResponseBody(val accept: Boolean)
@Serializable data class StatusBody(val status: String, val reason: String? = null)
@Serializable data class DiagnosisBody(val text: String)
@Serializable data class EstimateBody(val cost: Double, val description: String = "")
@Serializable data class InterventionBody(val description: String)
@Serializable data class AssignBody(val assignedTo: String)
