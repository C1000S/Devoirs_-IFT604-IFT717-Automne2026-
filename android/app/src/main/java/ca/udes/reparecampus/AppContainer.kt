package ca.udes.reparecampus

import android.content.Context
import ca.udes.reparecampus.data.local.AppDatabase
import ca.udes.reparecampus.data.realtime.RealtimeClient
import ca.udes.reparecampus.data.remote.AuthInterceptor
import ca.udes.reparecampus.data.remote.NetworkModule
import ca.udes.reparecampus.data.remote.apiCall
import ca.udes.reparecampus.data.repository.AuthRepository
import ca.udes.reparecampus.data.repository.DraftRepository
import ca.udes.reparecampus.data.repository.RequestRepository
import ca.udes.reparecampus.data.session.SessionState
import ca.udes.reparecampus.data.session.SessionStore
import ca.udes.reparecampus.data.sync.NetworkMonitor
import ca.udes.reparecampus.data.sync.SyncScheduler
import ca.udes.reparecampus.notifications.AppNotifier
import ca.udes.reparecampus.notifications.NotificationPollWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Injection de dépendances manuelle : une seule instance par application. */
@OptIn(FlowPreview::class)
class AppContainer(private val context: Context, private val isInForeground: () -> Boolean) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore = SessionStore(context, applicationScope)
    val networkMonitor = NetworkMonitor(context, applicationScope)

    private val httpClient = NetworkModule.createClient(
        authInterceptor = AuthInterceptor(
            tokenProvider = { sessionStore.currentToken },
            onUnauthorized = { applicationScope.launch { sessionStore.clear() } },
        ),
        reachabilityInterceptor = networkMonitor.interceptor,
    )
    private val api = NetworkModule.createApi(httpClient)

    private val database = AppDatabase.build(context)
    private val syncScheduler = SyncScheduler(context)

    val authRepository = AuthRepository(api, sessionStore)
    val draftRepository = DraftRepository(database.draftDao())
    val requestRepository = RequestRepository(
        api = api,
        db = database,
        json = NetworkModule.json,
        scope = applicationScope,
        scheduler = syncScheduler,
        currentSession = { (sessionStore.state.value as? SessionState.LoggedIn)?.session },
    )

    val notifier = AppNotifier(context)

    /** Demandes de rafraîchissement venant du flux temps réel, regroupées (anti-rafale). */
    private val realtimeRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val realtimeClient = RealtimeClient(
        client = NetworkModule.createSseClient(httpClient),
        baseUrl = BuildConfig.API_BASE_URL,
        json = NetworkModule.json,
        scope = applicationScope,
        onNotification = { n ->
            if (requestRepository.onRealtimeNotification(n) && !isInForeground()) notifier.show(n)
            realtimeRefresh.tryEmit(Unit)
        },
        onRequestChanged = { realtimeRefresh.tryEmit(Unit) },
    )

    private val loggedIn: Boolean get() = sessionStore.state.value is SessionState.LoggedIn

    init {
        // Connexion : flux temps réel + vérification périodique des notifications.
        // Déconnexion (bouton ou jeton expiré) : tout s'arrête et les données de la personne précédente sont effacées.
        applicationScope.launch {
            sessionStore.state.map { it is SessionState.LoggedIn }.distinctUntilChanged().collect { connected ->
                if (connected) {
                    realtimeClient.start()
                    NotificationPollWorker.schedulePeriodic(context)
                } else if (sessionStore.state.value is SessionState.LoggedOut) {
                    realtimeClient.stop()
                    NotificationPollWorker.cancelAll(context)
                    requestRepository.clearSessionData()
                }
            }
        }
        // Un changement annoncé par le flux SSE rafraîchit l'affichage (au premier plan seulement).
        applicationScope.launch {
            realtimeRefresh.debounce(1_000).collect { if (isInForeground()) requestRepository.refresh() }
        }
        // Retour en ligne : envoyer la file d'actions puis rafraîchir le cache.
        applicationScope.launch {
            networkMonitor.isOnline.drop(1).filter { it }.collect { if (loggedIn) requestRepository.refresh() }
        }
        // API injoignable alors que le réseau semble présent (serveur arrêté, adb reverse retiré…) :
        // on la sonde régulièrement pour détecter son retour.
        applicationScope.launch {
            while (true) {
                delay(15_000)
                if (loggedIn && !networkMonitor.apiReachable.value) apiCall { api.health() }
            }
        }
    }
}
