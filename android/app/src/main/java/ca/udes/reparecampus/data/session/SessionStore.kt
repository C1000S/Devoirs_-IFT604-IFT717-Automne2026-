package ca.udes.reparecampus.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.udes.reparecampus.domain.Role
import ca.udes.reparecampus.domain.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** État de la session tel que vu par l'UI. */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val session: Session) : SessionState
}

/**
 * Persiste le jeton JWT et le profil (dont le rôle) dans DataStore, stockage
 * privé de l'application. Aucune donnée sensible n'est codée en dur.
 */
class SessionStore(private val context: Context, scope: CoroutineScope) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val NAME = stringPreferencesKey("name")
        val EMAIL = stringPreferencesKey("email")
        val ROLE = stringPreferencesKey("role")
    }

    val state: StateFlow<SessionState> = context.sessionDataStore.data
        .map { prefs ->
            val token = prefs[Keys.TOKEN]
            if (token == null) {
                SessionState.LoggedOut
            } else {
                SessionState.LoggedIn(
                    Session(
                        token = token,
                        userId = prefs[Keys.USER_ID].orEmpty(),
                        name = prefs[Keys.NAME].orEmpty(),
                        email = prefs[Keys.EMAIL].orEmpty(),
                        role = Role.fromApi(prefs[Keys.ROLE]),
                    )
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /** Jeton courant, lu de façon synchrone par l'intercepteur OkHttp. */
    val currentToken: String?
        get() = (state.value as? SessionState.LoggedIn)?.session?.token

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = session.token
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.NAME] = session.name
            prefs[Keys.EMAIL] = session.email
            prefs[Keys.ROLE] = session.role.apiValue
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
