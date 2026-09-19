package com.simplexray.re.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simplexray.re.BuildConfig
import com.simplexray.re.R
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.prefs.TunnelMode
import java.io.File

class BenchmarkService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) {
            stopSelf()
            return
        }
        val channelId = "benchmark_service_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Benchmark Service", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SimpleXray Headless Benchmark")
            .setContentText("Running headless benchmark test...")
            .setSmallIcon(R.drawable.ic_stat_lineal)
            .setOngoing(true)
            .build()
        startForeground(9999, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        Log.d(TAG, "BenchmarkService created and entered foreground.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.DEBUG || intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val cmd = intent.getStringExtra(EXTRA_CMD) ?: "status"
        val backend = intent.getStringExtra(EXTRA_BACKEND)
        val mtu = intent.getIntExtra(EXTRA_MTU, 1500)

        Log.d(TAG, "BenchmarkService onStartCommand: cmd=$cmd, backend=$backend, mtu=$mtu")

        val prefs = Preferences(applicationContext)

        when (cmd) {
            "start" -> {
                if (backend != null) {
                    when (backend.lowercase()) {
                        "hev", "hev_socks5_tunnel" -> prefs.tunnelMode = TunnelMode.HevSocks5Tunnel
                        "xray", "xray_tun" -> prefs.tunnelMode = TunnelMode.XrayTun
                        "sing", "sing_tun", "singtun" -> prefs.tunnelMode = TunnelMode.SingTun
                        "mips", "mips_tun", "mipstun" -> prefs.tunnelMode = TunnelMode.MipsTun
                    }
                }
                prefs.tunnelMtu = mtu
                prefs.disableVpn = false
                prefs.bypassLan = false
                prefs.global = true

                val configFile = File(filesDir, "benchmark_direct.json")
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
        "udp": true,
        "ip": "127.0.0.1"
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

                val vpnIntent = Intent(this, TProxyService::class.java).apply {
                    action = TProxyService.ACTION_CONNECT
                }
                startService(vpnIntent)
                Log.d(TAG, "BenchmarkService launched TProxyService with backend ${prefs.tunnelMode.value}")
            }

            "stop" -> {
                val vpnIntent = Intent(this, TProxyService::class.java).apply {
                    action = TProxyService.ACTION_DISCONNECT
                }
                startService(vpnIntent)
                Log.d(TAG, "BenchmarkService requested TProxyService disconnect, stopping self.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "BenchmarkService destroyed.")
    }

    companion object {
        const val EXTRA_CMD = "cmd"
        const val EXTRA_BACKEND = "backend"
        const val EXTRA_MTU = "mtu"
        private const val TAG = "BenchmarkService"
    }
}
