package com.simplexray.re.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simplexray.re.R
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
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver triggered by AlarmManager or Boot for automatic geo rule file updates.
 * Downloads standard and third-party rule files, validates, and atomically replaces.
 */
class GeoUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "GeoUpdateReceiver received action: $action")

        val prefs = Preferences(context)
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Re-register alarm on reboot / update
            if (prefs.geoUpdateIntervalHours > 0) {
                schedule(context, prefs.geoUpdateIntervalHours)
            }
            return
        }

        if (action == ACTION_GEO_UPDATE) {
            performUpdate(context)
        }
    }

    companion object {
        const val ACTION_GEO_UPDATE = "com.simplexray.re.action.GEO_UPDATE"
        private const val TAG = "GeoUpdateReceiver"
        private const val REQUEST_CODE = 0x6E30
        private const val NOTIFICATION_ID_GEO_UPDATE = 0x6E31
        private const val CHANNEL_ID_GEO_UPDATE = "geo_update_channel"

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Checks whether the rule update has timed out based on lastGeoUpdateTime and geoUpdateIntervalHours.
         * If timed out and interval > 0, triggers a silent background update.
         */
        fun checkAndTriggerCatchUp(context: Context) {
            val prefs = Preferences(context)
            val intervalHours = prefs.geoUpdateIntervalHours
            if (intervalHours <= 0) return

            val intervalMs = intervalHours * 60L * 60L * 1000L
            val now = System.currentTimeMillis()
            val lastUpdate = prefs.lastGeoUpdateTime

            if (lastUpdate == 0L || (now - lastUpdate) >= intervalMs) {
                Log.d(TAG, "Catch-up rule update triggered: lastUpdate=$lastUpdate, now=$now, intervalMs=$intervalMs")
                performUpdate(context)
            }
        }

        /**
         * Performs the rule files update in background.
         */
        fun performUpdate(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
            receiverScope.launch {
                val prefs = Preferences(context)
                val fileManager = FileManager(context.applicationContext as android.app.Application, prefs)

                val isServiceRunning = TProxyService::class.java.let { cls ->
                    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                    @Suppress("DEPRECATION")
                    manager?.getRunningServices(Int.MAX_VALUE)?.any { it.service.className == cls.name } == true
                }

                val proxy = if (isServiceRunning)
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort))
                else Proxy.NO_PROXY

                val client = OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val standardTargets = listOf(
                    prefs.geoipUrl to "geoip.dat",
                    prefs.geositeUrl to "geosite.dat"
                )
                val customTargets = prefs.customDatUrls
                    .filter { (_, url) -> url.isNotBlank() }
                    .map { (fileName, url) -> url to fileName }

                val allTargets = standardTargets + customTargets
                var anySuccess = false
                var hasFailure = false

                allTargets.forEach { (url, fileName) ->
                    if (url.isBlank()) return@forEach
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Auto-update failed for $fileName: HTTP ${response.code}")
                            hasFailure = true
                            return@forEach
                        }
                        val body = response.body
                        val tempFile = File(context.filesDir, "$fileName.autoupdate.tmp")
                        FileOutputStream(tempFile).use { body.byteStream().copyTo(it) }
                        val success = fileManager.saveRuleFileFromTemp(tempFile, fileName)
                        if (success) {
                            anySuccess = true
                            Log.d(TAG, "Auto-update $fileName: SUCCESS")
                        } else {
                            hasFailure = true
                            Log.w(TAG, "Auto-update $fileName: FAILED (validation)")
                        }
                    } catch (e: Exception) {
                        hasFailure = true
                        Log.e(TAG, "Auto-update error for $fileName", e)
                    }
                }

                if (anySuccess) {
                    prefs.lastGeoUpdateTime = System.currentTimeMillis()
                }

                // If all targets failed and we had valid targets, post a low-priority dismissible notification
                if (!anySuccess && hasFailure && allTargets.any { it.first.isNotBlank() }) {
                    showFailureNotification(context)
                }

                onComplete?.invoke(anySuccess)
            }
        }

        private fun showFailureNotification(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID_GEO_UPDATE,
                        context.getString(R.string.rule_files_category_title),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.geo_update_interval_title)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val notification = NotificationCompat.Builder(context, CHANNEL_ID_GEO_UPDATE)
                    .setSmallIcon(R.drawable.ic_stat_lineal)
                    .setContentTitle(context.getString(R.string.geo_update_failed_title))
                    .setContentText(context.getString(R.string.geo_update_failed_body))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(NOTIFICATION_ID_GEO_UPDATE, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show auto-update failure notification", e)
            }
        }

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

            val prefs = Preferences(context)
            val intervalMs = intervalHours * 60L * 60L * 1000L
            val now = System.currentTimeMillis()
            val lastUpdate = prefs.lastGeoUpdateTime
            val elapsed = if (lastUpdate > 0L && now >= lastUpdate) now - lastUpdate else 0L
            val initialDelayMs = if (elapsed >= intervalMs) 0L else (intervalMs - elapsed)
            val triggerAtMs = now + initialDelayMs

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                intervalMs,
                pendingIntent
            )
            Log.d(TAG, "Geo auto-update scheduled: every $intervalHours hr(s), first in ${initialDelayMs / 1000 / 60} min(s).")
        }

        fun cancel(context: Context) {
            schedule(context, 0)
        }
    }
}
