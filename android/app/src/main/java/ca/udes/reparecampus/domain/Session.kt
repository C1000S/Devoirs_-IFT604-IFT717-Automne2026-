package ca.udes.reparecampus.domain

/** Session locale : jeton JWT + profil de la personne connectée. */
data class Session(
    val token: String,
    val userId: String,
    val name: String,
    val email: String,
    val role: Role,
)
