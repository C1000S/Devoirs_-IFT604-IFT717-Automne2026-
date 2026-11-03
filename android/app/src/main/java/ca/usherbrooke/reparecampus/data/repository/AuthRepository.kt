package ca.usherbrooke.reparecampus.data.repository

import ca.usherbrooke.reparecampus.data.local.AppDatabase
import ca.usherbrooke.reparecampus.data.remote.ApiService
import ca.usherbrooke.reparecampus.data.remote.dto.AuthResponse
import ca.usherbrooke.reparecampus.data.remote.dto.LoginBody
import ca.usherbrooke.reparecampus.data.remote.dto.RegisterBody
import ca.usherbrooke.reparecampus.data.session.Session
import ca.usherbrooke.reparecampus.data.session.SessionManager
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val api: () -> ApiService,
    private val session: SessionManager,
    private val db: AppDatabase
) {
    val sessionFlow: Flow<Session?> = session.sessionFlow

    suspend fun login(email: String, password: String): Result<AuthResponse> = runCatching {
        val response = api().login(LoginBody(email.trim(), password))
        session.save(response.token, response.user)
        response
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: String,
        skills: List<String>?
    ): Result<AuthResponse> = runCatching {
        val response = api().register(RegisterBody(name, email.trim(), password, role, skills = skills))
        session.save(response.token, response.user)
        response
    }

    suspend fun logout() {
        session.clear()
        // Vide le cache local propre à la session précédente.
        db.requestDao().clear()
        db.notificationDao().clear()
    }
}
