package com.simplexray.re.service

import org.json.JSONObject

/** Builds the small Android-specific Zeptun document used by TProxyService. */
internal object ZeptunConfigBuilder {
    fun build(
        host: String,
        port: Int,
        username: String,
        password: String,
        mtu: Int,
        fd: Int,
        udpInTcp: Boolean
    ): String {
        val endpointHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        val socks = JSONObject()
            .put("server", "$endpointHost:$port")
            .put("username", username)
            .put("password", password)
            .put("udp", true)
            .put("udp_mode", if (udpInTcp) "tcp" else "udp")
        return JSONObject()
            .put("preset", "mobile")
            // Supplying fd in the document makes Zeptun select its Android fd
            // device path before Engine.create() validates the configuration.
            .put("tun", JSONObject().put("fd", fd).put("mtu", mtu).put("configure", false))
            .put("stack", JSONObject().put("mode", "userspace"))
            .put("handler", JSONObject().put("kind", "socks5").put("socks5", socks))
            .put("route", JSONObject().put("auto_route", false))
            .toString()
    }
}
