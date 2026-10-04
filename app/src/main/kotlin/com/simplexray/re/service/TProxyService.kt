package com.simplexray.re.service

import android.app.Service
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.IBinder
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.prefs.TunnelMode
import com.simplexray.re.data.source.LogFileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.Volatile

class TProxyService : VpnService() {
    private enum class NativeBackend {
        NONE,
        HEV,
        SING,
        SIMPLETUN
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nativeLifecycleLock = Any()
    private val notificationHelper by lazy { VpnNotificationHelper(this) }
    private val vpnWakeLock by lazy { VpnWakeLock(this) }
    private val geoUpdateScheduler by lazy { GeoUpdateScheduler(applicationContext) }
    private val socksHealthMonitor: SocksHealthMonitor by lazy {
        SocksHealthMonitor(
            applicationContext,
            serviceScope,
            object : SocksHealthMonitor.Target {
                override fun xrayPid(): Int = xrayProcessRunner.pid

                override fun isXrayReady(): Boolean = xrayProcessRunner.isStarted

                override fun isStopping(): Boolean = isStopping

                override fun hasStartupFailed(): Boolean = startupFailed

                override fun restartXray() {
                    if (isStopping || startupFailed) return
                    xrayProcessRunner.restart(serviceScope)
                }
            },
            TAG,
        )
    }



    private lateinit var logFileManager: LogFileManager

    private val xrayProcessRunner: XrayProcessRunner by lazy {
        XrayProcessRunner(
            applicationContext,
            logFileManager,
            spawn = { xrayPath, assetDir, vpnFd -> nativeSpawnXray(xrayPath, assetDir, vpnFd) },
            reap = { pid -> nativeReapChild(pid) },
            callbacks = object : XrayProcessRunner.Callbacks {
                override fun isStopping(): Boolean = isStopping

                override fun hasStartupFailed(): Boolean = startupFailed

                override fun hasVpnTunnel(): Boolean = tunFd != null

                override fun vpnFd(): Int? = tunFd?.fd

                override fun onCoreReady() {
                    VpnStateHub.updateState(VpnRunningState.Connected)
                    geoUpdateScheduler.start(serviceScope)
                    socksHealthMonitor.start()
                }

                override fun onStartFailure(reason: String) = failStart(reason)

                override fun onUnexpectedExit() = stopXray()

                override fun emitLog(line: String) {
                    VpnStateHub.emitLog(line)
                }
            },
            tag = TAG,
        )
    }

    private var isStopping = false

    @Volatile
    private var startupFailed = false

    private var tunFd: ParcelFileDescriptor? = null
    @Volatile
    private var activeBackend = NativeBackend.NONE
    private var goTunBinder: IGoTunBackend? = null
    private var goTunConnection: ServiceConnection? = null
    private var goTunGeneration = 0

    @Volatile
    private var reloadingRequested = false



    private val networkMonitor by lazy {
        VpnNetworkMonitor(this, TAG) { networks -> setUnderlyingNetworks(networks) }
    }

    private val isStartingLock = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        logFileManager = LogFileManager(this)
        Log.d(TAG, "TProxyService created.")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action
        when (action) {
            ACTION_DISCONNECT -> {
                VpnStateHub.updateState(VpnRunningState.Disconnected)
                stopXray()
                return START_NOT_STICKY
            }

            ACTION_RELOAD_CONFIG -> {
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
                    Log.d(TAG, "Received RELOAD_CONFIG action (core-only mode)")
                    reloadingRequested = true
                    xrayProcessRunner.restart(serviceScope)
                    return START_NOT_STICKY
                }
                if (tunFd == null) {
                    Log.w(TAG, "Cannot reload config, VPN service is not running.")
                    return START_NOT_STICKY
                }
                Log.d(TAG, "Received RELOAD_CONFIG action.")
                reloadingRequested = true
                xrayProcessRunner.restart(serviceScope)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
                    if (!acquireStart("ACTION_START")) {
                        return START_NOT_STICKY
                    }
                    VpnStateHub.updateState(VpnRunningState.Connecting)
                    logFileManager.clearLogs()
                    xrayProcessRunner.launch(serviceScope)

                    @Suppress("SameParameterValue") val channelName = "nosocks"
                    showForegroundNotification(channelName)

                } else {
                    startXray()
                }
                return START_NOT_STICKY
            }

            else -> {
                startXray()
                return START_NOT_STICKY
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isStartingLock.set(false)
        networkMonitor.stop()
        geoUpdateScheduler.stop()
        socksHealthMonitor.stop()
        serviceScope.cancel()
        xrayProcessRunner.kill()
        val pfd = tunFd
        tunFd = null
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            stopActiveBackend()
            pfd?.let { runCatching { it.close() } }
        }
        vpnWakeLock.release()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        if (!startupFailed) {
            VpnStateHub.updateState(VpnRunningState.Disconnected)
        }
        Log.d(TAG, "TProxyService destroyed.")
    }

    override fun onRevoke() {
        stopXray()
        super.onRevoke()
    }

    private fun startXray() {
        if (!acquireStart("startXray")) return
        @Suppress("SameParameterValue") val channelName = "socks5"
        showForegroundNotification(channelName)
        VpnStateHub.updateState(VpnRunningState.Connecting)
        vpnWakeLock.acquireIfEnabled()
        logFileManager.clearLogs()
        if (!startService()) {
            failStart("VPN service establishment failed")
            return
        }
        xrayProcessRunner.launch(serviceScope)
    }

    /**
     * Serializes normal start requests. A reload intentionally replaces the
     * current process and does not call this method.
     */
    private fun acquireStart(source: String): Boolean {
        if (!isStartingLock.compareAndSet(false, true)) {
            Log.d(TAG, "Ignoring duplicate start request from $source.")
            return false
        }
        isStopping = false
        startupFailed = false
        xrayProcessRunner.resetAttempt()
        return true
    }

    /**
     * Terminal transition for every startup failure that happens before Xray
     * process supervision is running. Without this, early returns left the UI
     * in Connecting and kept the start lock held.
     */
    private fun failStart(reason: String) {
        if (startupFailed) {
            Log.d(TAG, "Ignoring duplicate startup failure: $reason")
            return
        }
        startupFailed = true
        Log.e(TAG, "Xray startup failed: $reason")
        val failedState = VpnRunningState.Failed(
            applicationContext.getString(R.string.core_start_failed)
        )
        VpnStateHub.updateState(failedState)
        stopService(failedState)
    }







    /**
     * Replaces the Go-log timestamp prefix of an xray log line ("2006/01/02
     * 15:04:05.xxxxxx ") with the current device-local time. Lines without such
     * a prefix are returned unchanged.
     */



    private fun stopXray() {
        isStopping = true
        Log.d(TAG, "stopXray called with keepExecutorAlive=" + false)
        geoUpdateScheduler.stop()
        xrayProcessRunner.stop()
        Log.d(TAG, "xrayProcess reference nulled and killed.")

        Log.d(TAG, "Calling stopService (stopping VPN).")
        stopService()
    }

    private fun startService(): Boolean {
        if (tunFd != null) return true
        val prefs = Preferences(this)

        val selectedConfigPath = prefs.selectedConfigPath
        var tunMtu = prefs.tunnelMtu
        if (prefs.tunnelMode == TunnelMode.SimpleTun) {
            tunMtu = 1500
        } else if (prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn && selectedConfigPath != null) {
            val configFile = File(selectedConfigPath)
            if (configFile.exists()) {
                val configContent = runCatching { configFile.readText() }.getOrDefault("")
                val extractedMtu = ConfigUtils.extractTunMtu(configContent)
                if (extractedMtu != null) {
                    tunMtu = extractedMtu
                }
            }
        }
        val builder = VpnBuilderFactory.create(this, prefs, tunMtu)
        var establishAttempts = 0
        while (tunFd == null && establishAttempts < 3) {
            tunFd = builder.establish()
            if (tunFd == null) {
                establishAttempts++
                Log.w(TAG, "builder.establish() returned null, retrying ($establishAttempts/3)...")
                SystemClock.sleep(300)
            }
        }
        if (tunFd == null) {
            Log.e(TAG, "builder.establish() returned null after 3 attempts, stopping.")
            stopXray()
            return false
        }
        networkMonitor.start()

        if (prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn) {
            Log.d(TAG, "Using Xray Native TUN mode, skipping external tunnel.")
        } else if (prefs.tunnelMode == TunnelMode.SingTun && !prefs.disableVpn) {
            val fd = tunFd?.fd
            if (fd == null) {
                Log.e(TAG, "tunFd is null after establish()")
                stopXray()
                return false
            }
            Log.d(TAG, "Starting SingTUN backend on fd=$fd")
            val host = prefs.socksAddress.ifEmpty { "127.0.0.1" }
            val ok = synchronized(nativeLifecycleLock) {
                val started = startGoTunService(
                    serviceClass = SingTunService::class.java,
                    socksHost = host,
                    socksPort = prefs.socksPort,
                    mtu = tunMtu,
                    username = prefs.socksUsername,
                    password = prefs.socksPassword
                )
                if (started) {
                    activeBackend = NativeBackend.SING
                }
                started
            }
            if (!ok) {
                Log.e(TAG, "SingTunStartService failed")
                stopXray()
                return false
            }
        } else if (prefs.tunnelMode == TunnelMode.SimpleTun && !prefs.disableVpn) {
            val fd = tunFd?.fd
            if (fd == null) {
                Log.e(TAG, "tunFd is null after establish()")
                stopXray()
                return false
            }
            Log.d(TAG, "Starting SimpleTUN backend on fd=$fd")
            val host = prefs.socksAddress.ifEmpty { "127.0.0.1" }
            if (prefs.socksUsername.isNotEmpty() || prefs.socksPassword.isNotEmpty()) {
                Log.e(TAG, "SimpleTUN does not support authenticated SOCKS5 inbounds. Please clear credentials.")
                stopXray()
                return false
            }
            val isIpv4 = host.split('.').let { parts ->
                parts.size == 4 && parts.all { part ->
                    part.isNotEmpty() && part.length <= 3 && part.toIntOrNull() in 0..255
                }
            }
            if (!isIpv4) {
                Log.e(TAG, "SimpleTUN currently supports IPv4 dotted-decimal SOCKS5 inbounds only: $host")
                stopXray()
                return false
            }
            val result = synchronized(nativeLifecycleLock) {
                val res = runCatching {
                    SimpleTunNative.nativeStart(fd, host, prefs.socksPort)
                }.onFailure {
                    Log.e(TAG, "Failed to start SimpleTUN backend", it)
                }.getOrDefault(-1)
                if (res == 0) {
                    activeBackend = NativeBackend.SIMPLETUN
                }
                res
            }
            if (result != 0) {
                Log.e(TAG, "SimpleTUN nativeStart failed: $result")
                stopXray()
                return false
            }
        } else {
            val tproxyFile = File(cacheDir, "tproxy.conf")
            try {
                tproxyFile.createNewFile()
                FileOutputStream(tproxyFile, false).use { fos ->
                    val tproxyConf = TproxyConfigBuilder.build(prefs)
                    fos.write(tproxyConf.toByteArray())
                }
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
                stopXray()
                return false
            }

            val started = tunFd?.fd?.let { fd ->
                synchronized(nativeLifecycleLock) {
                    if (TProxyStartService(tproxyFile.absolutePath, fd)) {
                        activeBackend = NativeBackend.HEV
                        true
                    } else {
                        Log.e(TAG, "TProxyStartService failed")
                        false
                    }
                }
            } ?: run {
                Log.e(TAG, "tunFd is null after establish()")
                false
            }
            if (!started) {
                stopXray()
                return false
            }
        }

        @Suppress("SameParameterValue") val channelName = "socks5"
        showForegroundNotification(channelName)
        return true
    }

    private fun startGoTunService(
        serviceClass: Class<out GoTunService>,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        username: String,
        password: String
    ): Boolean {
        val generation = ++goTunGeneration
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (generation != goTunGeneration) {
                    Log.w(TAG, "Ignoring stale ${serviceClass.simpleName} connection")
                    runCatching { unbindService(this) }
                    return
                }
                val binder = IGoTunBackend.Stub.asInterface(service)
                goTunBinder = binder
                val fd = tunFd?.fileDescriptor
                if (fd == null) {
                    Log.e(TAG, "tunFd is null for ${serviceClass.simpleName}")
                    Handler(mainLooper).post {
                        if (generation == goTunGeneration) stopXray()
                    }
                    return
                }
                val pfd = runCatching {
                    ParcelFileDescriptor.dup(fd)
                }.getOrElse {
                    Log.e(TAG, "Failed to duplicate VPN fd for ${serviceClass.simpleName}", it)
                    Handler(mainLooper).post {
                        if (generation == goTunGeneration) stopXray()
                    }
                    return
                }
                val ok = runCatching {
                    binder.start(pfd, socksHost, socksPort, mtu, username, password)
                }.onFailure {
                    Log.e(TAG, "Failed to start ${serviceClass.simpleName} backend", it)
                }.getOrDefault(false)
                runCatching { pfd.close() }
                if (!ok) {
                    Log.e(TAG, "${serviceClass.simpleName} backend rejected start")
                    Handler(mainLooper).post {
                        if (generation == goTunGeneration) stopXray()
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (generation != goTunGeneration) return
                goTunBinder = null
                Log.w(TAG, "${serviceClass.simpleName} process disconnected")
                Handler(mainLooper).post {
                    if (generation == goTunGeneration && tunFd != null) stopXray()
                }
            }
        }
        goTunConnection?.let { runCatching { unbindService(it) } }
        goTunConnection = connection
        return runCatching {
            bindService(Intent(this, serviceClass), connection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            if (goTunConnection === connection) goTunConnection = null
            Log.e(TAG, "Failed to bind ${serviceClass.simpleName}", it)
        }.getOrDefault(false)
    }

    private fun stopService(finalState: VpnRunningState = VpnRunningState.Disconnected) {
        socksHealthMonitor.stop()
        isStartingLock.set(false)
        networkMonitor.stop()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        val pfd = tunFd
        tunFd = null
        stopActiveBackend()
        pfd?.let { runCatching { it.close() } }
        stopSelf()
        vpnWakeLock.release()
        exit(finalState)
    }

    /**
     * Stops exactly the backend that owns the current tun fd.
     */
    private fun stopActiveBackend() {
        synchronized(nativeLifecycleLock) {
            val backend = activeBackend
            activeBackend = NativeBackend.NONE
            val result = when (backend) {
                NativeBackend.HEV -> runCatching { TProxyStopService() }
                NativeBackend.SING -> runCatching {
                    goTunBinder?.stop() ?: true
                }
                NativeBackend.SIMPLETUN -> runCatching { SimpleTunNative.nativeStop() }
                NativeBackend.NONE -> return
            }
            goTunBinder = null
            goTunConnection?.let { connection ->
                runCatching { unbindService(connection) }
                goTunConnection = null
            }
            result.onSuccess {
                Log.d(TAG, "Stopped native backend $backend: ok=$it")
            }.onFailure {
                Log.w(TAG, "Failed to stop native backend $backend", it)
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun showForegroundNotification(channelName: String) {
        notificationHelper.ensureChannel(channelName)
        startForeground(
            1,
            notificationHelper.buildForegroundNotification(channelName),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun exit(finalState: VpnRunningState = VpnRunningState.Disconnected) {
        isStartingLock.set(false)
        VpnStateHub.updateState(finalState)
        stopSelf()
    }

    private external fun TProxyStartService(configPath: String, fd: Int): Boolean
    private external fun TProxyStopService(): Boolean
    private external fun TProxyIsRunning(): Boolean
    private external fun TProxyGetStats(): LongArray?

    companion object {
        const val ACTION_CONNECT: String = "com.simplexray.re.CONNECT"
        const val ACTION_DISCONNECT: String = "com.simplexray.re.DISCONNECT"
        const val ACTION_START: String = "com.simplexray.re.START"
        const val ACTION_RELOAD_CONFIG: String = "com.simplexray.re.RELOAD_CONFIG"
        private const val TAG = "TProxyService"

        init {
            try {
                System.loadLibrary("hev-socks5-tunnel")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load hev-socks5-tunnel library", e)
            }
            try {
                System.loadLibrary("xray-exec")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load xray-exec library", e)
            }
        }

        @JvmStatic
        private external fun nativeSpawnXray(xrayPath: String, assetDir: String, vpnFd: Int): IntArray?

        @JvmStatic
        private external fun nativeReapChild(pid: Int)

        fun getNativeLibraryDir(context: Context?): String? {
            if (context == null) {
                Log.e(TAG, "Context is null")
                return null
            }
            try {
                val applicationInfo = context.applicationInfo
                if (applicationInfo != null) {
                    val nativeLibraryDir = applicationInfo.nativeLibraryDir
                    Log.d(TAG, "Native Library Directory: $nativeLibraryDir")
                    return nativeLibraryDir
                } else {
                    Log.e(TAG, "ApplicationInfo is null")
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting native library dir", e)
                return null
            }
        }
    }
}
