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

internal class SocksHealthMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val target: Target,
    private val tag: String,
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
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (job?.isActive == true) return
        failures = 0
        job = scope.launch {
            while (isActive) {
                delay(HEALTH_INTERVAL_MS)
                if (target.isStopping() || target.hasStartupFailed() || !target.isXrayReady()) {
                    failures = 0
                    continue
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
