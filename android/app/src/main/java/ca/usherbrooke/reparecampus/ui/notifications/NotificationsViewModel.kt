package ca.usherbrooke.reparecampus.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val refreshing: Boolean = false,
    val online: Boolean = true,
    val error: String? = null
)

// Notifications persistantes : observe le cache Room, rafraîchit depuis le serveur,
// marque comme lu. Le travailleur d'arrière-plan alimente aussi ce cache.
class NotificationsViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(NotificationsUiState())
    val ui: StateFlow<NotificationsUiState> = _ui.asStateFlow()

    val notifications = container.notificationRepository.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            container.connectivity.isOnline.collect { online -> _ui.update { it.copy(online = online) } }
        }
        refresh()
    }

    fun refresh() {
        if (_ui.value.refreshing) return
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true, error = null) }
            container.notificationRepository.refresh()
                .onFailure { e -> _ui.update { it.copy(error = e.toUserMessage()) } }
            _ui.update { it.copy(refreshing = false) }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            container.notificationRepository.markRead(id)
            container.notificationRepository.refresh()
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            container.notificationRepository.markAllRead()
            container.notificationRepository.refresh()
        }
    }
}
