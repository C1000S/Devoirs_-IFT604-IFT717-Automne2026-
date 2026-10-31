package ca.udes.reparecampus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.udes.reparecampus.notifications.AppNotifier
import ca.udes.reparecampus.ui.navigation.AppRoot
import ca.udes.reparecampus.ui.theme.RepareCampusTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Demande à ouvrir, transmise par une notification système. */
    private val deepLinkRequestId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val sessionStore = (application as RepareCampusApp).container.sessionStore
        setContent {
            RepareCampusTheme {
                val sessionState by sessionStore.state.collectAsStateWithLifecycle()
                val deepLink by deepLinkRequestId.collectAsStateWithLifecycle()
                AppRoot(sessionState, deepLink, onDeepLinkHandled = { deepLinkRequestId.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(AppNotifier.EXTRA_REQUEST_ID)?.let {
            deepLinkRequestId.value = it
            // Consommé : une recréation de l'activité (rotation…) ne doit pas rouvrir la demande.
            intent.removeExtra(AppNotifier.EXTRA_REQUEST_ID)
        }
    }
}
