package ca.usherbrooke.reparecampus

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.work.NotificationHelper
import ca.usherbrooke.reparecampus.work.NotificationWorker
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class RepareCampusApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Charge le jeton et l'URL persistés avant toute requête réseau.
        runBlocking { container.warmUp() }

        NotificationHelper.createChannel(this)
        scheduleNotificationPolling()
    }

    // Vérifie périodiquement les nouvelles notifications en arrière-plan.
    // Période minimale imposée par WorkManager : 15 minutes.
    private fun scheduleNotificationPolling() {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notification-polling",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
