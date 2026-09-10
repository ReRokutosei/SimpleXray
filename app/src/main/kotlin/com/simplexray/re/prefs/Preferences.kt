package com.simplexray.re.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.simplexray.re.R
import com.simplexray.re.common.ThemeMode
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class LogLevel(val value: String) {
    Auto("auto"),
    Debug("debug"),
    Info("info"),
    Warning("warning"),
    Error("error"),
    None("none");

    companion object {
        fun fromString(value: String): LogLevel {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: Auto
        }
    }
}

enum class TunnelMode(val value: String) {
    XrayTun("xray_tun"),
    HevSocks5Tunnel("hev_socks5_tunnel");

    companion object {
        fun fromString(value: String): TunnelMode =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: XrayTun
    }
}

class Preferences(context: Context) {
    private val context1: Context = context.applicationContext
    private val sp: SharedPreferences =
        context1.getSharedPreferences("${context1.packageName}_preferences", Context.MODE_PRIVATE)

    private fun safeGetBoolean(key: String, default: Boolean): Boolean {
        return try {
            sp.getBoolean(key, default)
        } catch (e: ClassCastException) {
            sp.getString(key, null)?.toBooleanStrictOrNull() ?: default
        }
    }

    private fun safeGetInt(key: String, default: Int): Int {
        return try {
            sp.getInt(key, default)
        } catch (e: ClassCastException) {
            sp.getString(key, null)?.toIntOrNull() ?: default
        }
    }

    private fun safeGetLong(key: String, default: Long): Long {
        return try {
            sp.getLong(key, default)
        } catch (e: ClassCastException) {
            sp.getString(key, null)?.toLongOrNull() ?: default
        }
    }

    // --- SharedPreferences property delegates ---

    private fun stringPref(key: String, default: () -> String = { "" }): ReadWriteProperty<Any?, String> =
        object : ReadWriteProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String =
                sp.getString(key, null) ?: default()
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
                sp.edit { putString(key, value) }
            }
        }

    private fun nullableStringPref(key: String): ReadWriteProperty<Any?, String?> =
        object : ReadWriteProperty<Any?, String?> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String? =
                sp.getString(key, null)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
                sp.edit {
                    if (value == null) remove(key) else putString(key, value)
                }
            }
        }

    private fun booleanPref(key: String, default: Boolean): ReadWriteProperty<Any?, Boolean> =
        object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
                safeGetBoolean(key, default)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
                sp.edit { putBoolean(key, value) }
            }
        }

    private fun intPref(key: String, default: Int): ReadWriteProperty<Any?, Int> =
        object : ReadWriteProperty<Any?, Int> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Int =
                safeGetInt(key, default)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                sp.edit { putInt(key, value) }
            }
        }

    private fun longPref(key: String, default: Long): ReadWriteProperty<Any?, Long> =
        object : ReadWriteProperty<Any?, Long> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Long =
                safeGetLong(key, default)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
                sp.edit { putLong(key, value) }
            }
        }

    var socksAddress: String by stringPref(SOCKS_ADDR) { "127.0.0.1" }
    var socksPort: Int by intPref(SOCKS_PORT, 10808)
    var socksUsername: String by stringPref(SOCKS_USER)
    var socksPassword: String by stringPref(SOCKS_PASS)
    var dnsIpv4: String by stringPref(DNS_IPV4) { "8.8.8.8" }
    var dnsIpv6: String by stringPref(DNS_IPV6) { "2001:4860:4860::8888" }

    val udpInTcp: Boolean
        get() = safeGetBoolean(UDP_IN_TCP, false)

    var ipv4: Boolean by booleanPref(IPV4, true)
    var ipv6: Boolean by booleanPref(IPV6, false)
    var global: Boolean by booleanPref(GLOBAL, false)

    var apps: Set<String?>?
        get() {
            val jsonSet = sp.getString(APPS, null)
            return jsonSet?.let {
                try {
                    Json.decodeFromString<Set<String>>(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing APPS StringSet", e)
                    null
                }
            }
        }
        set(apps) {
            val validSet = apps?.filterNotNull()?.toSet() ?: emptySet()
            val jsonSet = Json.encodeToString(validSet)
            sp.edit { putString(APPS, jsonSet) }
        }

    var enable: Boolean by booleanPref(ENABLE, false)
    var disableVpn: Boolean by booleanPref(DISABLE_VPN, false)
    var tunnelMode: TunnelMode
        get() = sp.getString(TUNNEL_MODE, null)?.let { TunnelMode.fromString(it) } ?: TunnelMode.XrayTun
        set(value) {
            sp.edit { putString(TUNNEL_MODE, value.value) }
        }

    var tunnelMtu: Int by intPref(TUNNEL_MTU, 1500)
    val tunnelIpv4Address: String get() = "198.18.0.1"
    val tunnelIpv4Prefix: Int get() = 32
    val tunnelIpv6Address: String get() = "fc00::1"
    val tunnelIpv6Prefix: Int get() = 128
    val taskStackSize: Int get() = 81920

    var selectedConfigPath: String? by nullableStringPref(SELECTED_CONFIG_PATH)
    var bypassLan: Boolean by booleanPref(BYPASS_LAN, true)
    var hideFromRecents: Boolean by booleanPref(HIDE_FROM_RECENTS, true)
    var geoUpdateIntervalHours: Int by intPref(GEO_UPDATE_INTERVAL_HOURS, 0)
    var lastGeoUpdateTime: Long by longPref(LAST_GEO_UPDATE_TIME, 0L)
    var httpProxyEnabled: Boolean by booleanPref(HTTP_PROXY_ENABLED, false)
    var httpPort: Int by intPref(HTTP_PORT, 10809)
    var customGeoipImported: Boolean by booleanPref(CUSTOM_GEOIP_IMPORTED, false)
    var customGeositeImported: Boolean by booleanPref(CUSTOM_GEOSITE_IMPORTED, false)
    var keepAwake: Boolean by booleanPref(KEEP_AWAKE, false)

    var configFilesOrder: List<String>
        get() {
            val jsonList = sp.getString(CONFIG_FILES_ORDER, null)
            return jsonList?.let {
                try {
                    Json.decodeFromString<List<String>>(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing CONFIG_FILES_ORDER List<String>", e)
                    emptyList()
                }
            } ?: emptyList()
        }
        set(order) {
            val jsonList = Json.encodeToString(order)
            sp.edit { putString(CONFIG_FILES_ORDER, jsonList) }
        }

    var geoipUrl: String by stringPref(GEOIP_URL) { context1.getString(R.string.geoip_url) }
    var geositeUrl: String by stringPref(GEOSITE_URL) { context1.getString(R.string.geosite_url) }
    var apiAddress: String by stringPref(API_ADDRESS) { "127.0.0.1" }
    var appIcon: String? by nullableStringPref(APP_ICON)
    var apiPort: Int by intPref(API_PORT, 0)
    var bypassSelectedApps: Boolean by booleanPref(BYPASS_SELECTED_APPS, false)

    var theme: ThemeMode
        get() = sp.getString(THEME, null)?.let { ThemeMode.fromString(it) } ?: ThemeMode.Auto
        set(value) {
            sp.edit { putString(THEME, value.value) }
        }

    var notificationPrompted: Boolean by booleanPref(NOTIFICATION_PROMPTED, false)

    var customDatUrls: Map<String, String>
        get() {
            val json = sp.getString(CUSTOM_DAT_URLS, null)
            return if (!json.isNullOrEmpty()) {
                runCatching { Json.decodeFromString<Map<String, String>>(json) }.getOrDefault(emptyMap())
            } else emptyMap()
        }
        set(value) {
            val json = Json.encodeToString(value)
            sp.edit { putString(CUSTOM_DAT_URLS, json) }
        }

    var logLevel: LogLevel
        get() = sp.getString(LOG_LEVEL, null)?.let { LogLevel.fromString(it) } ?: LogLevel.Auto
        set(level) {
            sp.edit { putString(LOG_LEVEL, level.value) }
        }

    var accessLog: Boolean
        get() = safeGetBoolean(ACCESS_LOG, true)
        set(value) {
            sp.edit { putBoolean(ACCESS_LOG, value) }
        }

    var dnsLog: Boolean
        get() = safeGetBoolean(DNS_LOG, false)
        set(value) {
            sp.edit { putBoolean(DNS_LOG, value) }
        }

    companion object {
        const val LOG_LEVEL: String = "LogLevel"
        const val ACCESS_LOG: String = "AccessLog"
        const val DNS_LOG: String = "DnsLog"
        const val SOCKS_ADDR: String = "SocksAddr"
        const val SOCKS_PORT: String = "SocksPort"
        const val HTTP_PORT: String = "HttpPort"
        const val SOCKS_USER: String = "SocksUser"
        const val SOCKS_PASS: String = "SocksPass"
        const val DNS_IPV4: String = "DnsIpv4"
        const val DNS_IPV6: String = "DnsIpv6"
        const val IPV4: String = "Ipv4"
        const val IPV6: String = "Ipv6"
        const val GLOBAL: String = "Global"
        const val UDP_IN_TCP: String = "UdpInTcp"
        const val APPS: String = "Apps"
        const val ENABLE: String = "Enable"
        const val SELECTED_CONFIG_PATH: String = "SelectedConfigPath"
        const val BYPASS_LAN: String = "BypassLan"
        const val HTTP_PROXY_ENABLED: String = "HttpProxyEnabled"
        const val CUSTOM_GEOIP_IMPORTED: String = "CustomGeoipImported"
        const val CUSTOM_GEOSITE_IMPORTED: String = "CustomGeositeImported"
        const val CONFIG_FILES_ORDER: String = "ConfigFilesOrder"
        const val DISABLE_VPN: String = "DisableVpn"
        const val TUNNEL_MODE: String = "TunnelMode"
        const val TUNNEL_MTU: String = "TunnelMtu"
        const val APP_ICON: String = "AppIcon"
        const val GEOIP_URL: String = "GeoipUrl"
        const val GEOSITE_URL: String = "GeositeUrl"
        const val API_ADDRESS: String = "ApiAddress"
        const val API_PORT: String = "ApiPort"
        const val BYPASS_SELECTED_APPS: String = "BypassSelectedApps"
        const val THEME: String = "Theme"
        const val HIDE_FROM_RECENTS: String = "HideFromRecents"
        const val KEEP_AWAKE: String = "KeepAwake"
        const val NOTIFICATION_PROMPTED: String = "NotificationPrompted"
        const val GEO_UPDATE_INTERVAL_HOURS: String = "GeoUpdateIntervalHours"
        const val LAST_GEO_UPDATE_TIME: String = "LastGeoUpdateTime"
        const val CUSTOM_DAT_URLS: String = "CustomDatUrls"
        private const val TAG = "Preferences"
    }
}
