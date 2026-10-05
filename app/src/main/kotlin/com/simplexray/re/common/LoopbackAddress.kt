package com.simplexray.re.common

import java.net.InetAddress

/**
 * Returns true only for IP literals that resolve to a loopback address
 * (127.0.0.0/8 or ::1). Hostnames are rejected without DNS resolution so the
 * SOCKS inbound cannot accidentally bind to a LAN/WAN interface that the
 * internal health monitor and download clients cannot reach via 127.0.0.1.
 */
fun isLoopbackAddress(address: String): Boolean {
    val candidate = address.trim().removePrefix("[").removeSuffix("]")
    if (candidate.isEmpty()) return false
    val looksLikeIpLiteral = candidate.contains(':') || candidate.all { it.isDigit() || it == '.' }
    if (!looksLikeIpLiteral) return false
    return runCatching { InetAddress.getByName(candidate).isLoopbackAddress }.getOrDefault(false)
}
