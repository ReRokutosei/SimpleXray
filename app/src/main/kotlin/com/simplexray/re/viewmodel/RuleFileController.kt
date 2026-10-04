package com.simplexray.re.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import com.simplexray.re.R
import com.simplexray.re.data.source.FileManager
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

private const val TAG = "RuleFileController"

internal class RuleFileController(
    private val application: Application,
    private val prefs: Preferences,
    private val fileManager: FileManager,
    private val scope: CoroutineScope,
    private val isVpnEnabled: () -> Boolean,
    private val onRuleFileStateChanged: (String) -> Unit,
    private val onSettingsRefreshNeeded: () -> Unit,
    private val sendEvent: (MainViewUiEvent) -> Unit,
) {
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

    private val _customDatVersion = MutableStateFlow(0L)
    val customDatVersion: StateFlow<Long> = _customDatVersion.asStateFlow()

    private fun updateCustomDatProgress(fileName: String, progress: String?) {
        val map = _customDatDownloadProgress.value.toMutableMap()
        if (progress == null) map.remove(fileName) else map[fileName] = progress
        _customDatDownloadProgress.value = map
    }

    fun importRuleFile(uri: Uri, fileName: String) {
        scope.launch(Dispatchers.IO) {
            val success = fileManager.importRuleFile(uri, fileName)
            if (success) {
                onRuleFileStateChanged(fileName)
                sendEvent(
                    MainViewUiEvent.ShowSnackbar(
                        "$fileName ${application.getString(R.string.import_success)}"
                    )
                )
            } else {
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_validation_failed)))
            }
        }
    }

    fun extractAssetsIfNeeded() {
        fileManager.extractAssetsIfNeeded()
    }

    fun restoreDefaultGeoip(callback: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeoip()
            onRuleFileStateChanged("geoip.dat")
            sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geoip_success)))
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Restored default geoip.dat.")
                callback()
            }
        }
    }

    fun restoreDefaultGeosite(callback: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeosite()
            onRuleFileStateChanged("geosite.dat")
            sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geosite_success)))
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Restored default geosite.dat.")
                callback()
            }
        }
    }

    fun cancelDownload(fileName: String) {
        scope.launch {
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
        job = scope.launch(Dispatchers.IO) {
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
                if (isVpnEnabled()) {
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

                val body = response.body
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
                            onRuleFileStateChanged(targetName)
                        }
                        onSettingsRefreshNeeded()
                        refreshCustomDatFiles()
                        sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_success)))
                    } else {
                        sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_validation_failed)))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $fileName", e)
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_failed)))
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

    fun refreshCustomDatFiles() {
        _customDatVersion.value = System.currentTimeMillis()
    }

    fun importCustomDatFile(uri: android.net.Uri) {
        scope.launch(Dispatchers.IO) {
            val candidateName = fileManager.getDatFileNameFromUri(application, uri)
            if (candidateName == null) {
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.unsupported_dat_format)))
                return@launch
            }
            // Defense: reject standard GEO file names (case-insensitive) before importing.
            if (FileManager.isStandardGeoDat(candidateName)) {
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.standard_geo_file_rejected)))
                return@launch
            }
            val fileName = fileManager.importDatFileFromUri(application, uri)
            if (fileName != null) {
                refreshCustomDatFiles()
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.file_imported, fileName)))
            } else {
                sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_validation_failed)))
            }
        }
    }

    fun downloadDatFromUrl(url: String) {
        if (url.isBlank()) {
            sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.invalid_dat_url)))
            return
        }
        val fileName = extractDatFileName(url)
        if (fileName == null) {
            sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.invalid_dat_url)))
            return
        }
        if (FileManager.isStandardGeoDat(fileName)) {
            sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.standard_geo_file_rejected)))
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
        scope.launch(Dispatchers.IO) {
            val file = File(application.filesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            val urls = prefs.customDatUrls.toMutableMap()
            urls.remove(fileName)
            prefs.customDatUrls = urls
            refreshCustomDatFiles()
            sendEvent(MainViewUiEvent.ShowSnackbar(application.getString(R.string.file_deleted, fileName)))
        }
    }

    fun updateCustomDatUrl(fileName: String, url: String) {
        val urls = prefs.customDatUrls.toMutableMap()
        urls[fileName] = url
        prefs.customDatUrls = urls
        refreshCustomDatFiles()
    }
}
