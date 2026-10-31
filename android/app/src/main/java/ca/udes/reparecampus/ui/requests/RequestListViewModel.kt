package ca.udes.reparecampus.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.udes.reparecampus.data.local.DraftEntity
import ca.udes.reparecampus.data.local.PendingActionEntity
import ca.udes.reparecampus.data.model.AppNotification
import ca.udes.reparecampus.data.model.Category
import ca.udes.reparecampus.data.model.Priority
import ca.udes.reparecampus.data.model.RepairRequest
import ca.udes.reparecampus.data.model.RequestStatus
import ca.udes.reparecampus.data.remote.ApiException
import ca.udes.reparecampus.data.repository.AuthRepository
import ca.udes.reparecampus.data.repository.DraftRepository
import ca.udes.reparecampus.data.repository.RequestRepository
import ca.udes.reparecampus.data.sync.NetworkMonitor
import ca.udes.reparecampus.domain.Role
import ca.udes.reparecampus.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer

/** Portée de la liste pour une personne réparatrice (le serveur renvoie ses demandes + les non assignées). */
enum class RepairerScope(val label: String) {
    ASSIGNED_TO_ME("Assignées à moi"),
    UNASSIGNED("Non assignées"),
    ALL("Toutes"),
}

data class RequestFilters(
    val query: String = "",
    val status: RequestStatus? = null,
    val category: String? = null,
    val priority: Priority? = null,
    val repairerScope: RepairerScope = RepairerScope.ASSIGNED_TO_ME,
    val hideFinal: Boolean = false,
) {
    val activeCount: Int
        get() = listOfNotNull(status, category, priority).size + (if (hideFinal) 1 else 0)
}

data class RequestListUiState(
    val requests: List<RepairRequest> = emptyList(),
    val totalCount: Int = 0,
    val categories: List<Category> = emptyList(),
    val filters: RequestFilters = RequestFilters(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    fun categoryName(id: String): String = categories.firstOrNull { it.id == id }?.name ?: id
}

class RequestListViewModel(
    private val session: Session,
    private val requestRepository: RequestRepository,
    private val authRepository: AuthRepository,
    draftRepository: DraftRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val lastSync: StateFlow<Long?> = requestRepository.observeLastSync()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val notifications: StateFlow<List<AppNotification>> = requestRepository.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markNotificationsRead(ids: List<String>) {
        viewModelScope.launch { requestRepository.markNotificationsRead(ids) }
    }

    val pendingActions: StateFlow<List<PendingActionEntity>> = requestRepository.observePendingActions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Créer une demande : réservé aux demandeurs (et à l'admin) côté API. */
    val canCreate: Boolean = session.role == Role.REQUESTER || session.role == Role.ADMIN

    val drafts: StateFlow<List<DraftEntity>> = draftRepository.observeDrafts(session.userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filters = MutableStateFlow(RequestFilters())
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<RequestListUiState> = combine(
        requestRepository.observeRequests(),
        requestRepository.observeCategories(),
        filters,
        refreshing,
        error,
    ) { requests, categories, f, isRefreshing, err ->
        val visible = requests.filter { matches(it, f, categories) }
        RequestListUiState(visible, requests.size, categories, f, isRefreshing, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestListUiState(isRefreshing = true))

    /** Rafraîchit à l'ouverture de l'écran, au retour au premier plan et sur demande. */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            requestRepository.refresh()
                .onSuccess { error.value = null }
                // Hors ligne, le bandeau suffit : on affiche le cache sans message d'erreur.
                .onFailure { e -> error.value = if (e is ApiException && e.isNetwork) null else e.message }
            authRepository.refreshProfile(session)
            refreshing.value = false
        }
    }

    fun dismissError() { error.value = null }

    fun discardFailedActions() {
        viewModelScope.launch { requestRepository.discardFailedActions() }
    }
    fun onQueryChange(q: String) = filters.update { it.copy(query = q) }
    fun onStatusFilter(s: RequestStatus?) = filters.update { it.copy(status = s) }
    fun onCategoryFilter(c: String?) = filters.update { it.copy(category = c) }
    fun onPriorityFilter(p: Priority?) = filters.update { it.copy(priority = p) }
    fun onRepairerScope(s: RepairerScope) = filters.update { it.copy(repairerScope = s) }
    fun onHideFinal(hide: Boolean) = filters.update { it.copy(hideFinal = hide) }
    fun clearFilters() = filters.update { RequestFilters(query = it.query, repairerScope = it.repairerScope) }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun matches(r: RepairRequest, f: RequestFilters, categories: List<Category>): Boolean {
        if (session.role == Role.REPAIRER) {
            val mine = r.assignedTo?.id == session.userId
            when (f.repairerScope) {
                RepairerScope.ASSIGNED_TO_ME -> if (!mine) return false
                RepairerScope.UNASSIGNED -> if (r.assignedTo != null) return false
                RepairerScope.ALL -> Unit
            }
        }
        if (f.status != null && r.statusEnum != f.status) return false
        if (f.category != null && r.category != f.category) return false
        if (f.priority != null && Priority.fromApi(r.priority) != f.priority) return false
        if (f.hideFinal && r.statusEnum.isFinal) return false
        if (f.query.isNotBlank()) {
            val categoryName = categories.firstOrNull { it.id == r.category }?.name.orEmpty()
            val haystack = listOf(
                r.problemDescription, r.device.label, r.category, categoryName,
                r.requester?.name.orEmpty(), r.assignedTo?.name.orEmpty(), r.statusEnum.label,
            ).joinToString(" ").normalized()
            if (f.query.normalized().split(" ").filter { it.isNotBlank() }.any { it !in haystack }) return false
        }
        return true
    }
}

/** Minuscules sans accents, pour une recherche tolérante ("ecran" trouve "Écran"). */
private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
