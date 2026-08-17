package com.simplexray.re.prefs

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.util.Log
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
    private val contentResolver: ContentResolver
    private val context1: Context = context.applicationContext

    init {
        this.contentResolver = context1.contentResolver
    }

    private fun getPrefData(key: String): Pair<String?, String?> {
        val uri = PrefsContract.PrefsEntry.CONTENT_URI.buildUpon().appendPath(key).build()
        try {
            contentResolver.query(
                uri, arrayOf(
                    PrefsContract.PrefsEntry.COLUMN_PREF_VALUE,
                    PrefsContract.PrefsEntry.COLUMN_PREF_TYPE
                ), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val valueColumnIndex =
                        cursor.getColumnIndex(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE)
                    val typeColumnIndex =
                        cursor.getColumnIndex(PrefsContract.PrefsEntry.COLUMN_PREF_TYPE)
                    val value =
                        if (valueColumnIndex != -1) cursor.getString(valueColumnIndex) else null
                    val type =
                        if (typeColumnIndex != -1) cursor.getString(typeColumnIndex) else null
                    return Pair(value, type)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading preference data for key: $key", e)
        }
        return Pair(null, null)
    }

    private fun getBooleanPref(key: String, default: Boolean): Boolean {
        val (value, type) = getPrefData(key)
        if (value != null && "Boolean" == type) {
            return value.toBoolean()
        }
        return default
    }

    private fun setValueInProvider(key: String, value: Any?) {
        val uri = PrefsContract.PrefsEntry.CONTENT_URI.buildUpon().appendPath(key).build()
        val values = ContentValues()
        when (value) {
            is String -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            is Int -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            is Boolean -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            is Long -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            is Float -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            else -> {
                if (value != null) {
                    Log.e(TAG, "Unsupported type for key: $key with value: $value")
                    return
                }
                values.putNull(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE)
            }
        }
        try {
            val rows = contentResolver.update(uri, values, null, null)
            if (rows == 0) {
                Log.w(TAG, "Update failed or key not found for: $key")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preference for key: $key", e)
        }
    }

    // --- Provider-backed property delegates (collapse the repetitive get/set boilerplate) ---

    private fun stringPref(key: String, default: () -> String = { "" }): ReadWriteProperty<Any?, String> =
        object : ReadWriteProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String =
                getPrefData(key).first ?: default()
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
                setValueInProvider(key, value)
        }

    private fun nullableStringPref(key: String): ReadWriteProperty<Any?, String?> =
        object : ReadWriteProperty<Any?, String?> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String? =
                getPrefData(key).first
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) =
                setValueInProvider(key, value)
        }

    private fun booleanPref(key: String, default: Boolean): ReadWriteProperty<Any?, Boolean> =
        object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
                getBooleanPref(key, default)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
                setValueInProvider(key, value)
        }

    private fun intPref(key: String, default: Int, logTag: String? = null): ReadWriteProperty<Any?, Int> =
        object : ReadWriteProperty<Any?, Int> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
                val value = getPrefData(key).first
                val intValue = value?.toIntOrNull()
                if (value != null && intValue == null) {
                    logTag?.let { Log.e(TAG, "Failed to parse $it as Integer: $value") }
                }
                return intValue ?: default
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
                setValueInProvider(key, value)
        }

    var socksAddress: String by stringPref(SOCKS_ADDR) { "127.0.0.1" }
    var socksPort: Int by intPref(SOCKS_PORT, 10808, "SocksPort")
    var socksUsername: String by stringPref(SOCKS_USER)
    var socksPassword: String by stringPref(SOCKS_PASS)
    var dnsIpv4: String by stringPref(DNS_IPV4) { "8.8.8.8" }
    var dnsIpv6: String by stringPref(DNS_IPV6) { "2001:4860:4860::8888" }

    val udpInTcp: Boolean
        get() = getBooleanPref(UDP_IN_TCP, false)

    var ipv4: Boolean by booleanPref(IPV4, true)
    var ipv6: Boolean by booleanPref(IPV6, false)
    var global: Boolean by booleanPref(GLOBAL, false)

    var apps: Set<String?>?
        get() {
            val jsonSet = getPrefData(APPS).first
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
            setValueInProvider(APPS, jsonSet)
        }

    var enable: Boolean by booleanPref(ENABLE, false)
    var disableVpn: Boolean by booleanPref(DISABLE_VPN, false)
    var tunnelMode: TunnelMode
        get() = getPrefData(TUNNEL_MODE).first?.let { TunnelMode.fromString(it) } ?: TunnelMode.XrayTun
        set(value) {
            setValueInProvider(TUNNEL_MODE, value.value)
        }

    // Fixed tunnel constants (not persisted)
    val tunnelMtu: Int get() = 8500
    val tunnelMtuForXrayTun: Int get() = 1500
    val tunnelIpv4Address: String get() = "198.18.0.1"
    val tunnelIpv4Prefix: Int get() = 32
    val tunnelIpv6Address: String get() = "fc00::1"
    val tunnelIpv6Prefix: Int get() = 128
    val taskStackSize: Int get() = 81920

    var selectedConfigPath: String? by nullableStringPref(SELECTED_CONFIG_PATH)
    var bypassLan: Boolean by booleanPref(BYPASS_LAN, true)
    var useTemplate: Boolean by booleanPref(USE_TEMPLATE, true)
    var hideFromRecents: Boolean by booleanPref(HIDE_FROM_RECENTS, true)
    var geoUpdateIntervalHours: Int by intPref(GEO_UPDATE_INTERVAL_HOURS, 0)
    var httpProxyEnabled: Boolean by booleanPref(HTTP_PROXY_ENABLED, false)
    var httpPort: Int by intPref(HTTP_PORT, 10809, "HttpPort")
    var customGeoipImported: Boolean by booleanPref(CUSTOM_GEOIP_IMPORTED, false)
    var customGeositeImported: Boolean by booleanPref(CUSTOM_GEOSITE_IMPORTED, false)
    var keepAwake: Boolean by booleanPref(KEEP_AWAKE, false)

    var configFilesOrder: List<String>
        get() {
            val jsonList = getPrefData(CONFIG_FILES_ORDER).first
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
            setValueInProvider(CONFIG_FILES_ORDER, jsonList)
        }

    var geoipUrl: String by stringPref(GEOIP_URL) { context1.getString(R.string.geoip_url) }
    var geositeUrl: String by stringPref(GEOSITE_URL) { context1.getString(R.string.geosite_url) }
    var apiAddress: String by stringPref(API_ADDRESS) { "127.0.0.1" }
    var appIcon: String? by nullableStringPref(APP_ICON)
    var apiPort: Int by intPref(API_PORT, 0)
    var bypassSelectedApps: Boolean by booleanPref(BYPASS_SELECTED_APPS, false)

    var theme: ThemeMode
        get() = getPrefData(THEME).first?.let { ThemeMode.fromString(it) } ?: ThemeMode.Auto
        set(value) {
            setValueInProvider(THEME, value.value)
        }

    var notificationPrompted: Boolean by booleanPref(NOTIFICATION_PROMPTED, false)

    var customDatUrls: Map<String, String>
        get() {
            val json = getPrefData(CUSTOM_DAT_URLS).first
            return if (!json.isNullOrEmpty()) {
                runCatching { Json.decodeFromString<Map<String, String>>(json) }.getOrDefault(emptyMap())
            } else emptyMap()
        }
        set(value) {
            val json = Json.encodeToString(value)
            setValueInProvider(CUSTOM_DAT_URLS, json)
        }

    var logLevel: LogLevel
        get() = getPrefData(LOG_LEVEL).first?.let { LogLevel.fromString(it) } ?: LogLevel.Auto
        set(level) {
            setValueInProvider(LOG_LEVEL, level.value)
        }

    companion object {
        const val LOG_LEVEL: String = "LogLevel"
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
        const val USE_TEMPLATE: String = "UseTemplate"
        const val HTTP_PROXY_ENABLED: String = "HttpProxyEnabled"
        const val CUSTOM_GEOIP_IMPORTED: String = "CustomGeoipImported"
        const val CUSTOM_GEOSITE_IMPORTED: String = "CustomGeositeImported"
        const val CONFIG_FILES_ORDER: String = "ConfigFilesOrder"
        const val DISABLE_VPN: String = "DisableVpn"
        const val TUNNEL_MODE: String = "TunnelMode"
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
        const val CUSTOM_DAT_URLS: String = "CustomDatUrls"
        private const val TAG = "Preferences"
    }
}
