package com.simplexray.re.common

import android.util.Log
import com.simplexray.re.prefs.LogLevel
import com.simplexray.re.prefs.Preferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress

fun File.isConfigFile(): Boolean {
    val ext = extension.lowercase()
    return ext == "json" || ext == "yaml" || ext == "yml"
}

fun String.isConfigFile(): Boolean {
    val ext = substringAfterLast('.', "").lowercase()
    return ext == "json" || ext == "yaml" || ext == "yml"
}

object ConfigUtils {
    private const val TAG = "ConfigUtils"

    private val EFFECTIVE_MATCH_KEYS = setOf(
        "domain", "ip", "port", "network", "process", "geosite", "geoip", "inboundtag", "protocol", "user", "attrs"
    )

    private val EXCLUDED_OUTBOUND_PROTOCOLS = setOf("freedom", "blackhole", "dns")

    // UDP-only protocols that cannot be latency-tested via TCP connect.
    private val UDP_ONLY_OUTBOUND_PROTOCOLS = setOf("wireguard", "hysteria2")

    data class OutboundInfo(val tag: String, val protocol: String)

    data class OutboundEndpoint(val tag: String, val protocol: String, val host: String, val port: Int)

    fun extractTunMtu(configContent: String): Int? {
        try {
            val jsonObject = parseToJsonObject(configContent) ?: return null
            val inbounds = jsonObject.optJSONArray("inbounds") ?: return null
            for (i in 0 until inbounds.length()) {
                val inbound = inbounds.optJSONObject(i) ?: continue
                if (inbound.optString("protocol") == "tun") {
                    return inbound.optJSONObject("settings")?.optInt("MTU", -1)
                        ?.takeIf { it > 0 }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON for TUN MTU extraction", e)
        }
        return null
    }

    fun sanitizeConfig(content: String, prefs: Preferences? = null): String {
        val rootJson = parseToJsonObject(content) ?: return content

        // 1. Process and sanitize log block
        val logObj = rootJson.optJSONObject("log") ?: JSONObject().also { rootJson.put("log", it) }
        logObj.remove("access")
        logObj.remove("error")

        // Remove geodata block: xray's built-in geodata cron updater is not safe on Android
        // (bypasses GUI sandbox validation). GUI manages rule file updates independently.
        if (rootJson.has("geodata")) {
            rootJson.remove("geodata")
            Log.d(TAG, "Removed geodata block: GUI manages geo rule updates with sandbox validation.")
        }

        if (prefs != null && prefs.logLevel != LogLevel.Auto) {
            logObj.put("loglevel", prefs.logLevel.value)
            Log.d(TAG, "Override loglevel to ${prefs.logLevel.value} from Preferences.")
        } else {
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

        for (i in inbounds.length() - 1 downTo 0) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            val protocol = inbound.optString("protocol").lowercase()
            if (protocol == "tun") {
                if (prefs?.useXrayTun == true && prefs.disableVpn == false) {
                    hasTunInbound = true
                    val settings = inbound.optJSONObject("settings") ?: JSONObject().also { inbound.put("settings", it) }
                    settings.put("autoRoute", false)
                    settings.put("strictRoute", false)
                    settings.remove("autoSystemRoutingTable")
                    settings.remove("autoOutboundsInterface")
                    Log.d(TAG, "Sanitized existing tun inbound for Android VpnService (removed auto-routing).")
                } else {
                    inbounds.remove(i)
                    Log.d(TAG, "Removed desktop-only tun inbound at index $i to prevent Android permission denied.")
                }
                continue
            }
            if (protocol == "socks") {
                hasSocksInbound = true
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
                Log.d(TAG, "Synchronized SOCKS inbound port to $targetPort and listen to $targetListen.")
            } else {
                val listen = inbound.optString("listen")
                if (listen == "::" || listen == "0.0.0.0") {
                    inbound.put("listen", "127.0.0.1")
                    Log.d(TAG, "Converted bind address from $listen to 127.0.0.1 for inbound at index $i.")
                }
            }
        }

        if (prefs?.useXrayTun == true && prefs.disableVpn == false && !hasTunInbound) {
            val newTunInbound = JSONObject().apply {
                put("protocol", "tun")
                put("tag", "tun-inbound")
                put("settings", JSONObject().apply {
                    put("network", "tcp,udp")
                    put("autoRoute", false)
                    put("strictRoute", false)
                })
            }
            inbounds.put(newTunInbound)
            Log.d(TAG, "Injected default Android-compatible tun inbound.")
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
                        val k = keys.next().lowercase()
                        if (EFFECTIVE_MATCH_KEYS.contains(k)) {
                            val v = rule.opt(k)
                            if (v is JSONArray && v.length() > 0) {
                                hasEffectiveField = true
                                break
                            } else if (v is String && v.isNotEmpty()) {
                                hasEffectiveField = true
                                break
                            } else if (v !is JSONArray && v !is String) {
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

        // 5. Observatory probeTimeout safety injection
        val observatory = rootJson.optJSONObject("observatory")
        if (observatory != null) {
            if (!observatory.has("probeTimeout")) {
                observatory.put("probeTimeout", "2s")
                Log.d(TAG, "Injected default probeTimeout: 2s into observatory.")
            }
        }

        return rootJson.toString(2)
    }

    private fun parseToJsonObject(content: String): JSONObject? {
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            return runCatching { JSONObject(trimmed) }.getOrNull()
        }
        return runCatching {
            val map = Yaml().load<Any>(content)
            if (map is Map<*, *>) {
                toJSONObject(map)
            } else {
                null
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to parse YAML content into JSON AST", e)
            null
        }
    }

    private fun toJSONObject(map: Map<*, *>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            if (key != null) {
                json.put(key.toString(), convertValue(value))
            }
        }
        return json
    }

    private fun toJSONArray(list: List<*>): JSONArray {
        val array = JSONArray()
        for (item in list) {
            array.put(convertValue(item))
        }
        return array
    }

    private fun convertValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> toJSONObject(value)
            is List<*> -> toJSONArray(value)
            is Number, is Boolean, is String -> value
            else -> value.toString()
        }
    }

    @Throws(JSONException::class)
    fun formatConfigContent(content: String): String {
        return sanitizeConfig(content)
    }

    /**
     * Returns true when [content] parses as a full config in JSON or YAML form.
     * Used to reject share/subscription URIs and other non-config text.
     */
    fun isValidConfigContent(content: String): Boolean = parseToJsonObject(content) != null

    @Throws(JSONException::class)
    fun injectStatsService(prefs: Preferences, configContent: String): String {
        val sanitized = sanitizeConfig(configContent, prefs)
        val jsonObject = JSONObject(sanitized)

        val apiObject = JSONObject()
        apiObject.put("tag", "api")
        apiObject.put("listen", "${prefs.apiAddress}:${prefs.apiPort}")
        val servicesArray = JSONArray()
        servicesArray.put("StatsService")
        apiObject.put("services", servicesArray)

        jsonObject.put("api", apiObject)
        jsonObject.put("stats", JSONObject())

        val policyObject = JSONObject()
        val systemObject = JSONObject()
        systemObject.put("statsOutboundUplink", true)
        systemObject.put("statsOutboundDownlink", true)
        policyObject.put("system", systemObject)

        jsonObject.put("policy", policyObject)

        if (prefs.httpProxyEnabled) {
            val inbounds = jsonObject.optJSONArray("inbounds") ?: JSONArray().also { jsonObject.put("inbounds", it) }
            var hasHttpInbound = false
            for (i in 0 until inbounds.length()) {
                val inb = inbounds.optJSONObject(i)
                if (inb?.optString("protocol")?.lowercase() == "http") {
                    hasHttpInbound = true
                    break
                }
            }
            if (!hasHttpInbound) {
                val httpInbound = JSONObject()
                httpInbound.put("tag", "http-inbound")
                httpInbound.put("listen", "127.0.0.1")
                httpInbound.put("port", prefs.httpPort)
                httpInbound.put("protocol", "http")
                inbounds.put(httpInbound)
            }
        }

        return jsonObject.toString(2)
    }

    fun File.isConfigFile(): Boolean {
        val ext = extension.lowercase()
        return ext == "json" || ext == "yaml" || ext == "yml"
    }

    /**
     * Extracts proxy outbounds (tag + protocol) from a config in JSON or YAML form,
     * in config order. Non-proxy protocols (freedom/blackhole/dns) are excluded.
     */
    fun extractOutbounds(content: String): List<OutboundInfo> {
        val root = parseToJsonObject(content) ?: return emptyList()
        return extractOutboundsFrom(root)
    }

    private fun extractOutboundsFrom(root: JSONObject): List<OutboundInfo> {
        val outbounds = root.optJSONArray("outbounds") ?: return emptyList()
        return buildList {
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val protocol = ob.optString("protocol").lowercase()
                if (protocol in EXCLUDED_OUTBOUND_PROTOCOLS) continue
                val tag = ob.optString("tag")
                if (tag.isEmpty()) continue
                add(OutboundInfo(tag, protocol))
            }
        }
    }

    /**
     * Extracts the TCP server endpoint (host:port) of each proxy outbound, in
     * config order. Protocols with no TCP endpoint to test are skipped:
     * non-proxy protocols (freedom/blackhole/dns), UDP-only protocols
     * (wireguard/hysteria2) and QUIC transports.
     */
    fun extractOutboundEndpoints(content: String): List<OutboundEndpoint> {
        val root = parseToJsonObject(content) ?: return emptyList()
        val outbounds = root.optJSONArray("outbounds") ?: return emptyList()
        return buildList {
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val protocol = ob.optString("protocol").lowercase()
                if (protocol in EXCLUDED_OUTBOUND_PROTOCOLS) continue
                if (protocol in UDP_ONLY_OUTBOUND_PROTOCOLS) continue
                val network = ob.optJSONObject("streamSettings")
                    ?.optString("network")?.lowercase()
                if (network == "quic") continue
                val tag = ob.optString("tag")
                if (tag.isEmpty()) continue
                val (host, port) = extractServerEndpoint(ob, protocol) ?: continue
                // Reject IP literals pointing at private/loopback/link-local space
                // so a malicious or mistyped config cannot turn the dashboard
                // probe into an internal-network scanner (SSRF-like surface).
                if (isPrivateOrLoopbackHost(host)) continue
                add(OutboundEndpoint(tag, protocol, host, port))
            }
        }
    }

    /**
     * True when [host] is an IP literal in private, loopback, link-local or
     * reserved space. Hostnames are resolved by the system DNS at connect
     * time and are intentionally not validated here.
     */
    private fun isPrivateOrLoopbackHost(host: String): Boolean {
        if (host.isEmpty()) return true
        if (!isIpLiteral(host)) return false // hostname, resolved by DNS
        return runCatching {
            // getByName on an IP literal (including inet_aton short/octal/hex
            // forms like 127.1, 2130706433 or 0xC0A80101) parses locally and
            // matches the semantics Socket.connect uses.
            val addr = InetAddress.getByName(host)
            if (addr is Inet6Address) {
                val b = addr.address
                // fc00::/7 unique local addresses (not covered by isSiteLocalAddress).
                if (b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC) return@runCatching true
            }
            addr.isAnyLocalAddress || addr.isLoopbackAddress ||
                addr.isLinkLocalAddress || addr.isSiteLocalAddress ||
                addr.isMulticastAddress
        }.getOrDefault(true) // unparseable literal -> skip probing
    }

    /** True when [host] looks like an IP literal rather than a hostname. */
    private fun isIpLiteral(host: String): Boolean {
        val h = host.trim('[', ']')
        if (h.contains(':')) {
            return h.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
        }
        // inet_aton IPv4 forms: 1-4 dot-separated segments; each segment is
        // decimal, octal (leading 0), or hexadecimal (0x/0X prefix).
        return h.split('.').all { seg ->
            if (seg.isEmpty()) return@all false
            if (seg.startsWith("0x", ignoreCase = true)) {
                seg.length > 2 &&
                    seg.substring(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            } else {
                seg.all { it.isDigit() }
            }
        }
    }

    private fun extractServerEndpoint(ob: JSONObject, protocol: String): Pair<String, Int>? {
        val settings = ob.optJSONObject("settings") ?: return null
        val server = when (protocol) {
            "vless", "vmess" -> settings.optJSONArray("vnext")?.optJSONObject(0)
            "trojan", "shadowsocks", "http", "socks" -> settings.optJSONArray("servers")?.optJSONObject(0)
            else -> null
        } ?: return null
        val host = server.optString("address").takeIf { it.isNotBlank() } ?: return null
        val port = server.optInt("port").takeIf { it > 0 && it <= 65535 } ?: return null
        return host to port
    }

    fun buildInjectedConfig(content: String, isYaml: Boolean, prefs: Preferences): String {
        return injectStatsService(prefs, content)
    }

    fun extractPortsFromJson(jsonContent: String): Set<Int> {
        val ports = mutableSetOf<Int>()
        try {
            val jsonObject = JSONObject(jsonContent)
            extractPortsRecursive(jsonObject, ports)
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing JSON for port extraction", e)
        }
        Log.d(TAG, "Extracted ports: $ports")
        return ports
    }

    private fun extractPortsRecursive(jsonObject: JSONObject, ports: MutableSet<Int>) {
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = jsonObject.opt(key)) {
                is Int -> {
                    if (value in 1..65535) {
                        ports.add(value)
                    }
                }

                is JSONObject -> {
                    extractPortsRecursive(value, ports)
                }

                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        if (item is JSONObject) {
                            extractPortsRecursive(item, ports)
                        }
                    }
                }
            }
        }
    }
}
