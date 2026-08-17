package com.simplexray.re.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.prefs.TunnelMode
import java.io.File

class BenchmarkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_BENCHMARK) return

        val cmd = intent.getStringExtra(EXTRA_CMD) ?: "status"
        val backend = intent.getStringExtra(EXTRA_BACKEND) // "hev" or "xray"
        val mtu = intent.getIntExtra(EXTRA_MTU, 0)
        val testConfigName = "benchmark_direct.json"

        Log.d(TAG, "BenchmarkReceiver received cmd: $cmd, backend: $backend, mtu: $mtu")

        val prefs = Preferences(context.applicationContext)

        when (cmd) {
            "setup", "start" -> {
                // Configure Preferences for pure Direct / LAN testing
                if (backend != null) {
                    when (backend.lowercase()) {
                        "hev", "hev_socks5_tunnel" -> prefs.tunnelMode = TunnelMode.HevSocks5Tunnel
                        "xray", "xray_tun" -> prefs.tunnelMode = TunnelMode.XrayTun
                    }
                }
                prefs.disableVpn = false
                prefs.bypassLan = false // Important: Route LAN traffic through TUN to test LAN speed to PC
                prefs.global = true

                // Write a clean direct config file into filesDir
                val filesDir = context.filesDir
                val configFile = File(filesDir, testConfigName)
                val mtuSetting = if (mtu > 0) """ "mtu": $mtu, """ else ""

                val configJson = """
{
  "log": {
    "loglevel": "warning"
  },
  "inbounds": [
    {
      "tag": "tun-inbound",
      "protocol": "tun",
      "settings": {
        "name": "tun-inbound",
        $mtuSetting
        "network": "tcp,udp"
      }
    },
    {
      "tag": "socks-in",
      "protocol": "socks",
      "listen": "127.0.0.1",
      "port": ${prefs.socksPort},
      "settings": {
        "auth": "noauth",
        "udp": true
      }
    }
  ],
  "outbounds": [
    {
      "tag": "direct",
      "protocol": "freedom",
      "settings": {}
    }
  ]
}
""".trimIndent()

                configFile.writeText(configJson)
                prefs.selectedConfigPath = configFile.absolutePath

                if (cmd == "start") {
                    val startIntent = Intent(context, TProxyService::class.java).apply {
                        this.action = TProxyService.ACTION_CONNECT
                    }
                    ContextCompat.startForegroundService(context, startIntent)
                    Log.d(TAG, "Started TProxyService in ${prefs.tunnelMode.value} mode with config: ${configFile.absolutePath}")
                }
            }

            "stop" -> {
                val stopIntent = Intent(context, TProxyService::class.java).apply {
                    this.action = TProxyService.ACTION_DISCONNECT
                }
                ContextCompat.startForegroundService(context, stopIntent)
                Log.d(TAG, "Sent stop request to TProxyService.")
            }
        }
    }

    companion object {
        const val ACTION_BENCHMARK = "com.simplexray.re.action.BENCHMARK"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_BACKEND = "backend"
        const val EXTRA_MTU = "mtu"
        private const val TAG = "BenchmarkReceiver"
    }
}
