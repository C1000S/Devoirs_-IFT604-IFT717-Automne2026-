package ca.usherbrooke.reparecampus.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.callbackFlow

// Émet l'état de connectivité réseau (en ligne / hors ligne) sous forme de Flow.
// Sert à afficher la bannière « mode hors ligne » et à activer la démonstration hors ligne.
class ConnectivityObserver(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnlineNow(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(isOnlineNow()) }
            override fun onUnavailable() { trySend(false) }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(isOnlineNow())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.onStart { emit(isOnlineNow()) }.distinctUntilChanged()
}
