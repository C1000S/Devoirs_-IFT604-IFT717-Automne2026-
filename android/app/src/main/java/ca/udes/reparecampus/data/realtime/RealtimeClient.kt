package ca.udes.reparecampus.data.realtime

import android.util.Log
import ca.udes.reparecampus.data.model.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import kotlin.coroutines.resume

/**
 * Client du flux SSE `GET /api/events` (filtré par rôle côté serveur).
 * Se reconnecte automatiquement avec un délai croissant (2 s → 60 s).
 */
class RealtimeClient(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val json: Json,
    private val scope: CoroutineScope,
    private val onNotification: suspend (AppNotification) -> Unit,
    private val onRequestChanged: suspend () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = 2_000L
            while (true) {
                val connectedAt = System.currentTimeMillis()
                listenOnce()
                // Connexion restée ouverte un moment : on repart d'un délai court.
                if (System.currentTimeMillis() - connectedAt > 30_000) backoff = 2_000L
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Ouvre le flux et suspend jusqu'à sa fermeture (erreur, fin, ou annulation). */
    private suspend fun listenOnce() = suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(baseUrl + "api/events").header("Accept", "text/event-stream").build()
        val source = EventSources.createFactory(client).newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                scope.launch {
                    when (type) {
                        "notification" -> runCatching { json.decodeFromString(AppNotification.serializer(), data) }
                            .onSuccess { onNotification(it) }
                        "repair-request" -> onRequestChanged()
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.d(TAG, "Flux SSE interrompu : ${t?.message ?: response?.code}")
                if (cont.isActive) cont.resume(Unit)
            }
        })
        cont.invokeOnCancellation { source.cancel() }
    }

    private companion object { const val TAG = "RealtimeClient" }
}
