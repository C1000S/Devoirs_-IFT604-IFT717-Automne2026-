package ca.usherbrooke.reparecampus.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.data.local.entity.DraftEntity
import ca.usherbrooke.reparecampus.data.remote.dto.ContactDto
import ca.usherbrooke.reparecampus.data.remote.dto.CreateRequestBody
import ca.usherbrooke.reparecampus.data.remote.dto.DeviceDto
import ca.usherbrooke.reparecampus.util.toUserMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CreateEvent {
    data object Created : CreateEvent
    data object DraftSaved : CreateEvent
}

data class CreateUiState(
    val categoryId: String = "",
    val categoryName: String = "",
    val deviceType: String = "",
    val deviceBrand: String = "",
    val deviceModel: String = "",
    val problemDescription: String = "",
    val priority: String = "normal",
    val contactValue: String = "",
    val loading: Boolean = false,
    val online: Boolean = true,
    val error: String? = null
) {
    val canSubmit: Boolean
        get() = categoryId.isNotBlank() && deviceType.isNotBlank() && problemDescription.length >= 5
}

// Création d'une demande. En ligne : envoi direct au serveur. Hors ligne (ou au choix) :
// enregistrement d'un brouillon local Room, renvoyé plus tard depuis l'écran « Brouillons ».
class CreateRequestViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    private val _events = Channel<CreateEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val categories = container.categoryRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { container.categoryRepository.refresh() }
        viewModelScope.launch {
            container.connectivity.isOnline.collect { online -> _state.update { it.copy(online = online) } }
        }
    }

    fun onCategory(id: String, name: String) = _state.update { it.copy(categoryId = id, categoryName = name) }
    fun onDeviceType(v: String) = _state.update { it.copy(deviceType = v) }
    fun onDeviceBrand(v: String) = _state.update { it.copy(deviceBrand = v) }
    fun onDeviceModel(v: String) = _state.update { it.copy(deviceModel = v) }
    fun onProblem(v: String) = _state.update { it.copy(problemDescription = v, error = null) }
    fun onPriority(v: String) = _state.update { it.copy(priority = v) }
    fun onContact(v: String) = _state.update { it.copy(contactValue = v) }

    // Envoi au serveur.
    fun submit() {
        val s = _state.value
        if (!s.canSubmit || s.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val body = CreateRequestBody(
                categoryId = s.categoryId,
                device = DeviceDto(s.deviceType, s.deviceBrand.ifBlank { null }, s.deviceModel.ifBlank { null }),
                problemDescription = s.problemDescription,
                priority = s.priority,
                contact = s.contactValue.ifBlank { null }?.let { ContactDto("email", it) }
            )
            container.requestRepository.create(body)
                .onSuccess { _events.send(CreateEvent.Created) }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
            _state.update { it.copy(loading = false) }
        }
    }

    // Enregistrement en brouillon local (toujours disponible, y compris hors ligne).
    fun saveDraft() {
        val s = _state.value
        if (!s.canSubmit) {
            _state.update { it.copy(error = "Catégorie, type d'appareil et description sont requis.") }
            return
        }
        viewModelScope.launch {
            container.draftRepository.save(
                DraftEntity(
                    categoryId = s.categoryId,
                    categoryName = s.categoryName,
                    deviceType = s.deviceType,
                    deviceBrand = s.deviceBrand,
                    deviceModel = s.deviceModel,
                    problemDescription = s.problemDescription,
                    priority = s.priority,
                    contactValue = s.contactValue,
                    createdAt = System.currentTimeMillis()
                )
            )
            _events.send(CreateEvent.DraftSaved)
        }
    }
}
