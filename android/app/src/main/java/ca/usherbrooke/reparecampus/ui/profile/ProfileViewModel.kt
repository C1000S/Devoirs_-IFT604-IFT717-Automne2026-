package ca.usherbrooke.reparecampus.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.data.session.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Profil de la personne connectée et déconnexion.
class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    val session: StateFlow<Session?> = container.authRepository.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val baseUrl: StateFlow<String> = container.session.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun logout() {
        viewModelScope.launch { container.authRepository.logout() }
    }
}
