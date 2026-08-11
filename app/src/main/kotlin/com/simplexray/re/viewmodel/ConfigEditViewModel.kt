package com.simplexray.re.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.isConfigFile
import com.simplexray.re.common.FilenameValidator
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
    data object NavigateBack : ConfigEditUiEvent()
}

class ConfigEditViewModel(
    application: Application,
    private val initialFilePath: String,
    prefs: Preferences
) :
    AndroidViewModel(application) {

    private var _configFile: File
    private var _originalFilePath: String = initialFilePath

    private val _configTextFieldValue = MutableStateFlow(TextFieldValue())
    val configTextFieldValue: StateFlow<TextFieldValue> = _configTextFieldValue.asStateFlow()

    private val _filename = MutableStateFlow("")
    val filename: StateFlow<String> = _filename.asStateFlow()

    private val _filenameErrorMessage = MutableStateFlow<String?>(null)
    val filenameErrorMessage: StateFlow<String?> = _filenameErrorMessage.asStateFlow()

    private val _uiEvent = Channel<ConfigEditUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _hasConfigChanged = MutableStateFlow(false)
    val hasConfigChanged: StateFlow<Boolean> = _hasConfigChanged.asStateFlow()

    private val fileManager: FileManager = FileManager(application, prefs)

    init {
        _configFile = File(initialFilePath)
        _filename.value = _configFile.nameWithoutExtension

        viewModelScope.launch(Dispatchers.IO) {
            val content = readConfigFileContent()
            withContext(Dispatchers.Main) {
                _configTextFieldValue.value = _configTextFieldValue.value.copy(text = content)
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

    fun onConfigContentChange(newValue: TextFieldValue) {
        _configTextFieldValue.value = newValue
        _hasConfigChanged.value = true
    }

    fun onFilenameChange(newFilename: String) {
        _filename.value = newFilename
        _filenameErrorMessage.value = validateFilename(newFilename)
        _hasConfigChanged.value = true
    }

    private fun validateFilename(name: String): String? {
        return FilenameValidator.validateFilename(application, name)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun saveConfigFile() {
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

            var formattedContent: String = _configTextFieldValue.value.text
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

                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.config_save_success)
                    )
                )
                _configTextFieldValue.value =
                    _configTextFieldValue.value.copy(text = formattedContent)
                _filename.value = _configFile.nameWithoutExtension
                _hasConfigChanged.value = false
            } else {
                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.save_fail)
                    )
                )
            }
        }
    }

    fun handleAutoIndent(text: String, newlinePosition: Int): Pair<String, Int> {
        val prevLineStart = text.lastIndexOf('\n', newlinePosition - 1).let {
            if (it == -1) 0 else it + 1
        }
        val prevLine = text.substring(prevLineStart, newlinePosition)
        val leadingSpaces = prevLine.takeWhile { it.isWhitespace() }.length
        val additionalIndent = if (prevLine.trimEnd().let {
                it.endsWith('{') || it.endsWith('[')
            }) 2 else 0
        val shouldDedent = run {
            val nextLineStart = newlinePosition + 1
            nextLineStart < text.length &&
                    text.substring(nextLineStart).substringBefore('\n').trimStart().let {
                        it.startsWith('}') || it.startsWith(']')
                    }
        }
        val finalIndent = (
                leadingSpaces + additionalIndent - if (shouldDedent) 2 else 0
                ).coerceAtLeast(0)
        val indent = " ".repeat(finalIndent)
        val indentedText = StringBuilder(text).insert(newlinePosition + 1, indent).toString()
        return indentedText to (newlinePosition + 1 + finalIndent)
    }
}
