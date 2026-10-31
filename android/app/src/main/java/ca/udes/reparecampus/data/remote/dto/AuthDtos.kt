package ca.udes.reparecampus.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

/** Réponse de POST /api/auth/login et /api/auth/register. */
@Serializable
data class AuthResponse(val token: String, val user: UserDto)

/** User.toPublicJSON() : { id, name, email, role, skills }. */
@Serializable
data class UserDto(
    @SerialName("id") val id: String,
    val name: String,
    val email: String,
    val role: String,
    val skills: List<String> = emptyList(),
)

/** Corps d'erreur renvoyé par l'API : { "error": "..." }. */
@Serializable
data class ApiErrorBody(val error: String? = null)
