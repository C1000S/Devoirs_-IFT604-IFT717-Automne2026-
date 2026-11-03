package ca.usherbrooke.reparecampus.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.usherbrooke.reparecampus.RepareCampusApp
import ca.usherbrooke.reparecampus.data.local.entity.NotificationEntity

// Interroge périodiquement le serveur et émet une notification Android pour chaque
// nouvelle notification non lue. Les identifiants déjà signalés sont mémorisés afin
// de ne pas notifier deux fois la même chose.
class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val prefs = context.getSharedPreferences("notif_worker", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val app = applicationContext as RepareCampusApp
        val container = app.container

        // Personne non connectée : rien à faire.
        if (container.session.cachedToken.isNullOrBlank()) return Result.success()

        return try {
            val page = container.notificationRepository.fetchRemote()

            // Met à jour le cache local.
            container.db.notificationDao().upsert(
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

            val alreadyNotified = prefs.getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet()
            val newUnread = page.items.filter { !it.read && it.id !in alreadyNotified }

            newUnread.forEach { notif ->
                NotificationHelper.show(applicationContext, notif.id.hashCode(), notif.title, notif.message)
            }

            // Mémorise les identifiants des notifications non lues connues.
            val updated = page.items.filter { !it.read }.map { it.id }.toSet()
            prefs.edit().putStringSet(KEY_NOTIFIED, updated).apply()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_NOTIFIED = "notified_ids"
    }
}
