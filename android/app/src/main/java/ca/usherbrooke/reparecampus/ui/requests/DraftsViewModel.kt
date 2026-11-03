package ca.usherbrooke.reparecampus.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.data.local.entity.DraftEntity
import ca.usherbrooke.reparecampus.data.remote.dto.ContactDto
import ca.usherbrooke.reparecampus.data.remote.dto.CreateRequestBody
import ca.usherbrooke.reparecampus.data.remote.dto.DeviceDto
import ca.usherbrooke.reparecampus.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftsUiState(
    val online: Boolean = true,
    val submittingId: Long? = null,
    val error: String? = null,
    val message: String? = null
)

// Brouillons créés hors ligne : on peut les renvoyer au serveur ou les supprimer.
class DraftsViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(DraftsUiState())
    val ui: StateFlow<DraftsUiState> = _ui.asStateFlow()

    val drafts = container.draftRepository.drafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            container.connectivity.isOnline.collect { online -> _ui.update { it.copy(online = online) } }
        }
    }

    fun submit(draft: DraftEntity) {
        if (_ui.value.submittingId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(submittingId = draft.localId, error = null, message = null) }
            val body = CreateRequestBody(
                categoryId = draft.categoryId,
                device = DeviceDto(
                    draft.deviceType,
                    draft.deviceBrand.ifBlank { null },
                    draft.deviceModel.ifBlank { null }
                ),
                problemDescription = draft.problemDescription,
                priority = draft.priority,
                contact = draft.contactValue.ifBlank { null }?.let { ContactDto("email", it) }
            )
            container.requestRepository.create(body)
                .onSuccess {
                    container.draftRepository.delete(draft)
                    _ui.update { it.copy(message = "Brouillon envoyé.") }
                }
                .onFailure { e -> _ui.update { it.copy(error = e.toUserMessage()) } }
            _ui.update { it.copy(submittingId = null) }
        }
    }

    fun delete(draft: DraftEntity) {
        viewModelScope.launch { container.draftRepository.delete(draft) }
    }

    fun consumeMessages() = _ui.update { it.copy(error = null, message = null) }
}
