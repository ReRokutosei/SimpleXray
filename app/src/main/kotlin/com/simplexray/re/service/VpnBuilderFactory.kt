package com.simplexray.re.service

import android.content.pm.PackageManager
import android.net.IpPrefix
import android.net.ProxyInfo
import android.net.VpnService
import android.util.Log
import com.simplexray.re.BuildConfig
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.prefs.TunnelMode
import java.net.InetAddress

/**
 * Builds the VpnService interface from user preferences.
 *
 * Extracted from TProxyService so the builder rules can be reviewed independently
 * from the VPN process lifecycle.
 */
internal object VpnBuilderFactory {
    private const val TAG = "VpnBuilderFactory"

    /**
     * @param httpProxyEndpoint effective local endpoint of the HTTP inbound when
     *   the system HTTP proxy is enabled; [Pair.second] is the actual port from
     *   the selected config, falling back to prefs.httpPort when no HTTP inbound
     *   was declared.
     */
    fun create(
        service: VpnService,
        prefs: Preferences,
        tunMtu: Int,
        httpProxyEndpoint: Pair<String, Int>? = null,
    ): VpnService.Builder =
        service.Builder().apply {
            setBlocking(false)
            setMtu(tunMtu)
            setMetered(false)

            if (prefs.bypassLan) {
                runCatching {
                    excludeRoute(IpPrefix(InetAddress.getByName("10.0.0.0"), 8))
                    excludeRoute(IpPrefix(InetAddress.getByName("100.64.0.0"), 10))
                    excludeRoute(IpPrefix(InetAddress.getByName("169.254.0.0"), 16))
                    excludeRoute(IpPrefix(InetAddress.getByName("172.16.0.0"), 12))
                    excludeRoute(IpPrefix(InetAddress.getByName("192.168.0.0"), 16))
                    if (prefs.ipv6 && prefs.tunnelMode != TunnelMode.SimpleTun) {
                        excludeRoute(IpPrefix(InetAddress.getByName("fc00::"), 7))
                        excludeRoute(IpPrefix(InetAddress.getByName("fe80::"), 10))
                    }
                }.onFailure { Log.w(TAG, "Failed to exclude LAN routes", it) }
            }
            if (prefs.httpProxyEnabled) {
                val proxyHost = httpProxyEndpoint?.first ?: "127.0.0.1"
                val proxyPort = httpProxyEndpoint?.second ?: prefs.httpPort
                setHttpProxy(ProxyInfo.buildDirectProxy(proxyHost, proxyPort))
            }
            if (prefs.ipv4) {
                addAddress(prefs.tunnelIpv4Address, prefs.tunnelIpv4Prefix)
                addRoute("0.0.0.0", 0)
                prefs.dnsIpv4.takeIf { it.isNotEmpty() }?.also { addDnsServer(it) }
            }
            if (prefs.ipv6 && prefs.tunnelMode != TunnelMode.SimpleTun) {
                addAddress(prefs.tunnelIpv6Address, prefs.tunnelIpv6Prefix)
                addRoute("::", 0)
                prefs.dnsIpv6.takeIf { it.isNotEmpty() }?.also { addDnsServer(it) }
            }

            val rawApps = prefs.apps
            if (!rawApps.isNullOrEmpty()) {
                val validApps = mutableSetOf<String>()
                var hadInvalid = false
                for (appName in rawApps) {
                    if (appName.isNullOrBlank()) continue
                    try {
                        service.packageManager.getPackageInfo(appName, 0)
                        validApps.add(appName)
                        if (prefs.bypassSelectedApps) {
                            addDisallowedApplication(appName)
                        } else {
                            addAllowedApplication(appName)
                        }
                    } catch (e: PackageManager.NameNotFoundException) {
                        hadInvalid = true
                        Log.d(TAG, "Pruning uninstalled app package from VPN routing: $appName")
                    }
                }
                if (hadInvalid) {
                    prefs.apps = validApps
                }
            }
            if (prefs.bypassSelectedApps || prefs.apps.isNullOrEmpty()) {
                addDisallowedApplication(BuildConfig.APPLICATION_ID)
            }
        }
}
