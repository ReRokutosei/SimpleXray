package com.simplexray.re.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.simplexray.re.R
import com.simplexray.re.data.source.FileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Jetpack WorkManager CoroutineWorker for reliable periodic geo rule file auto-updates.
 * Executes in background under network connectivity constraints, with Doze mode wake-up support.
 */
class GeoUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "GeoUpdateWorker started execution.")
        val success = performUpdate(context)
        if (success) {
            Log.d(TAG, "GeoUpdateWorker completed successfully.")
            Result.success()
        } else {
            Log.w(TAG, "GeoUpdateWorker completed with failures or was skipped.")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "GeoUpdateWorker"
        const val UNIQUE_WORK_NAME = "GeoRuleFilesAutoUpdate"
        private const val NOTIFICATION_ID_GEO_UPDATE = 0x6E31
        private const val CHANNEL_ID_GEO_UPDATE = "geo_update_channel"

        /**
         * Checks whether the local SOCKS proxy port is active and accepting connections.
         * Bypasses Android process isolation limits to detect if Xray core is running.
         */
        private fun isProxyAvailable(socksPort: Int): Boolean {
            return try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", socksPort), 300)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Checks whether the rule update has timed out based on lastGeoUpdateTime and geoUpdateIntervalHours.
         * Triggered only when Xray core is connected or periodically while active.
         */
        suspend fun checkAndTriggerCatchUp(context: Context) {
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
         * Returns true if any rule file was successfully updated, or false if errors occurred.
         */
        suspend fun performUpdate(context: Context, onComplete: ((Boolean) -> Unit)? = null): Boolean {
            return withContext(Dispatchers.IO) {
                val prefs = Preferences(context)
                val app = context.applicationContext as? android.app.Application ?: return@withContext false
                val fileManager = FileManager(app, prefs)

                val proxyAvailable = isProxyAvailable(prefs.socksPort)
                val proxy = if (proxyAvailable)
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort))
                else Proxy.NO_PROXY

                Log.d(TAG, "performUpdate running: proxyAvailable=$proxyAvailable, socksPort=${prefs.socksPort}")

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

                // Only post failure notification if proxy was active or if targets genuinely failed while reachable
                if (!anySuccess && hasFailure && proxyAvailable && allTargets.any { it.first.isNotBlank() }) {
                    showFailureNotification(context)
                }

                onComplete?.invoke(anySuccess)
                anySuccess
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

        /**
         * Schedules periodic rule updates via Jetpack WorkManager without immediately firing on registration.
         */
        fun schedule(context: Context, intervalHours: Int, forceUpdate: Boolean = false) {
            val workManager = WorkManager.getInstance(context)
            if (intervalHours <= 0) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                Log.d(TAG, "Geo auto-update cancelled via WorkManager.")
                return
            }

            val prefs = Preferences(context)
            val intervalMs = intervalHours * 60L * 60L * 1000L
            val now = System.currentTimeMillis()
            val lastUpdate = prefs.lastGeoUpdateTime
            val elapsed = if (lastUpdate > 0L && now >= lastUpdate) now - lastUpdate else 0L
            val initialDelayMs = if (elapsed >= intervalMs) 0L else (intervalMs - elapsed)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<GeoUpdateWorker>(
                intervalHours.toLong(),
                TimeUnit.HOURS
            )
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                policy,
                workRequest
            )
            Log.d(TAG, "Geo auto-update scheduled via WorkManager: every $intervalHours hr(s), first in ${initialDelayMs / 1000 / 60} min(s).")
        }

        fun cancel(context: Context) {
            schedule(context, 0)
        }
    }
}
