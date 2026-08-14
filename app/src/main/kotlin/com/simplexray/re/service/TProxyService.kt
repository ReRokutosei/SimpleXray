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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simplexray.re.BuildConfig
import com.simplexray.re.R
import com.simplexray.re.activity.MainActivity
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.ConfigUtils.extractPortsFromJson
import com.simplexray.re.data.source.LogFileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.net.ServerSocket
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
                    xrayProcess?.destroy()
                    serviceScope.launch { runXrayProcess() }
                    return START_NOT_STICKY
                }
                if (tunFd == null) {
                    Log.w(TAG, "Cannot reload config, VPN service is not running.")
                    return START_NOT_STICKY
                }
                Log.d(TAG, "Received RELOAD_CONFIG action.")
                reloadingRequested = true
                xrayProcess?.destroy()
                serviceScope.launch { runXrayProcess() }
                return START_NOT_STICKY
            }

            ACTION_START -> {
                logFileManager.clearLogs()
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
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
                logFileManager.clearLogs()
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
        startService()
        serviceScope.launch { runXrayProcess() }
    }

    private fun runXrayProcess() {
        var stdoutPfd: ParcelFileDescriptor? = null

        try {
            Log.d(TAG, "Attempting to start native Xray process with TUN fd & UDS API.")
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

            val processBuilder = getProcessBuilder(xrayPath)
            val currentProcess = processBuilder.start()
            this.xrayProcess = currentProcess
            Log.d(TAG, "Xray child process started successfully via ProcessBuilder.")

            currentProcess.outputStream.use { os ->
                os.write(finalConfigContent.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val reader = BufferedReader(InputStreamReader(currentProcess.inputStream))
            Log.d(TAG, "Reading native Xray process log stream.")
            var line = reader.readLine()
            var hasBroadcastedStarted = false
            while (line != null) {
                Log.d(TAG, "XrayLog: $line")
                if (!hasBroadcastedStarted && line.contains("Xray") && line.contains("started")) {
                    hasBroadcastedStarted = true
                    Log.d(TAG, "Xray core started detected! Broadcasting ACTION_START to UI.")
                    val successIntent = Intent(ACTION_START)
                    successIntent.setPackage(application.packageName)
                    sendBroadcast(successIntent)
                }
                logFileManager.appendLog(line)
                synchronized(logBroadcastBuffer) {
                    logBroadcastBuffer.add(line)
                    if (!handler.hasCallbacks(broadcastLogsRunnable)) {
                        handler.postDelayed(broadcastLogsRunnable, BROADCAST_DELAY_MS)
                    }
                }
                line = reader.readLine()
            }
            Log.d(TAG, "Native Xray process log stream finished.")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing native Xray", e)
        } finally {
            stdoutPfd?.close()
            Log.d(TAG, "Native Xray process task finished.")
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

    private fun stopXray() {
        Log.d(TAG, "stopXray called with keepExecutorAlive=" + false)
        serviceScope.cancel()
        Log.d(TAG, "CoroutineScope cancelled.")

        xrayProcess?.destroy()
        xrayProcess = null
        Log.d(TAG, "xrayProcess reference nulled.")

        Log.d(TAG, "Calling stopService (stopping VPN).")
        stopService()
    }

    private fun startService() {
        if (tunFd != null) return
        val prefs = Preferences(this)
        val builder = getVpnBuilder(prefs)
        tunFd = builder.establish()
        if (tunFd == null) {
            stopXray()
            return
        }

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

        @Suppress("SameParameterValue") val channelName = "socks5"
        initNotificationChannel(channelName)
        createNotification(channelName)
    }

    private fun getVpnBuilder(prefs: Preferences): Builder = Builder().apply {
        setBlocking(false)
        setMtu(prefs.tunnelMtu)

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
            .setSmallIcon(R.drawable.ic_stat_name).setContentIntent(pi).build()
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
        const val ACTION_LOG_UPDATE: String = "com.simplexray.re.LOG_UPDATE"
        const val ACTION_RELOAD_CONFIG: String = "com.simplexray.re.RELOAD_CONFIG"
        const val EXTRA_LOG_DATA: String = "log_data"
        private const val TAG = "TProxyService"
        private const val BROADCAST_DELAY_MS: Long = 3000

        init {
            try {
                System.loadLibrary("hev-socks5-tunnel")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load hev-socks5-tunnel library", e)
            }
        }

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
