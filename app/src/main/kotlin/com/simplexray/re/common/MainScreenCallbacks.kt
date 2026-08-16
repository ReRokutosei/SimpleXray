package com.simplexray.re.common

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import com.simplexray.re.R
import com.simplexray.re.service.TProxyService
import com.simplexray.re.viewmodel.LogViewModel
import com.simplexray.re.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

data class MainScreenCallbacks(
    val onCreateNewConfigFileAndEdit: () -> Unit,
    val onImportConfigFromClipboard: () -> Unit,
    val onPerformExport: () -> Unit,
    val onDeleteConfigClick: (File, () -> Unit) -> Unit,
    val onSwitchVpnService: () -> Unit
)

@Composable
fun rememberMainScreenCallbacks(
    mainViewModel: MainViewModel,
    logViewModel: LogViewModel,
    launchers: MainScreenLaunchers,
    applicationContext: Context
): MainScreenCallbacks {
    val scope = rememberCoroutineScope()
    val onCreateNewConfigFileAndEdit: () -> Unit = {
        scope.launch {
            val filePath = mainViewModel.createConfigFile()
            filePath?.let {
                mainViewModel.editConfig(it)
            }
        }
    }

    val onImportConfigFromClipboard: () -> Unit = {
        scope.launch {
            val filePath = mainViewModel.importConfigFromClipboard()
            filePath?.let {
                mainViewModel.editConfig(it)
            }
        }
    }

    val onPerformExport: () -> Unit = {
        scope.launch {
            val logFile = logViewModel.getLogFile()
            if (logFile.exists() && logViewModel.logEntries.value.isNotEmpty()) {
                try {
                    val fileUri =
                        FileProvider.getUriForFile(
                            applicationContext,
                            "${applicationContext.packageName}.fileprovider",
                            logFile
                        )
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.setType("text/plain")
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val chooserIntent =
                        Intent.createChooser(
                            shareIntent,
                            applicationContext.getString(R.string.export)
                        )
                    mainViewModel.shareIntent(chooserIntent, applicationContext.packageManager)
                } catch (e: IllegalArgumentException) {
                    Log.e(
                        "MainActivity",
                        "Error getting Uri for file using FileProvider during export.",
                        e
                    )
                    mainViewModel.showExportFailedSnackbar()
                }
            } else {
                Log.w("MainActivity", "Export log file is null, empty, or no logs in adapter.")
            }
        }
    }

    val onDeleteConfigClick: (File, () -> Unit) -> Unit = { file, callback ->
        scope.launch {
            mainViewModel.deleteConfigFile(file, callback)
        }
    }

    val onSwitchVpnService: () -> Unit = {
        if (mainViewModel.isServiceEnabled.value) {
            mainViewModel.setServiceEnabled(false)
            mainViewModel.stopTProxyService()
        } else {
            mainViewModel.setControlMenuClickable(false)
            if (mainViewModel.settingsState.value.switches.disableVpn) {
                mainViewModel.startTProxyService(TProxyService.ACTION_START)
            } else {
                mainViewModel.prepareAndStartVpn(launchers.vpnPrepareLauncher)
            }
        }
    }

    return MainScreenCallbacks(
        onCreateNewConfigFileAndEdit = onCreateNewConfigFileAndEdit,
        onImportConfigFromClipboard = onImportConfigFromClipboard,
        onPerformExport = onPerformExport,
        onDeleteConfigClick = onDeleteConfigClick,
        onSwitchVpnService = onSwitchVpnService
    )
}
