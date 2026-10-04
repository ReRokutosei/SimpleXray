package com.simplexray.re.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.simplexray.re.R
import com.simplexray.re.activity.MainActivity

internal class VpnNotificationHelper(private val context: Context) {
    fun ensureChannel(channelName: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name: CharSequence = context.getString(R.string.app_name)
        val channel =
            NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(channelName: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, channelName)
            .setContentTitle(context.getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_stat_lineal)
            .setContentIntent(pendingIntent)
            .build()
    }
}
