package com.simplexray.re.viewmodel

import com.simplexray.re.common.ThemeMode

import com.simplexray.re.prefs.LogLevel
import com.simplexray.re.prefs.TunnelMode

data class InputFieldState(
    val value: String,
    val error: String? = null,
    val isValid: Boolean = true
)

data class SwitchStates(
    val ipv6Enabled: Boolean,
    val hideFromRecents: Boolean = true,
    val keepAwake: Boolean = false,
    val httpProxyEnabled: Boolean,
    val bypassLanEnabled: Boolean,
    val disableVpn: Boolean,
    val tunnelMode: TunnelMode,
    val themeMode: ThemeMode,
    val logLevel: LogLevel = LogLevel.Auto,
    val accessLog: Boolean = true,
    val dnsLog: Boolean = false
)

data class InfoStates(
    val appVersion: String,
    val kernelVersion: String,
    val geoipSummary: String,
    val geositeSummary: String,
    val geoipUrl: String,
    val geositeUrl: String
)

data class FileStates(
    val isGeoipCustom: Boolean,
    val isGeositeCustom: Boolean
)

data class SettingsState(
    val socksAddress: InputFieldState,
    val socksPort: InputFieldState,
    val socksUser: InputFieldState,
    val socksPass: InputFieldState,
    val dnsIpv4: InputFieldState,
    val dnsIpv6: InputFieldState,
    val switches: SwitchStates,
    val info: InfoStates,
    val files: FileStates,
    val geoUpdateIntervalHours: InputFieldState = InputFieldState("0"),
    val lastGeoUpdateTime: Long = 0L,
    val tunnelMtu: InputFieldState = InputFieldState("1500")
) 
