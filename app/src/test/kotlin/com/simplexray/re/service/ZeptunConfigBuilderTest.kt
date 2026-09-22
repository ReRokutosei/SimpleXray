package com.simplexray.re.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeptunConfigBuilderTest {
    @Test
    fun buildsAndroidUserspaceSocksConfig() {
        val root = JSONObject(
            ZeptunConfigBuilder.build(
                host = "127.0.0.1",
                port = 10808,
                username = "user",
                password = "pa\"ss",
                mtu = 9000,
                fd = 42,
                udpInTcp = false
            )
        )

        assertEquals("mobile", root.getString("preset"))
        assertEquals("userspace", root.getJSONObject("stack").getString("mode"))
        assertFalse(root.getJSONObject("route").getBoolean("auto_route"))

        val tun = root.getJSONObject("tun")
        assertEquals(42, tun.getInt("fd"))
        assertEquals(9000, tun.getInt("mtu"))
        assertFalse(tun.getBoolean("configure"))

        val socks = root.getJSONObject("handler").getJSONObject("socks5")
        assertEquals("socks5", root.getJSONObject("handler").getString("kind"))
        assertEquals("127.0.0.1:10808", socks.getString("server"))
        assertEquals("user", socks.getString("username"))
        assertEquals("pa\"ss", socks.getString("password"))
        assertTrue(socks.getBoolean("udp"))
        assertEquals("udp", socks.getString("udp_mode"))
    }

    @Test
    fun formatsIpv6EndpointAndUdpOverTcp() {
        val root = JSONObject(
            ZeptunConfigBuilder.build(
                host = "2001:db8::1",
                port = 1080,
                username = "",
                password = "",
                mtu = 1500,
                fd = 7,
                udpInTcp = true
            )
        )

        val socks = root.getJSONObject("handler").getJSONObject("socks5")
        assertEquals("[2001:db8::1]:1080", socks.getString("server"))
        assertEquals("tcp", socks.getString("udp_mode"))
    }

    @Test
    fun preservesAlreadyBracketedIpv6Endpoint() {
        val root = JSONObject(
            ZeptunConfigBuilder.build(
                host = "[2001:db8::2]",
                port = 443,
                username = "",
                password = "",
                mtu = 1500,
                fd = 8,
                udpInTcp = false
            )
        )

        assertEquals(
            "[2001:db8::2]:443",
            root.getJSONObject("handler").getJSONObject("socks5").getString("server")
        )
    }
}
