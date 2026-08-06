package com.simplexray.an.common

import android.util.Log
import com.simplexray.an.prefs.Preferences
import org.json.JSONException
import org.json.JSONObject

object ConfigUtils {
    private const val TAG = "ConfigUtils"

    private val EFFECTIVE_MATCH_KEYS = setOf(
        "domain", "ip", "port", "network", "process", "geosite", "geoip", "inboundtag", "protocol", "user", "attrs"
    )

    fun sanitizeConfig(content: String): String {
        try {
            val jsonObject = JSONObject(content)
            if (jsonObject.has("inbounds")) {
                jsonObject.remove("inbounds")
            }
            if (jsonObject.has("log")) {
                jsonObject.remove("log")
            }

            // Sanitize JSON routing rules
            val routing = jsonObject.optJSONObject("routing")
            val rules = routing?.optJSONArray("rules")
            if (rules != null) {
                for (i in rules.length() - 1 downTo 0) {
                    val rule = rules.optJSONObject(i) ?: continue
                    val processArr = rule.optJSONArray("process")
                    if (processArr != null) {
                        val cleanedProcess = org.json.JSONArray()
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

                    // Check for remaining effective match fields
                    var hasEffectiveField = false
                    val keys = rule.keys()
                    while (keys.hasNext()) {
                        val k = keys.next().lowercase()
                        if (EFFECTIVE_MATCH_KEYS.contains(k)) {
                            val v = rule.get(k)
                            if (v is org.json.JSONArray && v.length() > 0) {
                                hasEffectiveField = true
                                break
                            } else if (v is String && v.isNotEmpty()) {
                                hasEffectiveField = true
                                break
                            } else if (v !is org.json.JSONArray && v !is String) {
                                hasEffectiveField = true
                                break
                            }
                        }
                    }
                    if (!hasEffectiveField) {
                        rules.remove(i)
                    }
                }
            }
            return jsonObject.toString(2)
        } catch (ignored: Exception) {
        }

        val lines = content.lines()
        val result = StringBuilder()
        var currentSection = ""
        var skipSection = false
        var inRulesSubSection = false
        var seenFirstRule = false
        var currentRuleLines = mutableListOf<String>()

        fun flushRuleBlock() {
            if (currentRuleLines.isEmpty()) return
            var hasTag = false
            var hasMatchField = false
            val cleanedLines = mutableListOf<String>()

            for (l in currentRuleLines) {
                val trimmed = l.trim()
                if (trimmed.contains(".exe", ignoreCase = true)) {
                    if (trimmed.startsWith("-") && trimmed.endsWith(".exe", ignoreCase = true)) {
                        continue
                    }
                    var c = l.replace(Regex("(?i)\\b[\\w\\.-]+\\.exe\\b,?\\s*"), "")
                    if (c.contains("process:") && c.endsWith(", ]")) {
                        c = c.replace(", ]", "]")
                    }
                    val procStripped = c.trim().removePrefix("-").trim()
                    if (procStripped == "process: []" || procStripped == "process:") {
                        continue
                    }
                    cleanedLines.add(c)
                } else {
                    cleanedLines.add(l)
                }
            }

            for (l in cleanedLines) {
                val trimmed = l.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                if (trimmed.contains(":")) {
                    val key = trimmed.substringBefore(":").trim().removePrefix("-").trim().lowercase()
                    val value = trimmed.substringAfter(":", "").trim()

                    if (key == "outboundtag" || key == "balancertag") {
                        if (value.isNotEmpty()) {
                            hasTag = true
                        }
                    } else if (EFFECTIVE_MATCH_KEYS.contains(key)) {
                        if (key != "process") {
                            hasMatchField = true
                        } else {
                            if (value.isNotEmpty() && value != "[]") {
                                hasMatchField = true
                            }
                        }
                    }
                }

                if (trimmed.startsWith("- ")) {
                    val item = trimmed.substringAfter("- ").trim()
                    if (item.isNotEmpty() && !item.endsWith(":") && !item.endsWith("[]") && !item.endsWith(".exe", ignoreCase = true)) {
                        hasMatchField = true
                    }
                }
            }

            if (hasTag && hasMatchField) {
                for (l in cleanedLines) {
                    result.append(l).append("\n")
                }
            } else {
                Log.d(TAG, "Dropped invalid/empty routing rule block (hasTag=$hasTag, hasMatchField=$hasMatchField).")
            }
            currentRuleLines.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            val isTopLevelMappingKey = line.isNotEmpty()
                && !line[0].isWhitespace()
                && (line[0].isLetterOrDigit() || line[0] == '_')
                && line.contains(":")

            if (isTopLevelMappingKey) {
                flushRuleBlock()
                val key = trimmed.substringBefore(":").trim().lowercase()
                currentSection = key
                skipSection = (key == "inbounds" || key == "log" || key == "observatory" || key == "burstobservatory")
                inRulesSubSection = false
                seenFirstRule = false
            }

            if (skipSection) continue

            // Strip fallbackTag to break Observatory dependency deadlock
            if (trimmed.startsWith("fallbackTag:")) continue

            // Strip invalid echConfigList with http URL to break TLS ECH fetch 20s timeout hang
            if (trimmed.startsWith("echConfigList:") && trimmed.contains("http", ignoreCase = true)) continue

            val isRulesHeader = (trimmed == "rules:" || trimmed.startsWith("rules:"))
            if (isRulesHeader) {
                flushRuleBlock()
                inRulesSubSection = true
                seenFirstRule = false
                result.append(line).append("\n")
                continue
            }

            if (inRulesSubSection) {
                if (isTopLevelMappingKey || (line.isNotEmpty() && !line[0].isWhitespace() && line.contains(":"))) {
                    flushRuleBlock()
                    inRulesSubSection = false
                    seenFirstRule = false
                    result.append(line).append("\n")
                    continue
                }

                val isNewBlockStart = line.startsWith("  - ") || (line.startsWith("- ") && !line.startsWith("    "))
                if (isNewBlockStart) {
                    flushRuleBlock()
                    seenFirstRule = true
                }

                if (!seenFirstRule) {
                    result.append(line).append("\n")
                } else {
                    currentRuleLines.add(line)
                }
            } else {
                result.append(line).append("\n")
            }
        }
        flushRuleBlock()
        var sanitizedYaml = result.toString()
        sanitizedYaml = sanitizedYaml.replace("domainMatcher: mph", "domainMatcher: hybrid")
        sanitizedYaml = sanitizedYaml.replace("type: leastPing", "type: random").replace("type: leastLoad", "type: random")

        return sanitizedYaml
    }

    fun buildInjectedConfig(configContent: String, isYaml: Boolean, prefs: Preferences): String {
        if (!isYaml) {
            return buildInjectedJsonConfig(configContent, prefs)
        }

        val sb = StringBuilder(configContent.trimEnd())
        sb.append("\n\n# System Injected Inbounds & Management Services\n")
        sb.append("api:\n")
        sb.append("  tag: api\n")
        sb.append("  listen: 127.0.0.1:${prefs.apiPort}\n")
        sb.append("  services: [StatsService]\n\n")
        sb.append("policy:\n")
        sb.append("  system:\n")
        sb.append("    statsInboundUplink: true\n")
        sb.append("    statsInboundDownlink: true\n")
        sb.append("    statsOutboundUplink: true\n")
        sb.append("    statsOutboundDownlink: true\n\n")
        sb.append("stats: {}\n\n")

        if (!configContent.contains("dns:")) {
            sb.append("dns:\n")
            sb.append("  servers:\n")
            sb.append("    - 1.1.1.1\n")
            sb.append("    - 8.8.8.8\n")
            sb.append("    - 223.5.5.5\n\n")
        }

        sb.append("inbounds:\n")
        sb.append("  - tag: tun-inbound\n")
        sb.append("    port: 0\n")
        sb.append("    protocol: tun\n")
        sb.append("    settings:\n")
        sb.append("      name: tun4\n")
        sb.append("      mtu: ${prefs.tunnelMtu}\n")
        sb.append("      gateway:\n")
        sb.append("        - 198.18.0.1/16\n")
        sb.append("  - tag: socks-inbound\n")
        sb.append("    port: ${prefs.socksPort}\n")
        sb.append("    listen: 127.0.0.1\n")
        sb.append("    protocol: socks\n")
        sb.append("    settings:\n")
        sb.append("      auth: noauth\n")
        sb.append("      udp: true\n")

        return sb.toString()
    }

    private fun buildInjectedJsonConfig(configContent: String, prefs: Preferences): String {
        val jsonObject = try {
            JSONObject(configContent)
        } catch (e: Exception) {
            return configContent
        }

        val apiObject = JSONObject()
        apiObject.put("tag", "api")
        apiObject.put("listen", "127.0.0.1:${prefs.apiPort}")
        val servicesArray = org.json.JSONArray()
        servicesArray.put("StatsService")
        apiObject.put("services", servicesArray)

        val policyObject = JSONObject()
        val systemObject = JSONObject()
        systemObject.put("statsInboundUplink", true)
        systemObject.put("statsInboundDownlink", true)
        systemObject.put("statsOutboundUplink", true)
        systemObject.put("statsOutboundDownlink", true)
        policyObject.put("system", systemObject)

        val dns = jsonObject.optJSONObject("dns") ?: JSONObject()
        val servers = dns.optJSONArray("servers") ?: org.json.JSONArray()
        if (servers.length() == 0) {
            servers.put("1.1.1.1")
            servers.put("8.8.8.8")
            servers.put("223.5.5.5")
            dns.put("servers", servers)
            jsonObject.put("dns", dns)
        }

        val tunSettings = JSONObject()
        tunSettings.put("name", "tun4")
        tunSettings.put("mtu", prefs.tunnelMtu)
        val gatewayArr = org.json.JSONArray()
        gatewayArr.put("198.18.0.1/16")
        tunSettings.put("gateway", gatewayArr)

        val tunInbound = JSONObject()
        tunInbound.put("tag", "tun-inbound")
        tunInbound.put("port", 0)
        tunInbound.put("protocol", "tun")
        tunInbound.put("settings", tunSettings)

        val socksSettings = JSONObject()
        socksSettings.put("auth", "noauth")
        socksSettings.put("udp", true)

        val socksInbound = JSONObject()
        socksInbound.put("tag", "socks-inbound")
        socksInbound.put("port", prefs.socksPort)
        socksInbound.put("listen", "127.0.0.1")
        socksInbound.put("protocol", "socks")
        socksInbound.put("settings", socksSettings)

        val inboundsArray = jsonObject.optJSONArray("inbounds") ?: org.json.JSONArray()
        inboundsArray.put(tunInbound)
        inboundsArray.put(socksInbound)

        jsonObject.put("api", apiObject)
        jsonObject.put("inbounds", inboundsArray)
        jsonObject.put("stats", JSONObject())
        jsonObject.put("policy", policyObject)

        var result = jsonObject.toString(2)
        result = result.replace("\\/", "/")
        return result
    }

    fun extractTunMtu(configContent: String): Int? {
        try {
            val jsonObject = JSONObject(configContent)
            val inbounds = jsonObject.optJSONArray("inbounds") ?: return null
            for (i in 0 until inbounds.length()) {
                val inbound = inbounds.optJSONObject(i) ?: continue
                if (inbound.optString("protocol") == "tun") {
                    return inbound.optJSONObject("settings")?.optInt("MTU", -1)
                        ?.takeIf { it > 0 }
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing JSON for TUN MTU extraction", e)
        }
        return null
    }

    @Throws(JSONException::class)
    fun formatConfigContent(content: String): String {
        val jsonObject = try {
            JSONObject(content)
        } catch (e: Exception) {
            return content
        }
        (jsonObject["log"] as? JSONObject)?.apply {
            if (has("access") && optString("access") != "none") {
                remove("access")
                Log.d(TAG, "Removed log.access")
            }
            if (has("error") && optString("error") != "none") {
                remove("error")
                Log.d(TAG, "Removed log.error")
            }
        }
        var formattedContent = jsonObject.toString(2)
        formattedContent = formattedContent.replace("\\/", "/")
        return formattedContent
    }

    fun ensureValidDns(configContent: String): String {
        val jsonObject = try {
            JSONObject(configContent)
        } catch (e: Exception) {
            return configContent
        }

        val dns = jsonObject.optJSONObject("dns") ?: JSONObject()
        val servers = dns.optJSONArray("servers") ?: org.json.JSONArray()
        if (servers.length() == 0) {
            servers.put("1.1.1.1")
            servers.put("8.8.8.8")
            servers.put("223.5.5.5")
            dns.put("servers", servers)
            jsonObject.put("dns", dns)
        }

        return jsonObject.toString(2)
    }

    @Throws(JSONException::class)
    fun injectStatsService(prefs: Preferences, configContent: String): String {
        val jsonObject = try {
            JSONObject(configContent)
        } catch (e: Exception) {
            return configContent
        }

        val apiObject = JSONObject()
        apiObject.put("tag", "api")
        apiObject.put("listen", "${prefs.apiAddress}:${prefs.apiPort}")
        val servicesArray = org.json.JSONArray()
        servicesArray.put("StatsService")
        apiObject.put("services", servicesArray)

        val policyObject = JSONObject()
        val systemObject = JSONObject()
        systemObject.put("statsOutboundUplink", true)
        systemObject.put("statsOutboundDownlink", true)
        policyObject.put("system", systemObject)

        jsonObject.put("api", apiObject)
        jsonObject.put("stats", JSONObject())
        jsonObject.put("policy", policyObject)

        var result = jsonObject.toString(2)
        result = result.replace("\\/", "/")
        return result
    }

    fun buildSocksInboundJson(address: String = "127.0.0.1", port: Int = 10808): String {
        val settings = JSONObject()
        settings.put("auth", "noauth")
        settings.put("udp", true)

        val inbound = JSONObject()
        inbound.put("tag", "socks-inbound")
        inbound.put("port", port)
        inbound.put("listen", address)
        inbound.put("protocol", "socks")
        inbound.put("settings", settings)

        val inboundsArray = org.json.JSONArray()
        inboundsArray.put(inbound)

        val root = JSONObject()
        root.put("inbounds", inboundsArray)
        return root.toString(2)
    }

    fun buildNativeTunInboundJson(mtu: Int = 1500): String {
        val settings = JSONObject()
        settings.put("name", "tun4")
        settings.put("mtu", mtu)

        val inbound = JSONObject()
        inbound.put("tag", "tun-inbound")
        inbound.put("port", 0)
        inbound.put("protocol", "tun")
        inbound.put("settings", settings)

        val inboundsArray = org.json.JSONArray()
        inboundsArray.put(inbound)

        val root = JSONObject()
        root.put("inbounds", inboundsArray)
        return root.toString(2)
    }

    fun injectUdsApiAndStats(socketPath: String, configContent: String): String {
        val jsonObject = try {
            JSONObject(configContent)
        } catch (e: Exception) {
            return configContent
        }

        val apiObject = JSONObject()
        apiObject.put("tag", "api")
        val servicesArray = org.json.JSONArray()
        servicesArray.put("StatsService")
        apiObject.put("services", servicesArray)

        val policyObject = JSONObject()
        val systemObject = JSONObject()
        systemObject.put("statsOutboundUplink", true)
        systemObject.put("statsOutboundDownlink", true)
        policyObject.put("system", systemObject)

        val udsSettings = JSONObject()
        udsSettings.put("address", "127.0.0.1")

        val udsInbound = JSONObject()
        udsInbound.put("tag", "api-inbound")
        udsInbound.put("listen", socketPath)
        udsInbound.put("protocol", "dokodemo-door")
        udsInbound.put("settings", udsSettings)

        val inboundsArray = jsonObject.optJSONArray("inbounds") ?: org.json.JSONArray()
        inboundsArray.put(udsInbound)

        jsonObject.put("api", apiObject)
        jsonObject.put("inbounds", inboundsArray)
        jsonObject.put("stats", JSONObject())
        jsonObject.put("policy", policyObject)

        var result = jsonObject.toString(2)
        result = result.replace("\\/", "/")
        return result
    }

    @Throws(JSONException::class)
    fun mergeAdditionalInbounds(baseConfig: String, extraInboundsJson: String): String {
        val base = try {
            JSONObject(baseConfig)
        } catch (e: Exception) {
            return baseConfig
        }
        val extra = try {
            JSONObject(extraInboundsJson)
        } catch (e: Exception) {
            return baseConfig
        }

        val baseInbounds = base.optJSONArray("inbounds") ?: org.json.JSONArray()
        val extraInbounds = extra.optJSONArray("inbounds") ?: return baseConfig

        for (i in 0 until extraInbounds.length()) {
            baseInbounds.put(extraInbounds.get(i))
        }
        base.put("inbounds", baseInbounds)

        var result = base.toString(2)
        result = result.replace("\\/", "/")
        return result
    }

    fun extractPortsFromJson(jsonContent: String): Set<Int> {
        val ports = mutableSetOf<Int>()
        try {
            val jsonObject = JSONObject(jsonContent)
            extractPortsRecursive(jsonObject, ports)
        } catch (e: JSONException) {
            // Silently ignore non-JSON contents
        }
        Log.d(TAG, "Extracted ports: $ports")
        return ports
    }

    private fun extractPortsRecursive(jsonObject: JSONObject, ports: MutableSet<Int>) {
        for (key in jsonObject.keys()) {
            when (val value = jsonObject.get(key)) {
                is Int -> {
                    if (value in 1..65535) {
                        ports.add(value)
                    }
                }

                is JSONObject -> {
                    extractPortsRecursive(value, ports)
                }

                is org.json.JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.get(i)
                        if (item is JSONObject) {
                            extractPortsRecursive(item, ports)
                        }
                    }
                }
            }
        }
    }
}

fun java.io.File.isConfigFile(): Boolean {
    val name = this.name.lowercase()
    return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".toml")
}

fun String.isConfigFile(): Boolean {
    val name = this.lowercase()
    return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".toml")
}

