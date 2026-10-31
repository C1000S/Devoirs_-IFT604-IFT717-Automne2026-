package ca.udes.reparecampus.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.udes.reparecampus.data.local.DraftEntity
import ca.udes.reparecampus.data.model.Category
import ca.udes.reparecampus.data.model.Device
import ca.udes.reparecampus.data.model.Priority
import ca.udes.reparecampus.data.remote.dto.CreateRequestBody
import ca.udes.reparecampus.data.repository.DraftRepository
import ca.udes.reparecampus.data.repository.RequestRepository
import ca.udes.reparecampus.data.sync.Outcome
import ca.udes.reparecampus.domain.Session
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RequestForm(
    val category: String = "",
    val deviceType: String = "",
    val brand: String = "",
    val model: String = "",
    val problemDescription: String = "",
    val priority: Priority = Priority.NORMAL,
    val contactMethod: String = "",
) {
    val isBlank: Boolean
        get() = listOf(category, deviceType, brand, model, problemDescription, contactMethod).all { it.isBlank() }

    /** Champs exigés par POST /api/repair-requests. */
    val missingFields: List<String>
        get() = buildList {
            if (category.isBlank()) add("catégorie")
            if (deviceType.isBlank()) add("type d'appareil")
            if (problemDescription.isBlank()) add("description du problème")
        }
}

data class CreateRequestUiState(
    val form: RequestForm = RequestForm(),
    val draftId: Long? = null,
    val draftSavedAt: Long? = null,
    val showErrors: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** Identifiant de la demande créée : l'écran navigue alors vers son détail. */
    val createdRequestId: String? = null,
    /** Créée hors ligne : mise en file, l'écran revient à la liste. */
    val queuedOffline: Boolean = false,
)

@OptIn(FlowPreview::class)
class CreateRequestViewModel(
    private val session: Session,
    initialDraftId: Long?,
    private val requestRepository: RequestRepository,
    private val draftRepository: DraftRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateRequestUiState(draftId = initialDraftId))
    val state: StateFlow<CreateRequestUiState> = _state.asStateFlow()

    val categories: StateFlow<List<Category>> = requestRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            initialDraftId?.let { id -> draftRepository.get(id)?.let { d -> _state.update { it.copy(form = d.toForm(), draftSavedAt = d.updatedAt) } } }
            if (categories.value.isEmpty()) requestRepository.refresh()
            // Enregistrement automatique du brouillon, 800 ms après la dernière frappe.
            _state.map { it.form }.distinctUntilChanged().drop(1).debounce(800).collect { saveDraft() }
        }
    }

    fun update(transform: (RequestForm) -> RequestForm) = _state.update { it.copy(form = transform(it.form), error = null) }

    fun saveDraft() {
        val s = _state.value
        if (s.form.isBlank || s.createdRequestId != null || s.queuedOffline) return
        viewModelScope.launch {
            val id = draftRepository.save(s.form.toEntity(s.draftId ?: 0L, session.userId))
            _state.update { it.copy(draftId = id, draftSavedAt = System.currentTimeMillis()) }
        }
    }

    fun discardDraft(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value.draftId?.let { draftRepository.delete(it) }
            _state.update { it.copy(form = RequestForm(), draftId = null) }
            onDone()
        }
    }

    fun submit() {
        val s = _state.value
        if (s.isSubmitting) return
        if (s.form.missingFields.isNotEmpty()) {
            _state.update { it.copy(showErrors = true, error = "À compléter : ${s.form.missingFields.joinToString()}.") }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val f = s.form
            val body = CreateRequestBody(
                category = f.category,
                device = Device(f.deviceType.trim(), f.brand.trim(), f.model.trim()),
                problemDescription = f.problemDescription.trim(),
                priority = f.priority.apiValue,
                contactMethod = f.contactMethod.trim(),
            )
            requestRepository.create(body)
                .onSuccess { (outcome, id) ->
                    _state.value.draftId?.let { draftRepository.delete(it) }
                    _state.update {
                        if (outcome == Outcome.QUEUED) it.copy(isSubmitting = false, draftId = null, queuedOffline = true)
                        else it.copy(isSubmitting = false, draftId = null, createdRequestId = id)
                    }
                }
                .onFailure { e ->
                    // Le brouillon est conservé : rien n'est perdu si l'envoi échoue.
                    saveDraft()
                    _state.update { it.copy(isSubmitting = false, error = "${e.message} Le brouillon est conservé.") }
                }
        }
    }
}

fun DraftEntity.toForm() = RequestForm(
    category = category,
    deviceType = deviceType,
    brand = brand,
    model = model,
    problemDescription = problemDescription,
    priority = Priority.fromApi(priority),
    contactMethod = contactMethod,
)

private fun RequestForm.toEntity(id: Long, userId: String) = DraftEntity(
    id = id,
    userId = userId,
    category = category,
    deviceType = deviceType,
    brand = brand,
    model = model,
    problemDescription = problemDescription,
    priority = priority.apiValue,
    contactMethod = contactMethod,
)
