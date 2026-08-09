package com.simplexray.an.common

import android.util.Log
import com.simplexray.an.prefs.Preferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.File

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

    fun sanitizeConfig(content: String): String {
        val rootJson = parseToJsonObject(content) ?: return content

        // 1. Process and sanitize inbounds (remove desktop tun & convert global listen)
        rootJson.remove("log")
        val inbounds = rootJson.optJSONArray("inbounds")
        if (inbounds != null) {
            for (i in inbounds.length() - 1 downTo 0) {
                val inbound = inbounds.optJSONObject(i) ?: continue
                val protocol = inbound.optString("protocol").lowercase()
                if (protocol == "tun") {
                    inbounds.remove(i)
                    Log.d(TAG, "Removed desktop-only tun inbound at index $i to prevent Android permission denied.")
                    continue
                }
                val listen = inbound.optString("listen")
                if (listen == "::" || listen == "0.0.0.0") {
                    inbound.put("listen", "127.0.0.1")
                    Log.d(TAG, "Converted bind address from $listen to 127.0.0.1 for inbound at index $i.")
                }
            }
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

    @Throws(JSONException::class)
    fun injectStatsService(prefs: Preferences, configContent: String): String {
        val sanitized = sanitizeConfig(configContent)
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
