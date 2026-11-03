package ca.usherbrooke.reparecampus.ui.requests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.data.remote.dto.RequestDto
import ca.usherbrooke.reparecampus.data.remote.dto.SlotDto
import ca.usherbrooke.reparecampus.data.remote.dto.UserDto
import ca.usherbrooke.reparecampus.util.Constants
import ca.usherbrooke.reparecampus.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val request: RequestDto? = null,
    val loading: Boolean = true,
    val working: Boolean = false,
    val online: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val role: String = Constants.ROLE_REQUESTER,
    val userId: String = "",
    val repairers: List<UserDto> = emptyList(),
    val slots: List<SlotDto> = emptyList()
)

// Détail d'une demande et toutes les actions du cycle de vie, filtrées selon le rôle.
class RequestDetailViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.authRepository.sessionFlow.collect { session ->
                if (session != null) {
                    _state.update { it.copy(role = session.userRole, userId = session.userId) }
                }
            }
        }
        viewModelScope.launch {
            container.connectivity.isOnline.collect { online -> _state.update { it.copy(online = online) } }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            container.requestRepository.detail(requestId)
                .onSuccess { dto -> _state.update { it.copy(request = dto, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
        }
    }

    // Transitions d'état autorisées depuis l'état courant (miroir du graphe serveur).
    fun allowedNext(status: String): List<String> = when (status) {
        Constants.STATUS_TO_TRIAGE -> listOf(Constants.STATUS_ASSIGNED, Constants.STATUS_CANCELLED)
        Constants.STATUS_ASSIGNED -> listOf(
            Constants.STATUS_IN_DIAGNOSIS, Constants.STATUS_TO_TRIAGE, Constants.STATUS_CANCELLED
        )
        Constants.STATUS_IN_DIAGNOSIS -> listOf(
            Constants.STATUS_WAITING, Constants.STATUS_IN_REPAIR,
            Constants.STATUS_NOT_REPAIRABLE, Constants.STATUS_CANCELLED
        )
        Constants.STATUS_WAITING -> listOf(
            Constants.STATUS_IN_DIAGNOSIS, Constants.STATUS_IN_REPAIR,
            Constants.STATUS_NOT_REPAIRABLE, Constants.STATUS_CANCELLED
        )
        Constants.STATUS_IN_REPAIR -> listOf(
            Constants.STATUS_WAITING, Constants.STATUS_READY, Constants.STATUS_NOT_REPAIRABLE
        )
        Constants.STATUS_READY -> listOf(Constants.STATUS_CLOSED, Constants.STATUS_IN_REPAIR)
        Constants.STATUS_NOT_REPAIRABLE -> listOf(Constants.STATUS_CLOSED)
        else -> emptyList()
    }

    val isStaff: Boolean
        get() = _state.value.role == Constants.ROLE_REPAIRER ||
            _state.value.role == Constants.ROLE_COORDINATOR ||
            _state.value.role == Constants.ROLE_ADMIN

    val canAssign: Boolean
        get() = _state.value.role == Constants.ROLE_COORDINATOR || _state.value.role == Constants.ROLE_ADMIN

    // Enveloppe commune : exécute une action, met à jour la demande et signale le résultat.
    private fun act(successMsg: String, block: suspend () -> Result<RequestDto>) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null, message = null) }
            block()
                .onSuccess { dto -> _state.update { it.copy(request = dto, message = successMsg) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
            _state.update { it.copy(working = false) }
        }
    }

    fun claim() = act("Demande prise en charge.") { container.requestRepository.claim(requestId) }

    fun changeStatus(status: String, reason: String?) =
        act("État mis à jour.") { container.requestRepository.changeStatus(requestId, status, reason) }

    fun addDiagnosis(notes: String) =
        act("Diagnostic ajouté.") { container.requestRepository.addDiagnosis(requestId, notes) }

    fun addEstimate(amount: Double, details: String?) =
        act("Estimation ajoutée.") { container.requestRepository.addEstimate(requestId, amount, details) }

    fun respondEstimate(decision: String) =
        act("Réponse envoyée.") { container.requestRepository.respondEstimate(requestId, decision) }

    fun addIntervention(notes: String, minutes: Int) =
        act("Intervention consignée.") { container.requestRepository.addIntervention(requestId, notes, minutes) }

    fun addPart(name: String, quantity: Int, unitCost: Double) =
        act("Pièce ajoutée.") { container.requestRepository.addPart(requestId, name, quantity, unitCost) }

    fun assign(repairerId: String, slotId: String?) =
        act("Demande affectée.") { container.requestRepository.assign(requestId, repairerId, slotId) }

    // Charge l'annuaire des réparateurs et les créneaux pour le dialogue d'affectation.
    fun loadDirectory() {
        if (!canAssign) return
        viewModelScope.launch {
            val categoryId = _state.value.request?.category?.id
            container.directoryRepository.repairers(categoryId)
                .onSuccess { list -> _state.update { it.copy(repairers = list) } }
            container.directoryRepository.slots()
                .onSuccess { list -> _state.update { it.copy(slots = list) } }
        }
    }

    fun consumeMessages() = _state.update { it.copy(error = null, message = null) }
}
