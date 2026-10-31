package ca.udes.reparecampus.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ca.udes.reparecampus.RepareCampusApp
import ca.udes.reparecampus.data.session.SessionState
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Vérifie les nouvelles notifications quand l'application est en arrière-plan
 * (le flux SSE ne survit pas au gel des applications en cache depuis Android 14).
 * Deux modes : une boucle rapprochée (~1 min) tant que l'app est en arrière-plan,
 * et une vérification périodique (15 min, minimum Android) en filet de sécurité.
 */
class NotificationPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RepareCampusApp
        val container = app.container
        if (container.sessionStore.state.first { it !is SessionState.Loading } !is SessionState.LoggedIn) return Result.success()

        container.requestRepository.pullNewNotifications().onSuccess { fresh ->
            if (!app.isInForeground) fresh.forEach(container.notifier::show)
        }
        if (inputData.getBoolean(KEY_CHAIN, false) && !app.isInForeground) scheduleBackgroundLoop(applicationContext, POLL_DELAY_SECONDS)
        return Result.success()
    }

    companion object {
        private const val KEY_CHAIN = "chain"
        private const val LOOP_WORK = "notification-poll-loop"
        private const val PERIODIC_WORK = "notification-poll-periodic"
        private const val POLL_DELAY_SECONDS = 60L
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Boucle rapprochée tant que l'application reste en arrière-plan. */
        fun scheduleBackgroundLoop(context: Context, delaySeconds: Long = 20) {
            val request = OneTimeWorkRequestBuilder<NotificationPollWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(network)
                .setInputData(workDataOf(KEY_CHAIN to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(LOOP_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelBackgroundLoop(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(LOOP_WORK)

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LOOP_WORK)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }
    }
}
