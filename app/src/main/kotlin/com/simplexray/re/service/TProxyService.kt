package com.simplexray.re.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simplexray.re.BuildConfig
import com.simplexray.re.R
import com.simplexray.re.activity.MainActivity
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.ConfigUtils.extractPortsFromJson
import com.simplexray.re.common.CoreStatsClient
import com.simplexray.re.prefs.TunnelMode
import com.simplexray.re.data.source.LogFileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlin.system.exitProcess

class TProxyService : VpnService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val logBroadcastBuffer: MutableList<String> = mutableListOf()
    private val broadcastLogsRunnable = Runnable {
        synchronized(logBroadcastBuffer) {
            if (logBroadcastBuffer.isNotEmpty()) {
                val logUpdateIntent = Intent(ACTION_LOG_UPDATE)
                logUpdateIntent.setPackage(application.packageName)
                logUpdateIntent.putStringArrayListExtra(
                    EXTRA_LOG_DATA, ArrayList(logBroadcastBuffer)
                )
                sendBroadcast(logUpdateIntent)
                logBroadcastBuffer.clear()
                Log.d(TAG, "Broadcasted a batch of logs.")
            }
        }
    }

    private fun findAvailablePort(excludedPorts: Set<Int>): Int? {
        (10000..65535)
            .shuffled()
            .forEach { port ->
                if (port in excludedPorts) return@forEach
                runCatching {
                    ServerSocket(port).use { socket ->
                        socket.reuseAddress = true
                    }
                    port
                }.onFailure {
                    Log.d(TAG, "Port $port unavailable: ${it.message}")
                }.onSuccess {
                    return port
                }
            }
        return null
    }

    private lateinit var logFileManager: LogFileManager

    @Volatile
    private var xrayProcess: Process? = null
    private var xrayPid: Int = -1
    private var isStopping = false
    @Volatile
    private var xrayStarted = false
    private var xrayStartAttempt = 0
    private var tunFd: ParcelFileDescriptor? = null

    @Volatile
    private var reloadingRequested = false

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
                stopXray()
                return START_NOT_STICKY
            }

            ACTION_RELOAD_CONFIG -> {
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
                    Log.d(TAG, "Received RELOAD_CONFIG action (core-only mode)")
                    reloadingRequested = true
                    killXrayProcess()
                    serviceScope.launch { runXrayProcess() }
                    return START_NOT_STICKY
                }
                if (tunFd == null) {
                    Log.w(TAG, "Cannot reload config, VPN service is not running.")
                    return START_NOT_STICKY
                }
                Log.d(TAG, "Received RELOAD_CONFIG action.")
                reloadingRequested = true
                killXrayProcess()
                serviceScope.launch { runXrayProcess() }
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
                    if (!acquireStart("ACTION_START")) {
                        return START_NOT_STICKY
                    }
                    logFileManager.clearLogs()
                    serviceScope.launch { runXrayProcess() }
                    val successIntent = Intent(ACTION_START)
                    successIntent.setPackage(application.packageName)
                    sendBroadcast(successIntent)

                    @Suppress("SameParameterValue") val channelName = "nosocks"
                    initNotificationChannel(channelName)
                    createNotification(channelName)

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
        handler.removeCallbacks(broadcastLogsRunnable)
        broadcastLogsRunnable.run()
        serviceScope.cancel()
        tunFd?.let {
            runCatching { it.close() }
            tunFd = null
        }
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "TProxyService destroyed.")
        exitProcess(0)
    }

    override fun onRevoke() {
        stopXray()
        super.onRevoke()
    }

    private fun startXray() {
        if (!acquireStart("startXray")) return
        logFileManager.clearLogs()
        startService()
        serviceScope.launch { runXrayProcess() }
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
        xrayStartAttempt = 0
        return true
    }

    private fun runXrayProcess() {
        xrayStarted = false
        var stdoutPfd: ParcelFileDescriptor? = null
        var currentProcess: Process? = null
        var currentPid = -1

        try {
            Log.d(TAG, "Attempting to start native Xray process with TUN fd & local gRPC API.")
            val libraryDir = getNativeLibraryDir(applicationContext)
            val prefs = Preferences(applicationContext)
            val selectedConfigPath = prefs.selectedConfigPath ?: return
            val xrayPath = "$libraryDir/libxray.so"
            val configFile = File(selectedConfigPath)
            if (!configFile.exists()) {
                Log.e(TAG, "Selected config file does not exist: $selectedConfigPath")
                return
            }

            val rawConfigContent = runCatching { configFile.readText() }.getOrDefault("")
            Log.d(TAG, "Loaded raw user config: ${configFile.name}, ${rawConfigContent.length} chars")

            val sanitizedConfigContent = ConfigUtils.sanitizeConfig(rawConfigContent)

            val isYaml = configFile.extension.lowercase() in listOf("yaml", "yml")
            val format = "json"

            val ports = runCatching { extractPortsFromJson(sanitizedConfigContent) }.getOrDefault(emptySet())
            val apiPort = findAvailablePort(ports) ?: return
            prefs.apiPort = apiPort
            prefs.apiAddress = "127.0.0.1"

            val finalConfigContent = ConfigUtils.injectStatsService(prefs, sanitizedConfigContent)
            Log.d(TAG, "Injected final config (${finalConfigContent.length} chars) ready for stdin ($format)")

            val useXrayTun = prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn
            val reader: BufferedReader

            if (useXrayTun) {
                val vpnFd = tunFd?.fd ?: run {
                    Log.e(TAG, "tunFd is null for Xray TUN mode")
                    return
                }
                val spawnResult = nativeSpawnXray(xrayPath, applicationContext.filesDir.path, vpnFd)
                    ?: run {
                        Log.e(TAG, "nativeSpawnXray returned null - spawn failed")
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
                        (probeProcess?.isAlive == true || currentPid > 0) &&
                        System.currentTimeMillis() < deadline
                    ) {
                        if (client.getSystemStats() != null) {
                            xrayStarted = true
                            xrayStartAttempt = 0
                            Log.d(TAG, "Xray core ready (gRPC API reachable), broadcasting ACTION_START.")
                            val successIntent = Intent(ACTION_START)
                            successIntent.setPackage(application.packageName)
                            sendBroadcast(successIntent)
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
                Log.d(TAG, "XrayLog: $line")
                // xray (Go log) prepends "2006/01/02 15:04:05.xxxxxx " whose clock
                // may be UTC on Android. Replace it with the device-local time so
                // the log view is consistent (same approach as v2rayNG/MikuRay,
                // which stamp logs on the app side).
                val stampedLine = stampLogLine(line)
                logFileManager.appendLog(stampedLine)
                synchronized(logBroadcastBuffer) {
                    logBroadcastBuffer.add(stampedLine)
                    if (!handler.hasCallbacks(broadcastLogsRunnable)) {
                        handler.postDelayed(broadcastLogsRunnable, BROADCAST_DELAY_MS)
                    }
                }
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
        }
    }

    private fun onXrayExited(process: Process?, pid: Int) {
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
            exit()
            return
        }
        if (xrayStartAttempt < MAX_START_ATTEMPTS) {
            xrayStartAttempt++
            Log.w(TAG, "Xray failed to start, retrying (attempt $xrayStartAttempt/$MAX_START_ATTEMPTS).")
            serviceScope.launch { runXrayProcess() }
        } else {
            Log.e(TAG, "Xray failed to start after $MAX_START_ATTEMPTS attempts, stopping service.")
            val failIntent = Intent(ACTION_START_FAILED)
            failIntent.setPackage(application.packageName)
            sendBroadcast(failIntent)
            exit()
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
        }
    }

    private fun stopXray() {
        isStopping = true
        Log.d(TAG, "stopXray called with keepExecutorAlive=" + false)
        serviceScope.cancel()
        Log.d(TAG, "CoroutineScope cancelled.")

        killXrayProcess()
        Log.d(TAG, "xrayProcess reference nulled and killed.")

        Log.d(TAG, "Calling stopService (stopping VPN).")
        stopService()
    }

    private fun startService() {
        if (tunFd != null) return
        val prefs = Preferences(this)

        val selectedConfigPath = prefs.selectedConfigPath
        var tunMtu = if (prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn) prefs.tunnelMtuForXrayTun else prefs.tunnelMtu
        if (prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn && selectedConfigPath != null) {
            val configFile = File(selectedConfigPath)
            if (configFile.exists()) {
                val configContent = runCatching { configFile.readText() }.getOrDefault("")
                val extractedMtu = ConfigUtils.extractTunMtu(configContent)
                if (extractedMtu != null) {
                    tunMtu = extractedMtu
                }
            }
        }
        val builder = getVpnBuilder(prefs, tunMtu)
        tunFd = builder.establish()
        if (tunFd == null) {
            stopXray()
            return
        }

        if (prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn) {
            Log.d(TAG, "Using Xray Native TUN mode, skipping hev-socks5-tunnel.")
        } else {
            val tproxyFile = File(cacheDir, "tproxy.conf")
            try {
                tproxyFile.createNewFile()
                FileOutputStream(tproxyFile, false).use { fos ->
                    val tproxyConf = getTproxyConf(prefs)
                    fos.write(tproxyConf.toByteArray())
                }
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
                stopXray()
                return
            }

            tunFd?.fd?.let { fd ->
                TProxyStartService(tproxyFile.absolutePath, fd)
            } ?: run {
                Log.e(TAG, "tunFd is null after establish()")
                stopXray()
                return
            }
        }

        @Suppress("SameParameterValue") val channelName = "socks5"
        initNotificationChannel(channelName)
        createNotification(channelName)
    }

    private fun getVpnBuilder(prefs: Preferences, tunMtu: Int): Builder = Builder().apply {
        setBlocking(false)
        setMtu(tunMtu)

        setMetered(false)

        if (prefs.bypassLan) {
            addRoute("10.0.0.0", 8)
            addRoute("100.64.0.0", 10)
            addRoute("169.254.0.0", 16)
            addRoute("172.16.0.0", 12)
            addRoute("192.0.0.0", 24)
            addRoute("192.0.2.0", 24)
            addRoute("192.88.99.0", 24)
            addRoute("192.168.0.0", 16)
            addRoute("198.18.0.0", 15)
            addRoute("198.51.100.0", 24)
            addRoute("203.0.113.0", 24)
            if (prefs.ipv6) {
                addRoute("fc00::", 7)
                addRoute("fe80::", 10)
            }
        }
        if (prefs.httpProxyEnabled) {
            setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", prefs.httpPort))
        }
        if (prefs.ipv4) {
            addAddress(prefs.tunnelIpv4Address, prefs.tunnelIpv4Prefix)
            addRoute("0.0.0.0", 0)
            prefs.dnsIpv4.takeIf { it.isNotEmpty() }?.also { addDnsServer(it) }
        }
        if (prefs.ipv6) {
            addAddress(prefs.tunnelIpv6Address, prefs.tunnelIpv6Prefix)
            addRoute("::", 0)
            prefs.dnsIpv6.takeIf { it.isNotEmpty() }?.also { addDnsServer(it) }
        }

        prefs.apps?.forEach { appName ->
            appName?.let { name ->
                try {
                    when {
                        prefs.bypassSelectedApps -> addDisallowedApplication(name)
                        else -> addAllowedApplication(name)
                    }
                } catch (ignored: PackageManager.NameNotFoundException) {
                }
            }
        }
        if (prefs.bypassSelectedApps || prefs.apps.isNullOrEmpty())
            addDisallowedApplication(BuildConfig.APPLICATION_ID)
    }

    private fun stopService() {
        isStartingLock.set(false)
        tunFd?.let {
            try {
                it.close()
            } catch (ignored: IOException) {
            } finally {
                tunFd = null
            }
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            runCatching { TProxyStopService() }
        }
        stopSelf()
        exit()
    }

    @Suppress("SameParameterValue")
    private fun createNotification(channelName: String) {
        val i = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, channelName)
        val notify = notification.setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_stat_lineal).setContentIntent(pi).build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notify)
        } else {
            startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    private fun exit() {
        val stopIntent = Intent(ACTION_STOP)
        stopIntent.setPackage(application.packageName)
        sendBroadcast(stopIntent)
        stopSelf()
    }

    @Suppress("SameParameterValue")
    private fun initNotificationChannel(channelName: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val name: CharSequence = getString(R.string.app_name)
        val channel = NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
    }

    private external fun TProxyStartService(configPath: String, fd: Int): Boolean
    private external fun TProxyStopService(): Boolean
    private external fun TProxyIsRunning(): Boolean
    private external fun TProxyGetStats(): LongArray?

    companion object {
        const val ACTION_CONNECT: String = "com.simplexray.re.CONNECT"
        const val ACTION_DISCONNECT: String = "com.simplexray.re.DISCONNECT"
        const val ACTION_START: String = "com.simplexray.re.START"
        const val ACTION_STOP: String = "com.simplexray.re.STOP"
        const val ACTION_START_FAILED: String = "com.simplexray.re.START_FAILED"
        const val ACTION_LOG_UPDATE: String = "com.simplexray.re.LOG_UPDATE"
        const val ACTION_RELOAD_CONFIG: String = "com.simplexray.re.RELOAD_CONFIG"
        const val EXTRA_LOG_DATA: String = "log_data"
        private const val TAG = "TProxyService"
        private const val BROADCAST_DELAY_MS: Long = 3000
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

        private fun getTproxyConf(prefs: Preferences): String {
            var tproxyConf = """misc:
  task-stack-size: ${prefs.taskStackSize}
tunnel:
  mtu: ${prefs.tunnelMtu}
"""
            tproxyConf += """socks5:
  port: ${prefs.socksPort}
  address: '${prefs.socksAddress}'
  udp: '${if (prefs.udpInTcp) "tcp" else "udp"}'
"""
            if (prefs.socksUsername.isNotEmpty() && prefs.socksPassword.isNotEmpty()) {
                tproxyConf += "  username: '" + prefs.socksUsername + "'\n"
                tproxyConf += "  password: '" + prefs.socksPassword + "'\n"
            }
            return tproxyConf
        }
    }
}
