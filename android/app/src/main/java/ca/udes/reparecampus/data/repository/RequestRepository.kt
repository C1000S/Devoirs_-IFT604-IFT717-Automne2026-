package ca.udes.reparecampus.data.repository

import androidx.room.withTransaction
import ca.udes.reparecampus.data.local.AppDatabase
import ca.udes.reparecampus.data.local.CategoryEntity
import ca.udes.reparecampus.data.local.DraftEntity
import ca.udes.reparecampus.data.local.MetaEntity
import ca.udes.reparecampus.data.local.NotificationEntity
import ca.udes.reparecampus.data.local.PendingActionEntity
import ca.udes.reparecampus.data.local.RequestEntity
import ca.udes.reparecampus.data.model.AppNotification
import ca.udes.reparecampus.data.model.Category
import ca.udes.reparecampus.data.model.Diagnosis
import ca.udes.reparecampus.data.model.Estimate
import ca.udes.reparecampus.data.model.Intervention
import ca.udes.reparecampus.data.model.RepairEvent
import ca.udes.reparecampus.data.model.RepairRequest
import ca.udes.reparecampus.data.model.RequestStatus
import ca.udes.reparecampus.data.model.UserRef
import ca.udes.reparecampus.data.remote.ApiException
import ca.udes.reparecampus.data.remote.RepareCampusApi
import ca.udes.reparecampus.data.remote.apiCall
import ca.udes.reparecampus.data.remote.dto.AssignBody
import ca.udes.reparecampus.data.remote.dto.CreateRequestBody
import ca.udes.reparecampus.data.remote.dto.DiagnosisBody
import ca.udes.reparecampus.data.remote.dto.EstimateBody
import ca.udes.reparecampus.data.remote.dto.EstimateResponseBody
import ca.udes.reparecampus.data.remote.dto.InterventionBody
import ca.udes.reparecampus.data.remote.dto.StatusBody
import ca.udes.reparecampus.data.remote.dto.UserDto
import ca.udes.reparecampus.data.sync.Outcome
import ca.udes.reparecampus.data.sync.RequestAction
import ca.udes.reparecampus.data.sync.SyncOp
import ca.udes.reparecampus.data.sync.SyncScheduler
import ca.udes.reparecampus.domain.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/** Bilan d'une passe de synchronisation de la file. */
data class SyncReport(val sent: Int, val failed: Int, val remaining: Int)

/**
 * Dépôt des demandes, catégories et notifications. **Room est la source de vérité** :
 * l'UI observe la base ; le réseau ne fait que la mettre à jour. Hors ligne, les actions
 * sont appliquées localement et mises en file ([PendingActionEntity]), puis rejouées
 * par [syncPending] au retour du réseau.
 */
class RequestRepository(
    private val api: RepareCampusApi,
    private val db: AppDatabase,
    private val json: Json,
    private val scope: CoroutineScope,
    private val scheduler: SyncScheduler,
    private val currentSession: () -> Session?,
) {
    private val requestDao = db.requestDao()
    private val categoryDao = db.categoryDao()
    private val notificationDao = db.notificationDao()
    private val pendingDao = db.pendingActionDao()
    private val metaDao = db.metaDao()
    private val syncMutex = Mutex()

    // ---------------------------------------------------------------- Lecture (cache)

    fun observeRequests(): Flow<List<RepairRequest>> = requestDao.observeAll().map { list -> list.map(::decode) }
    fun observeRequest(id: String): Flow<RepairRequest?> = requestDao.observe(id).map { it?.let(::decode) }
    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { Category(it.id, it.name, it.description) } }
    fun observeNotifications(): Flow<List<AppNotification>> =
        notificationDao.observeAll().map { list -> list.map { AppNotification(it.id, it.requestId, it.title, it.message, it.read, it.createdAt) } }
    fun observePendingActions(): Flow<List<PendingActionEntity>> = pendingDao.observeAll()
    fun observeLastSync(): Flow<Long?> = metaDao.observe(KEY_LAST_SYNC).map { it?.toLongOrNull() }

    // ---------------------------------------------------------------- Rafraîchissement

    /** Envoie d'abord la file en attente, puis recharge demandes, catégories et notifications. */
    suspend fun refresh(): Result<Unit> {
        syncPending()
        return apiCall {
            coroutineScope {
                val r = async { api.requests() }
                val c = async { api.categories() }
                val n = async { api.notifications() }
                saveSnapshot(r.await(), c.await(), n.await())
            }
        }.onSuccess { scope.launch { prefetchDetails() } }
    }

    suspend fun refreshRequest(id: String): Result<Unit> {
        if (id.startsWith(LOCAL_PREFIX)) return Result.success(Unit)
        return apiCall {
            coroutineScope {
                val r = async { api.request(id) }
                val n = async { api.notifications() }
                storeDetail(r.await())
                saveNotifications(n.await())
            }
        }
    }

    private suspend fun saveSnapshot(requests: List<RepairRequest>, categories: List<Category>, notifications: List<AppNotification>) {
        db.withTransaction {
            val existing = requestDao.getAll().associateBy { it.id }
            val pendingIds = pendingDao.pendingRequestIds().toSet()
            val entities = requests.take(MAX_CACHED_REQUESTS).map { r ->
                val old = existing[r.id]
                // La liste ne contient pas l'historique : on garde celui déjà en cache pour la consultation hors ligne.
                val oldEvents = old?.let { decode(it).events }.orEmpty()
                toEntity(
                    r.copy(events = oldEvents),
                    hasDetail = old?.hasDetail == true && old.updatedAt == r.updatedAt,
                    pendingSync = r.id in pendingIds,
                )
            }
            requestDao.deleteAllRemote()
            requestDao.upsertAll(entities)
            categoryDao.clear()
            categoryDao.upsertAll(categories.map { CategoryEntity(it.id, it.name, it.description) })
            saveNotifications(notifications)
            metaDao.put(MetaEntity(KEY_LAST_SYNC, System.currentTimeMillis().toString()))
        }
    }

    private suspend fun saveNotifications(list: List<AppNotification>) {
        notificationDao.clear()
        notificationDao.upsertAll(list.map { NotificationEntity(it.id, it.request, it.title, it.message, it.read, it.createdAt) })
    }

    /** Précharge l'historique des demandes récentes pour qu'il soit consultable hors ligne. */
    private suspend fun prefetchDetails() {
        requestDao.getAll()
            .filter { !it.isLocal && !it.hasDetail }
            .sortedByDescending { it.updatedAt }
            .take(PREFETCH_DETAILS)
            .forEach { e -> apiCall { storeDetail(api.request(e.id)) }.onFailure { return } }
    }

    // ---------------------------------------------------------------- Écriture

    /** Exécute l'action sur l'API ; sans réseau, l'applique localement et la met en file. */
    suspend fun perform(id: String, action: RequestAction): Result<Outcome> {
        if (id.startsWith(LOCAL_PREFIX)) {
            return Result.failure(ApiException("Cette demande n'est pas encore synchronisée avec le serveur.", 0))
        }
        val result = apiCall { executeOnServer(id, action) }
        return result.fold(
            onSuccess = { Result.success(Outcome.DONE) },
            onFailure = { e ->
                if (e is ApiException && e.isNetwork) {
                    applyLocally(id, action)
                    enqueue(SyncOp.OnRequest(id, action), describe(action), id)
                    Result.success(Outcome.QUEUED)
                } else Result.failure(e)
            },
        )
    }

    /** Crée la demande ; sans réseau, elle est ajoutée localement et envoyée plus tard. */
    suspend fun create(body: CreateRequestBody): Result<Pair<Outcome, String>> {
        val online = apiCall { api.createRequest(body) }
        online.onSuccess { created ->
            storeSummary(created)
            return Result.success(Outcome.DONE to created.id)
        }
        val e = online.exceptionOrNull()
        if (!(e is ApiException && e.isNetwork)) return Result.failure(e!!)

        val session = currentSession()
        val localId = LOCAL_PREFIX + UUID.randomUUID()
        val now = Instant.now().toString()
        val local = RepairRequest(
            id = localId,
            requester = session?.let { UserRef(it.userId, it.name, it.email) },
            category = body.category,
            device = body.device,
            problemDescription = body.problemDescription,
            contactMethod = body.contactMethod,
            priority = body.priority,
            createdAt = now,
            updatedAt = now,
            events = listOf(RepairEvent(type = "local", message = "Créée hors ligne — en attente d'envoi.", createdAt = now)),
        )
        requestDao.upsert(toEntity(local, hasDetail = true, pendingSync = true, isLocal = true))
        enqueue(SyncOp.Create(localId, body), "Créer : ${body.device.label}", localId)
        return Result.success(Outcome.QUEUED to localId)
    }

    suspend fun markNotificationsRead(ids: List<String>) {
        if (ids.isEmpty()) return
        notificationDao.markRead(ids)
        ids.forEach { id ->
            apiCall { api.markNotificationRead(id) }.onFailure { e ->
                if (e is ApiException && e.isNetwork) enqueue(SyncOp.MarkRead(id), "Notification lue", null)
            }
        }
    }

    /**
     * Recharge les notifications et retourne celles qui sont nouvelles (absentes du cache)
     * et non lues : ce sont elles qu'on signale par une notification système.
     */
    suspend fun pullNewNotifications(): Result<List<AppNotification>> = apiCall {
        val known = notificationDao.ids().toSet()
        val all = api.notifications()
        saveNotifications(all)
        // Cache vide (jamais synchronisé) : on n'inonde pas la personne avec l'historique.
        if (known.isEmpty()) emptyList() else all.filter { !it.read && it.id !in known }
    }

    /** Notification reçue par le flux temps réel ; retourne true si elle est nouvelle. */
    suspend fun onRealtimeNotification(n: AppNotification): Boolean {
        if (n.id in notificationDao.ids()) return false
        notificationDao.upsert(NotificationEntity(n.id, n.request, n.title, n.message, n.read, n.createdAt))
        return true
    }

    suspend fun repairers(): Result<List<UserDto>> = apiCall { api.users(role = "repairer") }

    // ---------------------------------------------------------------- Synchronisation

    /**
     * Rejoue la file dans l'ordre. Une erreur réseau interrompt la passe (on réessaiera) ;
     * un refus du serveur (4xx) marque l'opération en échec et on passe à la suivante.
     */
    suspend fun syncPending(): SyncReport = syncMutex.withLock {
        var sent = 0
        var failed = 0
        for (p in pendingDao.getPending()) {
            val op = runCatching { json.decodeFromString<SyncOp>(p.opJson) }.getOrNull()
            if (op == null) { pendingDao.delete(p.id); continue }
            val result = apiCall { execute(op) }
            val error = result.exceptionOrNull()
            when {
                error == null -> { pendingDao.delete(p.id); sent++ }
                error is ApiException && error.isNetwork -> break
                else -> {
                    pendingDao.markFailed(p.id, error.message ?: "Refusée par le serveur.")
                    if (op is SyncOp.Create) restoreAsDraft(op)
                    failed++
                }
            }
        }
        requestDao.clearPendingExcept(pendingDao.pendingRequestIds())
        SyncReport(sent, failed, pendingDao.getPending().size)
    }

    suspend fun discardFailedActions() = pendingDao.deleteFailed()

    /** Efface tout le cache de la session (les brouillons, propres à chaque personne, sont conservés). */
    suspend fun clearSessionData() {
        db.withTransaction {
            requestDao.clear()
            categoryDao.clear()
            notificationDao.clear()
            pendingDao.clear()
            metaDao.clear()
        }
        scheduler.cancel()
    }

    private suspend fun execute(op: SyncOp) {
        when (op) {
            is SyncOp.OnRequest -> executeOnServer(op.requestId, op.action)
            is SyncOp.Create -> {
                val created = api.createRequest(op.body)
                requestDao.delete(op.localId)
                storeSummary(created)
            }
            is SyncOp.MarkRead -> api.markNotificationRead(op.notificationId)
        }
    }

    private suspend fun executeOnServer(id: String, action: RequestAction) {
        val updated = when (action) {
            is RequestAction.RespondEstimate -> api.respondToEstimate(id, EstimateResponseBody(action.accept))
            is RequestAction.ChangeStatus -> api.changeStatus(id, StatusBody(action.status, action.reason))
            is RequestAction.AddDiagnosis -> api.addDiagnosis(id, DiagnosisBody(action.text))
            is RequestAction.ProposeEstimate -> api.proposeEstimate(id, EstimateBody(action.cost, action.description))
            is RequestAction.AddIntervention -> api.addIntervention(id, InterventionBody(action.description))
            is RequestAction.Assign -> api.assign(id, AssignBody(action.repairerId))
        }
        // Les réponses PATCH ne sont pas peuplées : on recharge le détail complet. L'action a déjà
        // réussi ; si ce rechargement échoue, on applique les champs modifiés à la version en cache.
        apiCall { api.request(id) }
            .onSuccess { storeDetail(it) }
            .onFailure {
                val previous = requestDao.get(id)?.let(::decode)
                storeSummary(previous?.mergeUpdate(updated) ?: updated)
            }
    }

    private suspend fun enqueue(op: SyncOp, label: String, requestId: String?) {
        pendingDao.insert(PendingActionEntity(opJson = json.encodeToString(SyncOp.serializer(), op), label = label, requestId = requestId))
        scheduler.schedule()
    }

    /** Mise à jour optimiste du cache, pour que l'UI reflète l'action en attente. */
    private suspend fun applyLocally(id: String, action: RequestAction) {
        val entity = requestDao.get(id) ?: return
        val r = decode(entity)
        val now = Instant.now().toString()
        val me = currentSession()?.let { UserRef(it.userId, it.name) }
        val updated = when (action) {
            is RequestAction.RespondEstimate -> r.copy(
                status = if (action.accept) RequestStatus.IN_REPAIR.apiValue else RequestStatus.NOT_REPAIRABLE.apiValue,
                estimate = r.estimate?.copy(status = if (action.accept) "accepted" else "refused", respondedAt = now),
            )
            is RequestAction.ChangeStatus -> r.copy(status = action.status)
            is RequestAction.AddDiagnosis -> r.copy(
                diagnosis = Diagnosis(action.text, now),
                status = if (r.statusEnum == RequestStatus.ASSIGNED) RequestStatus.IN_DIAGNOSIS.apiValue else r.status,
            )
            is RequestAction.ProposeEstimate -> r.copy(
                estimate = Estimate(action.cost, action.description, "pending"),
                status = RequestStatus.WAITING.apiValue,
            )
            is RequestAction.AddIntervention -> r.copy(interventions = r.interventions + Intervention(description = action.description, performedBy = me, performedAt = now))
            is RequestAction.Assign -> r.copy(assignedTo = UserRef(action.repairerId, action.repairerName), status = RequestStatus.ASSIGNED.apiValue)
        }
        val event = RepairEvent(type = "local", message = "${describe(action)} — en attente de synchronisation.", actor = me, createdAt = now)
        requestDao.upsert(entity.copy(json = encode(updated.copy(events = r.events + event, updatedAt = now)), pendingSync = true))
    }

    private suspend fun restoreAsDraft(op: SyncOp.Create) {
        requestDao.delete(op.localId)
        val userId = currentSession()?.userId ?: return
        val b = op.body
        db.draftDao().upsert(
            DraftEntity(
                userId = userId, category = b.category, deviceType = b.device.type, brand = b.device.brand,
                model = b.device.model, problemDescription = b.problemDescription, priority = b.priority, contactMethod = b.contactMethod,
            )
        )
    }

    private suspend fun storeDetail(r: RepairRequest) {
        val pending = r.id in pendingDao.pendingRequestIds()
        requestDao.upsert(toEntity(r, hasDetail = true, pendingSync = pending))
    }

    /** Version sans historique complet (réponse POST/PATCH) : on garde l'historique en cache. */
    private suspend fun storeSummary(r: RepairRequest) {
        val old = requestDao.get(r.id)?.let(::decode)
        requestDao.upsert(toEntity(r.copy(events = old?.events.orEmpty()), hasDetail = false, pendingSync = false))
    }

    private fun toEntity(r: RepairRequest, hasDetail: Boolean, pendingSync: Boolean, isLocal: Boolean = false) =
        RequestEntity(r.id, encode(r), r.createdAt, r.updatedAt, hasDetail, pendingSync, isLocal)

    private fun encode(r: RepairRequest) = json.encodeToString(RepairRequest.serializer(), r)

    private fun decode(e: RequestEntity): RepairRequest =
        json.decodeFromString(RepairRequest.serializer(), e.json).copy(pendingSync = e.pendingSync, isLocal = e.isLocal)

    /** Applique les champs modifiés par une action en gardant les références peuplées (noms). */
    private fun RepairRequest.mergeUpdate(u: RepairRequest) = copy(
        status = u.status,
        diagnosis = u.diagnosis,
        estimate = u.estimate,
        interventions = u.interventions,
        closedReason = u.closedReason,
        updatedAt = u.updatedAt,
        assignedTo = if (u.assignedTo?.id == assignedTo?.id) assignedTo else u.assignedTo,
    )

    companion object {
        const val LOCAL_PREFIX = "local-"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val MAX_CACHED_REQUESTS = 200
        private const val PREFETCH_DETAILS = 20

        fun describe(action: RequestAction): String = when (action) {
            is RequestAction.RespondEstimate -> if (action.accept) "Estimation acceptée" else "Estimation refusée"
            is RequestAction.ChangeStatus -> "État → ${RequestStatus.fromApi(action.status).label}"
            is RequestAction.AddDiagnosis -> "Diagnostic consigné"
            is RequestAction.ProposeEstimate -> "Estimation proposée (${action.cost} $)"
            is RequestAction.AddIntervention -> "Intervention : ${action.description}"
            is RequestAction.Assign -> "Affectée à ${action.repairerName ?: "une personne réparatrice"}"
        }
    }
}
