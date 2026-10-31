package ca.udes.reparecampus.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import okhttp3.Interceptor
import java.io.IOException

/**
 * État « en ligne » = réseau système disponible ET API joignable. Le second critère compte :
 * en USB avec `adb reverse`, l'appareil peut avoir du réseau sans atteindre l'API, et inversement.
 */
class NetworkMonitor(context: Context, scope: CoroutineScope) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    val systemOnline: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(currentlyOnline()) }
        }
        cm.registerDefaultNetworkCallback(callback)
        trySend(currentlyOnline())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.stateIn(scope, SharingStarted.Eagerly, currentlyOnline())

    private val _apiReachable = MutableStateFlow(true)
    val apiReachable: StateFlow<Boolean> = _apiReachable.asStateFlow()

    val isOnline: StateFlow<Boolean> = combine(systemOnline, apiReachable) { system, api -> system && api }
        .stateIn(scope, SharingStarted.Eagerly, true)

    fun reportApiReachable(reachable: Boolean) { _apiReachable.value = reachable }

    private fun currentlyOnline(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    /** Intercepteur OkHttp qui met à jour [apiReachable] selon le résultat de chaque appel. */
    val interceptor = Interceptor { chain ->
        try {
            chain.proceed(chain.request()).also { reportApiReachable(true) }
        } catch (e: IOException) {
            if (!chain.call().isCanceled()) reportApiReachable(false)
            throw e
        }
    }
}

