package com.simplexray.re.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.FilenameValidator
import com.simplexray.re.common.isConfigFile
import com.simplexray.re.data.source.FileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.File
import java.io.IOException

private const val TAG = "ConfigEditViewModel"

sealed class ConfigEditUiEvent {
    data class ShowSnackbar(val message: String) : ConfigEditUiEvent()
    data class MarkSaved(val documentVersion: Int) : ConfigEditUiEvent()
    data object NavigateBack : ConfigEditUiEvent()
}

data class ConfigDocumentSnapshot(
    val content: String = "",
    val generation: Int = 0
)

class ConfigEditViewModel(
    application: Application,
    private val initialFilePath: String,
    private val prefs: Preferences
) : AndroidViewModel(application) {

    private var _configFile: File
    private var _originalFilePath: String = initialFilePath
    @Volatile
    private var _savedSelectedConfig: Boolean = false

    private val _documentSnapshot = MutableStateFlow(ConfigDocumentSnapshot())
    val documentSnapshot: StateFlow<ConfigDocumentSnapshot> = _documentSnapshot.asStateFlow()

    private val _filename = MutableStateFlow("")
    val filename: StateFlow<String> = _filename.asStateFlow()

    private val _fileExtension = MutableStateFlow("json")
    val fileExtension: StateFlow<String> = _fileExtension.asStateFlow()

    private val _filenameErrorMessage = MutableStateFlow<String?>(null)
    val filenameErrorMessage: StateFlow<String?> = _filenameErrorMessage.asStateFlow()

    private val _uiEvent = Channel<ConfigEditUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _hasFilenameChanged = MutableStateFlow(false)
    val hasFilenameChanged: StateFlow<Boolean> = _hasFilenameChanged.asStateFlow()

    private val fileManager: FileManager = FileManager(application, prefs)

    init {
        _configFile = File(initialFilePath)
        _filename.value = _configFile.nameWithoutExtension
        _fileExtension.value = _configFile.extension.lowercase().ifEmpty { "json" }

        viewModelScope.launch(Dispatchers.IO) {
            val content = readConfigFileContent()
            withContext(Dispatchers.Main) {
                _documentSnapshot.value = ConfigDocumentSnapshot(
                    content = content,
                    generation = _documentSnapshot.value.generation + 1
                )
            }
        }
    }

    private val File.nameWithoutExtension: String
        get() {
            val name = this.name
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(0, dot) else name
        }

    private suspend fun readConfigFileContent(): String = withContext(Dispatchers.IO) {
        if (!_configFile.exists()) {
            Log.e(TAG, "Config not found at path: $initialFilePath")
            return@withContext ""
        }
        try {
            _configFile.readText()
        } catch (e: IOException) {
            Log.e(TAG, "Error reading config file", e)
            ""
        }
    }

    fun onFilenameChange(newFilename: String) {
        _filename.value = newFilename
        _filenameErrorMessage.value = validateFilename(newFilename)
        _hasFilenameChanged.value = newFilename.trim() != _configFile.nameWithoutExtension
    }

    fun discardUnsavedChanges() {
        _filename.value = _configFile.nameWithoutExtension
        _filenameErrorMessage.value = null
        _hasFilenameChanged.value = false
        _documentSnapshot.value = _documentSnapshot.value.copy(
            generation = _documentSnapshot.value.generation + 1
        )
    }

    fun consumeSavedSelectedConfig(): Boolean {
        val saved = _savedSelectedConfig
        _savedSelectedConfig = false
        return saved
    }

    private fun validateFilename(name: String): String? {
        return FilenameValidator.validateFilename(application, name)
    }

    fun saveConfigFile(rawContent: String, documentVersion: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFilePath = _configFile.absolutePath

            var newFilename = _filename.value.trim()

            val validationError = validateFilename(newFilename)
            if (validationError != null) {
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar(validationError))
                return@launch
            }

            val originalExt = _configFile.extension.let { if (it.isNotEmpty()) ".$it" else ".json" }
            if (!newFilename.isConfigFile()) {
                newFilename += originalExt
            }

            val parentDir = _configFile.parentFile
            if (parentDir == null) {
                Log.e(TAG, "Could not determine parent directory.")
                return@launch
            }
            val newFile = File(parentDir, newFilename)

            if (newFile.exists() && newFile.absolutePath != _configFile.absolutePath) {
                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.filename_already_exists)
                    )
                )
                return@launch
            }

            var formattedContent: String = rawContent
            if (newFile.name.lowercase().endsWith(".json")) {
                try {
                    formattedContent = ConfigUtils.formatConfigContent(formattedContent)
                } catch (e: JSONException) {
                    Log.e(TAG, "Invalid JSON format", e)
                    _uiEvent.trySend(
                        ConfigEditUiEvent.ShowSnackbar(
                            application.getString(R.string.invalid_config_format)
                        )
                    )
                    return@launch
                }
            }

            val success = fileManager.renameConfigFile(_configFile, newFile, formattedContent)

            if (success) {
                if (newFile.absolutePath != oldFilePath) {
                    _configFile = newFile
                    _originalFilePath = newFile.absolutePath
                }
                if (prefs.selectedConfigPath == _configFile.absolutePath) {
                    _savedSelectedConfig = true
                }

                withContext(Dispatchers.Main) {
                    _filename.value = _configFile.nameWithoutExtension
                    _fileExtension.value = _configFile.extension.lowercase().ifEmpty { "json" }
                    _hasFilenameChanged.value = false
                    if (formattedContent != rawContent) {
                        _documentSnapshot.value = ConfigDocumentSnapshot(
                            content = formattedContent,
                            generation = _documentSnapshot.value.generation + 1
                        )
                    } else {
                        _documentSnapshot.value = _documentSnapshot.value.copy(
                            content = formattedContent
                        )
                        _uiEvent.trySend(ConfigEditUiEvent.MarkSaved(documentVersion))
                    }
                    _uiEvent.trySend(
                        ConfigEditUiEvent.ShowSnackbar(
                            application.getString(R.string.config_save_success)
                        )
                    )
                }
            } else {
                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.save_fail)
                    )
                )
            }
        }
    }

    companion object {
        fun factory(
            application: Application,
            initialFilePath: String,
            prefs: Preferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConfigEditViewModel(
                    application = application,
                    initialFilePath = initialFilePath,
                    prefs = prefs
                ) as T
            }
        }
    }
}
