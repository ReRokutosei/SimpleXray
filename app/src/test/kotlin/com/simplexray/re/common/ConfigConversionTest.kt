package com.simplexray.re.common

import com.simplexray.re.prefs.FakeContext
import com.simplexray.re.prefs.LogLevel
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.prefs.TunnelMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfigConversionTest {

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
    fun testExtractOutboundEndpointsForVariousProtocols() {
        val config = """
        {
          "outbounds": [
            {
              "tag": "vless-node",
              "protocol": "vless",
              "settings": {
                "vnext": [{ "address": "vless.server.com", "port": 443 }]
              }
            },
            {
              "tag": "vmess-node",
              "protocol": "vmess",
              "settings": {
                "vnext": [{ "address": "vmess.server.com", "port": 10086 }]
              }
            },
            {
              "tag": "trojan-node",
              "protocol": "trojan",
              "settings": {
                "servers": [{ "address": "trojan.server.com", "port": 8443 }]
              }
            },
            {
              "tag": "ss-node",
              "protocol": "shadowsocks",
              "settings": {
                "servers": [{ "address": "ss.server.com", "port": 8388 }]
              }
            },
            {
              "tag": "socks-node",
              "protocol": "socks",
              "settings": {
                "servers": [{ "address": "socks.server.com", "port": 1080 }]
              }
            },
            {
              "tag": "http-node",
              "protocol": "http",
              "settings": {
                "servers": [{ "address": "http.server.com", "port": 8080 }]
              }
            }
          ]
        }
        """.trimIndent()

        val endpoints = ConfigUtils.extractOutboundEndpoints(config)
        assertEquals(6, endpoints.size)

        assertEquals("vless-node", endpoints[0].tag)
        assertEquals("vless", endpoints[0].protocol)
        assertEquals("vless.server.com", endpoints[0].host)
        assertEquals(443, endpoints[0].port)

        assertEquals("vmess-node", endpoints[1].tag)
        assertEquals("vmess", endpoints[1].protocol)
        assertEquals("vmess.server.com", endpoints[1].host)
        assertEquals(10086, endpoints[1].port)

        assertEquals("trojan-node", endpoints[2].tag)
        assertEquals("trojan", endpoints[2].protocol)
        assertEquals("trojan.server.com", endpoints[2].host)
        assertEquals(8443, endpoints[2].port)

        assertEquals("ss-node", endpoints[3].tag)
        assertEquals("shadowsocks", endpoints[3].protocol)
        assertEquals("ss.server.com", endpoints[3].host)
        assertEquals(8388, endpoints[3].port)

        assertEquals("socks-node", endpoints[4].tag)
        assertEquals("socks", endpoints[4].protocol)
        assertEquals("socks.server.com", endpoints[4].host)
        assertEquals(1080, endpoints[4].port)

        assertEquals("http-node", endpoints[5].tag)
        assertEquals("http", endpoints[5].protocol)
        assertEquals("http.server.com", endpoints[5].host)
        assertEquals(8080, endpoints[5].port)
    }

    @Test
    fun testExtractOutboundEndpointsExcludesUnprobeableProtocols() {
        val config = """
        {
          "outbounds": [
            {
              "tag": "wg-out",
              "protocol": "wireguard",
              "settings": {
                "servers": [{ "address": "wg.server.com", "port": 51820 }]
              }
            },
            {
              "tag": "hy2-out",
              "protocol": "hysteria2",
              "settings": {
                "servers": [{ "address": "hy2.server.com", "port": 443 }]
              }
            },
            {
              "tag": "quic-out",
              "protocol": "vless",
              "settings": {
                "vnext": [{ "address": "quic.server.com", "port": 443 }]
              },
              "streamSettings": {
                "network": "quic"
              }
            },
            {
              "tag": "direct-out",
              "protocol": "freedom"
            },
            {
              "tag": "block-out",
              "protocol": "blackhole"
            },
            {
              "tag": "dns-out",
              "protocol": "dns"
            },
            {
              "tag": "tcp-valid",
              "protocol": "vless",
              "settings": {
                "vnext": [{ "address": "tcp.server.com", "port": 443 }]
              },
              "streamSettings": {
                "network": "tcp"
              }
            }
          ]
        }
        """.trimIndent()

        val endpoints = ConfigUtils.extractOutboundEndpoints(config)
        assertEquals(1, endpoints.size)
        assertEquals("tcp-valid", endpoints[0].tag)
    }

    @Test
    fun testSsrfProtectionRejectsPrivateAndLoopbackHosts() {
        val config = """
        {
          "outbounds": [
            {
              "tag": "loopback-v4",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "127.0.0.1", "port": 443 }] }
            },
            {
              "tag": "private-10",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "10.1.2.3", "port": 443 }] }
            },
            {
              "tag": "private-172",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "172.20.0.1", "port": 443 }] }
            },
            {
              "tag": "private-192",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "192.168.1.1", "port": 443 }] }
            },
            {
              "tag": "link-local",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "169.254.1.1", "port": 443 }] }
            },
            {
              "tag": "loopback-v6",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "::1", "port": 443 }] }
            },
            {
              "tag": "ula-v6",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "fc00::1", "port": 443 }] }
            },
            {
              "tag": "octal-short",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "127.1", "port": 443 }] }
            },
            {
              "tag": "public-ip",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "1.1.1.1", "port": 443 }] }
            },
            {
              "tag": "domain-name",
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "node.cloudflare.com", "port": 443 }] }
            }
          ]
        }
        """.trimIndent()

        val endpoints = ConfigUtils.extractOutboundEndpoints(config)
        assertEquals(2, endpoints.size)
        assertEquals("public-ip", endpoints[0].tag)
        assertEquals("domain-name", endpoints[1].tag)
    }

    @Test
    fun testYamlToJsonAstConversion() {
        val yamlContent = """
        log:
          loglevel: debug
        inbounds:
          - tag: socks-in
            port: 10808
            protocol: socks
            listen: 127.0.0.1
        outbounds:
          - tag: proxy-yaml
            protocol: vless
            settings:
              vnext:
                - address: yaml.server.net
                  port: 443
        """.trimIndent()

        assertTrue(ConfigUtils.isValidConfigContent(yamlContent))

        val formattedJson = ConfigUtils.formatConfigContent(yamlContent)
        val json = JSONObject(formattedJson)

        assertEquals("debug", json.getJSONObject("log").getString("loglevel"))
        val inbounds = json.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        assertEquals("socks", inbounds.getJSONObject(0).getString("protocol"))

        val outbounds = ConfigUtils.extractOutbounds(yamlContent)
        assertEquals(1, outbounds.size)
        assertEquals("proxy-yaml", outbounds[0].tag)
        assertEquals("vless", outbounds[0].protocol)

        val endpoints = ConfigUtils.extractOutboundEndpoints(yamlContent)
        assertEquals(1, endpoints.size)
        assertEquals("yaml.server.net", endpoints[0].host)
        assertEquals(443, endpoints[0].port)
    }

    @Test
    fun testInjectStatsServiceWithHttpProxy() {
        prefs.apiAddress = "127.0.0.1"
        prefs.apiPort = 10085
        prefs.httpProxyEnabled = true
        prefs.httpPort = 10809

        val baseConfig = """
        {
          "inbounds": [
            { "tag": "socks-in", "port": 10808, "protocol": "socks" }
          ],
          "outbounds": [
            { "tag": "proxy", "protocol": "vless" }
          ]
        }
        """.trimIndent()

        val injected = ConfigUtils.injectStatsService(prefs, baseConfig)
        val json = JSONObject(injected)

        // Verify API object
        val api = json.getJSONObject("api")
        assertEquals("api", api.getString("tag"))
        assertEquals("127.0.0.1:10085", api.getString("listen"))
        val services = api.getJSONArray("services")
        assertEquals(1, services.length())
        assertEquals("StatsService", services.getString(0))

        // Verify stats block
        assertTrue(json.has("stats"))

        // Verify policy.system stats flags
        val systemPolicy = json.getJSONObject("policy").getJSONObject("system")
        assertTrue(systemPolicy.getBoolean("statsOutboundUplink"))
        assertTrue(systemPolicy.getBoolean("statsOutboundDownlink"))

        // Verify HTTP inbound injection
        val inbounds = json.getJSONArray("inbounds")
        var foundHttp = false
        for (i in 0 until inbounds.length()) {
            val inb = inbounds.getJSONObject(i)
            if (inb.getString("protocol") == "http") {
                foundHttp = true
                assertEquals("http-inbound", inb.getString("tag"))
                assertEquals(10809, inb.getInt("port"))
                assertEquals("127.0.0.1", inb.getString("listen"))
            }
        }
        assertTrue(foundHttp)
    }

    @Test
    fun testSanitizeConfigInboundTunAndAuth() {
        prefs.tunnelMode = TunnelMode.XrayTun
        prefs.disableVpn = false
        prefs.socksUsername = "testuser"
        prefs.socksPassword = "testpassword"

        val rawConfig = """
        {
          "inbounds": [
            {
              "tag": "tun-in",
              "protocol": "tun",
              "settings": {
                "name": "tun0",
                "autoSystemRoutingTable": true,
                "autoOutboundsInterface": true
              }
            }
          ]
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(rawConfig, prefs)
        val json = JSONObject(sanitized)
        val inbounds = json.getJSONArray("inbounds")

        // Should retain tun inbound but remove auto-routing table & autoOutboundsInterface
        var tunInbound: JSONObject? = null
        var socksInbound: JSONObject? = null
        for (i in 0 until inbounds.length()) {
            val inb = inbounds.getJSONObject(i)
            when (inb.getString("protocol")) {
                "tun" -> tunInbound = inb
                "socks" -> socksInbound = inb
            }
        }

        assertNotNull(tunInbound)
        val tunSettings = tunInbound!!.getJSONObject("settings")
        assertEquals("tun0", tunSettings.getString("name"))
        assertFalse(tunSettings.has("autoSystemRoutingTable"))
        assertFalse(tunSettings.has("autoOutboundsInterface"))

        // SOCKS inbound should be injected with authentication
        assertNotNull(socksInbound)
        val socksSettings = socksInbound!!.getJSONObject("settings")
        assertEquals("password", socksSettings.getString("auth"))
        val accounts = socksSettings.getJSONArray("accounts")
        assertEquals(1, accounts.length())
        assertEquals("testuser", accounts.getJSONObject(0).getString("user"))
        assertEquals("testpassword", accounts.getJSONObject(0).getString("pass"))
    }

    @Test
    fun testSanitizeConfigRoutingAndDnsRules() {
        val rawConfig = """
        {
          "routing": {
            "domainMatcher": "mph",
            "rules": [
              {
                "process": ["chrome.exe", "v2ray.exe", "my_app"],
                "outboundTag": "direct"
              },
              {
                "process": ["notepad.exe"],
                "outboundTag": "block"
              },
              {
                "outboundTag": "empty-rule"
              },
              {
                "domain": ["geosite:google"],
                "outboundTag": "proxy"
              }
            ]
          },
          "dns": {
            "servers": [
              "https://dns.alidns.com/dns-query"
            ]
          },
          "outbounds": [
            {
              "tag": "proxy",
              "protocol": "vless",
              "streamSettings": {
                "tlsSettings": {
                  "echConfigList": "https://example.com/ech"
                }
              }
            }
          ]
        }
        """.trimIndent()

        val sanitized = ConfigUtils.sanitizeConfig(rawConfig, null)
        val json = JSONObject(sanitized)

        // domainMatcher upgraded to hybrid
        val routing = json.getJSONObject("routing")
        assertEquals("hybrid", routing.getString("domainMatcher"))

        // Rules verification
        val rules = routing.getJSONArray("rules")
        // Rule 1: .exe removed, "my_app" remains
        val rule1 = rules.getJSONObject(0)
        val processArr = rule1.getJSONArray("process")
        assertEquals(1, processArr.length())
        assertEquals("my_app", processArr.getString(0))

        // Rule 2 was pruned because all processes were .exe
        // Rule 3 was pruned because it had no match criteria
        // Rule 4 remains
        assertEquals(2, rules.length())
        assertEquals("proxy", rules.getJSONObject(1).getString("outboundTag"))

        // DNS verification: alidns.com static IP injection
        val dns = json.getJSONObject("dns")
        val hosts = dns.getJSONObject("hosts")
        assertTrue(hosts.has("dns.alidns.com"))
        val ips = hosts.getJSONArray("dns.alidns.com")
        assertEquals("223.5.5.5", ips.getString(0))
        assertEquals("223.6.6.6", ips.getString(1))

        // TLS verification: invalid echConfigList with http/https URL pruned
        val obTls = json.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings").getJSONObject("tlsSettings")
        assertFalse(obTls.has("echConfigList"))
    }

    @Test
    fun testExtractTunMtu() {
        val configWithMtu = """
        {
          "inbounds": [
            {
              "protocol": "tun",
              "settings": { "mtu": 1420 }
            }
          ]
        }
        """.trimIndent()
        assertEquals(1420, ConfigUtils.extractTunMtu(configWithMtu))

        val configWithoutMtu = """
        {
          "inbounds": [
            { "protocol": "socks", "port": 10808 }
          ]
        }
        """.trimIndent()
        assertNull(ConfigUtils.extractTunMtu(configWithoutMtu))
    }

    @Test
    fun testExtractPortsFromJson() {
        val config = """
        {
          "inbounds": [
            { "port": 10808 },
            { "port": 10809 }
          ],
          "outbounds": [
            {
              "settings": {
                "vnext": [{ "port": 443 }]
              }
            }
          ],
          "api": { "port": 10085 },
          "invalid": { "port": 999999 }
        }
        """.trimIndent()

        val ports = ConfigUtils.extractPortsFromJson(config)
        assertEquals(setOf(10808, 10809, 443, 10085), ports)
    }
}
