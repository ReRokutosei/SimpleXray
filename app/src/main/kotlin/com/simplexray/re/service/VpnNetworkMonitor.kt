package com.simplexray.re.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Tracks Android's underlying networks and forwards them to [VpnService.setUnderlyingNetworks].
 *
 * This used to live inline in TProxyService; keeping it in a small class makes the VPN
 * lifecycle easier to audit and keeps ConnectivityManager callback state isolated.
 */
internal class VpnNetworkMonitor(
    context: Context,
    private val tag: String,
    private val applyUnderlyingNetworks: (Array<Network>?) -> Unit
) {
    private val appContext = context.applicationContext
    private val underlyingNetworks = mutableSetOf<Network>()
    private var connectivityManager: ConnectivityManager? = null
    private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (defaultNetworkCallback != null) return
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(tag, "Underlying network available: $network")
                updateUnderlyingNetworks { it.add(network) }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                Log.d(tag, "Underlying network capabilities changed: $network")
                updateUnderlyingNetworks { it.add(network) }
            }

            override fun onLost(network: Network) {
                Log.d(tag, "Underlying network lost: $network")
                updateUnderlyingNetworks { it.remove(network) }
            }
        }
        defaultNetworkCallback = callback
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            cm.activeNetwork?.let { activeNet ->
                updateUnderlyingNetworks { it.add(activeNet) }
            }
        }.onFailure {
            Log.w(tag, "Failed to register default network callback", it)
        }
    }

    fun stop() {
        val cm = connectivityManager
        val callback = defaultNetworkCallback
        defaultNetworkCallback = null
        synchronized(underlyingNetworks) {
            underlyingNetworks.clear()
        }
        if (cm != null && callback != null) {
            runCatching {
                cm.unregisterNetworkCallback(callback)
            }.onFailure {
                Log.w(tag, "Failed to unregister default network callback", it)
            }
        }
    }

    private fun updateUnderlyingNetworks(mutate: (MutableSet<Network>) -> Unit) {
        synchronized(underlyingNetworks) {
            mutate(underlyingNetworks)
            val networks = underlyingNetworks.toTypedArray()
            applyUnderlyingNetworks(if (networks.isEmpty()) null else networks)
        }
    }
}
