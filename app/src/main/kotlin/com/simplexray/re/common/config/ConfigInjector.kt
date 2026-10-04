package com.simplexray.re.common.config

import android.util.Log
import com.simplexray.re.prefs.Preferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object ConfigInjector {
    private const val TAG = "ConfigInjector"

    fun extractTunMtu(configContent: String): Int? {
        try {
            val jsonObject = ConfigCodec.parse(configContent) ?: return null
            val inbounds = jsonObject.optJSONArray("inbounds") ?: return null
            for (i in 0 until inbounds.length()) {
                val inbound = inbounds.optJSONObject(i) ?: continue
                if (inbound.optString("protocol") == "tun") {
                    return inbound.optJSONObject("settings")?.optInt("mtu", -1)
                        ?.takeIf { it > 0 }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON for TUN MTU extraction", e)
        }
        return null
    }

    fun injectStatsService(prefs: Preferences, configContent: String): String {
        val sanitized = ConfigSanitizer.sanitize(configContent, prefs)
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

    fun buildInjectedConfig(content: String, isYaml: Boolean, prefs: Preferences): String {
        return injectStatsService(prefs, content)
    }
}
