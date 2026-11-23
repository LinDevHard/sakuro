package com.rinwave.sakuro.engine.media3

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the device has a usable network connection right now. Reconnects wait on this
 * instead of burning retry attempts while offline, and resume as soon as a network returns.
 * Without the permission or the service it reports "online" so retries still proceed.
 */
internal class NetworkAvailability(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            // Not VALIDATED: LAN-only networks (a home media server over a hotspot) never validate.
            _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        // The default-network callback reports a replacement network via onCapabilitiesChanged.
        override fun onLost(network: Network) {
            _online.value = false
        }
    }

    private var registered = runCatching {
        connectivity?.registerDefaultNetworkCallback(callback)
        connectivity != null
    }.getOrDefault(false)

    private fun currentlyOnline(): Boolean = runCatching {
        val manager = connectivity ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    fun release() {
        if (!registered) return
        registered = false
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }
}
