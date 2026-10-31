package ca.udes.reparecampus.data.repository

import ca.udes.reparecampus.data.remote.RepareCampusApi
import ca.udes.reparecampus.data.remote.apiCall
import ca.udes.reparecampus.data.remote.dto.AuthResponse
import ca.udes.reparecampus.data.remote.dto.LoginRequest
import ca.udes.reparecampus.data.remote.dto.RegisterRequest
import ca.udes.reparecampus.data.session.SessionStore
import ca.udes.reparecampus.domain.Role
import ca.udes.reparecampus.domain.Session

class AuthRepository(
    private val api: RepareCampusApi,
    private val sessionStore: SessionStore,
) {
    suspend fun login(email: String, password: String): Result<Session> =
        apiCall { api.login(LoginRequest(email.trim(), password)) }.mapCatching { saveSession(it) }

    suspend fun register(name: String, email: String, password: String): Result<Session> =
        apiCall { api.register(RegisterRequest(name.trim(), email.trim(), password)) }.mapCatching { saveSession(it) }

    /**
     * Revalide le profil auprès de GET /api/auth/me (rôle modifié par un admin, etc.).
     * Hors ligne, on conserve la session locale ; un 401 déclenche la déconnexion via l'intercepteur.
     */
    suspend fun refreshProfile(current: Session) {
        apiCall { api.me() }.onSuccess { user ->
            sessionStore.save(current.copy(name = user.name, email = user.email, role = Role.fromApi(user.role)))
        }
    }

    suspend fun logout() = sessionStore.clear()

    private suspend fun saveSession(response: AuthResponse): Session {
        val session = Session(
            token = response.token,
            userId = response.user.id,
            name = response.user.name,
            email = response.user.email,
            role = Role.fromApi(response.user.role),
        )
        sessionStore.save(session)
        return session
    }
}
