package com.simplexray.re.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.simplexray.re.BuildConfig
import com.simplexray.re.R
import com.simplexray.re.common.ReleaseVersion
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy

private const val TAG = "UpdateController"

internal class UpdateController(
    private val application: Application,
    private val prefs: Preferences,
    private val scope: CoroutineScope,
    private val isVpnEnabled: () -> Boolean,
    private val sendEvent: (MainViewUiEvent) -> Unit,
) {
    private val _isCheckingForUpdates = MutableStateFlow(false)
    val isCheckingForUpdates: StateFlow<Boolean> = _isCheckingForUpdates.asStateFlow()

    private val _newVersionAvailable = MutableStateFlow<String?>(null)
    val newVersionAvailable: StateFlow<String?> = _newVersionAvailable.asStateFlow()

    fun checkForUpdates() {
        scope.launch(Dispatchers.IO) {
            _isCheckingForUpdates.value = true
            val client = OkHttpClient.Builder().apply {
                if (isVpnEnabled()) {
                    proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort)))
                }
            }.build()

            val apiUrl = application.getString(R.string.source_url)
                .replace("github.com", "api.github.com/repos") + "/releases?per_page=20"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            try {
                val response = client.newCall(request).await()
                val responseBody = try {
                    response.body.string()
                } finally {
                    response.close()
                }
                val releases = org.json.JSONArray(responseBody)
                var latestTag = ""
                for (i in 0 until releases.length()) {
                    val release = releases.optJSONObject(i) ?: continue
                    if (release.optBoolean("draft", false)) continue
                    val candidate = release.optString("tag_name", "").removePrefix("v")
                    if (candidate.isNotEmpty() &&
                        (latestTag.isEmpty() || ReleaseVersion.compare(candidate, latestTag) > 0)
                    ) {
                        latestTag = candidate
                    }
                }
                Log.d(TAG, "Latest version tag: $latestTag")
                val updateAvailable =
                    latestTag.isNotEmpty() && ReleaseVersion.compare(latestTag, BuildConfig.VERSION_NAME) > 0
                if (updateAvailable) {
                    _newVersionAvailable.value = latestTag
                } else {
                    sendEvent(
                        MainViewUiEvent.ShowSnackbar(
                            application.getString(R.string.no_new_version_available)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                sendEvent(
                    MainViewUiEvent.ShowSnackbar(
                        application.getString(R.string.failed_to_check_for_updates) + ": " + e.message
                    )
                )
            } finally {
                _isCheckingForUpdates.value = false
            }
        }
    }

    fun downloadNewVersion(versionTag: String) {
        val url = application.getString(R.string.source_url) + "/releases/tag/v$versionTag"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
        _newVersionAvailable.value = null
    }

    fun clearNewVersionAvailable() {
        _newVersionAvailable.value = null
    }
}
