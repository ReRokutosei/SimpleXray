package com.simplexray.re.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.Log
import com.simplexray.re.prefs.Preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AppIconController"

private const val APP_ICON_DEFAULT = "lineal"
private val APP_ICON_OPTIONS = listOf("flat", "lineal", "lineal_color")
// Full component class names: manifest relative names (".MainActivityFlat") are
// resolved against the namespace (com.simplexray.re), NOT the applicationId —
// the debug build has applicationIdSuffix ".debug" and must keep working.
private val APP_ICON_ALIASES = listOf(
    "flat" to "com.simplexray.re.MainActivityFlat",
    "lineal" to "com.simplexray.re.MainActivityLineal",
    "lineal_color" to "com.simplexray.re.MainActivityLinealColor"
)

internal class AppIconController(
    private val application: Application,
    private val prefs: Preferences,
) {
    private val _appIcon = MutableStateFlow(prefs.appIcon ?: APP_ICON_DEFAULT)
    val appIcon: StateFlow<String> = _appIcon.asStateFlow()

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
            // packageName here is the applicationId (may carry the ".debug"
            // suffix); className is the full component name (namespace-based),
            // which is what the merged manifest actually declares.
            val component = ComponentName(application.packageName, className)
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
}
