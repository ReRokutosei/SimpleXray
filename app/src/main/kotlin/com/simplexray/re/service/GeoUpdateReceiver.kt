package com.simplexray.re.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simplexray.re.data.source.FileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * BroadcastReceiver triggered by AlarmManager for automatic geo rule file updates.
 * Downloads geoip.dat and geosite.dat, validates with shadow sandbox, atomically replaces.
 */
class GeoUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GEO_UPDATE) return
        Log.d(TAG, "GeoUpdateReceiver triggered, starting background update...")

        val prefs = Preferences(context)
        val fileManager = FileManager(context.applicationContext as android.app.Application, prefs)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val isServiceRunning = TProxyService::class.java.let { cls ->
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                @Suppress("DEPRECATION")
                manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == cls.name }
            }

            val proxy = if (isServiceRunning)
                Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort))
            else Proxy.NO_PROXY

            val client = OkHttpClient.Builder().proxy(proxy).build()

            val standardTargets = listOf(
                prefs.geoipUrl to "geoip.dat",
                prefs.geositeUrl to "geosite.dat"
            )
            val customTargets = prefs.customDatUrls
                .filter { (_, url) -> url.isNotBlank() }
                .map { (fileName, url) -> url to fileName }

            (standardTargets + customTargets).forEach { (url, fileName) ->
                if (url.isBlank()) return@forEach
                try {
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Auto-update failed for $fileName: HTTP ${response.code}")
                        return@forEach
                    }
                    val body = response.body ?: return@forEach
                    val tempFile = File(context.filesDir, "$fileName.autoupdate.tmp")
                    FileOutputStream(tempFile).use { body.byteStream().copyTo(it) }
                    val success = fileManager.saveRuleFileFromTemp(tempFile, fileName)
                    Log.d(TAG, "Auto-update $fileName: ${if (success) "SUCCESS" else "FAILED (validation)"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-update error for $fileName", e)
                }
            }
        }
    }

    companion object {
        const val ACTION_GEO_UPDATE = "com.simplexray.re.action.GEO_UPDATE"
        private const val TAG = "GeoUpdateReceiver"
        private const val REQUEST_CODE = 0x6E30

        fun schedule(context: Context, intervalHours: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, GeoUpdateReceiver::class.java).apply {
                action = ACTION_GEO_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)

            if (intervalHours <= 0) {
                Log.d(TAG, "Geo auto-update disabled (interval=0).")
                return
            }

            val intervalMs = intervalHours * 60L * 60L * 1000L
            val triggerAtMs = System.currentTimeMillis() + intervalMs

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                intervalMs,
                pendingIntent
            )
            Log.d(TAG, "Geo auto-update scheduled every $intervalHours hour(s).")
        }

        fun cancel(context: Context) {
            schedule(context, 0)
        }
    }
}
