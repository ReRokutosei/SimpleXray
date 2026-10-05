package com.simplexray.re.service

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.ConfigUtils.extractPortsFromJson
import com.simplexray.re.common.CoreStatsClient
import com.simplexray.re.data.source.LogFileManager
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.prefs.TunnelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.net.ServerSocket
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.Volatile

internal class XrayProcessRunner(
    private val application: Context,
    private val logFileManager: LogFileManager,
    private val spawn: (xrayPath: String, assetDir: String, vpnFd: Int) -> IntArray?,
    private val reap: (pid: Int) -> Unit,
    private val callbacks: Callbacks,
    private val tag: String,
) {
    interface Callbacks {
        fun isStopping(): Boolean
        fun hasStartupFailed(): Boolean
        fun hasVpnTunnel(): Boolean
        fun vpnFd(): Int?
        fun onCoreReady()
        fun onStartFailure(reason: String)
        fun onUnexpectedExit()
        fun emitLog(line: String)
    }

    @Volatile
    private var process: Process? = null

    @Volatile
    var pid: Int = -1
        private set

    @Volatile
    var isStarted: Boolean = false
        private set

    private var job: Job? = null
    private var startAttempt = 0
    private var scope: CoroutineScope? = null

    fun resetAttempt() {
        startAttempt = 0
    }

    fun launch(scope: CoroutineScope) {
        this.scope = scope
        job?.cancel()
        job = scope.launch { run(scope) }
    }

    fun restart(scope: CoroutineScope) {
        kill()
        launch(scope)
    }

    fun kill() {
        process?.destroy()
        process = null
        val currentPid = pid
        if (currentPid > 0) {
            pid = -1
            try {
                Os.kill(currentPid, OsConstants.SIGKILL)
            } catch (e: ErrnoException) {
                Log.w(tag, "Failed to kill xray pid $currentPid: ${e.message}")
            }
            runCatching { reap(currentPid) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        kill()
    }

    private fun run(scope: CoroutineScope) {
        isStarted = false
        var stdoutPfd: ParcelFileDescriptor? = null
        var currentProcess: Process? = null
        var currentPid = -1
        var reader: BufferedReader? = null

        try {
            val prefs = Preferences(application)
            if (callbacks.isStopping()) {
                Log.d(tag, "Aborting Xray process startup after intentional stop.")
                return
            }
            if (!prefs.disableVpn && !callbacks.hasVpnTunnel()) {
                callbacks.onStartFailure("VPN tunnel is not established")
                return
            }
            Log.d(tag, "Attempting to start native Xray process with TUN fd & local gRPC API.")
            val libraryDir = TProxyService.getNativeLibraryDir(application)
            val selectedConfigPath = prefs.selectedConfigPath ?: run {
                callbacks.onStartFailure("No configuration file selected")
                return
            }
            val xrayPath = "$libraryDir/libxray.so"
            val configFile = File(selectedConfigPath)
            if (!configFile.exists()) {
                callbacks.onStartFailure("Selected config file does not exist: $selectedConfigPath")
                return
            }

            val rawConfigContent = runCatching { configFile.readText() }.getOrDefault("")
            Log.d(tag, "Loaded raw user config: ${configFile.name}, ${rawConfigContent.length} chars")

            val sanitizedConfigContent = ConfigUtils.sanitizeConfig(rawConfigContent, prefs)

            val isYaml = configFile.extension.lowercase() in listOf("yaml", "yml")
            val format = if (isYaml) "yaml" else "json"

            val ports = runCatching { extractPortsFromJson(sanitizedConfigContent) }.getOrDefault(emptySet())
            val apiPort = findAvailablePort(ports) ?: run {
                callbacks.onStartFailure("No local port available for the stats API")
                return
            }
            prefs.apiPort = apiPort
            prefs.apiAddress = "127.0.0.1"

            val finalConfigContent = ConfigUtils.injectStatsServiceIntoSanitized(prefs, sanitizedConfigContent)
            Log.d(tag, "Injected final config (${finalConfigContent.length} chars) ready for stdin ($format)")

            val useXrayTun = prefs.tunnelMode == TunnelMode.XrayTun && !prefs.disableVpn

            if (useXrayTun) {
                val vpnFd = callbacks.vpnFd() ?: run {
                    callbacks.onStartFailure("tunFd is null for Xray TUN mode")
                    return
                }
                val spawnResult = spawn(xrayPath, application.filesDir.path, vpnFd)
                    ?: run {
                        callbacks.onStartFailure("nativeSpawnXray returned null - spawn failed")
                        return
                    }
                currentPid = spawnResult[0]
                val stdoutReadFd = spawnResult[1]
                val stdinWriteFd = spawnResult[2]
                pid = currentPid
                Log.d(tag, "Xray TUN process started: pid=$currentPid")

                // Write the config on a separate IO job so the parent starts
                // draining stdout immediately. A synchronous write can deadlock
                // when both pipe buffers fill up (large config + early logs).
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        ParcelFileDescriptor.adoptFd(stdinWriteFd).use { pfd ->
                            ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                                out.write(finalConfigContent.toByteArray(Charsets.UTF_8))
                                out.flush()
                            }
                        }
                    }.onFailure {
                        Log.e(tag, "Failed to write configuration to Xray stdin", it)
                    }
                }
                stdoutPfd = ParcelFileDescriptor.adoptFd(stdoutReadFd)
                reader = BufferedReader(
                    InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(stdoutPfd))
                )
            } else {
                val processBuilder = getProcessBuilder(xrayPath)
                val processRef = processBuilder.start()
                currentProcess = processRef
                process = processRef
                Log.d(tag, "Xray child process started successfully via ProcessBuilder.")

                scope.launch(Dispatchers.IO) {
                    runCatching {
                        processRef.outputStream.use { os ->
                            os.write(finalConfigContent.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    }.onFailure {
                        Log.e(tag, "Failed to write configuration to Xray stdin", it)
                    }
                }
                reader = BufferedReader(InputStreamReader(processRef.inputStream))
            }

            scope.launch {
                val probeProcess = currentProcess
                val client = CoreStatsClient.create("127.0.0.1", prefs.apiPort)
                try {
                    val deadline = System.currentTimeMillis() + STARTUP_PROBE_TIMEOUT_MS
                    while (!isStarted &&
                        !callbacks.hasStartupFailed() &&
                        !callbacks.isStopping() &&
                        (probeProcess?.isAlive == true || currentPid > 0) &&
                        System.currentTimeMillis() < deadline
                    ) {
                        if (client.getSystemStats() != null) {
                            if (callbacks.hasStartupFailed() || callbacks.isStopping()) return@launch
                            isStarted = true
                            startAttempt = 0
                            Log.d(tag, "Xray core ready (gRPC API reachable), updating VpnStateHub.")
                            callbacks.onCoreReady()
                            break
                        }
                        delay(STARTUP_PROBE_INTERVAL_MS)
                    }

                    // The probe deadline expiring while the child process is still
                    // alive used to leave the service stuck in Connecting forever.
                    // Treat it as a normal startup failure so the retry/failure
                    // pipeline runs and the lock is released.
                    if (!isStarted &&
                        !callbacks.hasStartupFailed() &&
                        !callbacks.isStopping() &&
                        (probeProcess?.isAlive == true || currentPid > 0) &&
                        System.currentTimeMillis() >= deadline
                    ) {
                        Log.e(tag, "Xray startup probe timed out after ${STARTUP_PROBE_TIMEOUT_MS}ms.")
                        callbacks.onStartFailure(
                            "Xray core did not become ready within ${STARTUP_PROBE_TIMEOUT_MS / 1000}s"
                        )
                    }
                } finally {
                    client.close()
                }
            }

            Log.d(tag, "Reading native Xray process log stream.")
            val activeReader = reader ?: run {
                callbacks.onStartFailure("Failed to open Xray stdout stream")
                return
            }
            var line = activeReader.readLine()
            while (line != null) {
                val batch = mutableListOf<String>()
                val stampedLine = stampLogLine(line)
                Log.d(tag, "XrayLog: $stampedLine")
                batch.add(stampedLine)
                callbacks.emitLog(stampedLine)

                while (activeReader.ready() && batch.size < 50) {
                    val nextLine = activeReader.readLine() ?: break
                    val stampedNext = stampLogLine(nextLine)
                    Log.d(tag, "XrayLog: $stampedNext")
                    batch.add(stampedNext)
                    callbacks.emitLog(stampedNext)
                }

                logFileManager.appendLogs(batch)
                line = activeReader.readLine()
            }
            Log.d(tag, "Native Xray process log stream finished.")
            if (currentProcess != null) {
                onXrayExited(currentProcess, -1)
            } else {
                onXrayExited(null, currentPid)
            }
        } catch (e: InterruptedIOException) {
            Log.d(tag, "Xray log stream interrupted during shutdown.")
            if (currentProcess != null) {
                onXrayExited(currentProcess, -1)
            } else if (currentPid > 0) {
                onXrayExited(null, currentPid)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error executing native Xray", e)
            if (currentProcess != null) {
                onXrayExited(currentProcess, -1)
            } else if (currentPid > 0) {
                onXrayExited(null, currentPid)
            } else {
                callbacks.onStartFailure("Error executing native Xray: ${e.message}")
            }
        } finally {
            runCatching { reader?.close() }
            runCatching { stdoutPfd?.close() }
            Log.d(tag, "Native Xray process task finished.")
            if (currentProcess != null && process === currentProcess) {
                process = null
            }
            if (pid > 0 && pid == currentPid) {
                pid = -1
            }
            if (currentPid > 0) {
                runCatching { reap(currentPid) }
            }
        }
    }

    private fun onXrayExited(process: Process?, pid: Int) {
        if (pid > 0) {
            runCatching { reap(pid) }
        }
        if (callbacks.isStopping()) {
            Log.d(tag, "Xray process exited after intentional stop, ignoring.")
            return
        }
        if ((process != null && process !== this.process) || (process == null && pid != this.pid)) {
            Log.d(tag, "Xray process superseded by a newer one, ignoring.")
            return
        }
        if (isStarted) {
            Log.e(tag, "Xray process exited unexpectedly, stopping service.")
            callbacks.onUnexpectedExit()
            return
        }
        if (startAttempt < MAX_START_ATTEMPTS) {
            startAttempt++
            Log.w(tag, "Xray failed to start, retrying (attempt $startAttempt/$MAX_START_ATTEMPTS).")
            scope?.let { launch(it) }
        } else {
            callbacks.onStartFailure("Xray failed to start after $MAX_START_ATTEMPTS attempts")
        }
    }

    private fun getProcessBuilder(xrayPath: String): ProcessBuilder {
        val filesDir = application.filesDir
        val command = mutableListOf(xrayPath, "-config", "stdin:")
        val processBuilder = ProcessBuilder(command)
        val environment = processBuilder.environment()
        environment["XRAY_LOCATION_ASSET"] = filesDir.path
        processBuilder.directory(filesDir)
        processBuilder.redirectErrorStream(true)
        return processBuilder
    }

    private fun findAvailablePort(excludedPorts: Set<Int>): Int? {
        repeat(5) {
            val port = runCatching {
                ServerSocket(0).use { socket ->
                    socket.reuseAddress = true
                    socket.localPort
                }
            }.onFailure {
                Log.d(tag, "Ephemeral port allocation failed: ${it.message}")
            }.getOrNull()
            if (port != null && port !in excludedPorts) {
                return port
            }
        }
        return null
    }

    private fun stampLogLine(line: String): String {
        val message = GO_LOG_TIMESTAMP_PREFIX.replaceFirst(line, "")
        return if (message === line) line else "${LocalDateTime.now().format(logTimestampFormat)} $message"
    }

    companion object {
        private const val MAX_START_ATTEMPTS = 2
        private const val STARTUP_PROBE_TIMEOUT_MS: Long = 15000
        private const val STARTUP_PROBE_INTERVAL_MS: Long = 500
        private val GO_LOG_TIMESTAMP_PREFIX = Regex("""^\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}(\.\d+)? """)
        private val logTimestampFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS", Locale.US)
    }
}
