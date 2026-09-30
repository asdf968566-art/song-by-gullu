package com.sangeet.player.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Internet hai ya nahi, aur Wi-Fi (unmetered) hai ya mobile data. */
class NetworkMonitor(context: Context) {
    data class Status(val online: Boolean, val unmetered: Boolean)

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<Status> = _status.asStateFlow()

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _status.value = read() }
            override fun onLost(network: Network) { _status.value = read() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _status.value = read()
            }
        })
    }

    private fun read(): Status {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?: return Status(online = false, unmetered = false)
        return Status(
            online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }
}
