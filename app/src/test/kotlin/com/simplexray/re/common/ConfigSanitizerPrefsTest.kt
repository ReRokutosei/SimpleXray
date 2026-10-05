package com.simplexray.re.common

import com.simplexray.re.prefs.FakeContext
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.prefs.TunnelMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfigSanitizerPrefsTest {

    private lateinit var prefs: Preferences

    @Before
    fun setUp() {
        prefs = Preferences(FakeContext())
    }

    @Test
    fun bypassLanDisabledRemovesRuleWithoutRemainingMatchFields() {
        prefs.bypassLan = false
        val raw = """
        {
          "inbounds": [
            { "tag": "socks-in", "protocol": "socks", "listen": "127.0.0.1", "port": 1080 }
          ],
          "outbounds": [
            { "tag": "direct", "protocol": "freedom" },
            { "tag": "proxy", "protocol": "vless" }
          ],
          "routing": {
            "rules": [
              { "type": "field", "ip": ["geoip:private"], "outboundTag": "direct" },
              { "type": "field", "domain": ["example.com"], "ip": ["geoip:private"], "outboundTag": "direct" }
            ]
          }
        }
        """.trimIndent()

        val rules = JSONObject(ConfigUtils.sanitizeConfig(raw, prefs))
            .getJSONObject("routing")
            .getJSONArray("rules")

        assertEquals(1, rules.length())
        val remaining = rules.getJSONObject(0)
        assertEquals("direct", remaining.getString("outboundTag"))
        assertEquals("example.com", remaining.getJSONArray("domain").getString(0))
        assertFalse(remaining.has("ip"))
    }

    @Test
    fun emptyListenIsNormalizedToLoopback() {
        val raw = """
        {
          "inbounds": [
            { "tag": "http-in", "protocol": "http", "port": 8080, "settings": {} }
          ]
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(raw, prefs)
        val inbound = JSONObject(sanitized).getJSONArray("inbounds").getJSONObject(0)
        assertEquals("127.0.0.1", inbound.getString("listen"))

        val endpoint = ConfigUtils.extractHttpProxyEndpoint(raw)
        assertEquals("127.0.0.1" to 8080, endpoint)
    }

    @Test
    fun xrayTunInboundSurvivesRuntimeSanitizeAndInjection() {
        prefs.tunnelMode = TunnelMode.XrayTun
        prefs.disableVpn = false
        prefs.tunnelMtu = 9000
        val raw = """
        {
          "inbounds": [
            {
              "tag": "tun-inbound",
              "protocol": "tun",
              "settings": { "name": "custom-tun", "network": "tcp,udp", "mtu": 9000 }
            }
          ]
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(raw, prefs)
        val injected = ConfigUtils.injectStatsServiceIntoSanitized(prefs, sanitized)
        val tun = findInbound(JSONObject(injected), "tun")

        assertEquals("custom-tun", tun.getJSONObject("settings").getString("name"))
        assertEquals(9000, tun.getJSONObject("settings").getInt("mtu"))
    }

    @Test
    fun injectedTunUsesPreferenceMtuWhenConfigHasNoTun() {
        prefs.tunnelMode = TunnelMode.XrayTun
        prefs.disableVpn = false
        prefs.tunnelMtu = 1400

        val sanitized = ConfigUtils.sanitizeConfig("{}", prefs)
        val injected = ConfigUtils.injectStatsServiceIntoSanitized(prefs, sanitized)
        val tun = findInbound(JSONObject(injected), "tun")

        assertEquals(1400, tun.getJSONObject("settings").getInt("mtu"))
    }

    @Test
    fun simpleTunStripsConfigSideSocksAuth() {
        prefs.tunnelMode = TunnelMode.SimpleTun
        val raw = """
        {
          "inbounds": [
            {
              "tag": "socks-in",
              "protocol": "socks",
              "listen": "127.0.0.1",
              "port": 1080,
              "settings": {
                "auth": "password",
                "accounts": [ { "user": "u", "pass": "p" } ]
              }
            }
          ]
        }
        """.trimIndent()

        val settings = JSONObject(ConfigUtils.sanitizeConfig(raw, prefs))
            .getJSONArray("inbounds")
            .getJSONObject(0)
            .getJSONObject("settings")

        assertFalse(settings.has("auth"))
        assertFalse(settings.has("accounts"))
    }

    @Test
    fun simpleTunInjectedDefaultSocksInboundIsNoAuthEvenWithAppCredentials() {
        prefs.tunnelMode = TunnelMode.SimpleTun
        prefs.socksUsername = "u"
        prefs.socksPassword = "p"

        val inbound = JSONObject(ConfigUtils.sanitizeConfig("{}", prefs))
            .getJSONArray("inbounds")
            .getJSONObject(0)
        assertEquals("socks", inbound.getString("protocol"))
        assertEquals("noauth", inbound.getJSONObject("settings").optString("auth"))
    }

    @Test
    fun apiListenFormatsIpv6WithBrackets() {
        prefs.apiAddress = "::1"
        prefs.apiPort = 12345
        val sanitized = ConfigUtils.sanitizeConfig("{}", prefs)
        val injected = JSONObject(ConfigUtils.injectStatsServiceIntoSanitized(prefs, sanitized))
        assertEquals("[::1]:12345", injected.getJSONObject("api").getString("listen"))
    }

    @Test
    fun httpEndpointPrefersTaggedInboundAndFallsBackToFirstHttp() {
        val tagged = """
        {
          "inbounds": [
            { "tag": "http-other", "protocol": "http", "listen": "0.0.0.0", "port": 1111 },
            { "tag": "http-in", "protocol": "http", "listen": "0.0.0.0", "port": 2222 }
          ]
        }
        """.trimIndent()
        assertEquals("127.0.0.1" to 2222, ConfigUtils.extractHttpProxyEndpoint(tagged))

        val firstHttp = """
        {
          "inbounds": [
            { "tag": "socks-in", "protocol": "socks", "port": 1080 },
            { "tag": "some-http", "protocol": "http", "listen": "127.0.0.1", "port": 3333 }
          ]
        }
        """.trimIndent()
        assertEquals("127.0.0.1" to 3333, ConfigUtils.extractHttpProxyEndpoint(firstHttp))

        assertNull(ConfigUtils.extractHttpProxyEndpoint("""{"inbounds":[{"protocol":"socks","port":1}]}"""))
    }

    @Test
    fun extractsPrimarySocksCredentialsOnlyForPasswordAuth() {
        val passwordAuth = """
        {
          "inbounds": [
            {
              "tag": "socks-in",
              "protocol": "socks",
              "port": 1080,
              "settings": {
                "auth": "password",
                "accounts": [ { "user": "alice", "pass": "s3cret" } ]
              }
            }
          ]
        }
        """.trimIndent()
        assertEquals("alice" to "s3cret", ConfigUtils.extractPrimarySocksCredentials(passwordAuth))

        val noAuth = """
        {
          "inbounds": [
            { "tag": "socks-in", "protocol": "socks", "port": 1080, "settings": { "auth": "noauth" } }
          ]
        }
        """.trimIndent()
        assertNull(ConfigUtils.extractPrimarySocksCredentials(noAuth))
    }

    private fun findInbound(root: JSONObject, protocol: String): JSONObject {
        val inbounds: JSONArray = root.getJSONArray("inbounds")
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.getJSONObject(i)
            if (inbound.optString("protocol").equals(protocol, ignoreCase = true)) return inbound
        }
        throw AssertionError("Inbound '$protocol' not found")
    }
}
