package com.simplexray.re.common

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigUtilsTest {

    @Test
    fun testDesktopTunInboundRemoval() {
        val rawConfig = """
        {
          "inbounds": [
            {
              "tag": "tun-in",
              "protocol": "tun",
              "settings": { "name": "utun10" }
            },
            {
              "tag": "socks-in",
              "port": 10808,
              "protocol": "socks"
            }
          ]
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(rawConfig)
        val json = JSONObject(sanitized)
        val inbounds = json.getJSONArray("inbounds")

        assertEquals(1, inbounds.length())
        val inbound = inbounds.getJSONObject(0)
        assertEquals("socks-in", inbound.getString("tag"))
    }

    @Test
    fun testGlobalListenAddressConversion() {
        val rawConfig = """
        {
          "inbounds": [
            {
              "tag": "socks-in",
              "port": 10808,
              "listen": "::",
              "protocol": "socks"
            },
            {
              "tag": "http-in",
              "port": 10809,
              "listen": "0.0.0.0",
              "protocol": "http"
            }
          ]
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(rawConfig)
        val json = JSONObject(sanitized)
        val inbounds = json.getJSONArray("inbounds")

        assertEquals("127.0.0.1", inbounds.getJSONObject(0).getString("listen"))
        assertEquals("127.0.0.1", inbounds.getJSONObject(1).getString("listen"))
    }

    @Test
    fun testLogLevelHandlingInAutoMode() {
        val rawConfig = """
        {
          "log": {
            "loglevel": "info",
            "access": "/var/log/access.log",
            "error": "/var/log/error.log"
          }
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(rawConfig, null)
        val json = JSONObject(sanitized)
        val log = json.getJSONObject("log")

        assertFalse(log.has("access"))
        assertFalse(log.has("error"))
        assertEquals("info", log.getString("loglevel"))
    }

    @Test
    fun testExtractOutboundsExcludesNonProxyProtocols() {
        val rawConfig = """
        {
          "outbounds": [
            { "tag": "proxy-1", "protocol": "vless" },
            { "tag": "direct", "protocol": "freedom" },
            { "tag": "black", "protocol": "blackhole" },
            { "tag": "dns-out", "protocol": "dns" },
            { "tag": "proxy-2", "protocol": "VMess" }
          ]
        }
        """.trimIndent()

        val outbounds = ConfigUtils.extractOutbounds(rawConfig)

        assertEquals(2, outbounds.size)
        assertEquals("proxy-1", outbounds[0].tag)
        assertEquals("vless", outbounds[0].protocol)
        assertEquals("proxy-2", outbounds[1].tag)
        assertEquals("vmess", outbounds[1].protocol)
    }

    @Test
    fun testExtractOutboundsHandlesInvalidInput() {
        assertTrue(ConfigUtils.extractOutbounds("not json").isEmpty())
        assertTrue(ConfigUtils.extractOutbounds("{}").isEmpty())
        assertTrue(ConfigUtils.extractOutbounds("").isEmpty())
    }

    @Test
    fun testExtractOutboundsSkipsOutboundWithoutTag() {
        val rawConfig = """
        {
          "outbounds": [
            { "protocol": "vless" },
            { "tag": "ok", "protocol": "trojan" }
          ]
        }
        """.trimIndent()

        val outbounds = ConfigUtils.extractOutbounds(rawConfig)

        assertEquals(1, outbounds.size)
        assertEquals("ok", outbounds[0].tag)
        assertEquals("trojan", outbounds[0].protocol)
    }

    @Test
    fun testCreateDefaultSniffingObject() {
        val sniffing = ConfigUtils.createDefaultSniffingObject()
        assertTrue(sniffing.getBoolean("enabled"))
        assertFalse(sniffing.getBoolean("metadataOnly"))
        assertFalse(sniffing.getBoolean("routeOnly"))

        val destOverride = sniffing.getJSONArray("destOverride")
        val overrides = (0 until destOverride.length()).map { destOverride.getString(it) }.toSet()
        assertTrue(overrides.contains("http"))
        assertTrue(overrides.contains("tls"))
        assertTrue(overrides.contains("quic"))
        assertTrue(overrides.contains("fakedns"))
    }

    @Test
    fun testTemplateIntegrity() {
        val templateFile = java.io.File("src/main/assets/template").takeIf { it.exists() }
            ?: java.io.File("app/src/main/assets/template")
        assertTrue("Template file should exist", templateFile.exists())
        val content = templateFile.readText()
        val json = JSONObject(content)

        val outbounds = json.getJSONArray("outbounds")
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }.toSet()
        assertTrue("Template must contain proxy tag", tags.contains("proxy"))
        assertTrue("Template must contain direct tag", tags.contains("direct"))
        assertTrue("Template must contain block tag", tags.contains("block"))

        val routing = json.getJSONObject("routing")
        val rules = routing.getJSONArray("rules")
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.has("outboundTag")) {
                val tag = rule.getString("outboundTag")
                assertTrue("Outbound tag '$tag' used in rule $i must exist in outbounds $tags", tags.contains(tag))
            }
        }
    }

    @Test
    fun testRulePruningRetainsValidFields() {
        val rawConfig = """
        {
          "routing": {
            "rules": [
              {
                "sourceIP": ["192.168.1.100"],
                "outboundTag": "direct"
              },
              {
                "sourcePort": "1000-2000",
                "outboundTag": "direct"
              },
              {
                "localIP": ["10.0.0.1"],
                "outboundTag": "direct"
              }
            ]
          }
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(rawConfig, null)
        val json = JSONObject(sanitized)
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        assertEquals(3, rules.length())
    }

    @Test
    fun testRuleMigrationOfLegacyGeositeAndGeoip() {
        val rawConfig = """
        {
          "routing": {
            "rules": [
              {
                "geosite": ["google", "geosite:cn"],
                "outboundTag": "proxy"
              },
              {
                "geoip": ["cn"],
                "outboundTag": "direct"
              }
            ]
          }
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(rawConfig, null)
        val json = JSONObject(sanitized)
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        assertEquals(2, rules.length())

        val rule0 = rules.getJSONObject(0)
        assertFalse(rule0.has("geosite"))
        assertTrue(rule0.has("domain"))
        val domainArr = rule0.getJSONArray("domain")
        assertEquals(2, domainArr.length())
        assertEquals("geosite:google", domainArr.getString(0))
        assertEquals("geosite:cn", domainArr.getString(1))

        val rule1 = rules.getJSONObject(1)
        assertFalse(rule1.has("geoip"))
        assertTrue(rule1.has("ip"))
        val ipArr = rule1.getJSONArray("ip")
        assertEquals(1, ipArr.length())
        assertEquals("geoip:cn", ipArr.getString(0))
    }

    @Test
    fun testRulePruningRemovesEmptyOrInvalidRules() {
        val rawConfig = """
        {
          "routing": {
            "rules": [
              {
                "outboundTag": "direct"
              },
              {
                "unknownField": ["something"],
                "outboundTag": "direct"
              },
              {
                "geosite": [],
                "outboundTag": "direct"
              }
            ]
          }
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(rawConfig, null)
        val json = JSONObject(sanitized)
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        assertEquals(0, rules.length())
    }
}
