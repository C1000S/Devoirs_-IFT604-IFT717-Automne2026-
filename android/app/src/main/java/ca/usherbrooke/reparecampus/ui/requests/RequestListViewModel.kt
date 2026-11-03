package ca.usherbrooke.reparecampus.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.data.local.entity.RequestEntity
import ca.usherbrooke.reparecampus.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RequestFilters(
    val query: String = "",
    val status: String? = null,
    val priority: String? = null
)

data class RequestListUiState(
    val filters: RequestFilters = RequestFilters(),
    val refreshing: Boolean = false,
    val online: Boolean = true,
    val error: String? = null
)

// Liste des demandes : observe le cache Room (source de vérité, fonctionne hors ligne)
// et le rafraîchit depuis le serveur. La recherche et les filtres s'appliquent localement
// pour rester fonctionnels sans réseau.
class RequestListViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(RequestListUiState())
    val ui: StateFlow<RequestListUiState> = _ui.asStateFlow()

    // Liste filtrée, recombinée dès que le cache ou les filtres changent.
    val requests: StateFlow<List<RequestEntity>> =
        combine(container.requestRepository.requests, _ui) { list, state ->
            applyFilters(list, state.filters)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = container.categoryRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            container.connectivity.isOnline.collect { online ->
                _ui.update { it.copy(online = online) }
            }
        }
        refresh()
    }

    fun onQuery(v: String) = _ui.update { it.copy(filters = it.filters.copy(query = v)) }
    fun onStatus(v: String?) = _ui.update { it.copy(filters = it.filters.copy(status = v)) }
    fun onPriority(v: String?) = _ui.update { it.copy(filters = it.filters.copy(priority = v)) }
    fun clearFilters() = _ui.update { it.copy(filters = RequestFilters(query = it.filters.query)) }

    // Actualisation manuelle (bouton et tirer-pour-rafraîchir) : liste + catégories.
    fun refresh() {
        if (_ui.value.refreshing) return
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true, error = null) }
            container.categoryRepository.refresh()
            val result = container.requestRepository.refresh(emptyMap())
            result.onFailure { e -> _ui.update { it.copy(error = e.toUserMessage()) } }
            _ui.update { it.copy(refreshing = false) }
        }
    }

    private fun applyFilters(list: List<RequestEntity>, f: RequestFilters): List<RequestEntity> {
        val q = f.query.trim().lowercase()
        return list.filter { e ->
            (f.status == null || e.status == f.status) &&
                (f.priority == null || e.priority == f.priority) &&
                (q.isBlank() ||
                    e.deviceType.lowercase().contains(q) ||
                    e.deviceBrand.lowercase().contains(q) ||
                    e.problemDescription.lowercase().contains(q) ||
                    e.categoryName.lowercase().contains(q))
        }
    }
}
