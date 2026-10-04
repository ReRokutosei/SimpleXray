package com.simplexray.re.service

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.simplexray.re.prefs.Preferences

internal class VpnWakeLock(private val context: Context) {
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquireIfEnabled() {
        val prefs = Preferences(context)
        if (!prefs.keepAwake || wakeLock != null) return

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SimpleXray:WakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
        if (wakeLock != null) {
            Log.d(TAG, "Partial wake lock acquired.")
        }
    }

    fun release() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Partial wake lock released.")
            }
        }
        wakeLock = null
    }

    companion object {
        private const val TAG = "VpnWakeLock"
    }
}
