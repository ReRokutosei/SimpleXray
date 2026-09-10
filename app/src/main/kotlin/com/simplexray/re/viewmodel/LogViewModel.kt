package com.simplexray.re.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.re.data.source.LogFileManager
import com.simplexray.re.service.VpnStateHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

private const val TAG = "LogViewModel"
private const val MAX_LOG_ENTRIES = 300

@OptIn(FlowPreview::class)
class LogViewModel(application: Application) :
    AndroidViewModel(application) {

    private val logFileManager = LogFileManager(application)

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredEntries: StateFlow<List<String>> = combine(
        logEntries,
        searchQuery.debounce(300)
    ) { logs, query ->
        if (query.isBlank()) logs
        else logs.filter { it.contains(query, ignoreCase = true) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    private val _hasLogsToExport = MutableStateFlow(false)
    val hasLogsToExport: StateFlow<Boolean> = _hasLogsToExport.asStateFlow()

    private val logBuffer = ArrayDeque<String>(MAX_LOG_ENTRIES)
    private val logMutex = Mutex()
    private var pendingBatchJob: kotlinx.coroutines.Job? = null

    init {
        Log.d(TAG, "LogViewModel initialized.")
        viewModelScope.launch {
            VpnStateHub.logFlow.collect { line ->
                processNewLogs(listOf(line))
            }
        }
        viewModelScope.launch {
            logEntries.collect { entries ->
                _hasLogsToExport.value = entries.isNotEmpty() && logFileManager.logFile.exists()
            }
        }
    }

    fun loadLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Loading logs.")
            val savedLogData = logFileManager.readLogs()
            val initialLogs = if (!savedLogData.isNullOrEmpty()) {
                savedLogData.split("\n").filter { it.trim().isNotEmpty() }
            } else {
                emptyList()
            }
            processInitialLogs(initialLogs)
        }
    }

    private suspend fun processInitialLogs(initialLogs: List<String>) {
        logMutex.withLock {
            logBuffer.clear()
            // Keep at most MAX_LOG_ENTRIES in reverse order (newest first)
            for (line in initialLogs.takeLast(MAX_LOG_ENTRIES).reversed()) {
                logBuffer.addLast(line)
            }
            _logEntries.value = logBuffer.toList()
        }
        Log.d(TAG, "Processed initial logs: ${_logEntries.value.size} entries.")
    }

    private suspend fun processNewLogs(newLogs: List<String>) {
        logMutex.withLock {
            for (line in newLogs) {
                if (line.trim().isNotEmpty()) {
                    if (logBuffer.size >= MAX_LOG_ENTRIES) {
                        logBuffer.removeLast()
                    }
                    logBuffer.addFirst(line)
                }
            }
        }
        // Throttled UI update every 300ms
        if (pendingBatchJob?.isActive != true) {
            pendingBatchJob = viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                logMutex.withLock {
                    _logEntries.value = logBuffer.toList()
                }
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            logFileManager.clearLogs()
            logMutex.withLock {
                logBuffer.clear()
                _logEntries.value = emptyList()
            }
            Log.d(TAG, "Logs cleared.")
        }
    }

    fun getLogFile(): File {
        return logFileManager.logFile
    }
}

class LogViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LogViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
