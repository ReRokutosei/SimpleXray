package com.simplexray.re.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.application
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simplexray.re.R
import com.simplexray.re.common.NAVIGATION_DEBOUNCE_DELAY
import com.simplexray.re.common.ROUTE_CONFIG
import com.simplexray.re.common.ROUTE_LOG
import com.simplexray.re.common.ROUTE_SETTINGS
import com.simplexray.re.common.ROUTE_STATS
import com.simplexray.re.common.rememberMainScreenCallbacks
import com.simplexray.re.common.rememberMainScreenLaunchers
import com.simplexray.re.ui.components.ConfirmOverlayDialog
import com.simplexray.re.ui.navigation.BottomNavHost
import com.simplexray.re.ui.scaffold.AppScaffold
import com.simplexray.re.viewmodel.LogViewModel
import com.simplexray.re.viewmodel.LogViewModelFactory
import com.simplexray.re.viewmodel.MainViewModel
import com.simplexray.re.viewmodel.MainViewUiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    appNavController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val bottomNavController = rememberNavController()
    val scope = rememberCoroutineScope()

    val launchers = rememberMainScreenLaunchers(mainViewModel)

    val logViewModel: LogViewModel = viewModel(
        factory = LogViewModelFactory(mainViewModel.application)
    )

    val callbacks = rememberMainScreenCallbacks(
        mainViewModel = mainViewModel,
        logViewModel = logViewModel,
        launchers = launchers,
        applicationContext = mainViewModel.application
    )

    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    var showNotificationRationale by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // No explicit handling needed whether granted or denied
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission && !mainViewModel.prefs.notificationPrompted) {
                showNotificationRationale = true
            }
        }
    }

    DisposableEffect(mainViewModel) {
        mainViewModel.registerTProxyServiceReceivers()
        onDispose {
            mainViewModel.unregisterTProxyServiceReceivers()
        }
    }

    var lastNavigationTime = 0L

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            mainViewModel.extractAssetsIfNeeded()
        }

        mainViewModel.uiEvent.collectLatest { event ->
            when (event) {
                is MainViewUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }

                is MainViewUiEvent.ShareLauncher -> {
                    shareLauncher.launch(event.intent)
                }

                is MainViewUiEvent.StartService -> {
                    mainViewModel.application.startService(event.intent)
                }

                is MainViewUiEvent.RefreshConfigList -> {
                    mainViewModel.refreshConfigFileList()
                }

                is MainViewUiEvent.Navigate -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastNavigationTime >= NAVIGATION_DEBOUNCE_DELAY) {
                        lastNavigationTime = currentTime
                        appNavController.navigate(event.route)
                    }
                }
            }
        }
    }

    val logListState = rememberLazyListState()
    val configListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()

    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val mainScreenRoutes = listOf(ROUTE_STATS, ROUTE_CONFIG, ROUTE_LOG, ROUTE_SETTINGS)

    if (currentRoute in mainScreenRoutes) {
        AppScaffold(
            navController = bottomNavController,
            snackbarHostState = snackbarHostState,
            mainViewModel = mainViewModel,
            logViewModel = logViewModel,
            onCreateNewConfigFileAndEdit = callbacks.onCreateNewConfigFileAndEdit,
            onImportConfigFromClipboard = callbacks.onImportConfigFromClipboard,
            onPerformExport = callbacks.onPerformExport,
            onSwitchVpnService = callbacks.onSwitchVpnService,
            logListState = logListState,
            configListState = configListState,
            settingsScrollState = settingsScrollState
        ) { paddingValues ->
            BottomNavHost(
                navController = bottomNavController,
                paddingValues = paddingValues,
                mainViewModel = mainViewModel,
                onDeleteConfigClick = callbacks.onDeleteConfigClick,
                onCreateNewConfigFileAndEdit = callbacks.onCreateNewConfigFileAndEdit,
                onImportConfigFromClipboard = callbacks.onImportConfigFromClipboard,
                logViewModel = logViewModel,
                geoipFilePickerLauncher = launchers.geoipFilePickerLauncher,
                geositeFilePickerLauncher = launchers.geositeFilePickerLauncher,
                logListState = logListState,
                configListState = configListState,
                settingsScrollState = settingsScrollState,
                onSwitchVpnService = callbacks.onSwitchVpnService
            )
        }
    } else {
        BottomNavHost(
            navController = bottomNavController,
            paddingValues = androidx.compose.foundation.layout.PaddingValues(),
            mainViewModel = mainViewModel,
            onDeleteConfigClick = callbacks.onDeleteConfigClick,
            onCreateNewConfigFileAndEdit = callbacks.onCreateNewConfigFileAndEdit,
            onImportConfigFromClipboard = callbacks.onImportConfigFromClipboard,
            logViewModel = logViewModel,
            geoipFilePickerLauncher = launchers.geoipFilePickerLauncher,
            geositeFilePickerLauncher = launchers.geositeFilePickerLauncher,
            logListState = logListState,
            configListState = configListState,
            settingsScrollState = settingsScrollState,
            onSwitchVpnService = callbacks.onSwitchVpnService
        )
    }

    if (showNotificationRationale) {
        ConfirmOverlayDialog(
            title = stringResource(R.string.notification_permission_title),
            summary = stringResource(R.string.notification_permission_summary),
            confirmText = stringResource(R.string.confirm),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                showNotificationRationale = false
                mainViewModel.prefs.notificationPrompted = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = {
                showNotificationRationale = false
                mainViewModel.prefs.notificationPrompted = true
            }
        )
    }
}
