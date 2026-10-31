package ca.udes.reparecampus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.udes.reparecampus.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Gère la connexion et l'inscription. En cas de succès, la session est enregistrée
 * dans SessionStore ; la navigation réagit à ce changement d'état (pas d'événement à émettre).
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun login() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Courriel et mot de passe requis.") }
            return
        }
        submit { authRepository.login(s.email, s.password) }
    }

    fun register() {
        val s = _state.value
        when {
            s.name.isBlank() || s.email.isBlank() || s.password.isBlank() ->
                _state.update { it.copy(error = "Nom, courriel et mot de passe requis.") }
            !s.email.contains('@') -> _state.update { it.copy(error = "Courriel invalide.") }
            s.password.length < 8 -> _state.update { it.copy(error = "Le mot de passe doit contenir au moins 8 caractères.") }
            else -> submit { authRepository.register(s.name, s.email, s.password) }
        }
    }

    private fun submit(call: suspend () -> Result<*>) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            call()
                // Le ViewModel survit à la session : on vide le formulaire (dont le mot de passe)
                // pour que la personne suivante ne le retrouve pas après une déconnexion.
                .onSuccess { _state.value = AuthUiState() }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
