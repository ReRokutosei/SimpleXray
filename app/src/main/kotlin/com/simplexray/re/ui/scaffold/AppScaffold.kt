package com.simplexray.re.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationRailState
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalLayerBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

private val NAV_ROUTES = listOf(ROUTE_STATS, ROUTE_CONFIG, ROUTE_LOG, ROUTE_SETTINGS)

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
    settingsScrollState: LazyListState,
    content: @Composable (paddingValues: PaddingValues) -> Unit
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

    val isWideScreen = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width >= 600.dp.roundToPx()
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val railState = rememberGlassNavigationRailState(initialExpanded = true)
    val railWidth = if (isWideScreen) {
        (GlassNavigationRailDefaults.Margin + GlassNavigationRailDefaults.ExpandedWidth) * railState.progress
    } else {
        0.dp
    }
    val titleAlpha: () -> Float = remember(isWideScreen, railState) {
        if (isWideScreen) {
            { railState.progress }
        } else {
            { 1f }
        }
    }

    val topBarContent: @Composable () -> Unit = {
        AppTopAppBar(
            currentRoute = currentRoute,
            onCreateNewConfigFileAndEdit = onCreateNewConfigFileAndEdit,
            onImportConfigFromClipboard = onImportConfigFromClipboard,
            onPerformExport = onPerformExport,
            onSwitchVpnService = onSwitchVpnService,
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
            mainViewModel = mainViewModel,
            backdrop = backdrop,
            titleAlpha = titleAlpha,
            contentModifier = if (isWideScreen) Modifier.padding(start = railWidth) else Modifier
        )
    }

    CompositionLocalProvider(LocalLayerBackdrop provides backdrop) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = topBarContent,
                bottomBar = {
                    if (!isWideScreen) {
                        AppBottomNavigationBar(navController = navController, backdrop = backdrop)
                    }
                },
                snackbarHost = { SnackbarHost(state = snackbarHostState) },
                contentWindowInsets = WindowInsets(0)
            ) { paddingValues ->
                val bottomPad = if (isWideScreen) {
                    paddingValues.calculateBottomPadding().coerceAtLeast(12.dp)
                } else {
                    (paddingValues.calculateBottomPadding() + 12.dp).coerceAtLeast(100.dp)
                }
                val overlayPaddingValues = PaddingValues(
                    start = paddingValues.calculateStartPadding(LayoutDirection.Ltr) + railWidth,
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                    bottom = bottomPad
                )
                Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    content(overlayPaddingValues)
                }
            }
            if (isWideScreen) {
                AppNavigationRail(
                    navController = navController,
                    backdrop = backdrop,
                    state = railState
                )
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
    isServiceEnabled: Boolean,
    logViewModel: LogViewModel,
    logListState: LazyListState,
    configListState: LazyListState,
    settingsScrollState: LazyListState,
    isLogSearching: Boolean = false,
    onLogSearchingChange: (Boolean) -> Unit = {},
    logSearchQuery: String = "",
    onLogSearchQueryChange: (String) -> Unit = {},
    focusRequester: FocusRequester? = null,
    mainViewModel: MainViewModel,
    backdrop: Backdrop? = null,
    titleAlpha: () -> Float = { 1f },
    contentModifier: Modifier = Modifier
) {
    val title = when (currentRoute) {
        ROUTE_STATS -> stringResource(R.string.core_stats_title)
        ROUTE_CONFIG -> stringResource(R.string.configuration)
        ROUTE_LOG -> stringResource(R.string.log)
        ROUTE_SETTINGS -> stringResource(R.string.settings)
        else -> stringResource(R.string.app_name)
    }

    GlassTopAppBar(
        title = if (currentRoute == ROUTE_LOG && isLogSearching) "" else title,
        contentModifier = contentModifier,
        expandable = false,
        progressiveBlur = true,
        backdrop = backdrop,
        titleAlpha = titleAlpha,
        navigationIcon = {
            if (currentRoute == ROUTE_LOG && isLogSearching) {
                GlassIconButton(
                    onClick = {
                        onLogSearchingChange(false)
                        onLogSearchQueryChange("")
                    }
                ) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassIconButton(onClick = { onLogSearchQueryChange("") }) {
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
                    isServiceEnabled = isServiceEnabled,
                    logViewModel = logViewModel,
                    onLogSearchingChange = onLogSearchingChange,
                    mainViewModel = mainViewModel
                )
            }
        }
    )
}

@Composable
private fun TopAppBarActions(
    currentRoute: String?,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onPerformExport: () -> Unit,
    onSwitchVpnService: () -> Unit,
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
    val logsCleared = stringResource(R.string.logs_cleared)

    GlassIconButton(onClick = { onLogSearchingChange(true) }) {
        Icon(
            painter = painterResource(id = R.drawable.search),
            contentDescription = stringResource(R.string.search)
        )
    }
    Spacer(modifier = Modifier.width(8.dp))
    GlassIconButton(
        onClick = {
            if (hasLogsToExport) {
                onPerformExport()
            }
        }
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_export),
            contentDescription = stringResource(R.string.export),
            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (hasLogsToExport) 1f else 0.38f)
        )
    }
    Spacer(modifier = Modifier.width(8.dp))
    val hasEntries = logEntries.isNotEmpty()
    GlassIconButton(
        onClick = {
            if (hasEntries) {
                logViewModel.clearLogs()
                mainViewModel.showSnackbar(logsCleared)
            }
        }
    ) {
        Icon(
            painter = painterResource(id = R.drawable.delete),
            contentDescription = stringResource(R.string.clear_logs),
            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (hasEntries) 1f else 0.38f)
        )
    }
}

@Composable
private fun rememberGlassNavigationItems(includeLabels: Boolean = true): List<GlassNavigationItem> {
    val statsIcon = ImageVector.vectorResource(id = R.drawable.dashboard)
    val statsLabel = stringResource(R.string.core_stats_title)
    val configIcon = ImageVector.vectorResource(id = R.drawable.code)
    val configLabel = stringResource(R.string.configuration)
    val logIcon = ImageVector.vectorResource(id = R.drawable.history)
    val logLabel = stringResource(R.string.log)
    val settingsIcon = ImageVector.vectorResource(id = R.drawable.settings)
    val settingsLabel = stringResource(R.string.settings)

    return remember(
        includeLabels,
        statsIcon,
        statsLabel,
        configIcon,
        configLabel,
        logIcon,
        logLabel,
        settingsIcon,
        settingsLabel
    ) {
        listOf(
            GlassNavigationItem(
                icon = statsIcon,
                label = if (includeLabels) statsLabel else null,
                contentDescription = statsLabel
            ),
            GlassNavigationItem(
                icon = configIcon,
                label = if (includeLabels) configLabel else null,
                contentDescription = configLabel
            ),
            GlassNavigationItem(
                icon = logIcon,
                label = if (includeLabels) logLabel else null,
                contentDescription = logLabel
            ),
            GlassNavigationItem(
                icon = settingsIcon,
                label = if (includeLabels) settingsLabel else null,
                contentDescription = settingsLabel
            )
        )
    }
}

@Composable
fun AppNavigationRail(
    navController: NavHostController,
    backdrop: Backdrop? = null,
    state: GlassNavigationRailState = rememberGlassNavigationRailState(initialExpanded = true)
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val items = rememberGlassNavigationItems(includeLabels = true)
    val selectedIndex = NAV_ROUTES.indexOf(currentRoute).coerceAtLeast(0)

    GlassNavigationRail(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = { index ->
            NAV_ROUTES.getOrNull(index)?.let { route ->
                navigateToRoute(navController, route)
            }
        },
        backdrop = backdrop,
        state = state
    )
}

@Composable
fun AppBottomNavigationBar(
    navController: NavHostController,
    backdrop: LayerBackdrop? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val items = rememberGlassNavigationItems(includeLabels = false)
    val selectedIndex = NAV_ROUTES.indexOf(currentRoute).coerceAtLeast(0)

    GlassNavigationBar(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = { index ->
            NAV_ROUTES.getOrNull(index)?.let { route ->
                navigateToRoute(navController, route)
            }
        },
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )
}

private fun navigateToRoute(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
