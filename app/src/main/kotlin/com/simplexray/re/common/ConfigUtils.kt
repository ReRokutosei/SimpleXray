package com.simplexray.re.common

import com.simplexray.re.common.config.ConfigCodec
import com.simplexray.re.common.config.ConfigInjector
import com.simplexray.re.common.config.ConfigSanitizer
import com.simplexray.re.common.config.OutboundCatalog
import com.simplexray.re.prefs.Preferences
import org.json.JSONException
import org.json.JSONObject
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
    data class OutboundInfo(val tag: String, val protocol: String)

    data class OutboundEndpoint(val tag: String, val protocol: String, val host: String, val port: Int)

    fun extractTunMtu(configContent: String): Int? =
        ConfigInjector.extractTunMtu(configContent)

    fun sanitizeConfig(content: String, prefs: Preferences? = null): String =
        ConfigSanitizer.sanitize(content, prefs)

    @Throws(JSONException::class)
    fun formatConfigContent(content: String): String =
        ConfigSanitizer.sanitize(content)

    fun isValidConfigContent(content: String): Boolean =
        ConfigCodec.isValid(content)

    @Throws(JSONException::class)
    fun injectStatsService(prefs: Preferences, configContent: String): String =
        ConfigInjector.injectStatsService(prefs, configContent)

    fun extractOutbounds(content: String): List<OutboundInfo> =
        OutboundCatalog.extractOutbounds(content)

    fun extractOutboundEndpoints(content: String): List<OutboundEndpoint> =
        OutboundCatalog.extractOutboundEndpoints(content)

    fun buildInjectedConfig(content: String, isYaml: Boolean, prefs: Preferences): String =
        ConfigInjector.buildInjectedConfig(content, isYaml, prefs)

    fun extractPortsFromJson(jsonContent: String): Set<Int> =
        ConfigInjector.extractPortsFromJson(jsonContent)

    internal fun createDefaultSniffingObject(): JSONObject =
        ConfigSanitizer.createDefaultSniffingObject()
}
