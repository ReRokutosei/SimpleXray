package com.simplexray.re.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.net.Uri
import android.net.VpnService
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.simplexray.re.BuildConfig
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.CoreStatsClient
import com.simplexray.re.common.ReleaseVersion
import com.simplexray.re.common.SocksAuthenticatorInstaller
import com.simplexray.re.common.ROUTE_APP_LIST
import com.simplexray.re.common.TcpPing
import com.simplexray.re.common.ThemeMode
import com.simplexray.re.data.source.FileManager
import com.simplexray.re.prefs.LogLevel
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.service.TProxyService
import com.simplexray.re.service.VpnRunningState
import com.simplexray.re.service.VpnStateHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "MainViewModel"

sealed class MainViewUiEvent {
    data class ShowSnackbar(val message: String) : MainViewUiEvent()
    data class ShareLauncher(val intent: Intent) : MainViewUiEvent()
    data class StartService(val intent: Intent) : MainViewUiEvent()
    data object RefreshConfigList : MainViewUiEvent()
    data class Navigate(val route: String) : MainViewUiEvent()
}

class MainViewModel(application: Application) :
    AndroidViewModel(application) {
    val prefs: Preferences = Preferences(application)
    private val activityScope: CoroutineScope = viewModelScope

    private var coreStatsClient: CoreStatsClient? = null
    private var latencyTestJob: Job? = null

    private val fileManager: FileManager = FileManager(application, prefs)

    private val configFileController by lazy {
        ConfigFileController(
            application,
            prefs,
            fileManager,
            viewModelScope,
            { _isServiceEnabled.value },
            { _uiEvent.trySend(it) },
        )
    }

    private val settingsController by lazy {
        SettingsController(
            application,
            prefs,
            fileManager,
            { _isServiceEnabled.value },
            { showSnackbar(application.getString(R.string.tunnel_mode_restart_notice)) },
        )
    }

    private val appIconController by lazy {
        AppIconController(application, prefs)
    }

    var editingFilePath: String?
        get() = configFileController.editingFilePath
        set(value) {
            configFileController.editingFilePath = value
        }

    val settingsState: StateFlow<SettingsState>
        get() = settingsController.settingsState

    private val _coreStatsState = MutableStateFlow(CoreStatsState())
    val coreStatsState: StateFlow<CoreStatsState> = _coreStatsState.asStateFlow()

    private val _outboundNodes = MutableStateFlow<List<ConfigUtils.OutboundInfo>>(emptyList())
    val outboundNodes: StateFlow<List<ConfigUtils.OutboundInfo>> = _outboundNodes.asStateFlow()

    private val _outboundLatency = MutableStateFlow<Map<String, OutboundLatency>>(emptyMap())
    val outboundLatency: StateFlow<Map<String, OutboundLatency>> = _outboundLatency.asStateFlow()

    private val _controlMenuClickable = MutableStateFlow(true)
    val controlMenuClickable: StateFlow<Boolean> = _controlMenuClickable.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    val appIcon: StateFlow<String>
        get() = appIconController.appIcon

    private val _uiEvent = Channel<MainViewUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun showSnackbar(message: String) {
        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(message))
    }

    val configFiles: StateFlow<List<File>>
        get() = configFileController.configFiles

    val selectedConfigFile: StateFlow<File?>
        get() = configFileController.selectedConfigFile

    private val _geoipDownloadProgress = MutableStateFlow<String?>(null)
    val geoipDownloadProgress: StateFlow<String?> = _geoipDownloadProgress.asStateFlow()
    private var geoipDownloadJob: Job? = null

    private val _geositeDownloadProgress = MutableStateFlow<String?>(null)
    val geositeDownloadProgress: StateFlow<String?> = _geositeDownloadProgress.asStateFlow()
    private var geositeDownloadJob: Job? = null

    // Third-party dat files download state, keyed by file name.
    private val _customDatDownloadProgress = MutableStateFlow<Map<String, String?>>(emptyMap())
    val customDatDownloadProgress: StateFlow<Map<String, String?>> = _customDatDownloadProgress.asStateFlow()
    private val customDatDownloadJobs = mutableMapOf<String, Job>()

    private fun updateCustomDatProgress(fileName: String, progress: String?) {
        val map = _customDatDownloadProgress.value.toMutableMap()
        if (progress == null) map.remove(fileName) else map[fileName] = progress
        _customDatDownloadProgress.value = map
    }

    private val _isCheckingForUpdates = MutableStateFlow(false)
    val isCheckingForUpdates: StateFlow<Boolean> = _isCheckingForUpdates.asStateFlow()

    private val _newVersionAvailable = MutableStateFlow<String?>(null)
    val newVersionAvailable: StateFlow<String?> = _newVersionAvailable.asStateFlow()

    init {
        Log.d(TAG, "MainViewModel initialized.")

        SocksAuthenticatorInstaller.install(application)

        viewModelScope.launch {
            VpnStateHub.state.collect { state ->
                when (state) {
                    is VpnRunningState.Connected -> {
                        Log.d(TAG, "VPN state: Connected")
                        setServiceEnabled(true)
                        setControlMenuClickable(true)
                    }
                    is VpnRunningState.Connecting -> {
                        Log.d(TAG, "VPN state: Connecting")
                        setControlMenuClickable(false)
                    }
                    is VpnRunningState.Disconnected -> {
                        Log.d(TAG, "VPN state: Disconnected")
                        setServiceEnabled(false)
                        setControlMenuClickable(true)
                        _coreStatsState.value = CoreStatsState()
                        coreStatsClient?.close()
                        coreStatsClient = null
                    }
                    is VpnRunningState.Failed -> {
                        Log.d(TAG, "VPN state: Failed (${state.message})")
                        setServiceEnabled(false)
                        setControlMenuClickable(true)
                        _coreStatsState.value = CoreStatsState()
                        coreStatsClient?.close()
                        coreStatsClient = null
                        val msg = state.message ?: application.getString(R.string.core_start_failed)
                        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(msg))
                    }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Independent initializations run concurrently to reduce first-launch
            // latency; coroutineScope waits for all of them before init finishes.
            // updateSettingsState and loadKernelVersion both read-modify-write
            // _settingsState, so they must run serially (concurrent RMW would
            // drop fields); the rest are independent.
            coroutineScope {
                launch {
                    _isServiceEnabled.value = VpnStateHub.state.value is VpnRunningState.Connected
                }
                launch { ensureAppIconSelected() }
                launch {
                    if (prefs.geoUpdateIntervalHours > 0) {
                        com.simplexray.re.service.GeoUpdateWorker.schedule(application, prefs.geoUpdateIntervalHours)
                    }
                }
                launch {
                    updateSettingsState()
                    refreshConfigFileList()
                    loadKernelVersion()
                }
            }
        }
    }

    fun updateSettingsState() = settingsController.updateSettingsState()

    private fun loadKernelVersion() = settingsController.loadKernelVersion()

    fun setControlMenuClickable(isClickable: Boolean) {
        _controlMenuClickable.value = isClickable
    }

    fun setServiceEnabled(enabled: Boolean) {
        _isServiceEnabled.value = enabled
        prefs.enable = enabled
    }

    /**
     * Initializes the app icon preference on first launch: defaults to the
     * manifest-enabled alias (flat) so the first launcher icon and the settings
     * dropdown agree. Component states are never touched here — disabling the
     * currently running launcher alias mid-run makes the system rebuild the
     * task (feels like a crash + auto-restart). The choice is only applied when
     * the user manually switches via [setAppIcon].
     */
    fun ensureAppIconSelected() = appIconController.ensureAppIconSelected()

    fun setAppIcon(key: String) = appIconController.setAppIcon(key)





    suspend fun createConfigFile(): String? = configFileController.createConfigFile()

    suspend fun updateCoreStats() = withContext(Dispatchers.IO) {
        if (!_isServiceEnabled.value) return@withContext
        if (coreStatsClient == null) {
            Log.d(TAG, "=== [DEBUG gRPC] Connecting CoreStatsClient to ${prefs.apiAddress}:${prefs.apiPort} ===")
            coreStatsClient = CoreStatsClient.create(prefs.apiAddress, prefs.apiPort)
        }

        val stats = coreStatsClient?.getSystemStats()
        val traffic = coreStatsClient?.getTraffic()
        Log.d(TAG, "=== [DEBUG gRPC RESULT] uplink=${traffic?.uplink}, downlink=${traffic?.downlink}, sys=${stats?.sys} ===")

        if (stats == null && traffic == null) {
            Log.w(TAG, "=== [DEBUG gRPC FAILED] Both stats & traffic returned null, resetting client ===")
            coreStatsClient?.close()
            coreStatsClient = null
            return@withContext
        }

        _coreStatsState.value = CoreStatsState(
            uplink = traffic?.uplink ?: 0,
            downlink = traffic?.downlink ?: 0,
            numGoroutine = stats?.numGoroutine ?: 0,
            numGC = stats?.numGC ?: 0,
            alloc = stats?.alloc ?: 0,
            totalAlloc = stats?.totalAlloc ?: 0,
            sys = stats?.sys ?: 0,
            mallocs = stats?.mallocs ?: 0,
            frees = stats?.frees ?: 0,
            liveObjects = stats?.liveObjects ?: 0,
            pauseTotalNs = stats?.pauseTotalNs ?: 0,
            uptime = stats?.uptime ?: 0
        )
        Log.d(TAG, "Core stats updated")
    }

    /**
     * Refreshes the outbound node list from the currently selected config file.
     * Works whether or not the service is running.
     */
    suspend fun refreshOutboundNodes() {
        val file = configFileController.selectedConfigFile.value ?: return
        val content = withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrNull()
        } ?: return
        val nodes = ConfigUtils.extractOutbounds(content)
        _outboundNodes.value = nodes
        Log.d(TAG, "Refreshed ${nodes.size} outbound nodes from ${file.name}")
    }

    /**
     * Latency-tests every TCP-capable outbound endpoint (1-RTT TCP connect,
     * independent of the core). Called when the dashboard is shown and on
     * manual refresh. UDP-only protocols (wireguard/hysteria2) and QUIC
     * transports are skipped and keep showing no data.
     */
    suspend fun testOutboundLatency() {
        val file = configFileController.selectedConfigFile.value ?: return
        val content = withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrNull()
        } ?: return
        val endpoints = ConfigUtils.extractOutboundEndpoints(content)
        if (endpoints.isEmpty()) {
            _outboundLatency.value = emptyMap()
            return
        }
        val now = System.currentTimeMillis() / 1000
        val io = Dispatchers.IO.limitedParallelism(8)
        val results = coroutineScope {
            endpoints.map { ep ->
                async(io) {
                    val delay = withTimeoutOrNull(4000L) {
                        TcpPing.pingBlocking(ep.host, ep.port)
                    } ?: -1L
                    ep.tag to delay
                }
            }.awaitAll()
        }
        _outboundLatency.value = results.associate { (tag, delay) ->
            Log.d(TAG, "[tcping] tag=$tag delay=${if (delay >= 0) "${delay}ms" else "failed"}")
            tag to OutboundLatency(
                alive = delay >= 0,
                delayMs = delay.coerceAtLeast(0),
                lastTryTime = now
            )
        }
        Log.d(TAG, "Outbound latency (TCPing) updated: ${results.size} entries")
    }

    /** Non-suspend wrapper for UI callbacks (e.g. the dashboard refresh button). */
    fun refreshLatency() {
        if (latencyTestJob?.isActive == true) return
        latencyTestJob = viewModelScope.launch { testOutboundLatency() }
    }

    suspend fun importConfigFromClipboard(): String? = configFileController.importConfigFromClipboard()

    suspend fun deleteConfigFile(file: File, callback: () -> Unit) = configFileController.deleteConfigFile(file, callback)

    fun extractAssetsIfNeeded() {
        fileManager.extractAssetsIfNeeded()
    }

    fun updateSocksAddress(addressString: String): Boolean = settingsController.updateSocksAddress(addressString)

    fun updateSocksPort(portString: String): Boolean = settingsController.updateSocksPort(portString)

    fun updateSocksUser(userString: String): Boolean = settingsController.updateSocksUser(userString)

    fun updateSocksPass(passString: String): Boolean = settingsController.updateSocksPass(passString)

    fun updateDnsIpv4(ipv4Addr: String): Boolean = settingsController.updateDnsIpv4(ipv4Addr)

    fun updateDnsIpv6(ipv6Addr: String): Boolean = settingsController.updateDnsIpv6(ipv6Addr)

    fun setIpv6Enabled(enabled: Boolean) = settingsController.setIpv6Enabled(enabled)

    fun setHideFromRecentsEnabled(enabled: Boolean) = settingsController.setHideFromRecentsEnabled(enabled)

    fun updateGeoUpdateInterval(hoursString: String): Boolean = settingsController.updateGeoUpdateInterval(hoursString)

    fun updateTunnelMtu(mtuString: String): Boolean = settingsController.updateTunnelMtu(mtuString)

    fun setHttpProxyEnabled(enabled: Boolean) = settingsController.setHttpProxyEnabled(enabled)

    fun setBypassLanEnabled(enabled: Boolean) = settingsController.setBypassLanEnabled(enabled)

    fun setKeepAwakeEnabled(enabled: Boolean) = settingsController.setKeepAwakeEnabled(enabled)

    fun setLogLevel(logLevel: LogLevel) = settingsController.setLogLevel(logLevel)

    fun setAccessLog(enabled: Boolean) = settingsController.setAccessLog(enabled)

    fun setDnsLog(enabled: Boolean) = settingsController.setDnsLog(enabled)

    fun setDisableVpnEnabled(enabled: Boolean) = settingsController.setDisableVpnEnabled(enabled)

    fun setTunnelMode(mode: com.simplexray.re.prefs.TunnelMode) = settingsController.setTunnelMode(mode)

    fun setTheme(mode: ThemeMode) = settingsController.setTheme(mode)

    fun importRuleFile(uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = fileManager.importRuleFile(uri, fileName)
            if (success) {
                settingsController.refreshRuleFileState(fileName)
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
                        "$fileName ${application.getString(R.string.import_success)}"
                    )
                )
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_validation_failed)))
            }
        }
    }

    fun showExportFailedSnackbar() {
        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.export_failed)))
    }

    fun startTProxyService(action: String) {
        viewModelScope.launch {
            if (configFileController.selectedConfigFile.value == null) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
                Log.w(TAG, "Cannot start service: no config file selected.")
                setControlMenuClickable(true)
                return@launch
            }
            val intent = Intent(application, TProxyService::class.java).setAction(action)
            _uiEvent.trySend(MainViewUiEvent.StartService(intent))
        }
    }

    fun editConfig(filePath: String) = configFileController.editConfig(filePath)

    fun shareIntent(chooserIntent: Intent, packageManager: PackageManager) = configFileController.shareIntent(chooserIntent, packageManager)

    fun stopTProxyService() {
        viewModelScope.launch {
            val intent = Intent(
                application,
                TProxyService::class.java
            ).setAction(TProxyService.ACTION_DISCONNECT)
            _uiEvent.trySend(MainViewUiEvent.StartService(intent))
        }
    }

    fun prepareAndStartVpn(vpnPrepareLauncher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            if (configFileController.selectedConfigFile.value == null) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
                Log.w(TAG, "Cannot prepare VPN: no config file selected.")
                setControlMenuClickable(true)
                return@launch
            }
            val vpnIntent = VpnService.prepare(application)
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                startTProxyService(TProxyService.ACTION_CONNECT)
            }
        }
    }

    fun navigateToAppList() {
        viewModelScope.launch {
            _uiEvent.trySend(MainViewUiEvent.Navigate(ROUTE_APP_LIST))
        }
    }

    fun moveConfigFile(fromIndex: Int, toIndex: Int) = configFileController.moveConfigFile(fromIndex, toIndex)

    fun persistConfigFilesOrder() = configFileController.persistConfigFilesOrder()



    fun importConfigFromFile(uri: android.net.Uri) = configFileController.importConfigFromFile(uri)

    fun refreshConfigFileList() = configFileController.refreshConfigFileList()

    fun updateSelectedConfigFile(file: File?) = configFileController.updateSelectedConfigFile(file)

    fun restoreDefaultGeoip(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeoip()
            settingsController.refreshRuleFileState("geoip.dat")
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geoip_success)))
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Restored default geoip.dat.")
                callback()
            }
        }
    }

    fun restoreDefaultGeosite(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeosite()
            settingsController.refreshRuleFileState("geosite.dat")
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geosite_success)))
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Restored default geosite.dat.")
                callback()
            }
        }
    }

    fun cancelDownload(fileName: String) {
        viewModelScope.launch {
            when (fileName) {
                "geoip.dat" -> geoipDownloadJob?.cancel()
                "geosite.dat" -> geositeDownloadJob?.cancel()
                else -> customDatDownloadJobs[fileName]?.cancel()
            }
            Log.d(TAG, "Download cancellation requested for $fileName")
        }
    }

    fun downloadRuleFile(url: String, fileName: String) {
        // Normalize standard GEO file names (case-insensitive) so e.g. "GEOIP.dat"
        // always targets the built-in geoip.dat instead of an orphan custom file.
        val targetName = if (FileManager.isStandardGeoDat(fileName)) fileName.lowercase() else fileName
        val isStandard = targetName == "geoip.dat" || targetName == "geosite.dat"
        val currentJob = if (isStandard) {
            if (targetName == "geoip.dat") geoipDownloadJob else geositeDownloadJob
        } else {
            customDatDownloadJobs[targetName]
        }
        if (currentJob?.isActive == true) {
            Log.w(TAG, "Download already in progress for $fileName")
            return
        }

        // `job` must be declared before the coroutine: the coroutine body (and the
        // local setProgress) reference it for the latest-job guard, and Kotlin
        // forbids referencing a `val` from within its own initializer.
        var job: Job? = null
        job = viewModelScope.launch(Dispatchers.IO) {
            val standardProgress: MutableStateFlow<String?>? = when (targetName) {
                "geoip.dat" -> {
                    prefs.geoipUrl = url
                    _geoipDownloadProgress
                }

                "geosite.dat" -> {
                    prefs.geositeUrl = url
                    _geositeDownloadProgress
                }

                else -> {
                    // Third-party dat: persist its URL and report progress per file.
                    val urls = prefs.customDatUrls.toMutableMap()
                    urls[targetName] = url
                    prefs.customDatUrls = urls
                    null
                }
            }

            fun setProgress(text: String?) {
                // Only the latest job for this file may clear the progress, so a
                // cancelled job cannot wipe the state of a replacement download.
                if (text == null) {
                    val isLatest = when {
                        targetName == "geoip.dat" -> geoipDownloadJob === job
                        targetName == "geosite.dat" -> geositeDownloadJob === job
                        else -> customDatDownloadJobs[targetName] === job
                    }
                    if (!isLatest) return
                }
                if (standardProgress != null) {
                    standardProgress.value = text
                } else {
                    updateCustomDatProgress(targetName, text)
                }
            }

            val client = OkHttpClient.Builder().apply {
                if (_isServiceEnabled.value) {
                    proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort)))
                }
            }.build()

            try {
                setProgress(application.getString(R.string.connecting))

                val request = Request.Builder().url(url).build()
                val call = client.newCall(request)
                val response = call.await()

                if (!response.isSuccessful) {
                    throw IOException("Failed to download file: ${response.code}")
                }

                val body = response.body
                val totalBytes = body.contentLength()
                var bytesRead = 0L
                var lastProgress = -1

                body.byteStream().use { inputStream ->
                    val success = fileManager.saveRuleFile(inputStream, targetName) { read ->
                        ensureActive()
                        bytesRead += read
                        if (totalBytes > 0) {
                            val progress = (bytesRead * 100 / totalBytes).toInt()
                            if (progress != lastProgress) {
                                setProgress(
                                    application.getString(R.string.downloading, progress)
                                )
                                lastProgress = progress
                            }
                        } else {
                            if (lastProgress == -1) {
                                setProgress(
                                    application.getString(R.string.downloading_no_size)
                                )
                                lastProgress = 0
                            }
                        }
                    }
                    if (success) {
                        if (isStandard) {
                            settingsController.refreshRuleFileState(targetName)
                        }
                        updateSettingsState()
                        refreshCustomDatFiles()
                        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_success)))
                    } else {
                        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_validation_failed)))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $fileName", e)
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_failed)))
            } finally {
                setProgress(null)
            }
        }

        if (targetName == "geoip.dat") {
            geoipDownloadJob = job
        } else if (targetName == "geosite.dat") {
            geositeDownloadJob = job
        } else {
            customDatDownloadJobs[targetName] = job
        }

        job.invokeOnCompletion {
            // Only clear the stored job reference if it is still the latest one,
            // so a cancelled job cannot remove the reference of a replacement download.
            if (targetName == "geoip.dat") {
                if (geoipDownloadJob === job) geoipDownloadJob = null
            } else if (targetName == "geosite.dat") {
                if (geositeDownloadJob === job) geositeDownloadJob = null
            } else {
                if (customDatDownloadJobs[targetName] === job) customDatDownloadJobs.remove(targetName)
            }
        }
    }

    private val _customDatVersion = MutableStateFlow(0L)
    val customDatVersion: StateFlow<Long> = _customDatVersion.asStateFlow()

    fun refreshCustomDatFiles() {
        _customDatVersion.value = System.currentTimeMillis()
    }

    fun importCustomDatFile(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val candidateName = fileManager.getDatFileNameFromUri(application, uri)
            if (candidateName == null) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.unsupported_dat_format)))
                return@launch
            }
            // Defense: reject standard GEO file names (case-insensitive) before importing.
            if (FileManager.isStandardGeoDat(candidateName)) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.standard_geo_file_rejected)))
                return@launch
            }
            val fileName = fileManager.importDatFileFromUri(application, uri)
            if (fileName != null) {
                refreshCustomDatFiles()
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.file_imported, fileName)))
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_validation_failed)))
            }
        }
    }

    /**
     * Download and import a new third-party .dat file from a direct link.
     * The file name is inferred from the URL path; standard GEO file names
     * (case-insensitive) are rejected.
     */
    fun downloadDatFromUrl(url: String) {
        if (url.isBlank()) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.invalid_dat_url)))
            return
        }
        val fileName = extractDatFileName(url)
        if (fileName == null) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.invalid_dat_url)))
            return
        }
        if (FileManager.isStandardGeoDat(fileName)) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.standard_geo_file_rejected)))
            return
        }
        downloadRuleFile(url, fileName)
    }

    fun getCustomDatSummary(fileName: String): String = fileManager.getCustomDatSummary(fileName)

    private fun extractDatFileName(url: String): String? {
        return try {
            // java.net.URL.getPath() already returns the URL-decoded path.
            val path = URL(url).path
            val name = path.substringAfterLast('/')
            if (name.isBlank() || name == "." || name == ".." ||
                name.contains('/') || name.contains('\\')
            ) {
                null
            } else if (name.lowercase().endsWith(".dat")) {
                name
            } else {
                "$name.dat"
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteCustomDatFile(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(application.filesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            val urls = prefs.customDatUrls.toMutableMap()
            urls.remove(fileName)
            prefs.customDatUrls = urls
            refreshCustomDatFiles()
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.file_deleted, fileName)))
        }
    }

    fun updateCustomDatUrl(fileName: String, url: String) {
        val urls = prefs.customDatUrls.toMutableMap()
        urls[fileName] = url
        prefs.customDatUrls = urls
        refreshCustomDatFiles()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(Result.success(response))
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWith(Result.failure(e))
            }
        })
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Throwable) {
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingForUpdates.value = true
            val client = OkHttpClient.Builder().apply {
                if (_isServiceEnabled.value) {
                    proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort)))
                }
            }.build()

            val apiUrl = application.getString(R.string.source_url)
                .replace("github.com", "api.github.com/repos") + "/releases?per_page=20"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            try {
                val response = client.newCall(request).await()
                val responseBody = response.body.string()
                val releases = org.json.JSONArray(responseBody)
                var latestTag = ""
                for (i in 0 until releases.length()) {
                    val release = releases.optJSONObject(i) ?: continue
                    if (release.optBoolean("draft", false)) continue
                    val candidate = release.optString("tag_name", "").removePrefix("v")
                    if (candidate.isNotEmpty() &&
                        (latestTag.isEmpty() || ReleaseVersion.compare(candidate, latestTag) > 0)
                    ) {
                        latestTag = candidate
                    }
                }
                Log.d(TAG, "Latest version tag: $latestTag")
                val updateAvailable =
                    latestTag.isNotEmpty() && ReleaseVersion.compare(latestTag, BuildConfig.VERSION_NAME) > 0
                if (updateAvailable) {
                    _newVersionAvailable.value = latestTag
                } else {
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(
                            application.getString(R.string.no_new_version_available)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
                        application.getString(R.string.failed_to_check_for_updates) + ": " + e.message
                    )
                )
            } finally {
                _isCheckingForUpdates.value = false
            }
        }
    }

    fun downloadNewVersion(versionTag: String) {
        val url = application.getString(R.string.source_url) + "/releases/tag/v$versionTag"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
        _newVersionAvailable.value = null
    }

    fun clearNewVersionAvailable() {
        _newVersionAvailable.value = null
    }


    override fun onCleared() {
        coreStatsClient?.close()
        coreStatsClient = null
        super.onCleared()
    }


}

