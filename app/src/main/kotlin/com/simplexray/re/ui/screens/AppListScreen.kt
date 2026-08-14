package com.simplexray.re.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AppListScreen(viewModel: AppListViewModel, onBackClick: () -> Unit) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isSearching by remember { mutableStateOf(false) }
    val filteredList by viewModel.filteredList.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val showNoInternetApps by viewModel.showNoInternetApps.collectAsStateWithLifecycle()
    val bypassSelectedApps by viewModel.bypassSelectedApps.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior()

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
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is AppListViewUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
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
    val bypassSelectedAppsText = stringResource(R.string.bypass_selected_apps)

    val menuEntry = remember(showSystemApps, showNoInternetApps, bypassSelectedApps) {
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
                ),
                DropdownItem(
                    text = bypassSelectedAppsText,
                    selected = bypassSelectedApps,
                    onClick = { viewModel.onBypassSelectedAppsChange(!bypassSelectedApps) }
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (isSearching) "" else stringResource(R.string.apps_title),                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            viewModel.onSearchQueryChange("")
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear_search)
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.search),
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                        OverlayIconDropdownMenu(
                            entry = menuEntry,
                            collapseOnSelection = true
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isWideScreen = configuration.screenWidthDp >= 600

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.TopCenter
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val adapter = rememberScrollBarAdapter(scrollState = lazyListState)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isWideScreen) Modifier.widthIn(max = 840.dp) else Modifier
                    ),
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
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
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
                if (filteredList.isEmpty() && searchQuery.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.apps_not_found),
                        modifier = Modifier.align(Alignment.Center),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun AppItem(pkg: Package, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = pkg.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(pkg.packageName)
                drawableToBitmap(drawable)?.asImageBitmap()
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
