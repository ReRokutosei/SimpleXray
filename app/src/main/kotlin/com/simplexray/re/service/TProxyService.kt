package com.simplexray.re.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.isLoopbackAddress
import com.simplexray.re.prefs.TunnelMode
import com.simplexray.re.data.source.LogFileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.Volatile

class TProxyService : VpnService() {


    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationHelper by lazy { VpnNotificationHelper(this) }
    private val vpnWakeLock by lazy { VpnWakeLock(this) }
    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as? PowerManager }
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
            shouldPauseProbe = {
                shouldPauseSocksProbe(
                    wakeLockHeld = vpnWakeLock.isHeld,
                    interactive = powerManager?.isInteractive ?: true,
                )
            },
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

    private val tunnelBackendController: TunnelBackendController by lazy {
        TunnelBackendController(
            startHev = { path, fd -> TProxyStartService(path, fd) },
            stopHev = { TProxyStopService() },
            tag = TAG,
        )
    }

    private var isStopping = false

    @Volatile
    private var startupFailed = false

    @Volatile
    private var stopRequestedByUser = false

    @Volatile
    private var pendingStartFailure: String? = null

    private var tunFd: ParcelFileDescriptor? = null



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
                stopRequestedByUser = true
                VpnStateHub.updateState(VpnRunningState.Disconnected)
                stopXray()
                return START_NOT_STICKY
            }

            ACTION_RELOAD_CONFIG -> {
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
                    Log.d(TAG, "Received RELOAD_CONFIG action (core-only mode)")
                    xrayProcessRunner.restart(serviceScope)
                    return START_NOT_STICKY
                }
                if (tunFd == null) {
                    Log.w(TAG, "Cannot reload config, VPN service is not running.")
                    return START_NOT_STICKY
                }
                Log.d(TAG, "Received RELOAD_CONFIG action.")
                xrayProcessRunner.restart(serviceScope)
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
        // Block briefly so native backends and the VPN fd are released before
        // the process can be torn down; the old fire-and-forget scope could be
        // killed before teardown ran.
        runBlocking {
            withTimeoutOrNull(TEARDOWN_TIMEOUT_MS) {
                tunnelBackendController.stop()
                pfd?.let { runCatching { it.close() } }
            }
        }
        vpnWakeLock.release()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        if (!startupFailed) {
            VpnStateHub.updateState(VpnRunningState.Disconnected)
        }
        Log.d(TAG, "TProxyService destroyed.")
    }

    override fun onRevoke() {
        stopRequestedByUser = true
        stopXray()
        super.onRevoke()
    }

    private fun startXray() {
        if (!acquireStart("startXray")) return
        val prefs = Preferences(this)
        val coreOnly = prefs.disableVpn
        @Suppress("SameParameterValue") val channelName = if (coreOnly) "nosocks" else "socks5"
        showForegroundNotification(channelName)
        validateStartPreferences(prefs)?.let { reason ->
            failStart(reason)
            return
        }
        VpnStateHub.updateState(VpnRunningState.Connecting)
        vpnWakeLock.acquireIfEnabled()
        logFileManager.clearLogs()

        if (coreOnly) {
            xrayProcessRunner.launch(serviceScope)
            return
        }

        serviceScope.launch {
            val established = startService()
            if (stopRequestedByUser) {
                Log.d(TAG, "Startup aborted by an explicit user stop request.")
                return@launch
            }
            if (!established) {
                val reason = pendingStartFailure ?: getString(R.string.core_start_failed)
                withContext(Dispatchers.Main) {
                    failStart(reason)
                }
                return@launch
            }
            if (startupFailed || isStopping) return@launch
            xrayProcessRunner.launch(serviceScope)
        }
    }

    /**
     * Rejects persisted legacy values that cannot work with the local data
     * plane: non-loopback SOCKS binds (health monitor/downloads assume
     * 127.0.0.1) and one-sided credentials (all tunnel backends need both or
     * neither). Returns a user-facing reason, or null when the preferences are
     * valid.
     */
    private fun validateStartPreferences(prefs: Preferences): String? {
        val address = prefs.socksAddress
        if (address.isBlank() || !isLoopbackAddress(address)) {
            return getString(R.string.socks_address_loopback_only)
        }
        val hasUser = prefs.socksUsername.isNotEmpty()
        val hasPass = prefs.socksPassword.isNotEmpty()
        if (hasUser != hasPass) {
            return getString(R.string.socks_credentials_pair_required)
        }
        if (prefs.socksUsername.contains('\n') || prefs.socksUsername.contains('\r') ||
            prefs.socksPassword.contains('\n') || prefs.socksPassword.contains('\r')
        ) {
            return getString(R.string.socks_credentials_no_line_breaks)
        }
        return null
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
        stopRequestedByUser = false
        pendingStartFailure = null
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

    private suspend fun startService(): Boolean {
        if (tunFd != null) return true
        if (stopRequestedByUser) return false
        val prefs = Preferences(this)

        val selectedConfigPath = prefs.selectedConfigPath
        var tunMtu = prefs.tunnelMtu

        // Read the selected config once when the VPN setup needs raw values from
        // it: the XrayTun MTU and/or an existing HTTP inbound port. The full
        // runtime sanitization happens later in XrayProcessRunner.
        val needsRawConfig = prefs.httpProxyEnabled ||
            (prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn)
        val rawConfigContent = if (needsRawConfig && selectedConfigPath != null) {
            val configFile = File(selectedConfigPath)
            if (configFile.exists()) {
                runCatching { configFile.readText() }.getOrDefault("")
            } else {
                null
            }
        } else {
            null
        }

        if (prefs.tunnelMode == TunnelMode.SimpleTun) {
            tunMtu = 1500
        } else if (prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn) {
            rawConfigContent?.let { content ->
                ConfigUtils.extractTunMtu(content)?.let { tunMtu = it }
            }
        }

        val httpProxyEndpoint = if (prefs.httpProxyEnabled) {
            rawConfigContent?.let { ConfigUtils.extractHttpProxyEndpoint(it) }
        } else {
            null
        }

        val builder = VpnBuilderFactory.create(this, prefs, tunMtu, httpProxyEndpoint)
        var establishAttempts = 0
        while (tunFd == null && establishAttempts < 3 && !stopRequestedByUser) {
            tunFd = builder.establish()
            if (tunFd == null) {
                establishAttempts++
                Log.w(TAG, "builder.establish() returned null, retrying ($establishAttempts/3)...")
                delay(300)
            }
        }
        if (tunFd == null) {
            Log.e(TAG, "builder.establish() returned null after 3 attempts, stopping.")
            pendingStartFailure = getString(R.string.vpn_establish_failed)
            stopXray()
            return false
        }
        if (stopRequestedByUser) {
            val establishedFd = tunFd
            tunFd = null
            establishedFd?.let { runCatching { it.close() } }
            return false
        }
        networkMonitor.start()

        if (prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn) {
            Log.d(TAG, "Using Xray Native TUN mode, skipping external tunnel.")
        } else if (prefs.tunnelMode == TunnelMode.SimpleTun && !prefs.disableVpn) {
            val fd = tunFd?.fd
            if (fd == null) {
                Log.e(TAG, "tunFd is null after establish()")
                pendingStartFailure = getString(R.string.vpn_establish_failed)
                stopXray()
                return false
            }
            Log.d(TAG, "Starting SimpleTUN backend on fd=$fd")
            val host = prefs.socksAddress.ifEmpty { "127.0.0.1" }
            if (prefs.socksUsername.isNotEmpty() || prefs.socksPassword.isNotEmpty()) {
                // ConfigSanitizer forces the primary SOCKS inbound to no-auth in
                // SimpleTUN mode; the backend has no auth path, so stored
                // credentials are simply ignored instead of failing startup.
                Log.w(TAG, "SimpleTUN ignores configured SOCKS credentials (no-auth mode).")
            }
            val isIpv4 = host.split('.').let { parts ->
                parts.size == 4 && parts.all { part ->
                    part.isNotEmpty() && part.length <= 3 && part.toIntOrNull() in 0..255
                }
            }
            if (!isIpv4) {
                Log.e(TAG, "SimpleTUN currently supports IPv4 dotted-decimal SOCKS5 inbounds only: $host")
                pendingStartFailure = getString(R.string.backend_start_failed, TunnelMode.SimpleTun.displayName)
                stopXray()
                return false
            }
            val result = tunnelBackendController.startSimpleTun(fd, host, prefs.socksPort)
            if (result != 0) {
                Log.e(TAG, "SimpleTUN nativeStart failed: $result")
                pendingStartFailure = getString(R.string.backend_start_failed, TunnelMode.SimpleTun.displayName)
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
                pendingStartFailure = getString(R.string.tproxy_config_failed)
                stopXray()
                return false
            }

            val started = tunFd?.fd?.let { fd ->
                tunnelBackendController.startHevBackend(tproxyFile.absolutePath, fd)
            } ?: run {
                Log.e(TAG, "tunFd is null after establish()")
                false
            }
            if (!started) {
                pendingStartFailure = getString(R.string.backend_start_failed, TunnelMode.HevSocks5Tunnel.displayName)
                stopXray()
                return false
            }
        }

        @Suppress("SameParameterValue") val channelName = "socks5"
        showForegroundNotification(channelName)
        return true
    }



    private fun stopService(finalState: VpnRunningState = VpnRunningState.Disconnected) {
        socksHealthMonitor.stop()
        isStartingLock.set(false)
        networkMonitor.stop()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        val pfd = tunFd
        tunFd = null
        tunnelBackendController.stop()
        pfd?.let { runCatching { it.close() } }
        stopSelf()
        vpnWakeLock.release()
        exit(finalState)
    }

    /**
     * Stops exactly the backend that owns the current tun fd.
     */


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
        private const val TEARDOWN_TIMEOUT_MS: Long = 3_000L

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
