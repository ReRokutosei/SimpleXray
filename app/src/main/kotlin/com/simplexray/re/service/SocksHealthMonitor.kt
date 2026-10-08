package com.simplexray.re.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Returns true when [SocksHealthMonitor] must not probe the local SOCKS
 * listener because the device is suspended and this app does not hold a wake
 * lock. The loopback listener is normally backed by the kernel, so a probe
 * failure in this state is not trustworthy evidence that Xray is unhealthy.
 */
internal fun shouldPauseSocksProbe(wakeLockHeld: Boolean, interactive: Boolean): Boolean =
    !wakeLockHeld && !interactive

internal class SocksHealthMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val target: Target,
    private val tag: String,
    private val shouldPauseProbe: () -> Boolean,
) {
    interface Target {
        fun xrayPid(): Int
        fun isXrayReady(): Boolean
        fun isStopping(): Boolean
        fun hasStartupFailed(): Boolean
        fun restartXray()
    }

    private var job: Job? = null
    private var failures = 0
    private var lastRecoveryMs = 0L
    private var pausedForSleep = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (job?.isActive == true) return
        failures = 0
        pausedForSleep = false
        job = scope.launch {
            while (isActive) {
                delay(HEALTH_INTERVAL_MS)
                if (target.isStopping() || target.hasStartupFailed() || !target.isXrayReady()) {
                    failures = 0
                    pausedForSleep = false
                    continue
                }

                if (shouldPauseProbe()) {
                    if (!pausedForSleep) {
                        pausedForSleep = true
                        Log.d(tag, "Pausing SOCKS health checks while sleeping without a wake lock.")
                    }
                    // Probe failures cannot be trusted while the device is
                    // suspended, and restarting Xray here turns a transient
                    // doze wake-up into a full service failure.
                    failures = 0
                    continue
                }
                if (pausedForSleep) {
                    pausedForSleep = false
                    Log.d(tag, "Resuming SOCKS health checks after device wake.")
                }

                if (probeSocksListener()) {
                    if (failures > 0) {
                        Log.d(tag, "SOCKS listener recovered after $failures failed probe(s).")
                    }
                    failures = 0
                    continue
                }

                failures++
                val pid = target.xrayPid()
                val processAlive = pid > 0 && runCatching {
                    Os.kill(pid, 0)
                    true
                }.getOrDefault(false)
                Log.w(
                    tag,
                    "SOCKS listener probe failed ($failures/$FAILURE_THRESHOLD): " +
                        "xrayPid=$pid processAlive=$processAlive xrayStarted=${target.isXrayReady()}"
                )
                if (failures >= FAILURE_THRESHOLD) {
                    failures = 0
                    recoverSocksListener(processAlive)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        failures = 0
        pausedForSleep = false
    }

    private fun probeSocksListener(): Boolean {
        val port = Preferences(context).socksPort
        return runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MS)
            }
            true
        }.getOrDefault(false)
    }

    private fun recoverSocksListener(processAlive: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoveryMs < RECOVERY_COOLDOWN_MS) {
            Log.w(tag, "Skipping SOCKS listener recovery; cooldown is active.")
            return
        }
        lastRecoveryMs = now
        Log.e(tag, "SOCKS listener unavailable; restarting Xray core (processAlive=$processAlive).")
        mainHandler.post {
            target.restartXray()
        }
    }

    companion object {
        private const val HEALTH_INTERVAL_MS: Long = 5_000
        private const val PROBE_TIMEOUT_MS: Int = 1_000
        private const val FAILURE_THRESHOLD: Int = 3
        private const val RECOVERY_COOLDOWN_MS: Long = 30_000
    }
}
