package com.simplexray.re.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
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
    onSwitchVpnService: () -> Unit,
    logListState: LazyListState,
    configListState: LazyListState,
    settingsScrollState: androidx.compose.foundation.ScrollState,
    content: @Composable (paddingValues: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isLogSearching by remember { mutableStateOf(false) }
    val logSearchQuery by logViewModel.searchQuery.collectAsStateWithLifecycle()
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
            onSwitchVpnService = onSwitchVpnService,
            controlMenuClickable = mainViewModel.controlMenuClickable.collectAsStateWithLifecycle().value,
            isServiceEnabled = mainViewModel.isServiceEnabled.collectAsStateWithLifecycle().value,
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
                snackbarHost = { SnackbarHost(state = snackbarHostState) },
                contentWindowInsets = WindowInsets(0)
            ) { paddingValues ->
                content(paddingValues)
            }
        }
    } else {
        val surfaceColor = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = topBarContent,
            floatingToolbar = {
                AppBottomNavigationBar(navController, backdrop)
            },
            floatingToolbarPosition = top.yukonga.miuix.kmp.basic.ToolbarPosition.BottomCenter,
            snackbarHost = { SnackbarHost(state = snackbarHostState) },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            val overlayPaddingValues = PaddingValues(
                start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                top = paddingValues.calculateTopPadding(),
                end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                bottom = 100.dp
            )
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                content(overlayPaddingValues)
            }
        }
    }
}

@Composable
fun AppTopAppBar(
    currentRoute: String?,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onPerformExport: () -> Unit,
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

    SmallTopAppBar(
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
    onSwitchVpnService: () -> Unit,
    controlMenuClickable: Boolean,
    isServiceEnabled: Boolean,
    logViewModel: LogViewModel,
    onLogSearchingChange: (Boolean) -> Unit = {},
    mainViewModel: MainViewModel
) {
    when (currentRoute) {
        ROUTE_STATS -> { /* No actions */ }

        ROUTE_CONFIG -> { /* No actions */ }

        ROUTE_LOG -> LogActions(
            onPerformExport = onPerformExport,
            logViewModel = logViewModel,
            onLogSearchingChange = onLogSearchingChange,
            mainViewModel = mainViewModel
        )

        ROUTE_SETTINGS -> { /* No actions */ }
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
    IconButton(
        onClick = { onPerformExport() },
        enabled = hasLogsToExport
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_export),
            contentDescription = stringResource(R.string.export)
        )
    }
    IconButton(
        onClick = {
            logViewModel.clearLogs()
            mainViewModel.showSnackbar(context.getString(R.string.logs_cleared))
        },
        enabled = logEntries.isNotEmpty()
    ) {
        Icon(
            painter = painterResource(id = R.drawable.delete),
            contentDescription = stringResource(R.string.clear_logs)
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
fun AppBottomNavigationBar(navController: NavHostController, backdrop: LayerBackdrop? = null) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val barShape = remember { RoundedCornerShape(FloatingToolbarDefaults.CornerRadius) }
    val highlight = remember(isDark) {
        if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    }
    val blurColors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(color = surfaceContainer.copy(alpha = 0.65f))
        )
    )

    FloatingNavigationBar(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = barShape,
                blurRadius = 25f,
                colors = blurColors,
                highlight = null
            )
        } else Modifier,
        color = if (backdrop != null) Color.Transparent else surfaceContainer
    ) {
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
