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
  address: '${prefs.socksAddress}'
  udp: '${if (prefs.udpInTcp) "tcp" else "udp"}'
"""
        if (prefs.socksUsername.isNotEmpty() && prefs.socksPassword.isNotEmpty()) {
            tproxyConf += "  username: '" + prefs.socksUsername + "'\n"
            tproxyConf += "  password: '" + prefs.socksPassword + "'\n"
        }
        return tproxyConf
    }
}
