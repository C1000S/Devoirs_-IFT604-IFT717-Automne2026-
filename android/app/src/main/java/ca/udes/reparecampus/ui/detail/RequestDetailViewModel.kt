package ca.udes.reparecampus.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.udes.reparecampus.data.model.AppNotification
import ca.udes.reparecampus.data.model.Category
import ca.udes.reparecampus.data.model.RepairRequest
import ca.udes.reparecampus.data.model.RequestStatus
import ca.udes.reparecampus.data.remote.dto.UserDto
import ca.udes.reparecampus.data.remote.ApiException
import ca.udes.reparecampus.data.repository.RequestRepository
import ca.udes.reparecampus.data.sync.NetworkMonitor
import ca.udes.reparecampus.data.sync.Outcome
import ca.udes.reparecampus.data.sync.RequestAction
import ca.udes.reparecampus.domain.Role
import ca.udes.reparecampus.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ce que la personne connectée peut faire sur la demande affichée. */
data class AvailableActions(
    val respondToEstimate: Boolean = false,
    val statusTargets: List<RequestStatus> = emptyList(),
    val diagnosis: Boolean = false,
    val estimate: Boolean = false,
    val intervention: Boolean = false,
    val assign: Boolean = false,
) {
    val any: Boolean get() = respondToEstimate || statusTargets.isNotEmpty() || diagnosis || estimate || intervention || assign
}

data class RequestDetailUiState(
    val request: RepairRequest? = null,
    val categories: List<Category> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val actions: AvailableActions = AvailableActions(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val message: String? = null,
) {
    val categoryName: String
        get() = request?.let { r -> categories.firstOrNull { it.id == r.category }?.name ?: r.category }.orEmpty()
}

class RequestDetailViewModel(
    private val requestId: String,
    private val session: Session,
    private val repository: RequestRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val lastSync: StateFlow<Long?> = repository.observeLastSync()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val loading = MutableStateFlow(false)
    private val submitting = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val _repairers = MutableStateFlow<List<UserDto>>(emptyList())
    val repairers: StateFlow<List<UserDto>> = _repairers

    val state: StateFlow<RequestDetailUiState> = combine(
        repository.observeRequest(requestId),
        repository.observeCategories(),
        repository.observeNotifications(),
        combine(loading, submitting, message) { l, s, m -> Triple(l, s, m) },
    ) { request, categories, notifications, (l, s, m) ->
        RequestDetailUiState(
            request = request,
            categories = categories,
            notifications = notifications.filter { it.request == requestId },
            actions = request?.let(::actionsFor) ?: AvailableActions(),
            isLoading = l,
            isSubmitting = s,
            message = m,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestDetailUiState(isLoading = true))

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            // Hors ligne, le détail en cache reste affiché (le bandeau le signale).
            repository.refreshRequest(requestId).onFailure { e ->
                if (!(e is ApiException && e.isNetwork)) message.value = e.message
            }
            loading.value = false
            // Ouvrir le détail vaut lecture des notifications liées.
            repository.markNotificationsRead(state.value.notifications.filter { !it.read }.map { it.id })
        }
    }

    fun perform(action: RequestAction, successMessage: String) {
        if (submitting.value) return
        viewModelScope.launch {
            submitting.value = true
            repository.perform(requestId, action)
                .onSuccess { outcome ->
                    message.value = if (outcome == Outcome.QUEUED) "Hors ligne : action enregistrée, elle sera envoyée au retour du réseau."
                    else successMessage
                }
                .onFailure { message.value = it.message }
            submitting.value = false
        }
    }

    fun loadRepairers() {
        if (_repairers.value.isNotEmpty()) return
        viewModelScope.launch {
            repository.repairers().onSuccess { _repairers.value = it }.onFailure { message.value = it.message }
        }
    }

    fun dismissMessage() { message.value = null }

    private fun actionsFor(r: RepairRequest): AvailableActions {
        // Une demande créée hors ligne n'existe pas encore côté serveur.
        if (r.isLocal) return AvailableActions()
        val status = r.statusEnum
        return when (session.role) {
            Role.REQUESTER -> AvailableActions(
                respondToEstimate = r.requester?.id == session.userId && r.hasPendingEstimate &&
                    status == RequestStatus.WAITING,
            )
            Role.REPAIRER -> {
                if (r.assignedTo?.id != session.userId) return AvailableActions()
                AvailableActions(
                    // « Affectée » exige un assignedTo : elle passe par l'affectation, pas par le changement d'état.
                    statusTargets = status.allowedTransitions - RequestStatus.ASSIGNED,
                    diagnosis = status in listOf(RequestStatus.ASSIGNED, RequestStatus.IN_DIAGNOSIS),
                    estimate = status in listOf(RequestStatus.IN_DIAGNOSIS, RequestStatus.WAITING),
                    intervention = status == RequestStatus.IN_REPAIR,
                )
            }
            Role.COORDINATOR, Role.ADMIN -> AvailableActions(
                statusTargets = status.allowedTransitions - RequestStatus.ASSIGNED,
                assign = !status.isFinal,
            )
            Role.UNKNOWN -> AvailableActions()
        }
    }
}
