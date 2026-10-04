package com.simplexray.re.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.common.ThemeMode
import com.simplexray.re.prefs.TunnelMode
import com.simplexray.re.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.simplexray.re.ui.components.ConfirmOverlayDialog
import com.simplexray.re.ui.components.InfoOverlayDialog

@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    scrollState: androidx.compose.foundation.ScrollState,
    paddingValues: PaddingValues = PaddingValues()
) {
    val geoipProgress by mainViewModel.geoipDownloadProgress.collectAsStateWithLifecycle()
    val geositeProgress by mainViewModel.geositeDownloadProgress.collectAsStateWithLifecycle()
    val customDatProgress by mainViewModel.customDatDownloadProgress.collectAsStateWithLifecycle()
    val isCheckingForUpdates by mainViewModel.isCheckingForUpdates.collectAsStateWithLifecycle()
    val newVersionTag by mainViewModel.newVersionAvailable.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        mainViewModel.updateSettingsState()
        mainViewModel.refreshCustomDatFiles()
        onDispose { }
    }

    var showGeoipDeleteDialog by remember { mutableStateOf(false) }
    var showGeositeDeleteDialog by remember { mutableStateOf(false) }

    var editingRuleFile by remember { mutableStateOf<String?>(null) }
    var ruleFileUrl by remember { mutableStateOf("") }

    var showDatUrlImportSheet by remember { mutableStateOf(false) }
    var datImportUrl by remember { mutableStateOf("") }

    val geoipUrlDefault = stringResource(R.string.geoip_url)
    val geositeUrlDefault = stringResource(R.string.geosite_url)

    if (editingRuleFile != null) {
        OverlayBottomSheet(
            title = editingRuleFile ?: "Rule File URL",
            show = editingRuleFile != null,
            onDismissRequest = { editingRuleFile = null },
            startAction = {
                IconButton(onClick = { editingRuleFile = null }) {
                    Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.cancel))
                }
            },
            endAction = {
                IconButton(onClick = {
                    val fileName = editingRuleFile
                    if (fileName != null) {
                        if (fileName != "geoip.dat" && fileName != "geosite.dat") {
                            mainViewModel.updateCustomDatUrl(fileName, ruleFileUrl)
                        }
                        mainViewModel.downloadRuleFile(ruleFileUrl, fileName)
                    }
                    editingRuleFile = null
                }) {
                    Icon(imageVector = MiuixIcons.Ok, contentDescription = stringResource(R.string.update))
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
                if (editingRuleFile == "geoip.dat" || editingRuleFile == "geosite.dat") {
                    TextButton(
                        text = stringResource(id = R.string.restore_default_url),
                        onClick = {
                            ruleFileUrl =
                                if (editingRuleFile == "geoip.dat") geoipUrlDefault
                                else geositeUrlDefault
                        }
                    )
                }
            }
        }
    }

    if (showDatUrlImportSheet) {
        OverlayBottomSheet(
            title = stringResource(R.string.download_from_url_import),
            show = showDatUrlImportSheet,
            onDismissRequest = {
                showDatUrlImportSheet = false
                datImportUrl = ""
            },
            startAction = {
                IconButton(onClick = {
                    showDatUrlImportSheet = false
                    datImportUrl = ""
                }) {
                    Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.cancel))
                }
            },
            endAction = {
                IconButton(onClick = {
                    mainViewModel.downloadDatFromUrl(datImportUrl.trim())
                    showDatUrlImportSheet = false
                    datImportUrl = ""
                }) {
                    Icon(imageVector = MiuixIcons.Ok, contentDescription = stringResource(R.string.confirm))
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
    val bottomPadding = paddingValues.calculateBottomPadding().coerceAtLeast(12.dp)

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isWideScreen) Modifier.widthIn(max = 840.dp) else Modifier
                ),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding)
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
                    ruleFileUrl = url
                    editingRuleFile = fileName
                },
                onDeleteGeoip = { showGeoipDeleteDialog = true },
                onDeleteGeosite = { showGeositeDeleteDialog = true },
                onShowDatUrlImport = { showDatUrlImportSheet = true },
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



/**
 * Renders the third-party dat file rows. Kept as a separate composable so that
 * list-size changes (a download in progress adds/removes entries) are isolated
 * from the sibling ArrowPreference rows that follow in the settings card —
 * otherwise Compose slot movement can leak into those groups and crash with
 * "Boolean cannot be cast to ComposableLambdaImpl".
 */
@Composable
private fun CustomDatFilesSection(
    mainViewModel: MainViewModel,
    prefs: com.simplexray.re.prefs.Preferences,
    customDatProgress: Map<String, String?>,
    customDatPickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onEditUrl: (datName: String, url: String) -> Unit,
) {
    val context = LocalContext.current
    val customDatVersion by mainViewModel.customDatVersion.collectAsStateWithLifecycle()
    val customDatFiles = remember(customDatVersion, customDatProgress) {
        val names = LinkedHashSet<String>()
        context.filesDir.listFiles { file ->
            file.isFile &&
                file.name.lowercase().endsWith(".dat") &&
                !file.name.equals("geoip.dat", ignoreCase = true) &&
                !file.name.equals("geosite.dat", ignoreCase = true) &&
                !file.name.lowercase().startsWith("profileinstaller_")
        }?.forEach { names.add(it.name) }
        // Include files that are still downloading (may not exist on disk yet).
        names.addAll(customDatProgress.keys)
        names.toList()
    }

    customDatFiles.forEach { datName ->
        val customUrl = prefs.customDatUrls[datName] ?: ""
        val isDownloading = customDatProgress[datName] != null
        key(datName) {
            BasicComponent(
                title = datName,
                summary = customDatProgress[datName] ?: mainViewModel.getCustomDatSummary(datName),
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloading) {
                            IconButton(onClick = { mainViewModel.cancelDownload(datName) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.cancel),
                                    contentDescription = stringResource(R.string.cancel)
                                )
                            }
                        } else {
                            IconButton(onClick = { onEditUrl(datName, customUrl) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.cloud_download),
                                    contentDescription = stringResource(R.string.rule_file_update_url)
                                )
                            }
                            IconButton(onClick = {
                                if (customUrl.isNotEmpty()) {
                                    mainViewModel.downloadRuleFile(customUrl, datName)
                                } else {
                                    customDatPickerLauncher.launch(arrayOf("*/*"))
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.place_item),
                                    contentDescription = stringResource(R.string.import_file)
                                )
                            }
                            IconButton(onClick = { mainViewModel.deleteCustomDatFile(datName) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.delete),
                                    contentDescription = stringResource(R.string.delete_config)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
