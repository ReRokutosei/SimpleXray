package com.simplexray.re.prefs

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Resources
import com.simplexray.re.R
import com.simplexray.re.common.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSharedPreferences : SharedPreferences {
    val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(key: String?, defValue: String?): String? {
        val v = map[key] ?: return defValue
        return v as? String ?: throw ClassCastException("Value is not a String")
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val v = map[key] ?: return defValues
        return (v as? Set<String>)?.toMutableSet() ?: throw ClassCastException("Value is not a Set")
    }

    override fun getInt(key: String?, defValue: Int): Int {
        val v = map[key] ?: return defValue
        if (v is Int) return v
        if (v is Number) return v.toInt()
        throw ClassCastException("Value $v is not an Int")
    }

    override fun getLong(key: String?, defValue: Long): Long {
        val v = map[key] ?: return defValue
        if (v is Long) return v
        if (v is Number) return v.toLong()
        throw ClassCastException("Value $v is not a Long")
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        val v = map[key] ?: return defValue
        if (v is Float) return v
        if (v is Number) return v.toFloat()
        throw ClassCastException("Value $v is not a Float")
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        val v = map[key] ?: return defValue
        if (v is Boolean) return v
        throw ClassCastException("Value $v is not a Boolean")
    }

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val sp: FakeSharedPreferences) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) temp[key] = values?.toSet()
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) sp.map.clear()
            for ((k, v) in temp) {
                if (v == null) sp.map.remove(k) else sp.map[k] = v
            }
        }
    }
}

@Suppress("DEPRECATION")
class FakeResources : Resources(null, null, null) {
    override fun getString(id: Int): String {
        return when (id) {
            R.string.geoip_url -> "https://example.com/geoip.dat"
            R.string.geosite_url -> "https://example.com/geosite.dat"
            else -> ""
        }
    }
}

class FakeContext(
    val sharedPrefs: FakeSharedPreferences = FakeSharedPreferences(),
    private val fakeResources: Resources = FakeResources()
) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getPackageName(): String = "com.simplexray.re"
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = sharedPrefs
    override fun getResources(): Resources = fakeResources
}

class PreferencesTest {

    private lateinit var fakeContext: FakeContext
    private lateinit var prefs: Preferences

    @Before
    fun setUp() {
        fakeContext = FakeContext()
        fakeContext.sharedPrefs.map[Preferences.GEOIP_URL] = "https://example.com/geoip.dat"
        fakeContext.sharedPrefs.map[Preferences.GEOSITE_URL] = "https://example.com/geosite.dat"
        prefs = Preferences(fakeContext)
    }

    @Test
    fun testDefaultValues() {
        assertEquals("127.0.0.1", prefs.socksAddress)
        assertEquals(10808, prefs.socksPort)
        assertEquals("", prefs.socksUsername)
        assertEquals("", prefs.socksPassword)
        assertEquals("8.8.8.8", prefs.dnsIpv4)
        assertEquals("2001:4860:4860::8888", prefs.dnsIpv6)
        assertTrue(prefs.ipv4)
        assertFalse(prefs.ipv6)
        assertFalse(prefs.global)
        assertFalse(prefs.udpInTcp)
        assertFalse(prefs.enable)
        assertFalse(prefs.disableVpn)
        assertEquals(TunnelMode.XrayTun, prefs.tunnelMode)
        assertEquals(1500, prefs.tunnelMtu)
        assertEquals("198.18.0.1", prefs.tunnelIpv4Address)
        assertEquals(32, prefs.tunnelIpv4Prefix)
        assertEquals("fc00::1", prefs.tunnelIpv6Address)
        assertEquals(128, prefs.tunnelIpv6Prefix)
        assertEquals(81920, prefs.taskStackSize)
        assertNull(prefs.selectedConfigPath)
        assertTrue(prefs.bypassLan)
        assertTrue(prefs.hideFromRecents)
        assertEquals(0, prefs.geoUpdateIntervalHours)
        assertEquals(0L, prefs.lastGeoUpdateTime)
        assertFalse(prefs.httpProxyEnabled)
        assertEquals(10809, prefs.httpPort)
        assertFalse(prefs.customGeoipImported)
        assertFalse(prefs.customGeositeImported)
        assertFalse(prefs.keepAwake)
        assertTrue(prefs.configFilesOrder.isEmpty())
        assertEquals("https://example.com/geoip.dat", prefs.geoipUrl)
        assertEquals("https://example.com/geosite.dat", prefs.geositeUrl)
        assertEquals("127.0.0.1", prefs.apiAddress)
        assertNull(prefs.appIcon)
        assertEquals(0, prefs.apiPort)
        assertFalse(prefs.bypassSelectedApps)
        assertEquals(ThemeMode.Auto, prefs.theme)
        assertFalse(prefs.notificationPrompted)
        assertTrue(prefs.customDatUrls.isEmpty())
        assertEquals(LogLevel.Auto, prefs.logLevel)
        assertTrue(prefs.accessLog)
        assertFalse(prefs.dnsLog)
        assertNull(prefs.apps)
    }

    @Test
    fun testPrimitiveAndStringMutations() {
        prefs.socksAddress = "0.0.0.0"
        assertEquals("0.0.0.0", prefs.socksAddress)
        assertEquals("0.0.0.0", fakeContext.sharedPrefs.getString(Preferences.SOCKS_ADDR, null))

        prefs.socksPort = 20808
        assertEquals(20808, prefs.socksPort)
        assertEquals(20808, fakeContext.sharedPrefs.getInt(Preferences.SOCKS_PORT, 0))

        prefs.socksUsername = "user1"
        prefs.socksPassword = "secret"
        assertEquals("user1", prefs.socksUsername)
        assertEquals("secret", prefs.socksPassword)

        prefs.enable = true
        assertTrue(prefs.enable)
        assertTrue(fakeContext.sharedPrefs.getBoolean(Preferences.ENABLE, false))

        prefs.bypassLan = false
        assertFalse(prefs.bypassLan)
        assertFalse(fakeContext.sharedPrefs.getBoolean(Preferences.BYPASS_LAN, true))

        prefs.httpProxyEnabled = true
        prefs.httpPort = 8080
        assertTrue(prefs.httpProxyEnabled)
        assertEquals(8080, prefs.httpPort)

        prefs.selectedConfigPath = "/data/user/0/com.simplexray.re/files/config.json"
        assertEquals("/data/user/0/com.simplexray.re/files/config.json", prefs.selectedConfigPath)

        prefs.selectedConfigPath = null
        assertNull(prefs.selectedConfigPath)
        assertFalse(fakeContext.sharedPrefs.contains(Preferences.SELECTED_CONFIG_PATH))
    }

    @Test
    fun testEnumPersistence() {
        prefs.tunnelMode = TunnelMode.HevSocks5Tunnel
        assertEquals(TunnelMode.HevSocks5Tunnel, prefs.tunnelMode)
        assertEquals("hev_socks5_tunnel", fakeContext.sharedPrefs.getString(Preferences.TUNNEL_MODE, null))

        prefs.logLevel = LogLevel.Debug
        assertEquals(LogLevel.Debug, prefs.logLevel)
        assertEquals("debug", fakeContext.sharedPrefs.getString(Preferences.LOG_LEVEL, null))

        prefs.theme = ThemeMode.Dark
        assertEquals(ThemeMode.Dark, prefs.theme)
        assertEquals("Dark", fakeContext.sharedPrefs.getString(Preferences.THEME, null))
    }

    @Test
    fun testJsonSerializedCollections() {
        // Apps set
        val targetApps = setOf("com.google.android.youtube", "org.telegram.messenger")
        prefs.apps = targetApps
        assertEquals(targetApps, prefs.apps)

        // Config files order
        val targetOrder = listOf("hk.json", "us.yaml", "jp.json")
        prefs.configFilesOrder = targetOrder
        assertEquals(targetOrder, prefs.configFilesOrder)

        // Custom dat URLs map
        val targetMap = mapOf(
            "custom_anti_ad.dat" to "https://rules.example.com/anti-ad.dat",
            "custom_direct.dat" to "https://rules.example.com/direct.dat"
        )
        prefs.customDatUrls = targetMap
        assertEquals(targetMap, prefs.customDatUrls)
    }

    @Test
    fun testTypeFallbackSafety() {
        // Test string-to-boolean fallback in safeGetBoolean
        fakeContext.sharedPrefs.map[Preferences.ENABLE] = "true"
        assertTrue(prefs.enable)

        fakeContext.sharedPrefs.map[Preferences.ENABLE] = "false"
        assertFalse(prefs.enable)

        // Test string-to-int fallback in safeGetInt
        fakeContext.sharedPrefs.map[Preferences.SOCKS_PORT] = "12345"
        assertEquals(12345, prefs.socksPort)

        // Test string-to-long fallback in safeGetLong
        fakeContext.sharedPrefs.map[Preferences.LAST_GEO_UPDATE_TIME] = "9876543210"
        assertEquals(9876543210L, prefs.lastGeoUpdateTime)
    }
}
