package ca.usherbrooke.reparecampus.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.usherbrooke.reparecampus.data.remote.dto.UserDto
import ca.usherbrooke.reparecampus.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = Constants.PREFS_NAME)

// Session connectée : identité et jeton de la personne.
data class Session(
    val token: String,
    val userId: String,
    val userName: String,
    val userRole: String
)

// Persiste la session et l'URL du serveur via DataStore.
// Conserve aussi le jeton en mémoire pour l'intercepteur réseau (lecture synchrone).
class SessionManager(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    var cachedToken: String? = null
        private set

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
    }

    val sessionFlow: Flow<Session?> = appContext.dataStore.data.map { prefs ->
        val token = prefs[KEY_TOKEN]
        if (token.isNullOrBlank()) {
            null
        } else {
            Session(
                token = token,
                userId = prefs[KEY_USER_ID].orEmpty(),
                userName = prefs[KEY_USER_NAME].orEmpty(),
                userRole = prefs[KEY_USER_ROLE] ?: Constants.ROLE_REQUESTER
            )
        }
    }

    // Charge le jeton en cache au démarrage (appelé une fois par le conteneur).
    suspend fun warmUp() {
        cachedToken = appContext.dataStore.data.first()[KEY_TOKEN]
    }

    suspend fun save(token: String, user: UserDto) {
        cachedToken = token
        appContext.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USER_ID] = user.id
            prefs[KEY_USER_NAME] = user.name
            prefs[KEY_USER_ROLE] = user.role
        }
    }

    suspend fun clear() {
        cachedToken = null
        appContext.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_ROLE)
        }
    }

    val baseUrlFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: Constants.DEFAULT_BASE_URL
    }

    suspend fun getBaseUrl(): String =
        appContext.dataStore.data.first()[KEY_BASE_URL] ?: Constants.DEFAULT_BASE_URL

    suspend fun setBaseUrl(url: String) {
        appContext.dataStore.edit { it[KEY_BASE_URL] = normalize(url) }
    }

    private fun normalize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
