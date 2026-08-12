package com.simplexray.re.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.common.ThemeMode
import com.simplexray.re.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.simplexray.re.ui.components.ConfirmOverlayDialog

@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    scrollState: androidx.compose.foundation.ScrollState,
    paddingValues: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()
    val geoipProgress by mainViewModel.geoipDownloadProgress.collectAsStateWithLifecycle()
    val geositeProgress by mainViewModel.geositeDownloadProgress.collectAsStateWithLifecycle()
    val customDatProgress by mainViewModel.customDatDownloadProgress.collectAsStateWithLifecycle()
    val isCheckingForUpdates by mainViewModel.isCheckingForUpdates.collectAsStateWithLifecycle()
    val newVersionTag by mainViewModel.newVersionAvailable.collectAsStateWithLifecycle()

    val vpnDisabled = settingsState.switches.disableVpn

    var showGeoipDeleteDialog by remember { mutableStateOf(false) }
    var showGeositeDeleteDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    var editingRuleFile by remember { mutableStateOf<String?>(null) }
    var ruleFileUrl by remember { mutableStateOf("") }

    var showDatUrlImportSheet by remember { mutableStateOf(false) }
    var datImportUrl by remember { mutableStateOf("") }

    val themeOptions = listOf(
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark),
        stringResource(R.string.auto)
    )
    val themeModes = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Auto)
    val currentThemeIndex = themeModes.indexOf(settingsState.switches.themeMode).coerceAtLeast(0)

    val logLevelOptions = com.simplexray.re.prefs.LogLevel.entries
    val logLevelNames = logLevelOptions.map { it.name }
    val currentLogLevelIndex = logLevelOptions.indexOf(settingsState.switches.logLevel).coerceAtLeast(0)

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
                                if (editingRuleFile == "geoip.dat") context.getString(R.string.geoip_url)
                                else context.getString(R.string.geosite_url)
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

    if (newVersionTag != null) {
        ConfirmOverlayDialog(
            title = stringResource(R.string.new_version_available_title),
            summary = stringResource(R.string.new_version_available_message, newVersionTag!!),
            confirmText = stringResource(R.string.download),
            cancelText = stringResource(android.R.string.cancel),
            onConfirm = { mainViewModel.downloadNewVersion(newVersionTag!!) },
            onDismiss = { mainViewModel.clearNewVersionAvailable() }
        )
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
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
            SmallTitle(text = stringResource(R.string.general))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(R.string.use_template_title),
                    summary = stringResource(R.string.use_template_summary),
                    checked = settingsState.switches.useTemplateEnabled,
                    onCheckedChange = { mainViewModel.setUseTemplateEnabled(it) }
                )

                OverlayDropdownPreference(
                    title = stringResource(R.string.theme_title),
                    summary = stringResource(R.string.theme_summary),
                    items = themeOptions,
                    selectedIndex = currentThemeIndex,
                    onSelectedIndexChange = { index ->
                        mainViewModel.setTheme(themeModes[index])
                    }
                )

                SwitchPreference(
                    title = stringResource(R.string.hide_from_recents_title),
                    summary = stringResource(R.string.hide_from_recents_summary),
                    checked = settingsState.switches.hideFromRecents,
                    onCheckedChange = { mainViewModel.setHideFromRecentsEnabled(it) }
                )
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.vpn_interface))
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.apps_title),
                    summary = stringResource(R.string.apps_summary),
                    onClick = { mainViewModel.navigateToAppList() }
                )

                SwitchPreference(
                    title = stringResource(R.string.disable_vpn_title),
                    summary = stringResource(R.string.disable_vpn_summary),
                    checked = settingsState.switches.disableVpn,
                    onCheckedChange = { mainViewModel.setDisableVpnEnabled(it) }
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.socks_address),
                    currentValue = settingsState.socksAddress.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateSocksAddress(newValue) },
                    label = stringResource(R.string.socks_address),
                    supportingText = stringResource(R.string.socks_address_summary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !vpnDisabled
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.socks_port),
                    currentValue = settingsState.socksPort.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateSocksPort(newValue) },
                    label = stringResource(R.string.socks_port),
                    supportingText = stringResource(R.string.socks_port_summary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !vpnDisabled
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.socks_user),
                    currentValue = settingsState.socksUser.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateSocksUser(newValue) },
                    label = stringResource(R.string.socks_user),
                    supportingText = stringResource(R.string.socks_user_summary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    enabled = !vpnDisabled
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.socks_pass),
                    currentValue = settingsState.socksPass.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateSocksPass(newValue) },
                    label = stringResource(R.string.socks_pass),
                    supportingText = stringResource(R.string.socks_pass_summary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !vpnDisabled
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.dns_ipv4),
                    currentValue = settingsState.dnsIpv4.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateDnsIpv4(newValue) },
                    label = stringResource(R.string.dns_ipv4),
                    supportingText = stringResource(R.string.dns_ipv4_summary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !vpnDisabled
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.dns_ipv6),
                    currentValue = settingsState.dnsIpv6.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateDnsIpv6(newValue) },
                    label = stringResource(R.string.dns_ipv6),
                    supportingText = stringResource(R.string.dns_ipv6_summary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = settingsState.switches.ipv6Enabled && !vpnDisabled
                )

                SwitchPreference(
                    title = stringResource(R.string.ipv6),
                    summary = stringResource(R.string.ipv6_summary),
                    checked = settingsState.switches.ipv6Enabled,
                    onCheckedChange = { mainViewModel.setIpv6Enabled(it) },
                    enabled = !vpnDisabled
                )

                SwitchPreference(
                    title = stringResource(R.string.http_proxy_title),
                    summary = stringResource(R.string.http_proxy_summary),
                    checked = settingsState.switches.httpProxyEnabled,
                    onCheckedChange = { mainViewModel.setHttpProxyEnabled(it) },
                    enabled = !vpnDisabled
                )

                SwitchPreference(
                    title = stringResource(R.string.bypass_lan_title),
                    summary = stringResource(R.string.bypass_lan_summary),
                    checked = settingsState.switches.bypassLanEnabled,
                    onCheckedChange = { mainViewModel.setBypassLanEnabled(it) },
                    enabled = !vpnDisabled
                )

                OverlayDropdownPreference(
                    title = stringResource(R.string.loglevel_title),
                    summary = stringResource(R.string.loglevel_summary),
                    items = logLevelNames,
                    selectedIndex = currentLogLevelIndex,
                    onSelectedIndexChange = { index ->
                        mainViewModel.setLogLevel(logLevelOptions[index])
                    },
                    enabled = !vpnDisabled
                )
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.rule_files_category_title))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "geoip.dat",
                    summary = geoipProgress ?: if (!settingsState.files.isGeoipCustom) stringResource(R.string.rule_file_default) else settingsState.info.geoipSummary,
                    endActions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (geoipProgress != null) {
                                IconButton(onClick = { mainViewModel.cancelDownload("geoip.dat") }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cancel),
                                        contentDescription = stringResource(R.string.cancel)
                                    )
                                }
                            } else {
                                IconButton(onClick = {
                                    ruleFileUrl = settingsState.info.geoipUrl
                                    editingRuleFile = "geoip.dat"
                                }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cloud_download),
                                        contentDescription = stringResource(R.string.rule_file_update_url)
                                    )
                                }
                                if (!settingsState.files.isGeoipCustom) {
                                    IconButton(onClick = { geoipFilePickerLauncher.launch(arrayOf("*/*")) }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.place_item),
                                            contentDescription = stringResource(R.string.import_file)
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { showGeoipDeleteDialog = true }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.delete),
                                            contentDescription = stringResource(R.string.reset_file)
                                        )
                                    }
                                }
                            }
                        }
                    }
                )

                BasicComponent(
                    title = "geosite.dat",
                    summary = geositeProgress ?: if (!settingsState.files.isGeositeCustom) stringResource(R.string.rule_file_default) else settingsState.info.geositeSummary,
                    endActions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (geositeProgress != null) {
                                IconButton(onClick = { mainViewModel.cancelDownload("geosite.dat") }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cancel),
                                        contentDescription = stringResource(R.string.cancel)
                                    )
                                }
                            } else {
                                IconButton(onClick = {
                                    ruleFileUrl = settingsState.info.geositeUrl
                                    editingRuleFile = "geosite.dat"
                                }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cloud_download),
                                        contentDescription = stringResource(R.string.rule_file_update_url)
                                    )
                                }
                                if (!settingsState.files.isGeositeCustom) {
                                    IconButton(onClick = { geositeFilePickerLauncher.launch(arrayOf("*/*")) }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.place_item),
                                            contentDescription = stringResource(R.string.import_file)
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { showGeositeDeleteDialog = true }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.delete),
                                            contentDescription = stringResource(R.string.reset_file)
                                        )
                                    }
                                }
                            }
                        }
                    }
                )

                val prefs = remember { com.simplexray.re.prefs.Preferences(context) }
                val customDatPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        mainViewModel.importCustomDatFile(uri)
                    }
                }

                // The third-party dat list lives in its own composable: its size
                // changes while downloads are in progress, and keeping it in a
                // separate composition context prevents those changes from shifting
                // the Compose slots of the ArrowPreference rows below (previously
                // caused "Boolean cannot be cast to ComposableLambdaImpl").
                CustomDatFilesSection(
                    mainViewModel = mainViewModel,
                    prefs = prefs,
                    customDatProgress = customDatProgress,
                    customDatPickerLauncher = customDatPickerLauncher,
                    onEditUrl = { datName, url ->
                        ruleFileUrl = url
                        editingRuleFile = datName
                    }
                )

                // Wrap each ArrowPreference in its own keyed group: they are
                // @NonRestartableComposable and call the same BasicComponent
                // overload, so two adjacent rows would otherwise collide on the
                // same composable-lambda slot key during recomposition ("Boolean
                // cannot be cast to ComposableLambdaImpl").
                key("import-from-file") {
                    ArrowPreference(
                        title = "+ " + stringResource(R.string.import_from_file) + " (.dat)",
                        onClick = { customDatPickerLauncher.launch(arrayOf("*/*")) }
                    )
                }

                key("import-from-url") {
                    ArrowPreference(
                        title = "+ " + stringResource(R.string.download_from_url_import) + " (.dat)",
                        onClick = { showDatUrlImportSheet = true }
                    )
                }

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.geo_update_interval_title),
                    currentValue = settingsState.geoUpdateIntervalHours.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateGeoUpdateInterval(newValue) },
                    label = stringResource(R.string.geo_update_interval_title),
                    supportingText = stringResource(R.string.geo_update_interval_summary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.connectivity_test))
            Card(modifier = Modifier.fillMaxWidth()) {
                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.connectivity_test_target),
                    currentValue = settingsState.connectivityTestTarget.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateConnectivityTestTarget(newValue) },
                    label = stringResource(R.string.connectivity_test_target),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.connectivity_test_timeout),
                    currentValue = settingsState.connectivityTestTimeout.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateConnectivityTestTimeout(newValue) },
                    label = stringResource(R.string.connectivity_test_timeout),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.about))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.version),
                    summary = settingsState.info.appVersion,
                    endActions = {
                        TextButton(
                            text = if (isCheckingForUpdates) "检查中..." else stringResource(R.string.check_for_updates),
                            onClick = { mainViewModel.checkForUpdates() },
                            enabled = !isCheckingForUpdates
                        )
                    }
                )

                BasicComponent(
                    title = stringResource(R.string.kernel),
                    summary = settingsState.info.kernelVersion
                )

                ArrowPreference(
                    title = stringResource(R.string.source),
                    summary = stringResource(R.string.open_source),
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.source_url)))
                        context.startActivity(browserIntent)
                    }
                )

                ArrowPreference(
                    title = stringResource(R.string.privacy_disclaimer_title),
                    summary = stringResource(R.string.privacy_disclaimer_summary),
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_disclaimer_url)))
                        context.startActivity(browserIntent)
                    }
                )
            }
        }
    }
}
}

@Composable
fun EditableListItemWithMiuixBottomSheet(
    headline: String,
    currentValue: String,
    onValueConfirmed: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true
) {
    var showSheet by remember { mutableStateOf(false) }
    var tempValue by remember { mutableStateOf(currentValue) }

    if (showSheet) {
        OverlayBottomSheet(
            title = headline,
            show = true,
            onDismissRequest = { showSheet = false },
            startAction = {
                IconButton(onClick = { showSheet = false }) {
                    Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.cancel))
                }
            },
            endAction = {
                IconButton(onClick = {
                    onValueConfirmed(tempValue)
                    showSheet = false
                }) {
                    Icon(imageVector = MiuixIcons.Ok, contentDescription = stringResource(R.string.confirm))
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                TextField(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    label = label,
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!supportingText.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = supportingText,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }

    ArrowPreference(
        title = headline,
        summary = if (supportingText != null) "$supportingText\n$currentValue" else currentValue,
        onClick = {
            tempValue = currentValue
            showSheet = true
        },
        enabled = enabled
    )
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
