package ca.usherbrooke.reparecampus.data.repository

import ca.usherbrooke.reparecampus.data.local.AppDatabase
import ca.usherbrooke.reparecampus.data.local.entity.RequestEntity
import ca.usherbrooke.reparecampus.data.remote.ApiService
import ca.usherbrooke.reparecampus.data.remote.dto.AssignBody
import ca.usherbrooke.reparecampus.data.remote.dto.CreateRequestBody
import ca.usherbrooke.reparecampus.data.remote.dto.DiagnosisBody
import ca.usherbrooke.reparecampus.data.remote.dto.EstimateBody
import ca.usherbrooke.reparecampus.data.remote.dto.EstimateResponseBody
import ca.usherbrooke.reparecampus.data.remote.dto.InterventionBody
import ca.usherbrooke.reparecampus.data.remote.dto.PartBody
import ca.usherbrooke.reparecampus.data.remote.dto.RequestDto
import ca.usherbrooke.reparecampus.data.remote.dto.StatusBody
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

// Source unique de vérité : l'UI observe Room ; le réseau rafraîchit le cache.
class RequestRepository(
    private val api: () -> ApiService,
    private val db: AppDatabase,
    private val gson: Gson
) {
    private val dao = db.requestDao()

    val requests: Flow<List<RequestEntity>> = dao.observeAll()

    private fun toEntity(dto: RequestDto): RequestEntity = RequestEntity(
        id = dto.id,
        status = dto.status,
        priority = dto.priority,
        categoryName = dto.category?.name ?: "",
        deviceType = dto.device.type,
        deviceBrand = dto.device.brand ?: "",
        problemDescription = dto.problemDescription,
        requesterName = dto.requester?.name ?: "",
        assignedToName = dto.assignedTo?.name ?: "",
        hasEstimate = dto.estimate != null,
        updatedAt = dto.updatedAt ?: dto.createdAt ?: "",
        json = gson.toJson(dto)
    )

    fun parse(entity: RequestEntity): RequestDto = gson.fromJson(entity.json, RequestDto::class.java)

    // Rafraîchit la liste depuis le serveur selon des filtres et met à jour Room.
    suspend fun refresh(filters: Map<String, String>): Result<Int> = runCatching {
        val page = api().listRequests(filters + ("limit" to "100"))
        dao.upsert(page.items.map { toEntity(it) })
        page.total
    }

    // Détail : tente le réseau (données fraîches + historique), sinon le cache local.
    suspend fun detail(id: String): Result<RequestDto> = runCatching {
        try {
            val dto = api().getRequest(id)
            dao.upsertOne(toEntity(dto))
            dto
        } catch (e: Exception) {
            val cached = dao.findById(id) ?: throw e
            parse(cached)
        }
    }

    suspend fun create(body: CreateRequestBody): Result<RequestDto> = saveResult { api().createRequest(body) }
    suspend fun assign(id: String, repairerId: String, slotId: String?): Result<RequestDto> =
        saveResult { api().assign(id, AssignBody(repairerId, slotId)) }
    suspend fun claim(id: String): Result<RequestDto> = saveResult { api().claim(id) }
    suspend fun changeStatus(id: String, status: String, reason: String?): Result<RequestDto> =
        saveResult { api().changeStatus(id, StatusBody(status, reason)) }
    suspend fun addDiagnosis(id: String, notes: String): Result<RequestDto> =
        saveResult { api().addDiagnosis(id, DiagnosisBody(notes)) }
    suspend fun addEstimate(id: String, amount: Double, details: String?): Result<RequestDto> =
        saveResult { api().addEstimate(id, EstimateBody(amount, details = details)) }
    suspend fun respondEstimate(id: String, decision: String): Result<RequestDto> =
        saveResult { api().respondEstimate(id, EstimateResponseBody(decision)) }
    suspend fun addIntervention(id: String, notes: String, minutes: Int): Result<RequestDto> =
        saveResult { api().addIntervention(id, InterventionBody(notes, minutes)) }
    suspend fun addPart(id: String, name: String, quantity: Int, unitCost: Double): Result<RequestDto> =
        saveResult { api().addPart(id, PartBody(name, quantity, unitCost)) }

    // Exécute un appel renvoyant une demande et met à jour le cache local.
    private suspend fun saveResult(block: suspend () -> RequestDto): Result<RequestDto> = runCatching {
        val dto = block()
        dao.upsertOne(toEntity(dto))
        dto
    }
}
