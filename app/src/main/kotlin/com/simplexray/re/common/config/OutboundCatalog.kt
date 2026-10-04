package com.simplexray.re.common.config

import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.ConfigUtils.OutboundEndpoint
import com.simplexray.re.common.ConfigUtils.OutboundInfo
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress

internal object OutboundCatalog {
    private val EXCLUDED_OUTBOUND_PROTOCOLS = setOf("freedom", "blackhole", "dns")

    // UDP-only protocols that cannot be latency-tested via TCP connect.
    private val UDP_ONLY_OUTBOUND_PROTOCOLS = setOf("wireguard", "hysteria", "hysteria2")

    fun extractOutbounds(content: String): List<OutboundInfo> {
        val root = ConfigCodec.parse(content) ?: return emptyList()
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

    fun extractOutboundEndpoints(content: String): List<OutboundEndpoint> {
        val root = ConfigCodec.parse(content) ?: return emptyList()
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
        val nestedServer = when (protocol) {
            "vless", "vmess" -> settings.optJSONArray("vnext")?.optJSONObject(0)
            "trojan", "shadowsocks", "http", "socks" -> settings.optJSONArray("servers")?.optJSONObject(0)
            else -> null
        }
        val host = nestedServer?.optString("address")?.takeIf { it.isNotBlank() }
            ?: settings.optString("address").takeIf { it.isNotBlank() }
            ?: return null
        val port = nestedServer?.optInt("port")?.takeIf { it in 1..65535 }
            ?: settings.optInt("port").takeIf { it in 1..65535 }
            ?: return null
        return host to port
    }
}
