package ca.usherbrooke.reparecampus.data.repository

import ca.usherbrooke.reparecampus.data.local.AppDatabase
import ca.usherbrooke.reparecampus.data.local.entity.NotificationEntity
import ca.usherbrooke.reparecampus.data.remote.ApiService
import kotlinx.coroutines.flow.Flow

class NotificationRepository(
    private val api: () -> ApiService,
    private val db: AppDatabase
) {
    private val dao = db.notificationDao()

    val notifications: Flow<List<NotificationEntity>> = dao.observeAll()

    suspend fun refresh(): Result<Int> = runCatching {
        val page = api().notifications()
        dao.upsert(
            page.items.map {
                NotificationEntity(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    message = it.message,
                    read = it.read,
                    createdAt = it.createdAt ?: "",
                    requestId = it.request
                )
            }
        )
        page.unreadCount
    }

    // Récupère les notifications sans toucher au cache (utilisé par le travailleur d'arrière-plan).
    suspend fun fetchRemote() = api().notifications()

    suspend fun markRead(id: String): Result<Unit> = runCatching { api().markRead(id); Unit }
    suspend fun markAllRead(): Result<Unit> = runCatching { api().markAllRead(); Unit }
}
