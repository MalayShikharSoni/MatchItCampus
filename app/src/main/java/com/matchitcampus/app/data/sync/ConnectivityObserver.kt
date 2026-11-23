package com.matchitcampus.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "MatchIt/Connectivity"

/**
 * Whether the phone currently has a working internet connection, as a Flow.
 *
 * Android reports network changes through `ConnectivityManager.NetworkCallback`. `callbackFlow`
 * turns those callbacks into a Flow: the callback is registered when someone starts
 * collecting, and unregistered in `awaitClose` when they stop, so it can't leak.
 *
 * `SyncManager` uses it to push pending changes as soon as the connection comes back.
 */
class ConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** Emits the current state straight away, then every change. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasInternet())
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }
        }
        trySend(isCurrentlyOnline())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** A one-off check, for code that isn't collecting [isOnline]. */
    fun isCurrentlyOnline(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasInternet() == true
    }

    /**
     * INTERNET means the network claims to reach the internet; VALIDATED means Android has
     * checked that it really does (it isn't, for example, a Wi-Fi login page).
     */
    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    init {
        Log.d(TAG, "Created; online now: ${isCurrentlyOnline()}")
    }
}
