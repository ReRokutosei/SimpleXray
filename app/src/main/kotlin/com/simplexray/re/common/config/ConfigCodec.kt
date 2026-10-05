package com.simplexray.re.common.config

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml

internal object ConfigCodec {
    private const val TAG = "ConfigCodec"

    fun parse(content: String): JSONObject? {
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

    fun isValid(content: String): Boolean = parse(content) != null
}
