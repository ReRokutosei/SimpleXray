package com.simplexray.re.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.simplexray.re.R
import com.simplexray.re.common.ROUTE_CONFIG_EDIT
import com.simplexray.re.common.isConfigFile
import com.simplexray.re.data.source.FileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class ConfigFileController(
    private val application: Application,
    private val prefs: Preferences,
    private val fileManager: FileManager,
    private val scope: CoroutineScope,
    private val isVpnEnabled: () -> Boolean,
    private val sendEvent: (MainViewUiEvent) -> Unit,
    private val onConfigSelected: (File?) -> Unit,
) {
    private val _configFiles = MutableStateFlow<List<File>>(emptyList())
    val configFiles: StateFlow<List<File>> = _configFiles.asStateFlow()

    private val _selectedConfigFile = MutableStateFlow<File?>(null)
    val selectedConfigFile: StateFlow<File?> = _selectedConfigFile.asStateFlow()

    var editingFilePath: String? = null

    suspend fun createConfigFile(): String? {
        val filePath = fileManager.createConfigFile(application.assets)
        if (filePath == null) {
            sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.create_config_failed)))
        } else {
            rebuildConfigFileList()
        }
        return filePath
    }

    suspend fun importConfigFromClipboard(): String? {
        val filePath = fileManager.importConfigFromClipboard()
        if (filePath == null) {
            sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.import_failed)))
        } else {
            rebuildConfigFileList()
        }
        return filePath
    }

    fun deleteConfigFile(file: File) {
        scope.launch(Dispatchers.IO) {
            if (isVpnEnabled() && _selectedConfigFile.value != null &&
                _selectedConfigFile.value == file
            ) {
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.config_in_use)))
                Log.w(TAG, "Attempted to delete selected config file: ${file.name}")
                return@launch
            }

            val success = fileManager.deleteConfigFile(file)
            if (success) {
                // Rebuild (and reselect) before surfacing completion. The old
                // callback chain cleared the selected file and then raced the
                // async refresh, which could wipe the active selection.
                rebuildConfigFileList()
            } else {
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.delete_fail)))
            }
        }
    }

    fun editConfig(filePath: String) {
        scope.launch {
            editingFilePath = filePath
            sendEvent(MainViewUiEvent.Navigate(ROUTE_CONFIG_EDIT))
        }
    }

    fun shareIntent(chooserIntent: Intent, packageManager: PackageManager) {
        scope.launch {
            if (chooserIntent.resolveActivity(packageManager) != null) {
                sendEvent(MainViewUiEvent.ShareLauncher(chooserIntent))
                Log.d(TAG, "Export intent resolved and started.")
            } else {
                Log.w(TAG, "No activity found to handle export intent.")
                sendEvent(
                    MainViewUiEvent.ShowSnackbar(
                        application.getString(R.string.no_app_for_export)
                    )
                )
            }
        }
    }

    fun moveConfigFile(fromIndex: Int, toIndex: Int) {
        val currentList = _configFiles.value.toMutableList()
        val movedItem = currentList.removeAt(fromIndex)
        currentList.add(toIndex, movedItem)
        _configFiles.value = currentList
    }

    fun persistConfigFilesOrder() {
        prefs.configFilesOrder = _configFiles.value.map { it.name }
    }

    fun importConfigFromFile(uri: android.net.Uri) {
        scope.launch(Dispatchers.IO) {
            val path = fileManager.importConfigFileFromUri(application, uri)
            if (path != null) {
                // Await the rebuild so the subsequent selection update cannot be
                // overwritten by a late fire-and-forget refresh coroutine.
                rebuildConfigFileList()
                if (isVpnEnabled()) {
                    // Keep the running core untouched: do not switch the selected
                    // config while the service is active. The user can still pick
                    // the imported file manually (which reloads the core).
                    sendEvent(
                        MainViewUiEvent.ShowSnackbar(
                            application.getString(R.string.config_import_service_running)
                        )
                    )
                } else {
                    updateSelectedConfigFile(File(path))
                }
            } else {
                sendEvent(
                    MainViewUiEvent.ShowSnackbar(
                        application.getString(R.string.unsupported_config_format)
                    )
                )
            }
        }
    }

    fun refreshConfigFileList() {
        scope.launch(Dispatchers.IO) {
            rebuildConfigFileList()
        }
    }

    private suspend fun rebuildConfigFileList() {
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

        setSelectedConfig(fileToSelect)
    }

    fun updateSelectedConfigFile(file: File?) {
        setSelectedConfig(file)
    }

    private fun setSelectedConfig(file: File?) {
        val changed = _selectedConfigFile.value?.absolutePath != file?.absolutePath
        _selectedConfigFile.value = file
        prefs.selectedConfigPath = file?.absolutePath
        if (changed) onConfigSelected(file)
    }

    companion object {
        private const val TAG = "ConfigFileController"
    }
}
