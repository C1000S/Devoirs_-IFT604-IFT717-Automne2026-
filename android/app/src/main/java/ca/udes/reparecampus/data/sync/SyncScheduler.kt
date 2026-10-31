package ca.udes.reparecampus.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ca.udes.reparecampus.RepareCampusApp
import ca.udes.reparecampus.data.session.SessionState
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Planifie l'envoi de la file hors ligne avec WorkManager : la tâche ne s'exécute que
 * lorsqu'un réseau est disponible, même si l'application a été fermée entre-temps.
 */
class SyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancel() { workManager.cancelUniqueWork(WORK_NAME) }

    private companion object { const val WORK_NAME = "reparecampus-sync" }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RepareCampusApp).container
        // Au démarrage à froid, la session est encore en cours de lecture.
        if (container.sessionStore.state.first { it !is SessionState.Loading } !is SessionState.LoggedIn) {
            return Result.success()
        }
        val report = container.requestRepository.syncPending()
        if (report.remaining > 0) return Result.retry()
        container.requestRepository.refresh()
        return Result.success()
    }
}
