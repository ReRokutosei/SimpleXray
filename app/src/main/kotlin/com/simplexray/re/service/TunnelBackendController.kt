package com.simplexray.re.service

import android.util.Log

internal class TunnelBackendController(
    private val startHev: (tproxyConfigPath: String, fd: Int) -> Boolean,
    private val stopHev: () -> Boolean,
    private val tag: String,
) {
    internal enum class NativeBackend {
        NONE,
        HEV,
        SIMPLETUN
    }

    private val lifecycleLock = Any()

    @Volatile
    private var activeBackend = NativeBackend.NONE

    fun startHevBackend(tproxyConfigPath: String, fd: Int): Boolean {
        synchronized(lifecycleLock) {
            val started = runCatching {
                startHev(tproxyConfigPath, fd)
            }.onFailure {
                Log.e(tag, "Failed to start HEV backend", it)
            }.getOrDefault(false)
            if (started) {
                activeBackend = NativeBackend.HEV
            } else {
                Log.e(tag, "TProxyStartService failed")
            }
            return started
        }
    }

    fun startSimpleTun(fd: Int, host: String, port: Int): Int {
        synchronized(lifecycleLock) {
            val result = runCatching {
                SimpleTunNative.nativeStart(fd, host, port)
            }.onFailure {
                Log.e(tag, "Failed to start SimpleTUN backend", it)
            }.getOrDefault(-1)
            if (result == 0) {
                activeBackend = NativeBackend.SIMPLETUN
            }
            return result
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            val backend = activeBackend
            activeBackend = NativeBackend.NONE
            val result: Result<Boolean>? = when (backend) {
                NativeBackend.HEV -> runCatching { stopHev() }
                NativeBackend.SIMPLETUN -> runCatching { SimpleTunNative.nativeStop() == 0 }
                NativeBackend.NONE -> null
            }
            result?.onSuccess {
                Log.d(tag, "Stopped native backend $backend: ok=$it")
            }?.onFailure {
                Log.w(tag, "Failed to stop native backend $backend", it)
            }
        }
    }
}

