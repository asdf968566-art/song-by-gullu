package com.sangeet.player.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Internet hai ya nahi, aur Wi-Fi (unmetered) hai ya mobile data.
 *
 * Report #48 (Oct 14, a LAVA phone): "you are offline" right after opening the app with the internet on. When the
 * app starts in the background (an update check, Song of the day), Android can block its network then, and
 * `activeNetwork` is null. When the app comes to the screen, only `onBlockedStatusChanged` says so; the status stayed
 * "offline" because that wasn't handled. Now it is, the app reads it again when it comes back on screen, and anything
 * that is about to give up on the internet asks [refresh] first.
 */
class NetworkMonitor(context: Context) {
    data class Status(val online: Boolean, val unmetered: Boolean)

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<Status> = _status.asStateFlow()

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh() }
            override fun onLost(network: Network) { refresh() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { refresh() }
            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) { refresh() }
        })
    }

    /** Reads the connection again now (callbacks can come late, or not at all after a block is lifted). */
    fun refresh(): Status = read().also { _status.value = it }

    private fun read(): Status {
        // The default network, else any network with internet (`activeNetwork` is null for a moment while Wi-Fi hands
        // over to mobile data, and while the app's network is blocked in the background).
        val active = runCatching { cm.activeNetwork }.getOrNull()
        @Suppress("DEPRECATION")
        val all = runCatching { cm.allNetworks.toList() }.getOrDefault(emptyList())
        val caps = (listOfNotNull(active) + all).asSequence()
            .mapNotNull { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
            .firstOrNull { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
            ?: return Status(online = false, unmetered = false)
        return Status(online = true, unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }
}
