package com.simplexray.re.service

import com.simplexray.re.prefs.Preferences

internal object TproxyConfigBuilder {
    fun build(prefs: Preferences): String {
        var tproxyConf = """misc:
  task-stack-size: ${prefs.taskStackSize}
  connect-timeout: ${prefs.tcpConnectTimeout}
  tcp-read-write-timeout: ${prefs.tcpReadWriteTimeout}
  udp-read-write-timeout: ${prefs.udpReadWriteTimeout}
  udp-recv-buffer-size: ${prefs.udpRecvBufferSize}
tunnel:
  mtu: ${prefs.tunnelMtu}
"""
        tproxyConf += """socks5:
  port: ${prefs.socksPort}
  address: '${escapeSingleQuoted(prefs.socksAddress)}'
  udp: '${if (prefs.udpInTcp) "tcp" else "udp"}'
"""
        if (prefs.socksUsername.isNotEmpty() && prefs.socksPassword.isNotEmpty()) {
            tproxyConf += "  username: '" + escapeSingleQuoted(prefs.socksUsername) + "'\n"
            tproxyConf += "  password: '" + escapeSingleQuoted(prefs.socksPassword) + "'\n"
        }
        return tproxyConf
    }

    /**
     * Escapes a value for a YAML single-quoted scalar. An embedded single quote
     * is represented by doubling it (YAML 1.2, section 7.3.1); without this a
     * password like "pa'ss" produces an unparsable tproxy.conf.
     */
    private fun escapeSingleQuoted(value: String): String = value.replace("'", "''")
}
