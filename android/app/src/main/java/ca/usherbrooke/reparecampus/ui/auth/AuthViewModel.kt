package ca.usherbrooke.reparecampus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.util.Constants
import ca.usherbrooke.reparecampus.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val role: String = Constants.ROLE_REQUESTER,
    val serverUrl: String = Constants.DEFAULT_BASE_URL,
    val loading: Boolean = false,
    val error: String? = null
) {
    // Rôles proposés à l'inscription depuis l'application (le demandeur par défaut).
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.length >= 6 &&
            (mode == AuthMode.LOGIN || name.isNotBlank()) && serverUrl.isNotBlank()
}

// Gère la connexion et l'inscription, ainsi que le choix de l'URL du serveur.
class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // Pré-remplit l'URL du serveur persistée.
        viewModelScope.launch {
            _state.update { it.copy(serverUrl = container.session.getBaseUrl()) }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onRole(v: String) = _state.update { it.copy(role = v) }
    fun onServerUrl(v: String) = _state.update { it.copy(serverUrl = v, error = null) }

    fun toggleMode() = _state.update {
        it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN, error = null)
    }

    // Applique l'URL du serveur puis tente la connexion/inscription.
    // La session est persistée par le dépôt ; AppRoot réagit au changement.
    fun submit() {
        val s = _state.value
        if (!s.canSubmit || s.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            container.session.setBaseUrl(s.serverUrl)
            container.rebuildApi(container.session.getBaseUrl())

            val result = if (s.mode == AuthMode.LOGIN) {
                container.authRepository.login(s.email, s.password)
            } else {
                container.authRepository.register(s.name, s.email, s.password, s.role, skills = null)
            }

            result.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
            // En cas de succès : la session change et AppRoot bascule vers l'application.
        }
    }
}
