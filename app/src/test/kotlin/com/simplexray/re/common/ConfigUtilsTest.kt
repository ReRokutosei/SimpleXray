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
}
