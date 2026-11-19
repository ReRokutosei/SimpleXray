package com.simplexray.re.ui.screens

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.ui.components.ConfirmOverlayDialog
import com.simplexray.re.ui.components.InfoOverlayDialog
import com.simplexray.re.ui.scaffold.LocalLayerBackdrop
import com.simplexray.re.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    scrollState: LazyListState,
    paddingValues: PaddingValues = PaddingValues()
) {
    val geoipProgress by mainViewModel.geoipDownloadProgress.collectAsStateWithLifecycle()
    val geositeProgress by mainViewModel.geositeDownloadProgress.collectAsStateWithLifecycle()
    val customDatProgress by mainViewModel.customDatDownloadProgress.collectAsStateWithLifecycle()
    val isCheckingForUpdates by mainViewModel.isCheckingForUpdates.collectAsStateWithLifecycle()
    val newVersionTag by mainViewModel.newVersionAvailable.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        mainViewModel.updateSettingsState()
        mainViewModel.refreshCustomDatFiles()
        mainViewModel.refreshLuaScriptFiles()
    }

    var showGeoipDeleteDialog by remember { mutableStateOf(false) }
    var showGeositeDeleteDialog by remember { mutableStateOf(false) }
    var deletingCustomDatFileName by remember { mutableStateOf<String?>(null) }
    var deletingLuaFileName by remember { mutableStateOf<String?>(null) }

    var editingRuleFile by remember { mutableStateOf<String?>(null) }
    var editingRuleFileOriginalUrl by remember { mutableStateOf("") }
    var ruleFileUrl by remember { mutableStateOf("") }

    var showDatUrlImportSheet by remember { mutableStateOf(false) }
    var datImportUrl by remember { mutableStateOf("") }

    val geoipUrlDefault = stringResource(R.string.geoip_url)
    val geositeUrlDefault = stringResource(R.string.geosite_url)
    val backdrop = LocalLayerBackdrop.current

    if (editingRuleFile != null) {
        val fileName = editingRuleFile!!
        val isStandard = fileName == "geoip.dat" || fileName == "geosite.dat"
        val trimmedUrl = ruleFileUrl.trim()
        val canConfirm = trimmedUrl.isNotEmpty() || (!isStandard && editingRuleFileOriginalUrl.isNotBlank())
        GlassBottomSheet(
            title = fileName,
            show = editingRuleFile != null,
            backdrop = backdrop,
            onDismissRequest = { editingRuleFile = null },
            startAction = {
                IconButton(onClick = { editingRuleFile = null }) {
                    Icon(
                        imageVector = MiuixIcons.Glass.Close,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            endAction = {
                IconButton(
                    enabled = canConfirm,
                    onClick = {
                        if (isStandard) {
                            mainViewModel.downloadRuleFile(trimmedUrl, fileName)
                        } else {
                            mainViewModel.updateCustomDatUrl(fileName, trimmedUrl)
                            if (trimmedUrl.isNotEmpty()) {
                                mainViewModel.downloadRuleFile(trimmedUrl, fileName)
                            }
                        }
                        editingRuleFile = null
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Glass.Ok,
                        contentDescription = stringResource(R.string.update),
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.onSurface.copy(
                            alpha = if (canConfirm) 1f else 0.38f
                        )
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                TextField(
                    value = ruleFileUrl,
                    onValueChange = { ruleFileUrl = it },
                    label = "URL",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // "Restore default URL" only applies to the built-in GEO files;
                // third-party dat files have no default address.
                if (isStandard) {
                    TextButton(
                        text = stringResource(id = R.string.restore_default_url),
                        onClick = {
                            ruleFileUrl =
                                if (fileName == "geoip.dat") geoipUrlDefault
                                else geositeUrlDefault
                        }
                    )
                }
            }
        }
    }

    if (showDatUrlImportSheet) {
        GlassBottomSheet(
            title = stringResource(R.string.download_from_url_import),
            show = showDatUrlImportSheet,
            backdrop = backdrop,
            onDismissRequest = {
                showDatUrlImportSheet = false
                datImportUrl = ""
            },
            startAction = {
                IconButton(onClick = {
                    showDatUrlImportSheet = false
                    datImportUrl = ""
                }) {
                    Icon(
                        imageVector = MiuixIcons.Glass.Close,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            endAction = {
                IconButton(onClick = {
                    mainViewModel.downloadDatFromUrl(datImportUrl.trim())
                    showDatUrlImportSheet = false
                    datImportUrl = ""
                }) {
                    Icon(
                        imageVector = MiuixIcons.Glass.Ok,
                        contentDescription = stringResource(R.string.confirm),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                TextField(
                    value = datImportUrl,
                    onValueChange = { datImportUrl = it },
                    label = "URL",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showGeoipDeleteDialog) {
        ConfirmOverlayDialog(
            title = stringResource(R.string.delete_rule_file_title),
            summary = stringResource(R.string.delete_rule_file_message),
            confirmText = stringResource(R.string.confirm),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                mainViewModel.restoreDefaultGeoip { }
                showGeoipDeleteDialog = false
            },
            onDismiss = { showGeoipDeleteDialog = false }
        )
    }

    if (showGeositeDeleteDialog) {
        ConfirmOverlayDialog(
            title = stringResource(R.string.delete_rule_file_title),
            summary = stringResource(R.string.delete_rule_file_message),
            confirmText = stringResource(R.string.confirm),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                mainViewModel.restoreDefaultGeosite { }
                showGeositeDeleteDialog = false
            },
            onDismiss = { showGeositeDeleteDialog = false }
        )
    }

    if (deletingCustomDatFileName != null) {
        val fileName = deletingCustomDatFileName!!
        ConfirmOverlayDialog(
            title = stringResource(R.string.delete_rule_file_title),
            summary = stringResource(R.string.delete_custom_dat_file_message, fileName),
            confirmText = stringResource(R.string.confirm),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                mainViewModel.deleteCustomDatFile(fileName)
                deletingCustomDatFileName = null
            },
            onDismiss = { deletingCustomDatFileName = null }
        )
    }

    if (deletingLuaFileName != null) {
        val fileName = deletingLuaFileName!!
        ConfirmOverlayDialog(
            title = stringResource(R.string.delete_lua_file_title),
            summary = stringResource(R.string.delete_lua_file_message, fileName),
            confirmText = stringResource(R.string.confirm),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                mainViewModel.deleteLuaFile(fileName)
                deletingLuaFileName = null
            },
            onDismiss = { deletingLuaFileName = null }
        )
    }

    var activeHelpDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (activeHelpDialog != null) {
        InfoOverlayDialog(
            title = activeHelpDialog!!.first,
            summary = activeHelpDialog!!.second,
            onDismiss = { activeHelpDialog = null }
        )
    }

    if (newVersionTag != null) {
        ConfirmOverlayDialog(
            title = stringResource(R.string.new_version_available_title),
            summary = stringResource(R.string.new_version_available_message, newVersionTag!!),
            confirmText = stringResource(R.string.download),
            cancelText = stringResource(R.string.cancel),
            onConfirm = { mainViewModel.downloadNewVersion(newVersionTag!!) },
            onDismiss = { mainViewModel.clearNewVersionAvailable() }
        )
    }

    val isWideScreen = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width >= 600.dp.roundToPx()
    }
    val topPadding = paddingValues.calculateTopPadding() + 12.dp
    val bottomPadding = paddingValues.calculateBottomPadding().coerceAtLeast(12.dp)

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isWideScreen) Modifier.widthIn(max = 840.dp) else Modifier
                ),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPadding, bottom = bottomPadding)
        ) {
        item {
            SettingsGeneralSection(
                mainViewModel = mainViewModel,
                onShowHelp = { title, summary -> activeHelpDialog = title to summary },
            )
        }

        item {
            SettingsTunnelSection(mainViewModel = mainViewModel)
        }

        item {
            SettingsInboundSection(
                mainViewModel = mainViewModel,
                onShowHelp = { title, summary -> activeHelpDialog = title to summary },
            )
        }

        item {
            SettingsRuleFilesSection(
                mainViewModel = mainViewModel,
                geoipProgress = geoipProgress,
                geositeProgress = geositeProgress,
                customDatProgress = customDatProgress,
                geoipFilePickerLauncher = geoipFilePickerLauncher,
                geositeFilePickerLauncher = geositeFilePickerLauncher,
                onEditRuleFile = { fileName, url ->
                    editingRuleFileOriginalUrl = url
                    ruleFileUrl = url
                    editingRuleFile = fileName
                },
                onDeleteGeoip = { showGeoipDeleteDialog = true },
                onDeleteGeosite = { showGeositeDeleteDialog = true },
                onDeleteCustomDat = { fileName -> deletingCustomDatFileName = fileName },
                onShowDatUrlImport = { showDatUrlImportSheet = true },
                onShowHelp = { title, summary -> activeHelpDialog = title to summary },
            )
        }

        item {
            SettingsLuaScriptsSection(
                mainViewModel = mainViewModel,
                onDeleteLuaScript = { fileName -> deletingLuaFileName = fileName },
            )
        }

        item {
            SettingsNetworkSection(
                mainViewModel = mainViewModel,
                onShowHelp = { title, summary -> activeHelpDialog = title to summary },
            )
        }

        item {
            SettingsAboutSection(
                mainViewModel = mainViewModel,
                isCheckingForUpdates = isCheckingForUpdates,
            )
        }
    }
}
}
