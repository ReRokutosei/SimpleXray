package com.simplexray.re.ui.screens

import android.content.res.Configuration
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simplexray.re.R
import com.simplexray.re.viewmodel.ConfigEditViewModel
import com.simplexray.re.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

private const val TAG = "ConfigScreen"

@Composable
fun ConfigScreen(
    onReloadConfig: () -> Unit,
    onEditConfigClick: (File) -> Unit,
    onDeleteConfigClick: (File, () -> Unit) -> Unit,
    onCreateNewConfigFileAndEdit: () -> Unit = {},
    onImportConfigFromClipboard: () -> Unit = {},
    mainViewModel: MainViewModel,
    listState: LazyListState,
    paddingValues: PaddingValues = PaddingValues()
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isMasterDetailSupported = isLandscape && configuration.screenWidthDp >= 840
    val bottomPadding = paddingValues.calculateBottomPadding().coerceAtLeast(12.dp)

    val files by mainViewModel.configFiles.collectAsState()
    val selectedFile by mainViewModel.selectedConfigFile.collectAsState()
    var selectedFileForDetail by remember { mutableStateOf<File?>(null) }
    var isEditorExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditorExpanded) {
        isEditorExpanded = false
    }

    LaunchedEffect(files, selectedFile) {
        if (selectedFileForDetail == null || !files.contains(selectedFileForDetail)) {
            selectedFileForDetail = selectedFile ?: files.firstOrNull()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            mainViewModel.importConfigFromFile(uri)
        }
    }

    if (files.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = bottomPadding + 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.code),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = 12.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Text(
                    text = stringResource(R.string.no_config_files),
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ArrowPreference(
                            title = stringResource(R.string.create_new_config_title),
                            summary = stringResource(R.string.create_new_config_summary),
                            onClick = { onCreateNewConfigFileAndEdit() }
                        )
                        ArrowPreference(
                            title = stringResource(R.string.import_from_clipboard),
                            summary = stringResource(R.string.import_from_clipboard_summary),
                            onClick = {
                                scope.launch {
                                    delay(100)
                                    onImportConfigFromClipboard()
                                }
                            }
                        )
                        ArrowPreference(
                            title = stringResource(R.string.import_from_local_file_title),
                            summary = stringResource(R.string.import_from_local_file_summary),
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
        }
    } else if (isMasterDetailSupported) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (!isEditorExpanded) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                ) {
                    ConfigListPane(
                        files = files,
                        selectedFile = selectedFile,
                        selectedFileForDetail = selectedFileForDetail,
                        onFileSelectedForDetail = { file ->
                            selectedFileForDetail = file
                        },
                        onOpenFullscreenEditor = { file ->
                            selectedFileForDetail = file
                            isEditorExpanded = true
                        },
                        onReloadConfig = onReloadConfig,
                        onEditConfigClick = onEditConfigClick,
                        onDeleteConfigClick = onDeleteConfigClick,
                        onCreateNewConfigFileAndEdit = onCreateNewConfigFileAndEdit,
                        onImportConfigFromClipboard = onImportConfigFromClipboard,
                        mainViewModel = mainViewModel,
                        listState = listState,
                        isWideScreen = true,
                        paddingValues = paddingValues
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = if (isEditorExpanded) 0.dp else 8.dp)
            ) {
                if (selectedFileForDetail != null) {
                    val activeFile = selectedFileForDetail!!
                    key(activeFile.absolutePath) {
                        val editViewModel = remember(activeFile.absolutePath) {
                            ConfigEditViewModel(
                                mainViewModel.getApplication(),
                                activeFile.absolutePath,
                                mainViewModel.prefs
                            )
                        }
                        ConfigEditPane(
                            viewModel = editViewModel,
                            snackbarHostState = snackbarHostState,
                            showNavigationIcon = false,
                            onToggleExpand = { isEditorExpanded = !isEditorExpanded },
                            isExpanded = isEditorExpanded
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_config_files),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }
        }
    } else {
        ConfigListPane(
            files = files,
            selectedFile = selectedFile,
            selectedFileForDetail = null,
            onFileSelectedForDetail = {},
            onOpenFullscreenEditor = { file ->
                onEditConfigClick(file)
            },
            onReloadConfig = onReloadConfig,
            onEditConfigClick = onEditConfigClick,
            onDeleteConfigClick = onDeleteConfigClick,
            onCreateNewConfigFileAndEdit = onCreateNewConfigFileAndEdit,
            onImportConfigFromClipboard = onImportConfigFromClipboard,
            mainViewModel = mainViewModel,
            listState = listState,
            isWideScreen = false,
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun ConfigListPane(
    files: List<File>,
    selectedFile: File?,
    selectedFileForDetail: File?,
    onFileSelectedForDetail: (File) -> Unit,
    onOpenFullscreenEditor: (File) -> Unit,
    onReloadConfig: () -> Unit,
    onEditConfigClick: (File) -> Unit,
    onDeleteConfigClick: (File, () -> Unit) -> Unit,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    mainViewModel: MainViewModel,
    listState: LazyListState,
    isWideScreen: Boolean,
    paddingValues: PaddingValues = PaddingValues()
) {
    val bottomPadding = paddingValues.calculateBottomPadding().coerceAtLeast(12.dp)
    val showDeleteDialog = remember { mutableStateOf<File?>(null) }
    var showAddConfigSheet by remember { mutableStateOf(false) }

    val isServiceEnabled by mainViewModel.isServiceEnabled.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            mainViewModel.importConfigFromFile(uri)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.refreshConfigFileList()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.refreshConfigFileList()
    }

    val hapticFeedback = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index > 0 && to.index > 0) {
            mainViewModel.moveConfigFile(from.index - 1, to.index - 1)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding),
            state = listState
        ) {
            item(key = "add_config_item") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { showAddConfigSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Config",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.add_config_profile),
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }

            items(files, key = { it }) { file ->
                ReorderableItem(state = reorderableLazyListState, key = file) {
                    val isSelected = file == selectedFile
                    val isEditingDetail = isWideScreen && file == selectedFileForDetail

                    val cardModifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(
                            if (isEditingDetail) Modifier.border(
                                width = 2.dp,
                                color = MiuixTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .clickable {
                            if (isWideScreen) {
                                onFileSelectedForDetail(file)
                            }
                            mainViewModel.updateSelectedConfigFile(file)
                            if (isServiceEnabled) {
                                Log.d(
                                    TAG,
                                    "Config selected while service is running, requesting reload."
                                )
                                onReloadConfig()
                            }
                        }

                    Card(
                        modifier = cardModifier,
                        colors = CardDefaults.defaultColors(
                            color = if (isSelected) MiuixTheme.colorScheme.primaryContainer
                            else MiuixTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max)
                                .longPressDraggableHandle(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val extIndex = file.name.lastIndexOf('.')
                                val displayName = if (extIndex > 0) file.name.substring(0, extIndex) else file.name
                                val extTag = if (extIndex > 0) file.name.substring(extIndex + 1).uppercase() else "JSON"
                                Text(
                                    text = displayName,
                                    modifier = Modifier.weight(1f),
                                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                                    color = if (isSelected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
                                )
                                Surface(
                                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = extTag,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                IconButton(onClick = {
                                    onOpenFullscreenEditor(file)
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.edit),
                                        contentDescription = "Edit",
                                        tint = if (isSelected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { showDeleteDialog.value = file }) {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = "Delete",
                                        tint = if (isSelected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddConfigSheet) {
        OverlayBottomSheet(
            title = stringResource(R.string.add_config_profile),
            show = true,
            onDismissRequest = { showAddConfigSheet = false },
            startAction = {
                IconButton(onClick = { showAddConfigSheet = false }) {
                    Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.cancel))
                }
            },
            content = {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    ArrowPreference(
                        title = stringResource(R.string.create_new_config_title),
                        summary = stringResource(R.string.create_new_config_summary),
                        onClick = {
                            showAddConfigSheet = false
                            onCreateNewConfigFileAndEdit()
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.import_from_clipboard),
                        summary = stringResource(R.string.import_from_clipboard_summary),
                        onClick = {
                            showAddConfigSheet = false
                            scope.launch {
                                delay(100)
                                onImportConfigFromClipboard()
                            }
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.import_from_local_file_title),
                        summary = stringResource(R.string.import_from_local_file_summary),
                        onClick = {
                            showAddConfigSheet = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        )
    }

    showDeleteDialog.value?.let { fileToDelete ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.delete_config),
            summary = fileToDelete.name,
            onDismissRequest = { showDeleteDialog.value = null },
            content = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showDeleteDialog.value = null },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            showDeleteDialog.value = null
                            onDeleteConfigClick(fileToDelete) {
                                mainViewModel.refreshConfigFileList()
                                mainViewModel.updateSelectedConfigFile(null)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }
}
