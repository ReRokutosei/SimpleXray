package com.simplexray.re.ui.scaffold

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.simplexray.re.R
import com.simplexray.re.common.ROUTE_CONFIG
import com.simplexray.re.common.ROUTE_LOG
import com.simplexray.re.common.ROUTE_SETTINGS
import com.simplexray.re.common.ROUTE_STATS
import com.simplexray.re.viewmodel.LogViewModel
import com.simplexray.re.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppScaffold(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    mainViewModel: MainViewModel,
    logViewModel: LogViewModel,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onPerformExport: () -> Unit,
    onPerformBackup: () -> Unit,
    onPerformRestore: () -> Unit,
    onSwitchVpnService: () -> Unit,
    logListState: LazyListState,
    configListState: LazyListState,
    settingsScrollState: androidx.compose.foundation.ScrollState,
    content: @Composable (paddingValues: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isLogSearching by remember { mutableStateOf(false) }
    val logSearchQuery by logViewModel.searchQuery.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isLogSearching) {
        if (isLogSearching) {
            focusRequester.requestFocus()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val topBarContent: @Composable () -> Unit = {
        AppTopAppBar(
            currentRoute = currentRoute,
            onCreateNewConfigFileAndEdit = onCreateNewConfigFileAndEdit,
            onImportConfigFromClipboard = onImportConfigFromClipboard,
            onPerformExport = onPerformExport,
            onPerformBackup = onPerformBackup,
            onPerformRestore = onPerformRestore,
            onSwitchVpnService = onSwitchVpnService,
            controlMenuClickable = mainViewModel.controlMenuClickable.collectAsState().value,
            isServiceEnabled = mainViewModel.isServiceEnabled.collectAsState().value,
            logViewModel = logViewModel,
            logListState = logListState,
            configListState = configListState,
            settingsScrollState = settingsScrollState,
            isLogSearching = isLogSearching,
            onLogSearchingChange = { isLogSearching = it },
            logSearchQuery = logSearchQuery,
            onLogSearchQueryChange = { logViewModel.onSearchQueryChange(it) },
            focusRequester = focusRequester,
            mainViewModel = mainViewModel
        )
    }

    if (isWideScreen) {
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
            AppNavigationRail(navController = navController)
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = topBarContent,
                contentWindowInsets = WindowInsets(0)
            ) { paddingValues ->
                content(paddingValues)
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = topBarContent,
            bottomBar = {
                AppBottomNavigationBar(navController)
            },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}

@Composable
fun AppTopAppBar(
    currentRoute: String?,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onPerformExport: () -> Unit,
    onPerformBackup: () -> Unit,
    onPerformRestore: () -> Unit,
    onSwitchVpnService: () -> Unit,
    controlMenuClickable: Boolean,
    isServiceEnabled: Boolean,
    logViewModel: LogViewModel,
    logListState: LazyListState,
    configListState: LazyListState,
    settingsScrollState: androidx.compose.foundation.ScrollState,
    isLogSearching: Boolean = false,
    onLogSearchingChange: (Boolean) -> Unit = {},
    logSearchQuery: String = "",
    onLogSearchQueryChange: (String) -> Unit = {},
    focusRequester: FocusRequester? = null,
    mainViewModel: MainViewModel
) {
    val title = when (currentRoute) {
        ROUTE_STATS -> stringResource(R.string.core_stats_title)
        ROUTE_CONFIG -> stringResource(R.string.configuration)
        ROUTE_LOG -> stringResource(R.string.log)
        ROUTE_SETTINGS -> stringResource(R.string.settings)
        else -> stringResource(R.string.app_name)
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()

    TopAppBar(
        title = if (currentRoute == ROUTE_LOG && isLogSearching) "" else title,
        color = MiuixTheme.colorScheme.surface,
        navigationIcon = {
            if (currentRoute == ROUTE_LOG && isLogSearching) {
                IconButton(onClick = {
                    onLogSearchingChange(false)
                    onLogSearchQueryChange("")
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.close_search)
                    )
                }
            }
        },
        actions = {
            if (currentRoute == ROUTE_LOG && isLogSearching) {
                val inputModifier = if (focusRequester != null) {
                    Modifier.fillMaxWidth(0.7f).focusRequester(focusRequester)
                } else {
                    Modifier.fillMaxWidth(0.7f)
                }
                InputField(
                    query = logSearchQuery,
                    onQueryChange = onLogSearchQueryChange,
                    onSearch = {},
                    expanded = isLogSearching,
                    onExpandedChange = onLogSearchingChange,
                    label = stringResource(R.string.search),
                    modifier = inputModifier
                )
                if (logSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onLogSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear_search)
                        )
                    }
                }
            } else {
                TopAppBarActions(
                    currentRoute = currentRoute,
                    onCreateNewConfigFileAndEdit = onCreateNewConfigFileAndEdit,
                    onImportConfigFromClipboard = onImportConfigFromClipboard,
                    onPerformExport = onPerformExport,
                    onPerformBackup = onPerformBackup,
                    onPerformRestore = onPerformRestore,
                    onSwitchVpnService = onSwitchVpnService,
                    controlMenuClickable = controlMenuClickable,
                    isServiceEnabled = isServiceEnabled,
                    logViewModel = logViewModel,
                    onLogSearchingChange = onLogSearchingChange,
                    mainViewModel = mainViewModel
                )
            }
        },
        scrollBehavior = topAppBarScrollBehavior
    )
}

@Composable
private fun TopAppBarActions(
    currentRoute: String?,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onPerformExport: () -> Unit,
    onPerformBackup: () -> Unit,
    onPerformRestore: () -> Unit,
    onSwitchVpnService: () -> Unit,
    controlMenuClickable: Boolean,
    isServiceEnabled: Boolean,
    logViewModel: LogViewModel,
    onLogSearchingChange: (Boolean) -> Unit = {},
    mainViewModel: MainViewModel
) {
    when (currentRoute) {
        ROUTE_CONFIG -> ConfigActions(
            mainViewModel = mainViewModel
        )

        ROUTE_STATS -> { /* Actions moved to StatsScreen content card */ }

        ROUTE_LOG -> LogActions(
            onPerformExport = onPerformExport,
            logViewModel = logViewModel,
            onLogSearchingChange = onLogSearchingChange,
            mainViewModel = mainViewModel
        )

        ROUTE_SETTINGS -> SettingsActions(
            onPerformBackup = onPerformBackup,
            onPerformRestore = onPerformRestore
        )
    }
}

@Composable
private fun ConfigActions(
    mainViewModel: MainViewModel
) {
    IconButton(onClick = { mainViewModel.testConnectivity() }) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.connectivity_test)
        )
    }
}

@Composable
private fun LogActions(
    onPerformExport: () -> Unit,
    logViewModel: LogViewModel,
    onLogSearchingChange: (Boolean) -> Unit = {},
    mainViewModel: MainViewModel
) {
    val hasLogsToExport by logViewModel.hasLogsToExport.collectAsStateWithLifecycle()
    val logEntries by logViewModel.logEntries.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    IconButton(onClick = { onLogSearchingChange(true) }) {
        Icon(
            painter = painterResource(id = R.drawable.search),
            contentDescription = stringResource(R.string.search)
        )
    }

    val entry = remember(logEntries, hasLogsToExport) {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = context.getString(R.string.clear_logs),
                    onClick = {
                        logViewModel.clearLogs()
                        mainViewModel.showSnackbar(context.getString(R.string.logs_cleared))
                    }
                ),
                DropdownItem(
                    text = context.getString(R.string.export),
                    onClick = { onPerformExport() }
                )
            )
        )
    }

    OverlayIconDropdownMenu(
        entry = entry,
        collapseOnSelection = true
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more)
        )
    }
}

@Composable
private fun SettingsActions(
    onPerformBackup: () -> Unit,
    onPerformRestore: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val entry = remember {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = context.getString(R.string.backup),
                    onClick = { onPerformBackup() }
                ),
                DropdownItem(
                    text = context.getString(R.string.restore),
                    onClick = { onPerformRestore() }
                )
            )
        )
    }

    OverlayIconDropdownMenu(
        entry = entry,
        collapseOnSelection = true
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more)
        )
    }
}

@Composable
fun AppNavigationRail(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val railState = remember { top.yukonga.miuix.kmp.basic.NavigationRailState(initialValue = top.yukonga.miuix.kmp.basic.NavigationRailValue.Expanded) }

    top.yukonga.miuix.kmp.basic.NavigationRail(state = railState) {
        top.yukonga.miuix.kmp.basic.NavigationRailItem(
            selected = currentRoute == ROUTE_STATS,
            onClick = { navigateToRoute(navController, ROUTE_STATS) },
            icon = ImageVector.vectorResource(id = R.drawable.dashboard),
            label = stringResource(R.string.core_stats_title)
        )
        top.yukonga.miuix.kmp.basic.NavigationRailItem(
            selected = currentRoute == ROUTE_CONFIG,
            onClick = { navigateToRoute(navController, ROUTE_CONFIG) },
            icon = ImageVector.vectorResource(id = R.drawable.code),
            label = stringResource(R.string.configuration)
        )
        top.yukonga.miuix.kmp.basic.NavigationRailItem(
            selected = currentRoute == ROUTE_LOG,
            onClick = { navigateToRoute(navController, ROUTE_LOG) },
            icon = ImageVector.vectorResource(id = R.drawable.history),
            label = stringResource(R.string.log)
        )
        top.yukonga.miuix.kmp.basic.NavigationRailItem(
            selected = currentRoute == ROUTE_SETTINGS,
            onClick = { navigateToRoute(navController, ROUTE_SETTINGS) },
            icon = ImageVector.vectorResource(id = R.drawable.settings),
            label = stringResource(R.string.settings)
        )
    }
}

@Composable
fun AppBottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    FloatingNavigationBar {
        FloatingNavigationBarItem(
            selected = currentRoute == ROUTE_STATS,
            onClick = { navigateToRoute(navController, ROUTE_STATS) },
            icon = ImageVector.vectorResource(id = R.drawable.dashboard),
            label = stringResource(R.string.core_stats_title)
        )
        FloatingNavigationBarItem(
            selected = currentRoute == ROUTE_CONFIG,
            onClick = { navigateToRoute(navController, ROUTE_CONFIG) },
            icon = ImageVector.vectorResource(id = R.drawable.code),
            label = stringResource(R.string.configuration)
        )
        FloatingNavigationBarItem(
            selected = currentRoute == ROUTE_LOG,
            onClick = { navigateToRoute(navController, ROUTE_LOG) },
            icon = ImageVector.vectorResource(id = R.drawable.history),
            label = stringResource(R.string.log)
        )
        FloatingNavigationBarItem(
            selected = currentRoute == ROUTE_SETTINGS,
            onClick = { navigateToRoute(navController, ROUTE_SETTINGS) },
            icon = ImageVector.vectorResource(id = R.drawable.settings),
            label = stringResource(R.string.settings)
        )
    }
}

private fun navigateToRoute(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
