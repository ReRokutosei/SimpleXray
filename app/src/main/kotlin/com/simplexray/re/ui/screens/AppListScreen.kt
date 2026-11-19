package com.simplexray.re.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.viewmodel.AppListViewModel
import com.simplexray.re.viewmodel.AppListViewUiEvent
import com.simplexray.re.viewmodel.Package
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AppListScreen(viewModel: AppListViewModel, onBackClick: () -> Unit) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isSearching by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val moreAnchor = rememberGlassPopupAnchor()
    val filteredList by viewModel.filteredList.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val showNoInternetApps by viewModel.showNoInternetApps.collectAsStateWithLifecycle()
    val bypassSelectedApps by viewModel.bypassSelectedApps.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val isWideScreen = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width >= 600.dp.roundToPx()
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(searchQuery, showSystemApps, showNoInternetApps) {
        lazyListState.scrollToItem(0)
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is AppListViewUiEvent.ShowSnackbar -> {
                    launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    val selectAllText = stringResource(R.string.select_all)
    val inverseSelectionText = stringResource(R.string.inverse_selection)
    val exportToClipboardText = stringResource(R.string.export_to_clipboard)
    val importFromClipboardText = stringResource(R.string.import_from_clipboard)
    val showSystemAppsText = stringResource(R.string.show_system_apps)
    val showNoInternetAppsText = stringResource(R.string.show_no_internet_apps)
    val onlyProxySelectedAppsText = stringResource(R.string.only_proxy_selected_apps)
    val bypassSelectedAppsText = stringResource(R.string.bypass_selected_apps)

    val menuEntry = remember(showSystemApps, showNoInternetApps) {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = selectAllText,
                    onClick = { viewModel.selectAll() }
                ),
                DropdownItem(
                    text = inverseSelectionText,
                    onClick = { viewModel.inverseSelection() }
                ),
                DropdownItem(
                    text = exportToClipboardText,
                    onClick = { viewModel.exportAppsToClipboard(context) }
                ),
                DropdownItem(
                    text = importFromClipboardText,
                    onClick = { viewModel.importAppsFromClipboard(context) }
                ),
                DropdownItem(
                    text = showSystemAppsText,
                    selected = showSystemApps,
                    onClick = { viewModel.onShowSystemAppsChange(!showSystemApps) }
                ),
                DropdownItem(
                    text = showNoInternetAppsText,
                    selected = showNoInternetApps,
                    onClick = { viewModel.onShowNoInternetAppsChange(!showNoInternetApps) }
                )
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop),
            topBar = {
                GlassTopAppBar(
                    title = if (isSearching) "" else stringResource(R.string.apps_title),
                    subtitle = if (isSearching) "" else stringResource(R.string.apps_summary),
                    expandable = false,
                    progressiveBlur = true,
                    backdrop = backdrop,
                    navigationIcon = {
                        GlassIconButton(onClick = {
                            if (isSearching) {
                                isSearching = false
                                viewModel.onSearchQueryChange("")
                            } else {
                                onBackClick()
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Glass.Back,
                                contentDescription = stringResource(R.string.back),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        if (isSearching) {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { viewModel.onSearchQueryChange(it) },
                                onSearch = {},
                                expanded = isSearching,
                                onExpandedChange = { isSearching = it },
                                label = stringResource(R.string.search),
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .focusRequester(focusRequester)
                            )
                            if (searchQuery.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                GlassIconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = MiuixIcons.Glass.Close,
                                        contentDescription = stringResource(R.string.clear_search),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        } else {
                            GlassIconButton(onClick = { isSearching = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Glass.Search,
                                    contentDescription = stringResource(R.string.search),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassIconButton(
                                onClick = { showMoreMenu = !showMoreMenu },
                                modifier = Modifier.glassPopupAnchor(
                                    anchor = moreAnchor,
                                    cornerRadius = GlassTopAppBarDefaults.ButtonSize / 2,
                                    floating = true,
                                )
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Glass.More,
                                    contentDescription = stringResource(R.string.more),
                                    modifier = Modifier.size(24.dp).glassPopupAnchorContent(moreAnchor)
                                )
                            }
                        }
                    },
                    bottomContent = {
                        if (!isLoading) {
                            val modeTabs = listOf(onlyProxySelectedAppsText, bypassSelectedAppsText)
                            val selectedTabIndex = if (bypassSelectedApps) 1 else 0
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GlassSegmentedTabRow(
                                    tabs = modeTabs,
                                    selectedIndex = selectedTabIndex,
                                    onSelect = { index ->
                                        viewModel.onBypassSelectedAppsChange(index == 1)
                                    },
                                    backdrop = backdrop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isWideScreen) Modifier.widthIn(max = 840.dp) else Modifier
                                        )
                                )
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(state = snackbarHostState) },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            val topPadding = paddingValues.calculateTopPadding() + 4.dp
            val bottomPadding = paddingValues.calculateBottomPadding().coerceAtLeast(12.dp)
            val listContentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = topPadding,
                bottom = bottomPadding
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = topPadding)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isWideScreen) Modifier.widthIn(max = 840.dp) else Modifier
                            )
                    ) {
                        val adapter = rememberScrollBarAdapter(scrollState = lazyListState)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = lazyListState,
                            contentPadding = listContentPadding,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filteredList, key = { it.packageName }) { pkg ->
                                AppItem(pkg) { isChecked ->
                                    viewModel.onPackageSelected(pkg, isChecked)
                                }
                            }
                        }
                        VerticalScrollBar(
                            adapter = adapter,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            trackPadding = listContentPadding
                        )
                        if (filteredList.isEmpty() && searchQuery.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.apps_not_found),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = topPadding),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }
            }
        }

        GlassIconDropdownMenu(
            show = showMoreMenu,
            onDismissRequest = { showMoreMenu = false },
            anchor = moreAnchor,
            anchorContent = {
                Icon(
                    imageVector = MiuixIcons.Glass.More,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            entries = listOf(menuEntry),
            backdrop = pageBackdrop,
            collapseOnSelection = true
        )
    }
}

private val iconCache = LruCache<String, ImageBitmap>(200)

@Composable
fun AppItem(pkg: Package, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val cached = remember(pkg.packageName) { iconCache.get(pkg.packageName) }
    val iconBitmap by produceState<ImageBitmap?>(initialValue = cached, key1 = pkg.packageName) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(pkg.packageName)
                val bitmap = drawableToBitmap(drawable)?.asImageBitmap()
                if (bitmap != null) {
                    iconCache.put(pkg.packageName, bitmap)
                }
                bitmap
            }.getOrNull()
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!pkg.selected) },
        colors = CardDefaults.defaultColors(
            color = if (pkg.selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = stringResource(R.string.app_icon),
                    modifier = Modifier
                        .size(40.dp)
                        .fillMaxHeight(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.secondaryContainer)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = pkg.label,
                modifier = Modifier.weight(1f),
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                color = if (pkg.selected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(14.dp))
            Checkbox(
                state = if (pkg.selected) ToggleableState.On else ToggleableState.Off,
                onClick = { onCheckedChange(!pkg.selected) },
                enabled = true
            )
        }
    }
}

fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
