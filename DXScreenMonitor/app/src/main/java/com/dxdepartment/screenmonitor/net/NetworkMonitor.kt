package com.dxdepartment.screenmonitor.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

enum class NetType(val label: String) { WIFI("Wi-Fi"), MOBILE("Mobile data"), ETHERNET("Ethernet"), OTHER("Connected"), NONE("Offline") }

data class NetState(
    val type: NetType = NetType.NONE,
    /** Android checked that this network reaches the internet. */
    val validated: Boolean = false,
    val metered: Boolean = false,
    /** This phone's address on the local network (Wi-Fi, hotspot or Ethernet), for the local viewer. */
    val localAddress: String? = null,
    val changedAt: Long = System.currentTimeMillis(),
    /** How many times the default network changed since the app started (Wi-Fi ↔ mobile data). */
    val switches: Int = 0,
) {
    val online: Boolean get() = type != NetType.NONE
    val label: String get() = when {
        type == NetType.NONE -> "Offline"
        validated -> type.label
        else -> "${type.label} (no internet)"
    }
}

/**
 * Follows the default network (Wi-Fi ↔ Jio mobile data ↔ offline). The local viewer listens on every interface,
 * so a change only changes the address shown; the WebRTC phase will use this to restart ICE.
 */
object NetworkMonitor {
    private const val TAG = "DXNet"
    private val _state = MutableStateFlow(NetState())
    val state: StateFlow<NetState> = _state.asStateFlow()
    private var started = false
    private var current: Network? = null

    @Synchronized
    fun start(ctx: Context) {
        if (started) return
        started = true
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                update(network, cm.getNetworkCapabilities(network))
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                update(network, caps)
            }

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                update(network, cm.getNetworkCapabilities(network))
            }

            override fun onLost(network: Network) {
                if (network == current) {
                    current = null
                    Log.i(TAG, "network lost")
                    _state.value = _state.value.copy(
                        type = NetType.NONE, validated = false, localAddress = localIPv4(),
                        changedAt = System.currentTimeMillis(), switches = _state.value.switches + 1,
                    )
                }
            }
        })
        _state.value = _state.value.copy(localAddress = localIPv4())
    }

    private fun update(network: Network, caps: NetworkCapabilities?) {
        val type = when {
            caps == null -> NetType.OTHER
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetType.ETHERNET
            else -> NetType.OTHER
        }
        val old = _state.value
        val changed = network != current
        current = network
        val s = old.copy(
            type = type,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            localAddress = localIPv4(),
            changedAt = if (changed || old.type != type) System.currentTimeMillis() else old.changedAt,
            switches = if (changed && old.type != NetType.NONE) old.switches + 1 else old.switches,
        )
        if (s.type != old.type || s.validated != old.validated || s.localAddress != old.localAddress) {
            Log.i(TAG, "network: ${s.label}, local address ${s.localAddress}")
        }
        _state.value = s
    }

    /** First private IPv4 on Wi-Fi / hotspot / Ethernet (wlan*, ap*, swlan*, eth*), else any private IPv4. */
    fun localIPv4(): String? = try {
        val list = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().map { nif.name to it } }
            .filter { it.second.isSiteLocalAddress }
        val preferred = list.firstOrNull { (n, _) -> n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("eth") }
        (preferred ?: list.firstOrNull())?.second?.hostAddress
    } catch (e: Exception) {
        null
    }
}
