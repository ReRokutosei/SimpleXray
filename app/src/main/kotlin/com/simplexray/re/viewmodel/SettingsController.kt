package com.simplexray.re.viewmodel

import android.app.Application
import android.util.Log
import com.simplexray.re.BuildConfig
import com.simplexray.re.R
import com.simplexray.re.data.source.FileManager
import com.simplexray.re.prefs.LogLevel
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.ThemeMode
import com.simplexray.re.common.isLoopbackAddress
import com.simplexray.re.service.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

private const val TAG = "SettingsController"
private const val IPV4_REGEX =
    "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
private val IPV4_PATTERN: Pattern = Pattern.compile(IPV4_REGEX)
private const val IPV6_REGEX =
    "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80::(fe80(:[0-9a-fA-F]{0,4})?){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d))$"
private val IPV6_PATTERN: Pattern = Pattern.compile(IPV6_REGEX)

internal class SettingsController(
    private val application: Application,
    private val prefs: Preferences,
    private val fileManager: FileManager,
    private val scope: CoroutineScope,
    private val isVpnEnabled: () -> Boolean,
    private val showSnackbar: (String) -> Unit,
) {
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
                hideFromRecents = prefs.hideFromRecents,
                keepAwake = prefs.keepAwake,
                httpProxyEnabled = prefs.httpProxyEnabled,
                bypassLanEnabled = prefs.bypassLan,
                disableVpn = prefs.disableVpn,
                tunnelMode = prefs.tunnelMode,
                themeMode = prefs.theme,
                logLevel = prefs.logLevel,
                accessLog = prefs.accessLog,
                dnsLog = prefs.dnsLog
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
            geoUpdateIntervalHours = InputFieldState(prefs.geoUpdateIntervalHours.toString()),
            lastGeoUpdateTime = prefs.lastGeoUpdateTime,
            tunnelMtu = InputFieldState(prefs.tunnelMtu.toString())
        )
    )
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    fun updateSettingsState() {
        _settingsState.value = _settingsState.value.copy(
            socksAddress = InputFieldState(prefs.socksAddress),
            socksPort = InputFieldState(prefs.socksPort.toString()),
            socksUser = InputFieldState(prefs.socksUsername),
            socksPass = InputFieldState(prefs.socksPassword),
            dnsIpv4 = InputFieldState(prefs.dnsIpv4),
            dnsIpv6 = InputFieldState(prefs.dnsIpv6),
            switches = SwitchStates(
                ipv6Enabled = prefs.ipv6,
                hideFromRecents = prefs.hideFromRecents,
                keepAwake = prefs.keepAwake,
                httpProxyEnabled = prefs.httpProxyEnabled,
                bypassLanEnabled = prefs.bypassLan,
                disableVpn = prefs.disableVpn,
                tunnelMode = prefs.tunnelMode,
                themeMode = prefs.theme,
                logLevel = prefs.logLevel,
                accessLog = prefs.accessLog,
                dnsLog = prefs.dnsLog
            ),
            info = _settingsState.value.info.copy(
                appVersion = BuildConfig.VERSION_NAME,
                geoipUrl = prefs.geoipUrl,
                geositeUrl = prefs.geositeUrl
            ),
            files = FileStates(
                isGeoipCustom = prefs.customGeoipImported,
                isGeositeCustom = prefs.customGeositeImported
            ),
            geoUpdateIntervalHours = InputFieldState(prefs.geoUpdateIntervalHours.toString()),
            lastGeoUpdateTime = prefs.lastGeoUpdateTime
        )
        refreshRuleFileSummaries()
    }

    /**
     * Reads rule-file metadata off the main thread; the summaries are rendered
     * by SettingsScreen and only need to be refreshed, not awaited.
     */
    private fun refreshRuleFileSummaries() {
        scope.launch(Dispatchers.IO) {
            val geoipSummary = fileManager.getRuleFileSummary("geoip.dat")
            val geositeSummary = fileManager.getRuleFileSummary("geosite.dat")
            withContext(Dispatchers.Main) {
                _settingsState.value = _settingsState.value.copy(
                    info = _settingsState.value.info.copy(
                        geoipSummary = geoipSummary,
                        geositeSummary = geositeSummary
                    )
                )
            }
        }
    }

    fun loadKernelVersion() {
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

    fun refreshRuleFileState(fileName: String) {
        scope.launch(Dispatchers.IO) {
            val summary = fileManager.getRuleFileSummary(fileName)
            withContext(Dispatchers.Main) {
                when (fileName) {
                    "geoip.dat" -> {
                        _settingsState.value = _settingsState.value.copy(
                            files = _settingsState.value.files.copy(
                                isGeoipCustom = prefs.customGeoipImported
                            ),
                            info = _settingsState.value.info.copy(
                                geoipSummary = summary
                            )
                        )
                    }

                    "geosite.dat" -> {
                        _settingsState.value = _settingsState.value.copy(
                            files = _settingsState.value.files.copy(
                                isGeositeCustom = prefs.customGeositeImported
                            ),
                            info = _settingsState.value.info.copy(
                                geositeSummary = summary
                            )
                        )
                    }
                }
            }
        }
    }

    fun updateSocksAddress(addressString: String): Boolean {
        val matcherIpv4 = IPV4_PATTERN.matcher(addressString)
        val matcherIpv6 = IPV6_PATTERN.matcher(addressString)
        if (!matcherIpv4.matches() && !matcherIpv6.matches()) {
            showSnackbar(application.getString(R.string.invalid_ipv4_or_ipv6))
            return false
        }
        if (!isLoopbackAddress(addressString)) {
            // Internal health probes and rule/update downloads connect through
            // 127.0.0.1, so a non-loopback SOCKS bind would be unreachable and
            // trigger a restart loop.
            showSnackbar(application.getString(R.string.socks_address_loopback_only))
            return false
        }

        val changed = prefs.socksAddress != addressString
        prefs.socksAddress = addressString
        _settingsState.value = _settingsState.value.copy(
            socksAddress = InputFieldState(addressString)
        )
        if (changed) notifySocksSettingsChangedWhileRunning()
        return true
    }

    fun updateSocksPort(portString: String): Boolean {
        return try {
            val port = portString.toInt()
            if (port in 1025..65535) {
                val changed = prefs.socksPort != port
                prefs.socksPort = port
                _settingsState.value = _settingsState.value.copy(
                    socksPort = InputFieldState(portString)
                )
                if (changed) notifySocksSettingsChangedWhileRunning()
                true
            } else {
                showSnackbar(application.getString(R.string.invalid_port_range))
                false
            }
        } catch (e: NumberFormatException) {
            showSnackbar(application.getString(R.string.invalid_port))
            false
        }
    }

    fun updateSocksUser(userString: String): Boolean {
        if (userString.contains('\n') || userString.contains('\r')) {
            showSnackbar(application.getString(R.string.socks_credentials_no_line_breaks))
            return false
        }
        val byteCount = userString.toByteArray(Charsets.UTF_8).size
        if (byteCount > 255) {
            showSnackbar(application.getString(R.string.socks_username_too_long))
            return false
        }

        val changed = prefs.socksUsername != userString
        prefs.socksUsername = userString
        val paired = userString.isNotEmpty() == prefs.socksPassword.isNotEmpty()
        val error = if (paired) null else application.getString(R.string.socks_credentials_pair_required)
        _settingsState.value = _settingsState.value.copy(
            socksUser = InputFieldState(userString, error = error, isValid = paired),
            socksPass = _settingsState.value.socksPass.copy(error = error, isValid = paired)
        )
        if (changed) notifySocksSettingsChangedWhileRunning()
        if (!paired) showSnackbar(error ?: application.getString(R.string.socks_credentials_pair_required))
        return paired
    }

    fun updateSocksPass(passString: String): Boolean {
        if (passString.contains('\n') || passString.contains('\r')) {
            showSnackbar(application.getString(R.string.socks_credentials_no_line_breaks))
            return false
        }
        val byteCount = passString.toByteArray(Charsets.UTF_8).size
        if (byteCount > 255) {
            showSnackbar(application.getString(R.string.socks_password_too_long))
            return false
        }

        val changed = prefs.socksPassword != passString
        prefs.socksPassword = passString
        val paired = prefs.socksUsername.isNotEmpty() == passString.isNotEmpty()
        val error = if (paired) null else application.getString(R.string.socks_credentials_pair_required)
        _settingsState.value = _settingsState.value.copy(
            socksUser = _settingsState.value.socksUser.copy(error = error, isValid = paired),
            socksPass = InputFieldState(passString, error = error, isValid = paired)
        )
        if (changed) notifySocksSettingsChangedWhileRunning()
        if (!paired) showSnackbar(error ?: application.getString(R.string.socks_credentials_pair_required))
        return paired
    }

    /**
     * Adopts the primary SOCKS credentials declared by [configPath] once per
     * selected config, but only while the app-side credentials are still empty.
     */
    fun syncSocksCredentialsFromConfig(configPath: String?) {
        val path = configPath ?: return
        if (prefs.socksUsername.isNotEmpty() || prefs.socksPassword.isNotEmpty()) return
        if (prefs.socksAuthSyncedConfigPath == path) return

        scope.launch(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@launch
            val content = runCatching { file.readText() }.getOrNull() ?: return@launch
            val credentials = runCatching { ConfigUtils.extractPrimarySocksCredentials(content) }.getOrNull()
            prefs.socksAuthSyncedConfigPath = path
            if (credentials == null) return@launch

            prefs.socksUsername = credentials.first
            prefs.socksPassword = credentials.second
            withContext(Dispatchers.Main) {
                _settingsState.value = _settingsState.value.copy(
                    socksUser = InputFieldState(credentials.first),
                    socksPass = InputFieldState(credentials.second)
                )
                showSnackbar(application.getString(R.string.socks_credentials_synced))
            }
        }
    }

    private fun notifySocksSettingsChangedWhileRunning() {
        if (isVpnEnabled()) {
            showSnackbar(application.getString(R.string.socks_settings_restart_notice))
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
                com.simplexray.re.service.GeoUpdateWorker.cancel(application)
                _settingsState.value = _settingsState.value.copy(
                    geoUpdateIntervalHours = InputFieldState("0")
                )
                true
            }
            hours in 1..168 -> {
                prefs.geoUpdateIntervalHours = hours
                com.simplexray.re.service.GeoUpdateWorker.schedule(application, hours, forceUpdate = true)
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

    fun updateTunnelMtu(mtuString: String): Boolean {
        val mtu = mtuString.toIntOrNull()
        return when {
            mtu == null -> {
                _settingsState.value = _settingsState.value.copy(
                    tunnelMtu = InputFieldState(
                        value = mtuString,
                        error = application.getString(R.string.invalid_mtu),
                        isValid = false
                    )
                )
                false
            }
            mtu in 1280..9000 -> {
                prefs.tunnelMtu = mtu
                _settingsState.value = _settingsState.value.copy(
                    tunnelMtu = InputFieldState(mtu.toString())
                )
                true
            }
            else -> {
                _settingsState.value = _settingsState.value.copy(
                    tunnelMtu = InputFieldState(
                        value = mtuString,
                        error = application.getString(R.string.invalid_mtu),
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

    fun setKeepAwakeEnabled(enabled: Boolean) {
        prefs.keepAwake = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(keepAwake = enabled)
        )
    }

    fun setLogLevel(logLevel: LogLevel) {
        prefs.logLevel = logLevel
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(logLevel = logLevel)
        )
    }

    fun setAccessLog(enabled: Boolean) {
        prefs.accessLog = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(accessLog = enabled)
        )
    }

    fun setDnsLog(enabled: Boolean) {
        prefs.dnsLog = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(dnsLog = enabled)
        )
    }

    fun setDisableVpnEnabled(enabled: Boolean) {
        prefs.disableVpn = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(disableVpn = enabled)
        )
    }

    fun setTunnelMode(mode: com.simplexray.re.prefs.TunnelMode) {
        if (prefs.tunnelMode != mode) {
            prefs.tunnelMode = mode
            _settingsState.value = _settingsState.value.copy(
                switches = _settingsState.value.switches.copy(tunnelMode = mode)
            )
            if (isVpnEnabled()) {
                showSnackbar(application.getString(R.string.tunnel_mode_restart_notice))
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.theme = mode
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(themeMode = mode)
        )
    }
}
