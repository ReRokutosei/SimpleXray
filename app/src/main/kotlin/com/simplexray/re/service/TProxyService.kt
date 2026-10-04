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
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.ConfigUtils.extractPortsFromJson
import com.simplexray.re.common.CoreStatsClient
import com.simplexray.re.prefs.TunnelMode
import com.simplexray.re.data.source.LogFileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val socksHealthMonitor by lazy {
        SocksHealthMonitor(
            applicationContext,
            serviceScope,
            object : SocksHealthMonitor.Target {
                override fun xrayPid(): Int = xrayPid

                override fun isXrayReady(): Boolean = xrayStarted

                override fun isStopping(): Boolean = isStopping

                override fun hasStartupFailed(): Boolean = startupFailed

                override fun restartXray() {
                    if (isStopping || startupFailed) return
                    killXrayProcess()
                    launchXrayProcess()
                }
            },
            TAG,
        )
    }

    private fun findAvailablePort(excludedPorts: Set<Int>): Int? {
        repeat(5) {
            val port = runCatching {
                ServerSocket(0).use { socket ->
                    socket.reuseAddress = true
                    socket.localPort
                }
            }.onFailure {
                Log.d(TAG, "Ephemeral port allocation failed: ${it.message}")
            }.getOrNull()
            if (port != null && port !in excludedPorts) {
                return port
            }
        }
        return null
    }

    private lateinit var logFileManager: LogFileManager

    @Volatile
    private var xrayProcess: Process? = null
    private var xrayPid: Int = -1
    private var xrayJob: Job? = null
    private var isStopping = false

    @Volatile
    private var startupFailed = false

    @Volatile
    private var xrayStarted = false
    private var xrayStartAttempt = 0
    private var tunFd: ParcelFileDescriptor? = null
    @Volatile
    private var activeBackend = NativeBackend.NONE
    private var goTunBinder: IGoTunBackend? = null
    private var goTunConnection: ServiceConnection? = null
    private var goTunGeneration = 0

    @Volatile
    private var reloadingRequested = false

    private fun launchXrayProcess() {
        xrayJob?.cancel()
        xrayJob = serviceScope.launch { runXrayProcess() }
    }

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
                    killXrayProcess()
                    launchXrayProcess()
                    return START_NOT_STICKY
                }
                if (tunFd == null) {
                    Log.w(TAG, "Cannot reload config, VPN service is not running.")
                    return START_NOT_STICKY
                }
                Log.d(TAG, "Received RELOAD_CONFIG action.")
                reloadingRequested = true
                killXrayProcess()
                launchXrayProcess()
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
                    launchXrayProcess()

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
        killXrayProcess()
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
        launchXrayProcess()
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
        xrayStartAttempt = 0
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

    private fun runXrayProcess() {
        xrayStarted = false
        var stdoutPfd: ParcelFileDescriptor? = null
        var currentProcess: Process? = null
        var currentPid = -1

        try {
            val prefs = Preferences(applicationContext)
            if (isStopping) {
                Log.d(TAG, "Aborting Xray process startup after intentional stop.")
                return
            }
            if (!prefs.disableVpn && tunFd == null) {
                failStart("VPN tunnel is not established")
                return
            }
            Log.d(TAG, "Attempting to start native Xray process with TUN fd & local gRPC API.")
            val libraryDir = getNativeLibraryDir(applicationContext)
            val selectedConfigPath = prefs.selectedConfigPath ?: run {
                failStart("No configuration file selected")
                return
            }
            val xrayPath = "$libraryDir/libxray.so"
            val configFile = File(selectedConfigPath)
            if (!configFile.exists()) {
                failStart("Selected config file does not exist: $selectedConfigPath")
                return
            }

            val rawConfigContent = runCatching { configFile.readText() }.getOrDefault("")
            Log.d(TAG, "Loaded raw user config: ${configFile.name}, ${rawConfigContent.length} chars")

            val sanitizedConfigContent = ConfigUtils.sanitizeConfig(rawConfigContent)

            val isYaml = configFile.extension.lowercase() in listOf("yaml", "yml")
            val format = "json"

            val ports = runCatching { extractPortsFromJson(sanitizedConfigContent) }.getOrDefault(emptySet())
            val apiPort = findAvailablePort(ports) ?: run {
                failStart("No local port available for the stats API")
                return
            }
            prefs.apiPort = apiPort
            prefs.apiAddress = "127.0.0.1"

            val finalConfigContent = ConfigUtils.injectStatsService(prefs, sanitizedConfigContent)
            Log.d(TAG, "Injected final config (${finalConfigContent.length} chars) ready for stdin ($format)")

            val useXrayTun = prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn
            val reader: BufferedReader

            if (useXrayTun) {
                val vpnFd = tunFd?.fd ?: run {
                    failStart("tunFd is null for Xray TUN mode")
                    return
                }
                val spawnResult = nativeSpawnXray(xrayPath, applicationContext.filesDir.path, vpnFd)
                    ?: run {
                        failStart("nativeSpawnXray returned null - spawn failed")
                        return
                    }
                currentPid = spawnResult[0]
                val stdoutReadFd = spawnResult[1]
                val stdinWriteFd = spawnResult[2]
                this.xrayPid = currentPid
                Log.d(TAG, "Xray TUN process started: pid=$currentPid")

                ParcelFileDescriptor.adoptFd(stdinWriteFd).use { pfd ->
                    ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                        out.write(finalConfigContent.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
                stdoutPfd = ParcelFileDescriptor.adoptFd(stdoutReadFd)
                reader = BufferedReader(
                    InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(stdoutPfd))
                )
            } else {
                val processBuilder = getProcessBuilder(xrayPath)
                currentProcess = processBuilder.start()
                this.xrayProcess = currentProcess
                Log.d(TAG, "Xray child process started successfully via ProcessBuilder.")

                currentProcess.outputStream.use { os ->
                    os.write(finalConfigContent.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                reader = BufferedReader(InputStreamReader(currentProcess.inputStream))
            }

            // Startup detection must not rely on stdout text: with
            // "loglevel": "none" xray prints nothing, so a text-based "started"
            // match never fires and the UI never learns the core is up. Probe the
            // injected StatsService gRPC API instead — reachable API == ready,
            // independent of log level.
            serviceScope.launch {
                // Capture this attempt's process: on retry xrayProcess points at a
                // newer process, and a stale probe must not claim success for it.
                val probeProcess = currentProcess
                val client = CoreStatsClient.create("127.0.0.1", prefs.apiPort)
                try {
                    val deadline = System.currentTimeMillis() + STARTUP_PROBE_TIMEOUT_MS
                    while (!xrayStarted &&
                        !startupFailed &&
                        !isStopping &&
                        (probeProcess?.isAlive == true || currentPid > 0) &&
                        System.currentTimeMillis() < deadline
                    ) {
                        if (client.getSystemStats() != null) {
                            if (startupFailed || isStopping) return@launch
                            xrayStarted = true
                            xrayStartAttempt = 0
                            Log.d(TAG, "Xray core ready (gRPC API reachable), updating VpnStateHub.")
                            VpnStateHub.updateState(VpnRunningState.Connected)
                            geoUpdateScheduler.start(serviceScope)
                            socksHealthMonitor.start()
                            break
                        }
                        delay(STARTUP_PROBE_INTERVAL_MS)
                    }
                } finally {
                    client.close()
                }
            }

            Log.d(TAG, "Reading native Xray process log stream.")
            var line = reader.readLine()
            while (line != null) {
                val batch = mutableListOf<String>()
                val stampedLine = stampLogLine(line)
                Log.d(TAG, "XrayLog: $stampedLine")
                batch.add(stampedLine)
                VpnStateHub.emitLog(stampedLine)

                // Drain any additional log lines buffered in the stream (up to 50 at a time)
                while (reader.ready() && batch.size < 50) {
                    val nextLine = reader.readLine() ?: break
                    val stampedNext = stampLogLine(nextLine)
                    Log.d(TAG, "XrayLog: $stampedNext")
                    batch.add(stampedNext)
                    VpnStateHub.emitLog(stampedNext)
                }

                logFileManager.appendLogs(batch)
                line = reader.readLine()
            }
            Log.d(TAG, "Native Xray process log stream finished.")
            if (currentProcess != null) {
                onXrayExited(currentProcess, -1)
            } else {
                onXrayExited(null, currentPid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing native Xray", e)
            if (currentProcess != null) {
                onXrayExited(currentProcess, -1)
            } else if (currentPid > 0) {
                onXrayExited(null, currentPid)
            } else {
                failStart("Error executing native Xray: ${e.message}")
            }
        } finally {
            stdoutPfd?.close()
            Log.d(TAG, "Native Xray process task finished.")
            if (currentProcess != null && this.xrayProcess === currentProcess) {
                this.xrayProcess = null
            }
            if (xrayPid > 0 && xrayPid == currentPid) {
                xrayPid = -1
            }
            if (currentPid > 0) {
                runCatching { nativeReapChild(currentPid) }
            }
        }
    }

    private fun onXrayExited(process: Process?, pid: Int) {
        if (pid > 0) {
            runCatching { nativeReapChild(pid) }
        }
        if (isStopping) {
            Log.d(TAG, "Xray process exited after intentional stop, ignoring.")
            return
        }
        if ((process != null && process !== xrayProcess) || (process == null && pid != xrayPid)) {
            Log.d(TAG, "Xray process superseded by a newer one, ignoring.")
            return
        }
        if (xrayStarted) {
            Log.e(TAG, "Xray process exited unexpectedly, stopping service.")
            stopXray()
            return
        }
        if (xrayStartAttempt < MAX_START_ATTEMPTS) {
            xrayStartAttempt++
            Log.w(TAG, "Xray failed to start, retrying (attempt $xrayStartAttempt/$MAX_START_ATTEMPTS).")
            launchXrayProcess()
        } else {
            failStart("Xray failed to start after $MAX_START_ATTEMPTS attempts")
        }
    }

    private fun getProcessBuilder(xrayPath: String): ProcessBuilder {
        val filesDir = applicationContext.filesDir
        val command = mutableListOf(xrayPath)
        val processBuilder = ProcessBuilder(command)
        val environment = processBuilder.environment()
        environment["XRAY_LOCATION_ASSET"] = filesDir.path
        processBuilder.directory(filesDir)
        processBuilder.redirectErrorStream(true)
        return processBuilder
    }

    /**
     * Replaces the Go-log timestamp prefix of an xray log line ("2006/01/02
     * 15:04:05.xxxxxx ") with the current device-local time. Lines without such
     * a prefix are returned unchanged.
     */
    private fun stampLogLine(line: String): String {
        val message = GO_LOG_TIMESTAMP_PREFIX.replaceFirst(line, "")
        return if (message === line) line else "${logTimestampFormat.format(Date())} $message"
    }
    private fun killXrayProcess() {
        xrayProcess?.destroy()
        xrayProcess = null
        val pid = xrayPid
        if (pid > 0) {
            xrayPid = -1
            try {
                Os.kill(pid, OsConstants.SIGKILL)
            } catch (e: ErrnoException) {
                Log.w(TAG, "Failed to kill xray pid $pid: ${e.message}")
            }
            runCatching { nativeReapChild(pid) }
        }
    }

    private fun stopXray() {
        isStopping = true
        Log.d(TAG, "stopXray called with keepExecutorAlive=" + false)
        geoUpdateScheduler.stop()
        xrayJob?.cancel()
        xrayJob = null
        Log.d(TAG, "xrayJob cancelled.")

        killXrayProcess()
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
        private const val MAX_START_ATTEMPTS = 2
        private const val STARTUP_PROBE_TIMEOUT_MS: Long = 15000
        private const val STARTUP_PROBE_INTERVAL_MS: Long = 500
        private val GO_LOG_TIMESTAMP_PREFIX = Regex("""^\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}(\.\d+)? """)
        private val logTimestampFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US)

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
