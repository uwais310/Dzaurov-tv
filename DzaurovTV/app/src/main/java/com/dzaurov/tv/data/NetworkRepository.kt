package com.dzaurov.tv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class NetKind { Offline, Wifi, Ethernet, Other }

/** Current connection. [bars] is 0..4 for Wi-Fi, 4 for cable, 0 when offline. */
data class NetState(val kind: NetKind, val bars: Int) {
    val label: String
        get() = when (kind) {
            NetKind.Wifi -> "Wi-Fi"
            NetKind.Ethernet -> "Ethernet"
            NetKind.Other -> "Online"
            NetKind.Offline -> "Offline"
        }

    companion object {
        val OFFLINE = NetState(NetKind.Offline, 0)
    }
}

/**
 * Watches the default network with a system callback (no polling), so the header icon
 * changes the moment Wi-Fi drops or comes back.
 */
class NetworkRepository(private val context: Context) {

    private val _state = MutableStateFlow(NetState.OFFLINE)
    val state: StateFlow<NetState> = _state

    private var registered = false

    fun start() {
        if (registered) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        _state.value = runCatching { from(cm.getNetworkCapabilities(cm.activeNetwork)) }.getOrDefault(NetState.OFFLINE)
        registered = runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _state.value = from(caps)
                }

                override fun onLost(network: Network) {
                    _state.value = NetState.OFFLINE
                }
            })
        }.isSuccess
    }

    private fun from(caps: NetworkCapabilities?): NetState {
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetState.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetState(NetKind.Ethernet, 4)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetState(NetKind.Wifi, wifiBars(caps.signalStrength))
            else -> NetState(NetKind.Other, 3)
        }
    }

    /** RSSI in dBm to 1..4 bars; unknown strength shows as full. */
    private fun wifiBars(rssi: Int): Int = when {
        rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED -> 4
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        else -> 1
    }
}
