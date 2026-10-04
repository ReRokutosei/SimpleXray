package com.simplexray.re.common.config

import android.util.Log
import com.simplexray.re.prefs.LogLevel
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.prefs.TunnelMode
import org.json.JSONArray
import org.json.JSONObject

internal object ConfigSanitizer {
    private const val TAG = "ConfigSanitizer"

    private val EFFECTIVE_MATCH_KEYS = setOf(
        "domain", "domains", "ip", "port", "network", "sourceip", "source", "sourceport",
        "user", "vlessroute", "inboundtag", "protocol", "attrs", "localip", "localport",
        "process", "localos", "webhook"
    )

    private val STREAM_TCP_OUTBOUND_PROTOCOLS = setOf(
        "vless", "vmess", "trojan", "shadowsocks", "socks", "http"
    )

    private const val OUTBOUND_TCP_KEEPALIVE_IDLE_SECONDS = 15
    private const val OUTBOUND_TCP_KEEPALIVE_INTERVAL_SECONDS = 3
    private const val OUTBOUND_TCP_USER_TIMEOUT_MS = 15000
    private const val OUTBOUND_TCP_KEEPALIVE_COUNT = 3

    fun sanitize(content: String, prefs: Preferences? = null): String {
        val rootJson = ConfigCodec.parse(content) ?: return content

        // 1. Process and sanitize log block
        val logObj = rootJson.optJSONObject("log") ?: JSONObject().also { rootJson.put("log", it) }
        logObj.remove("error")

        // Remove geodata block: xray's built-in geodata cron updater is not safe on Android
        // (bypasses GUI sandbox validation). GUI manages rule file updates independently.
        if (rootJson.has("geodata")) {
            rootJson.remove("geodata")
            Log.d(TAG, "Removed geodata block: GUI manages geo rule updates with sandbox validation.")
        }

        if (prefs != null) {
            if (!prefs.accessLog) {
                logObj.put("access", "none")
                Log.d(TAG, "Override access to none from Preferences.")
            } else {
                logObj.remove("access")
            }

            logObj.put("dnsLog", prefs.dnsLog)
            Log.d(TAG, "Override dnsLog to ${prefs.dnsLog} from Preferences.")

            if (prefs.logLevel != LogLevel.Auto) {
                logObj.put("loglevel", prefs.logLevel.value)
                Log.d(TAG, "Override loglevel to ${prefs.logLevel.value} from Preferences.")
            } else {
                if (!logObj.has("loglevel") || logObj.optString("loglevel").isEmpty()) {
                    logObj.put("loglevel", "warning")
                }
            }
        } else {
            logObj.remove("access")
            if (!logObj.has("loglevel") || logObj.optString("loglevel").isEmpty()) {
                logObj.put("loglevel", "warning")
            }
        }

        // 2. Process and sanitize inbounds (remove desktop tun & sync/inject SOCKS inbound)
        var inbounds = rootJson.optJSONArray("inbounds")
        if (inbounds == null) {
            inbounds = JSONArray()
            rootJson.put("inbounds", inbounds)
        }

        var hasSocksInbound = false
        var hasTunInbound = false
        val targetPort = prefs?.socksPort ?: 10808
        val targetListen = prefs?.socksAddress.takeIf { !it.isNullOrEmpty() } ?: "127.0.0.1"

        var primarySocksInbound: JSONObject? = null
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            if (inbound.optString("protocol").lowercase() == "socks") {
                if (inbound.optString("tag") == "socks-in" || primarySocksInbound == null) {
                    primarySocksInbound = inbound
                    if (inbound.optString("tag") == "socks-in") break
                }
            }
        }

        for (i in inbounds.length() - 1 downTo 0) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            val protocol = inbound.optString("protocol").lowercase()
            if (protocol == "tun") {
                if (prefs?.tunnelMode == TunnelMode.XrayTun && prefs.disableVpn == false) {
                    hasTunInbound = true
                    val settings = inbound.optJSONObject("settings") ?: JSONObject().also { inbound.put("settings", it) }
                    // Android receives an already-established VPN fd. Xray's
                    // config builder otherwise tries to enumerate interfaces
                    // to generate a desktop TUN name, which can be denied by
                    // the Android sandbox before the fd-backed implementation
                    // is reached.
                    if (!settings.has("name") || settings.optString("name").isBlank()) {
                        settings.put("name", "tun-inbound")
                    }
                    settings.remove("autoSystemRoutingTable")
                    settings.remove("autoOutboundsInterface")
                    val sniffing = inbound.optJSONObject("sniffing")
                    if (sniffing == null) {
                        inbound.put("sniffing", createDefaultSniffingObject())
                        Log.d(TAG, "Injected missing sniffing block into existing tun inbound.")
                    } else {
                        if (!sniffing.has("enabled")) sniffing.put("enabled", true)
                        val destOverride = sniffing.optJSONArray("destOverride") ?: JSONArray().also { sniffing.put("destOverride", it) }
                        val overrides = (0 until destOverride.length()).map { destOverride.optString(it).lowercase() }.toSet()
                        if (!overrides.contains("fakedns")) {
                            destOverride.put("fakedns")
                        }
                    }
                    Log.d(TAG, "Sanitized existing tun inbound for Android VpnService (removed auto-routing, ensured sniffing).")
                } else {
                    inbounds.remove(i)
                    Log.d(TAG, "Removed desktop-only tun inbound at index $i to prevent Android permission denied.")
                }
                continue
            }
            if (protocol == "socks") {
                hasSocksInbound = true
                if (inbound === primarySocksInbound) {
                    inbound.put("port", targetPort)
                    inbound.put("listen", targetListen)
                    if (prefs != null && prefs.socksUsername.isNotEmpty() && prefs.socksPassword.isNotEmpty()) {
                        val settings = inbound.optJSONObject("settings") ?: JSONObject().also { inbound.put("settings", it) }
                        val accounts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("user", prefs.socksUsername)
                                put("pass", prefs.socksPassword)
                            })
                        }
                        settings.put("auth", "password")
                        settings.put("accounts", accounts)
                    }
                    Log.d(TAG, "Synchronized primary SOCKS inbound port to $targetPort and listen to $targetListen.")
                } else {
                    val listen = inbound.optString("listen")
                    if (listen == "::" || listen == "0.0.0.0") {
                        inbound.put("listen", "127.0.0.1")
                        Log.d(TAG, "Converted secondary SOCKS bind address from $listen to 127.0.0.1.")
                    }
                }
            } else {
                val listen = inbound.optString("listen")
                if (listen == "::" || listen == "0.0.0.0") {
                    inbound.put("listen", "127.0.0.1")
                    Log.d(TAG, "Converted bind address from $listen to 127.0.0.1 for inbound at index $i.")
                }
            }
        }

        if (prefs?.tunnelMode == TunnelMode.XrayTun && prefs.disableVpn == false && !hasTunInbound) {
            val newTunInbound = JSONObject().apply {
                put("protocol", "tun")
                put("tag", "tun-inbound")
                put("settings", JSONObject().apply {
                    put("name", "tun-inbound")
                    put("network", "tcp,udp")
                })
                put("sniffing", createDefaultSniffingObject())
            }
            inbounds.put(newTunInbound)
            Log.d(TAG, "Injected default Android-compatible tun inbound with sniffing.")
        }

        if (!hasSocksInbound && prefs != null) {
            val newSocksInbound = JSONObject().apply {
                put("protocol", "socks")
                put("listen", targetListen)
                put("port", targetPort)
                put("tag", "socks-in")
                put("settings", JSONObject().apply {
                    put("udp", true)
                    if (prefs.socksUsername.isNotEmpty() && prefs.socksPassword.isNotEmpty()) {
                        put("auth", "password")
                        put("accounts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("user", prefs.socksUsername)
                                put("pass", prefs.socksPassword)
                            })
                        })
                    } else {
                        put("auth", "noauth")
                    }
                })
            }
            inbounds.put(newSocksInbound)
            Log.d(TAG, "Injected default SOCKS inbound at port $targetPort.")
        }

        // 2. Process routing & domainMatcher
        val routing = rootJson.optJSONObject("routing")
        if (routing != null) {
            val domainMatcher = routing.optString("domainMatcher")
            if (domainMatcher.equals("mph", ignoreCase = true)) {
                routing.put("domainMatcher", "hybrid")
                Log.d(TAG, "Upgraded domainMatcher from mph to hybrid.")
            }

            val rules = routing.optJSONArray("rules")
            if (rules != null) {
                for (i in rules.length() - 1 downTo 0) {
                    val rule = rules.optJSONObject(i) ?: continue

                    // Auto-migrate legacy top-level "geosite" into "domain"
                    val geositeArr = rule.optJSONArray("geosite")
                    if (geositeArr != null && geositeArr.length() > 0) {
                        val domainArr = rule.optJSONArray("domain") ?: JSONArray().also { rule.put("domain", it) }
                        for (idx in 0 until geositeArr.length()) {
                            val entry = geositeArr.optString(idx)
                            if (entry.isNotEmpty()) {
                                val formatted = if (entry.startsWith("geosite:", ignoreCase = true)) entry else "geosite:$entry"
                                domainArr.put(formatted)
                            }
                        }
                    }
                    rule.remove("geosite")

                    // Auto-migrate legacy top-level "geoip" into "ip"
                    val geoipArr = rule.optJSONArray("geoip")
                    if (geoipArr != null && geoipArr.length() > 0) {
                        val ipArr = rule.optJSONArray("ip") ?: JSONArray().also { rule.put("ip", it) }
                        for (idx in 0 until geoipArr.length()) {
                            val entry = geoipArr.optString(idx)
                            if (entry.isNotEmpty()) {
                                val formatted = if (entry.startsWith("geoip:", ignoreCase = true)) entry else "geoip:$entry"
                                ipArr.put(formatted)
                            }
                        }
                    }
                    rule.remove("geoip")

                    // Process process array
                    val processArr = rule.optJSONArray("process")
                    if (processArr != null) {
                        val cleanedProcess = JSONArray()
                        for (j in 0 until processArr.length()) {
                            val proc = processArr.optString(j)
                            if (proc.isNotEmpty() && !proc.endsWith(".exe", ignoreCase = true)) {
                                cleanedProcess.put(proc)
                            }
                        }
                        if (cleanedProcess.length() > 0) {
                            rule.put("process", cleanedProcess)
                        } else {
                            rule.remove("process")
                        }
                    }

                    // Check for effective match criteria
                    var hasEffectiveField = false
                    val keys = rule.keys()
                    while (keys.hasNext()) {
                        val rawKey = keys.next()
                        val k = rawKey.lowercase()
                        if (EFFECTIVE_MATCH_KEYS.contains(k)) {
                            val v = rule.opt(rawKey)
                            if (v is JSONArray && v.length() > 0) {
                                hasEffectiveField = true
                                break
                            } else if (v is String && v.isNotEmpty()) {
                                hasEffectiveField = true
                                break
                            } else if (v != null && v !is JSONArray && v !is String && v != JSONObject.NULL) {
                                hasEffectiveField = true
                                break
                            }
                        }
                    }

                    if (!hasEffectiveField) {
                        rules.remove(i)
                        Log.d(TAG, "Pruned empty/invalid rule block at index $i.")
                    }
                }

                if (prefs != null) {
                    if (prefs.bypassLan) {
                        val hasPrivateIpRule = (0 until rules.length()).any { idx ->
                            val r = rules.optJSONObject(idx) ?: return@any false
                            val ipArr = r.optJSONArray("ip") ?: return@any false
                            (0 until ipArr.length()).any { ipArr.optString(it).equals("geoip:private", ignoreCase = true) }
                        }
                        if (!hasPrivateIpRule) {
                            val privateRule = JSONObject().apply {
                                put("ip", JSONArray().apply { put("geoip:private") })
                                put("outboundTag", "direct")
                            }
                            val newRules = JSONArray().apply {
                                put(privateRule)
                                for (idx in 0 until rules.length()) {
                                    put(rules.get(idx))
                                }
                            }
                            routing.put("rules", newRules)
                            Log.d(TAG, "Injected top-priority geoip:private -> direct rule for bypassLan.")
                        }
                    } else {
                        for (i in rules.length() - 1 downTo 0) {
                            val rule = rules.optJSONObject(i) ?: continue
                            if (rule.optString("outboundTag").equals("direct", ignoreCase = true)) {
                                val ipArr = rule.optJSONArray("ip") ?: continue
                                val newIpArr = JSONArray()
                                for (j in 0 until ipArr.length()) {
                                    val item = ipArr.optString(j)
                                    if (!item.equals("geoip:private", ignoreCase = true)) {
                                        newIpArr.put(item)
                                    }
                                }
                                if (newIpArr.length() > 0) {
                                    rule.put("ip", newIpArr)
                                } else {
                                    rule.remove("ip")
                                }
                            }
                        }
                        Log.d(TAG, "Removed geoip:private direct routing because bypassLan is disabled.")
                    }
                }
            }
        }

        // 3. DoH hosts auto-injection
        val dns = rootJson.optJSONObject("dns")
        if (dns != null) {
            val servers = dns.optJSONArray("servers")
            val hosts = dns.optJSONObject("hosts") ?: JSONObject().also { dns.put("hosts", it) }

            if (servers != null) {
                for (i in 0 until servers.length()) {
                    val s = servers.opt(i)
                    var addressStr = ""
                    if (s is JSONObject) {
                        addressStr = s.optString("address")
                    } else if (s is String) {
                        addressStr = s
                    }

                    if (addressStr.startsWith("https://", ignoreCase = true) && addressStr.contains("alidns.com", ignoreCase = true)) {
                        val domainRegex = Regex("(?i)https://([a-z0-9\\.-]+\\.alidns\\.com)")
                        val match = domainRegex.find(addressStr)
                        if (match != null) {
                            val domain = match.groupValues[1]
                            if (!hosts.has(domain)) {
                                val ips = JSONArray().apply {
                                    put("223.5.5.5")
                                    put("223.6.6.6")
                                }
                                hosts.put(domain, ips)
                                Log.d(TAG, "Auto-injected static hosts for dedicated DoH domain: $domain")
                            }
                        }
                    }
                }
            }
        }

        // 4. Prune invalid echConfigList with HTTP URL in outbounds
        val outbounds = rootJson.optJSONArray("outbounds")
        if (outbounds != null) {
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val streamSettings = ob.optJSONObject("streamSettings") ?: continue
                val tlsSettings = streamSettings.optJSONObject("tlsSettings")
                if (tlsSettings != null) {
                    val ech = tlsSettings.optString("echConfigList")
                    if (ech.startsWith("http://", ignoreCase = true) || ech.startsWith("https://", ignoreCase = true)) {
                        tlsSettings.remove("echConfigList")
                        Log.d(TAG, "Pruned invalid echConfigList HTTP URL in outbound tlsSettings.")
                    }
                }
            }
        }

        // 5. Keep outbound transports responsive after Android network switches.
        injectOutboundSocketTimeouts(rootJson)

        // 6. Observatory probeTimeout safety injection
        val observatory = rootJson.optJSONObject("observatory")
        if (observatory != null) {
            if (!observatory.has("probeTimeout")) {
                observatory.put("probeTimeout", "2s")
                Log.d(TAG, "Injected default probeTimeout: 2s into observatory.")
            }
        }

        return rootJson.toString(2)
    }

    private fun injectOutboundSocketTimeouts(rootJson: JSONObject) {
        val outbounds = rootJson.optJSONArray("outbounds") ?: return
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val protocol = outbound.optString("protocol").lowercase()
            if (protocol !in STREAM_TCP_OUTBOUND_PROTOCOLS) continue

            val streamSettings = outbound.optJSONObject("streamSettings")
                ?: JSONObject().also { outbound.put("streamSettings", it) }
            val sockopt = streamSettings.optJSONObject("sockopt")
                ?: JSONObject().also { streamSettings.put("sockopt", it) }

            if (!sockopt.has("tcpKeepAliveIdle")) {
                sockopt.put("tcpKeepAliveIdle", OUTBOUND_TCP_KEEPALIVE_IDLE_SECONDS)
            }
            if (!sockopt.has("tcpKeepAliveInterval")) {
                sockopt.put("tcpKeepAliveInterval", OUTBOUND_TCP_KEEPALIVE_INTERVAL_SECONDS)
            }
            if (!sockopt.has("tcpUserTimeout")) {
                sockopt.put("tcpUserTimeout", OUTBOUND_TCP_USER_TIMEOUT_MS)
            }
            ensureTcpKeepAliveCount(sockopt)
            Log.d(TAG, "Injected outbound transport timeouts for protocol '$protocol'.")
        }
    }

    private fun ensureTcpKeepAliveCount(sockopt: JSONObject) {
        val customSockopt = sockopt.optJSONArray("customSockopt")
        if (customSockopt != null) {
            for (i in 0 until customSockopt.length()) {
                val entry = customSockopt.optJSONObject(i) ?: continue
                val level = entry.optString("level", "6").ifBlank { "6" }
                if (entry.optString("opt") == "6" && level == "6") {
                    Log.d(TAG, "User-supplied TCP_KEEPCNT found; keeping it.")
                    return
                }
            }
        }
        val array = customSockopt ?: JSONArray().also { sockopt.put("customSockopt", it) }
        array.put(JSONObject().apply {
            put("type", "int")
            put("network", "tcp")
            put("level", "6")
            put("opt", "6")
            put("value", OUTBOUND_TCP_KEEPALIVE_COUNT.toString())
        })
    }

    internal fun createDefaultSniffingObject(): JSONObject = JSONObject().apply {
        put("enabled", true)
        put("destOverride", JSONArray().apply {
            put("http")
            put("tls")
            put("quic")
            put("fakedns")
        })
        put("metadataOnly", false)
        put("routeOnly", false)
    }
}
