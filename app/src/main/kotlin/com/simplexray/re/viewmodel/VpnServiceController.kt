package com.simplexray.re.viewmodel

import android.app.Application
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import com.simplexray.re.R
import com.simplexray.re.prefs.Preferences
import com.simplexray.re.service.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "VpnServiceController"

internal class VpnServiceController(
    private val application: Application,
    private val prefs: Preferences,
    private val scope: CoroutineScope,
    private val selectedConfig: StateFlow<File?>,
    private val sendEvent: (MainViewUiEvent) -> Unit,
) {
    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    fun setServiceEnabled(enabled: Boolean) {
        _isServiceEnabled.value = enabled
        prefs.enable = enabled
    }

    fun startTProxyService(action: String) {
        scope.launch {
            if (selectedConfig.value == null) {
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
                Log.w(TAG, "Cannot start service: no config file selected.")
                return@launch
            }
            val intent = Intent(application, TProxyService::class.java).setAction(action)
            sendEvent(MainViewUiEvent.StartService(intent))
        }
    }

    fun stopTProxyService() {
        scope.launch {
            val intent = Intent(
                application,
                TProxyService::class.java
            ).setAction(TProxyService.ACTION_DISCONNECT)
            sendEvent(MainViewUiEvent.StartService(intent))
        }
    }

    fun prepareAndStartVpn(vpnPrepareLauncher: ActivityResultLauncher<Intent>) {
        scope.launch {
            if (selectedConfig.value == null) {
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
                Log.w(TAG, "Cannot prepare VPN: no config file selected.")
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
}
