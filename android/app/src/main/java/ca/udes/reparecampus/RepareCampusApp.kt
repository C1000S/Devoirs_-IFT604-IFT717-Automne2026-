package ca.udes.reparecampus

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ca.udes.reparecampus.data.session.SessionState
import ca.udes.reparecampus.notifications.NotificationPollWorker

class RepareCampusApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Une activité de l'application est visible. Au premier plan, pas de notification système. */
    @Volatile
    var isInForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this) { isInForeground }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isInForeground = true
                NotificationPollWorker.cancelBackgroundLoop(this@RepareCampusApp)
            }

            override fun onStop(owner: LifecycleOwner) {
                isInForeground = false
                if (container.sessionStore.state.value is SessionState.LoggedIn) {
                    NotificationPollWorker.scheduleBackgroundLoop(this@RepareCampusApp)
                }
            }
        })
    }
}
