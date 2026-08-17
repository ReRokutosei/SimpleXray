package com.simplexray.re.viewmodel

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.re.BuildConfig
import com.simplexray.re.R
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class AppListViewUiEvent {
    data class ShowSnackbar(val message: String) : AppListViewUiEvent()
}

data class Package(
    var selected: Boolean,
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val hasInternetPermission: Boolean = true
)

/**
 * Pure filtering logic for the app list, kept free of Android dependencies so it
 * can be unit-tested on the JVM.
 */
internal fun filterPackages(
    list: List<Package>,
    query: String,
    showSystemApps: Boolean,
    showNoInternetApps: Boolean,
): List<Package> = list.filter { pkg ->
    (showSystemApps || !pkg.isSystemApp) &&
        (showNoInternetApps || pkg.hasInternetPermission) &&
        pkg.label.lowercase(Locale.getDefault())
            .contains(query.lowercase(Locale.getDefault()))
}

class AppListViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = Preferences(getApplication<Application>().applicationContext)

    private val _packageList = MutableStateFlow<List<Package>>(emptyList())
    val packageList: StateFlow<List<Package>> = _packageList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystemApps = MutableStateFlow(true)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _showNoInternetApps = MutableStateFlow(false)
    val showNoInternetApps: StateFlow<Boolean> = _showNoInternetApps.asStateFlow()

    private val _bypassSelectedApps = MutableStateFlow(prefs.bypassSelectedApps)
    val bypassSelectedApps: StateFlow<Boolean> = _bypassSelectedApps.asStateFlow()

    private var isChanged = false

    private val _uiEvent = Channel<AppListViewUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val filteredList: StateFlow<List<Package>> = combine(
        _packageList,
        _searchQuery,
        _showSystemApps,
        _showNoInternetApps
    ) { list, query, showSystem, showNoInternet ->
        filterPackages(list, query, showSystem, showNoInternet)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadAppList()
    }

    private fun loadAppList() {
        _isLoading.value = true
        val pm = getApplication<Application>().packageManager
        val appPackageName = getApplication<Application>().packageName
        val apps = prefs.apps ?: emptySet()
        var loadedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val startTime = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            while ((loadedPackages.isEmpty() || loadedPackages.size == 1)
                && System.currentTimeMillis() - startTime < 10000
            ) {
                loadedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                delay(500)
            }
            val installedPackageNames = loadedPackages.map { it.packageName }.toSet()
            val validSelectedApps = apps.filter { installedPackageNames.contains(it) }.toSet()
            if (validSelectedApps.size != apps.size) {
                prefs.apps = validSelectedApps
                Log.d(TAG, "Pruned ${apps.size - validSelectedApps.size} uninstalled app(s) from per-app proxy list.")
            }

            val list = loadedPackages.asSequence()
                .mapNotNull {
                    if (it.packageName == appPackageName) return@mapNotNull null
                    val appInfo = it.applicationInfo ?: return@mapNotNull null
                    val hasInternetPermission =
                        it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                    val isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val label = appInfo.loadLabel(pm).toString()
                    Package(
                        selected = validSelectedApps.contains(it.packageName),
                        label = label,
                        packageName = it.packageName,
                        isSystemApp = isSystemApp,
                        hasInternetPermission = hasInternetPermission
                    )
                }
                .sortedWith(
                    compareByDescending<Package> { it.selected }
                        .thenBy { it.label }
                )
                .toList()
            withContext(Dispatchers.Main) {
                _packageList.value = list
                _isLoading.value = false
            }
        }
    }

    fun onPackageSelected(pkg: Package, isSelected: Boolean) {
        val index = _packageList.value.indexOf(pkg)
        if (index != -1) {
            val updated = _packageList.value.toMutableList()
            updated[index] = pkg.copy(selected = isSelected)
            _packageList.value = updated
            isChanged = true
            saveChanges()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onShowSystemAppsChange(show: Boolean) {
        _showSystemApps.value = show
    }

    fun onShowNoInternetAppsChange(show: Boolean) {
        _showNoInternetApps.value = show
    }

    fun onBypassSelectedAppsChange(bypass: Boolean) {
        _bypassSelectedApps.value = bypass
        prefs.bypassSelectedApps = bypass
    }

    fun exportAppsToClipboard(context: Context) {
        val selectedApps = _packageList.value.filter { it.selected }.map { it.packageName }
        val bypassMode = bypassSelectedApps.value.toString()
        val exportString = (listOf(bypassMode) + selectedApps).joinToString("\n")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("App List", exportString)
        clipboard.setPrimaryClip(clip)
        _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.export_success)))
    }

    fun importAppsFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            val importString = clipData.getItemAt(0).text?.toString()
            if (!importString.isNullOrBlank()) {
                val lines = importString.split("\n")
                lines.indexOf(BuildConfig.APPLICATION_ID).let {
                    if (it > -1) lines.drop(it)
                }
                if (lines.isNotEmpty()) {
                    val newBypassMode = lines[0].toBooleanStrictOrNull()
                    if (newBypassMode != null) {
                        onBypassSelectedAppsChange(newBypassMode)
                        val importedPackageNames = lines.drop(1).toSet()
                        val updatedPackageList = _packageList.value.map { pkg ->
                            pkg.copy(selected = importedPackageNames.contains(pkg.packageName))
                        }
                        _packageList.value = updatedPackageList
                        isChanged = true
                        saveChanges()
                        _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_success)))
                    } else {
                        _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_invalid_format)))
                    }
                } else {
                    _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_invalid_format)))
                }
            } else {
                _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_failed)))
            }
        } else {
            _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_failed)))
        }
    }

    private fun saveChanges() {
        if (isChanged) {
            viewModelScope.launch(Dispatchers.IO) {
                val apps: MutableSet<String> = HashSet()
                _packageList.value.forEach { pkg ->
                    if (pkg.selected) apps.add(pkg.packageName)
                }
                prefs.apps = apps
                isChanged = false
            }
        }
    }

    fun selectAll() {
        _packageList.value = _packageList.value.map { pkg ->
            if (!pkg.selected) {
                isChanged = true
                pkg.copy(selected = true)
            } else {
                pkg
            }
        }
        saveChanges()
    }

    fun inverseSelection() {
        _packageList.value = _packageList.value.map { pkg ->
            isChanged = true
            pkg.copy(selected = !pkg.selected)
        }
        saveChanges()
    }

    companion object {
        private const val TAG = "AppListViewModel"
    }
}
