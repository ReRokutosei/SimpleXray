package com.simplexray.re.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.simplexray.re.BuildConfig
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.CoreStatsClient
import com.simplexray.re.common.ROUTE_APP_LIST
import com.simplexray.re.common.ROUTE_CONFIG_EDIT
import com.simplexray.re.common.TcpPing
import com.simplexray.re.common.isConfigFile
import com.simplexray.re.common.ThemeMode
import com.simplexray.re.data.source.FileManager
import com.simplexray.re.prefs.LogLevel
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.service.TProxyService
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.regex.Pattern
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "MainViewModel"

private const val APP_ICON_DEFAULT = "origin"
private val APP_ICON_OPTIONS = listOf("flat", "lineal", "lineal_color", "origin")
private val APP_ICON_ALIASES = listOf(
    "flat" to "MainActivityFlat",
    "lineal" to "MainActivityLineal",
    "lineal_color" to "MainActivityLinealColor",
    "origin" to "MainActivityOrigin"
)

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

    var reloadView: (() -> Unit)? = null

    lateinit var appListViewModel: AppListViewModel
    lateinit var configEditViewModel: ConfigEditViewModel

    private val _settingsState = MutableStateFlow(
        SettingsState(
            socksAddress = InputFieldState(prefs.socksAddress),
            socksPort = InputFieldState(prefs.socksPort.toString()),
            socksUser = InputFieldState(prefs.socksUsername),
            socksPass = InputFieldState(prefs.socksPassword),
            dnsIpv4 = InputFieldState(prefs.dnsIpv4),
            dnsIpv6 = InputFieldState(prefs.dnsIpv6),
            switches = SwitchStates(
                ipv6Enabled = prefs.ipv6,
                useTemplateEnabled = prefs.useTemplate,
                httpProxyEnabled = prefs.httpProxyEnabled,
                bypassLanEnabled = prefs.bypassLan,
                disableVpn = prefs.disableVpn,
                themeMode = prefs.theme,
                logLevel = prefs.logLevel
            ),
            info = InfoStates(
                appVersion = BuildConfig.VERSION_NAME,
                kernelVersion = "N/A",
                geoipSummary = "",
                geositeSummary = "",
                geoipUrl = prefs.geoipUrl,
                geositeUrl = prefs.geositeUrl
            ),
            files = FileStates(
                isGeoipCustom = prefs.customGeoipImported,
                isGeositeCustom = prefs.customGeositeImported
            ),
            geoUpdateIntervalHours = InputFieldState(prefs.geoUpdateIntervalHours.toString())
        )
    )
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

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

    private val _appIcon = MutableStateFlow(prefs.appIcon ?: APP_ICON_DEFAULT)
    val appIcon: StateFlow<String> = _appIcon.asStateFlow()

    private val _uiEvent = Channel<MainViewUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun showSnackbar(message: String) {
        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(message))
    }

    private val _configFiles = MutableStateFlow<List<File>>(emptyList())
    val configFiles: StateFlow<List<File>> = _configFiles.asStateFlow()

    private val _selectedConfigFile = MutableStateFlow<File?>(null)
    val selectedConfigFile: StateFlow<File?> = _selectedConfigFile.asStateFlow()

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

    private val startReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Service started")
            setServiceEnabled(true)
            setControlMenuClickable(true)
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Service stopped")
            setServiceEnabled(false)
            setControlMenuClickable(true)
            _coreStatsState.value = CoreStatsState()
            coreStatsClient?.close()
            coreStatsClient = null
        }
    }

    private val startFailedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Xray start failed")
            _uiEvent.trySend(
                MainViewUiEvent.ShowSnackbar(application.getString(R.string.core_start_failed))
            )
        }
    }

    init {
        Log.d(TAG, "MainViewModel initialized.")

        setupGlobalSocksAuthenticator()

        viewModelScope.launch(Dispatchers.IO) {
            val legacyExtraApi = File(application.filesDir, "extra_api.json")
            if (legacyExtraApi.exists()) {
                runCatching { legacyExtraApi.delete() }
            }

            // Independent initializations run concurrently to reduce first-launch
            // latency; coroutineScope waits for all of them before init finishes.
            // updateSettingsState and loadKernelVersion both read-modify-write
            // _settingsState, so they must run serially (concurrent RMW would
            // drop fields); the rest are independent.
            coroutineScope {
                launch {
                    _isServiceEnabled.value = isServiceRunning(application, TProxyService::class.java)
                }
                launch { ensureAppIconSelected() }
                launch {
                    updateSettingsState()
                    refreshConfigFileList()
                    loadKernelVersion()
                }
            }
        }
    }

    private fun updateSettingsState() {
        _settingsState.value = _settingsState.value.copy(
            socksAddress = InputFieldState(prefs.socksAddress),
            socksPort = InputFieldState(prefs.socksPort.toString()),
            socksUser = InputFieldState(prefs.socksUsername),
            socksPass = InputFieldState(prefs.socksPassword),
            dnsIpv4 = InputFieldState(prefs.dnsIpv4),
            dnsIpv6 = InputFieldState(prefs.dnsIpv6),
            switches = SwitchStates(
                ipv6Enabled = prefs.ipv6,
                useTemplateEnabled = prefs.useTemplate,
                hideFromRecents = prefs.hideFromRecents,
                httpProxyEnabled = prefs.httpProxyEnabled,
                bypassLanEnabled = prefs.bypassLan,
                disableVpn = prefs.disableVpn,
                themeMode = prefs.theme,
                logLevel = prefs.logLevel
            ),
            info = _settingsState.value.info.copy(
                appVersion = BuildConfig.VERSION_NAME,
                geoipSummary = fileManager.getRuleFileSummary("geoip.dat"),
                geositeSummary = fileManager.getRuleFileSummary("geosite.dat"),
                geoipUrl = prefs.geoipUrl,
                geositeUrl = prefs.geositeUrl
            ),
            files = FileStates(
                isGeoipCustom = prefs.customGeoipImported,
                isGeositeCustom = prefs.customGeositeImported
            ),
            geoUpdateIntervalHours = InputFieldState(prefs.geoUpdateIntervalHours.toString())
        )
    }

    private fun loadKernelVersion() {
        val libraryDir = TProxyService.getNativeLibraryDir(application)
        val xrayPath = "$libraryDir/libxray.so"
        try {
            val process = Runtime.getRuntime().exec("$xrayPath -version")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val firstLine = reader.readLine()
            process.destroy()
            _settingsState.value = _settingsState.value.copy(
                info = _settingsState.value.info.copy(
                    kernelVersion = firstLine ?: "N/A"
                )
            )
        } catch (e: IOException) {
            Log.e(TAG, "Failed to get xray version", e)
            _settingsState.value = _settingsState.value.copy(
                info = _settingsState.value.info.copy(
                    kernelVersion = "N/A"
                )
            )
        }
    }

    private fun setupGlobalSocksAuthenticator() {
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
                val user = prefs.socksUsername
                val pass = prefs.socksPassword

                return if (user.isNotEmpty() || pass.isNotEmpty()) {
                    java.net.PasswordAuthentication(user, pass.toCharArray())
                } else {
                    null
                }
            }
        })
    }

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
    fun ensureAppIconSelected() {
        val current = prefs.appIcon
        if (current == null) {
            prefs.appIcon = APP_ICON_DEFAULT
            _appIcon.value = APP_ICON_DEFAULT
            Log.d(TAG, "App icon defaulted to: $APP_ICON_DEFAULT")
        } else {
            _appIcon.value = current
        }
    }

    fun setAppIcon(key: String) {
        if (key !in APP_ICON_OPTIONS) return
        if (key == _appIcon.value) return
        applyAppIcon(key)
        prefs.appIcon = key
        _appIcon.value = key
        Log.d(TAG, "App icon switched to: $key")
    }

    private fun applyAppIcon(key: String) {
        val pm = application.packageManager
        APP_ICON_ALIASES.forEach { (option, className) ->
            val component = ComponentName(application, "${application.packageName}.$className")
            val targetState = if (option == key) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(component) != targetState) {
                pm.setComponentEnabledSetting(
                    component,
                    targetState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }



    suspend fun createConfigFile(): String? {
        val filePath = fileManager.createConfigFile(application.assets)
        if (filePath == null) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.create_config_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }

    suspend fun updateCoreStats() {
        if (!_isServiceEnabled.value) return
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
            return
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
        val file = _selectedConfigFile.value ?: return
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
        val file = _selectedConfigFile.value ?: return
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
                    ep.tag to TcpPing.pingBlocking(ep.host, ep.port)
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

    suspend fun importConfigFromClipboard(): String? {
        val filePath = fileManager.importConfigFromClipboard()
        if (filePath == null) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.import_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }

    suspend fun deleteConfigFile(file: File, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isServiceEnabled.value && _selectedConfigFile.value != null &&
                _selectedConfigFile.value == file
            ) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.config_in_use)))
                Log.w(TAG, "Attempted to delete selected config file: ${file.name}")
                return@launch
            }

            val success = fileManager.deleteConfigFile(file)
            if (success) {
                withContext(Dispatchers.Main) {
                    refreshConfigFileList()
                }
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.delete_fail)))
            }
            callback()
        }
    }

    fun extractAssetsIfNeeded() {
        fileManager.extractAssetsIfNeeded()
    }

    fun updateSocksAddress(addressString: String): Boolean {
        val matcherIpv4 = IPV4_PATTERN.matcher(addressString)
        val matcherIpv6 = IPV6_PATTERN.matcher(addressString)
        return if (matcherIpv4.matches()) {
            prefs.socksAddress = addressString
            _settingsState.value = _settingsState.value.copy(
                socksAddress = InputFieldState(addressString)
            )
            true
        } else if (matcherIpv6.matches()) {
            prefs.socksAddress = addressString
            _settingsState.value = _settingsState.value.copy(
                socksAddress = InputFieldState(addressString)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                socksAddress = InputFieldState(
                    value = addressString,
                    error = application.getString(R.string.invalid_ipv4_or_ipv6),
                    isValid = false
                )
            )
            false
        }
    }

    fun updateSocksPort(portString: String): Boolean {
        return try {
            val port = portString.toInt()
            if (port in 1025..65535) {
                prefs.socksPort = port
                _settingsState.value = _settingsState.value.copy(
                    socksPort = InputFieldState(portString)
                )
                true
            } else {
                _settingsState.value = _settingsState.value.copy(
                    socksPort = InputFieldState(
                        value = portString,
                        error = application.getString(R.string.invalid_port_range),
                        isValid = false
                    )
                )
                false
            }
        } catch (e: NumberFormatException) {
            _settingsState.value = _settingsState.value.copy(
                socksPort = InputFieldState(
                    value = portString,
                    error = application.getString(R.string.invalid_port),
                    isValid = false
                )
            )
            false
        }
    }

    fun updateSocksUser(userString: String): Boolean {
        val byteCount = userString.toByteArray(Charsets.UTF_8).size
        return if (byteCount <= 255) {
            prefs.socksUsername = userString
            _settingsState.value = _settingsState.value.copy(
                socksUser = InputFieldState(userString)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                socksUser = InputFieldState(
                    value = userString,
                    error = "Username length must not exceed 255 bytes",
                    isValid = false
                )
            )
            false
        }
    }

    fun updateSocksPass(passString: String): Boolean {
        val byteCount = passString.toByteArray(Charsets.UTF_8).size
        return if (byteCount <= 255) {
            prefs.socksPassword = passString
            _settingsState.value = _settingsState.value.copy(
                socksPass = InputFieldState(passString)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                socksPass = InputFieldState(
                    value = passString,
                    error = "Password length must not exceed 255 bytes",
                    isValid = false
                )
            )
            false
        }
    }

    fun updateDnsIpv4(ipv4Addr: String): Boolean {
        val matcher = IPV4_PATTERN.matcher(ipv4Addr)
        return if (matcher.matches()) {
            prefs.dnsIpv4 = ipv4Addr
            _settingsState.value = _settingsState.value.copy(
                dnsIpv4 = InputFieldState(ipv4Addr)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                dnsIpv4 = InputFieldState(
                    value = ipv4Addr,
                    error = application.getString(R.string.invalid_ipv4),
                    isValid = false
                )
            )
            false
        }
    }

    fun updateDnsIpv6(ipv6Addr: String): Boolean {
        val matcher = IPV6_PATTERN.matcher(ipv6Addr)
        return if (matcher.matches()) {
            prefs.dnsIpv6 = ipv6Addr
            _settingsState.value = _settingsState.value.copy(
                dnsIpv6 = InputFieldState(ipv6Addr)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                dnsIpv6 = InputFieldState(
                    value = ipv6Addr,
                    error = application.getString(R.string.invalid_ipv6),
                    isValid = false
                )
            )
            false
        }
    }

    fun setIpv6Enabled(enabled: Boolean) {
        prefs.ipv6 = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(ipv6Enabled = enabled)
        )
    }

    fun setUseTemplateEnabled(enabled: Boolean) {
        prefs.useTemplate = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(useTemplateEnabled = enabled)
        )
    }

    fun setHideFromRecentsEnabled(enabled: Boolean) {
        prefs.hideFromRecents = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(hideFromRecents = enabled)
        )
    }

    fun updateGeoUpdateInterval(hoursString: String): Boolean {
        val hours = hoursString.toIntOrNull()
        return when {
            hoursString.isBlank() || hours == null -> {
                _settingsState.value = _settingsState.value.copy(
                    geoUpdateIntervalHours = InputFieldState(
                        value = hoursString,
                        error = application.getString(R.string.invalid_geo_update_interval),
                        isValid = false
                    )
                )
                false
            }
            hours == 0 -> {
                prefs.geoUpdateIntervalHours = 0
                com.simplexray.re.service.GeoUpdateReceiver.cancel(application)
                _settingsState.value = _settingsState.value.copy(
                    geoUpdateIntervalHours = InputFieldState("0")
                )
                true
            }
            hours in 1..168 -> {
                prefs.geoUpdateIntervalHours = hours
                com.simplexray.re.service.GeoUpdateReceiver.schedule(application, hours)
                _settingsState.value = _settingsState.value.copy(
                    geoUpdateIntervalHours = InputFieldState(hours.toString())
                )
                true
            }
            else -> {
                _settingsState.value = _settingsState.value.copy(
                    geoUpdateIntervalHours = InputFieldState(
                        value = hoursString,
                        error = application.getString(R.string.invalid_geo_update_interval),
                        isValid = false
                    )
                )
                false
            }
        }
    }

    fun setHttpProxyEnabled(enabled: Boolean) {
        prefs.httpProxyEnabled = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(httpProxyEnabled = enabled)
        )
    }

    fun setBypassLanEnabled(enabled: Boolean) {
        prefs.bypassLan = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(bypassLanEnabled = enabled)
        )
    }

    fun setLogLevel(logLevel: LogLevel) {
        prefs.logLevel = logLevel
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(logLevel = logLevel)
        )
    }

    fun setDisableVpnEnabled(enabled: Boolean) {
        prefs.disableVpn = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(disableVpn = enabled)
        )
    }

    fun setTheme(mode: ThemeMode) {
        prefs.theme = mode
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(themeMode = mode)
        )
        reloadView?.invoke()
    }

    fun importRuleFile(uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = fileManager.importRuleFile(uri, fileName)
            if (success) {
                when (fileName) {
                    "geoip.dat" -> {
                        _settingsState.value = _settingsState.value.copy(
                            files = _settingsState.value.files.copy(
                                isGeoipCustom = prefs.customGeoipImported
                            ),
                            info = _settingsState.value.info.copy(
                                geoipSummary = fileManager.getRuleFileSummary("geoip.dat")
                            )
                        )
                    }

                    "geosite.dat" -> {
                        _settingsState.value = _settingsState.value.copy(
                            files = _settingsState.value.files.copy(
                                isGeositeCustom = prefs.customGeositeImported
                            ),
                            info = _settingsState.value.info.copy(
                                geositeSummary = fileManager.getRuleFileSummary("geosite.dat")
                            )
                        )
                    }
                }
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
            if (_selectedConfigFile.value == null) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
                Log.w(TAG, "Cannot start service: no config file selected.")
                setControlMenuClickable(true)
                return@launch
            }
            val intent = Intent(application, TProxyService::class.java).setAction(action)
            _uiEvent.trySend(MainViewUiEvent.StartService(intent))
        }
    }

    fun editConfig(filePath: String) {
        viewModelScope.launch {
            configEditViewModel = ConfigEditViewModel(application, filePath, prefs)
            _uiEvent.trySend(MainViewUiEvent.Navigate(ROUTE_CONFIG_EDIT))
        }
    }

    fun shareIntent(chooserIntent: Intent, packageManager: PackageManager) {
        viewModelScope.launch {
            if (chooserIntent.resolveActivity(packageManager) != null) {
                _uiEvent.trySend(MainViewUiEvent.ShareLauncher(chooserIntent))
                Log.d(TAG, "Export intent resolved and started.")
            } else {
                Log.w(TAG, "No activity found to handle export intent.")
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
                        application.getString(R.string.no_app_for_export)
                    )
                )
            }
        }
    }

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
            if (_selectedConfigFile.value == null) {
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
            appListViewModel = AppListViewModel(application)
            _uiEvent.trySend(MainViewUiEvent.Navigate(ROUTE_APP_LIST))
        }
    }

    fun moveConfigFile(fromIndex: Int, toIndex: Int) {
        val currentList = _configFiles.value.toMutableList()
        val movedItem = currentList.removeAt(fromIndex)
        currentList.add(toIndex, movedItem)
        _configFiles.value = currentList
        prefs.configFilesOrder = currentList.map { it.name }
    }



    fun importConfigFromFile(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = fileManager.importConfigFileFromUri(application, uri)
            if (path != null) {
                refreshConfigFileList()
                if (_isServiceEnabled.value) {
                    // Keep the running core untouched: do not switch the selected
                    // config while the service is active. The user can still pick
                    // the imported file manually (which reloads the core).
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(
                            application.getString(R.string.config_import_service_running)
                        )
                    )
                } else {
                    updateSelectedConfigFile(File(path))
                }
            }
        }
    }

    fun refreshConfigFileList() {
        viewModelScope.launch(Dispatchers.IO) {
            val filesDir = application.filesDir
            val actualFiles =
                filesDir.listFiles { file -> file.isFile && file.isConfigFile() && file.name != "extra_api.json" }?.toList()
                    ?: emptyList()
            val actualFilesByName = actualFiles.associateBy { it.name }
            val savedOrder = prefs.configFilesOrder

            val newOrder = mutableListOf<File>()
            val remainingActualFileNames = actualFilesByName.toMutableMap()

            savedOrder.forEach { filename ->
                actualFilesByName[filename]?.let { file ->
                    newOrder.add(file)
                    remainingActualFileNames.remove(filename)
                }
            }

            newOrder.addAll(remainingActualFileNames.values.filter { it !in newOrder })

            _configFiles.value = newOrder
            prefs.configFilesOrder = newOrder.map { it.name }

            val currentSelectedPath = prefs.selectedConfigPath
            var fileToSelect: File? = null

            if (currentSelectedPath != null) {
                val foundSelected = newOrder.find { it.absolutePath == currentSelectedPath }
                if (foundSelected != null) {
                    fileToSelect = foundSelected
                }
            }

            if (fileToSelect == null) {
                fileToSelect = newOrder.firstOrNull()
            }

            _selectedConfigFile.value = fileToSelect
            prefs.selectedConfigPath = fileToSelect?.absolutePath
        }
    }

    fun updateSelectedConfigFile(file: File?) {
        _selectedConfigFile.value = file
        prefs.selectedConfigPath = file?.absolutePath
    }

    fun registerTProxyServiceReceivers() {
        val application = application
        val startSuccessFilter = IntentFilter(TProxyService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(
                startReceiver,
                startSuccessFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(startReceiver, startSuccessFilter)
        }

        val stopSuccessFilter = IntentFilter(TProxyService.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(
                stopReceiver,
                stopSuccessFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(stopReceiver, stopSuccessFilter)
        }

        val startFailedFilter = IntentFilter(TProxyService.ACTION_START_FAILED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(
                startFailedReceiver,
                startFailedFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(startFailedReceiver, startFailedFilter)
        }
        Log.d(TAG, "TProxyService receivers registered.")
    }

    fun unregisterTProxyServiceReceivers() {
        val application = application
        application.unregisterReceiver(startReceiver)
        application.unregisterReceiver(stopReceiver)
        application.unregisterReceiver(startFailedReceiver)
        Log.d(TAG, "TProxyService receivers unregistered.")
    }

    fun restoreDefaultGeoip(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeoip()
            _settingsState.value = _settingsState.value.copy(
                files = _settingsState.value.files.copy(
                    isGeoipCustom = prefs.customGeoipImported
                ),
                info = _settingsState.value.info.copy(
                    geoipSummary = fileManager.getRuleFileSummary("geoip.dat")
                )
            )
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
            _settingsState.value = _settingsState.value.copy(
                files = _settingsState.value.files.copy(
                    isGeositeCustom = prefs.customGeositeImported
                ),
                info = _settingsState.value.info.copy(
                    geositeSummary = fileManager.getRuleFileSummary("geosite.dat")
                )
            )
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

                val body = response.body ?: throw IOException("Response body is null")
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
                            when (targetName) {
                                "geoip.dat" -> {
                                    _settingsState.value = _settingsState.value.copy(
                                        files = _settingsState.value.files.copy(
                                            isGeoipCustom = prefs.customGeoipImported
                                        ),
                                        info = _settingsState.value.info.copy(
                                            geoipSummary = fileManager.getRuleFileSummary("geoip.dat")
                                        )
                                    )
                                }

                                "geosite.dat" -> {
                                    _settingsState.value = _settingsState.value.copy(
                                        files = _settingsState.value.files.copy(
                                            isGeositeCustom = prefs.customGeositeImported
                                        ),
                                        info = _settingsState.value.info.copy(
                                            geositeSummary = fileManager.getRuleFileSummary("geosite.dat")
                                        )
                                    )
                                }
                            }
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
            // Defense: reject standard GEO file names (case-insensitive) before importing.
            val candidateName = fileManager.getDatFileNameFromUri(application, uri)
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
                .replace("github.com", "api.github.com/repos") + "/releases/latest"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            try {
                val response = client.newCall(request).await()
                val responseBody = response.body?.string() ?: ""
                val json = org.json.JSONObject(responseBody)
                val tagName = json.optString("tag_name", "").removePrefix("v")
                Log.d(TAG, "Latest version tag: $tagName")
                val updateAvailable = tagName.isNotEmpty() && compareVersions(tagName) > 0
                if (updateAvailable) {
                    _newVersionAvailable.value = tagName
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

    private fun compareVersions(version1: String): Int {
        val parts1 = version1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 =
            BuildConfig.VERSION_NAME.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    companion object {
        private const val IPV4_REGEX =
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        private val IPV4_PATTERN: Pattern = Pattern.compile(IPV4_REGEX)
        private const val IPV6_REGEX =
            "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80::(fe80(:[0-9a-fA-F]{0,4})?){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d))$"
        private val IPV6_PATTERN: Pattern = Pattern.compile(IPV6_REGEX)

        @Suppress("DEPRECATION")
        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return activityManager.getRunningServices(Int.MAX_VALUE).any { service ->
                serviceClass.name == service.service.className
            }
        }
    }
}

class MainViewModelFactory(
    private val application: Application
) : ViewModelProvider.AndroidViewModelFactory(application) {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

