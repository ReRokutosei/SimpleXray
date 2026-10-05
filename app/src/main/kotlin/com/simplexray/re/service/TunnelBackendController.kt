package com.simplexray.re.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log

internal class TunnelBackendController(
    private val context: Context,
    private val startHev: (tproxyConfigPath: String, fd: Int) -> Boolean,
    private val stopHev: () -> Boolean,
    private val vpnFd: () -> ParcelFileDescriptor?,
    private val onBackendFailure: () -> Unit,
    private val tag: String,
) {
    internal enum class NativeBackend {
        NONE,
        HEV,
        SING,
        SIMPLETUN
    }

    private val lifecycleLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeBackend = NativeBackend.NONE

    private var goTunBinder: IGoTunBackend? = null
    private var goTunConnection: ServiceConnection? = null
    private var goTunGeneration = 0

    fun startHevBackend(tproxyConfigPath: String, fd: Int): Boolean {
        synchronized(lifecycleLock) {
            // Invalidate any pending SingTUN ServiceConnection callback: a delayed
            // onServiceConnected must not start a second engine on this fd.
            goTunGeneration++
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
            goTunGeneration++
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

    fun startSing(
        serviceClass: Class<out GoTunService>,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        username: String,
        password: String,
    ): Boolean {
        synchronized(lifecycleLock) {
            val generation = ++goTunGeneration
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (generation != goTunGeneration || activeBackend != NativeBackend.SING) {
                        Log.w(tag, "Ignoring stale ${serviceClass.simpleName} connection")
                        runCatching { context.unbindService(this) }
                        return
                    }

                    val binder = IGoTunBackend.Stub.asInterface(service)
                    goTunBinder = binder
                    val fd = vpnFd()?.fileDescriptor
                    if (fd == null) {
                        Log.e(tag, "tunFd is null for ${serviceClass.simpleName}")
                        mainHandler.post {
                            if (generation == goTunGeneration) onBackendFailure()
                        }
                        return
                    }

                    val pfd = runCatching {
                        ParcelFileDescriptor.dup(fd)
                    }.getOrElse {
                        Log.e(tag, "Failed to duplicate VPN fd for ${serviceClass.simpleName}", it)
                        mainHandler.post {
                            if (generation == goTunGeneration) onBackendFailure()
                        }
                        return
                    }

                    val ok = runCatching {
                        binder.start(pfd, socksHost, socksPort, mtu, username, password)
                    }.onFailure {
                        Log.e(tag, "Failed to start ${serviceClass.simpleName} backend", it)
                    }.getOrDefault(false)
                    runCatching { pfd.close() }

                    if (!ok) {
                        Log.e(tag, "${serviceClass.simpleName} backend rejected start")
                        mainHandler.post {
                            if (generation == goTunGeneration) onBackendFailure()
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    if (generation != goTunGeneration || activeBackend != NativeBackend.SING) return
                    goTunBinder = null
                    Log.w(tag, "${serviceClass.simpleName} process disconnected")
                    mainHandler.post {
                        if (generation == goTunGeneration && vpnFd() != null) onBackendFailure()
                    }
                }
            }

            goTunConnection?.let { runCatching { context.unbindService(it) } }
            goTunConnection = connection
            val bound = runCatching {
                context.bindService(Intent(context, serviceClass), connection, Context.BIND_AUTO_CREATE)
            }.onFailure {
                if (goTunConnection === connection) goTunConnection = null
                Log.e(tag, "Failed to bind ${serviceClass.simpleName}", it)
            }.getOrDefault(false)
            if (bound) {
                activeBackend = NativeBackend.SING
            }
            return bound
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            // Bump before unbinding: unbindService is asynchronous and a queued
            // onServiceConnected callback would otherwise still pass the
            // generation check and run a backend after the service stopped.
            goTunGeneration++
            val backend = activeBackend
            activeBackend = NativeBackend.NONE
            val result: Result<Boolean>? = when (backend) {
                NativeBackend.HEV -> runCatching { stopHev() }
                NativeBackend.SING -> runCatching {
                    goTunBinder?.stop() ?: true
                }
                NativeBackend.SIMPLETUN -> runCatching { SimpleTunNative.nativeStop() == 0 }
                NativeBackend.NONE -> null
            }
            goTunBinder = null
            goTunConnection?.let { connection ->
                runCatching { context.unbindService(connection) }
                goTunConnection = null
            }
            result?.onSuccess {
                Log.d(tag, "Stopped native backend $backend: ok=$it")
            }?.onFailure {
                Log.w(tag, "Failed to stop native backend $backend", it)
            }
        }
    }
}
