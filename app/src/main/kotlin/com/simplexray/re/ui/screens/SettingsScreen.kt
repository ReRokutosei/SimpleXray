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

    val iconOptions = listOf(
        stringResource(R.string.icon_flat),
        stringResource(R.string.icon_lineal),
        stringResource(R.string.icon_lineal_color)
    )
    val iconKeys = listOf("flat", "lineal", "lineal_color")

    val geoipUrlDefault = stringResource(R.string.geoip_url)
    val geositeUrlDefault = stringResource(R.string.geosite_url)
    val sourceUrl = stringResource(R.string.source_url)
    val privacyDisclaimerUrl = stringResource(R.string.privacy_disclaimer_url)

    val logLevelOptions = com.simplexray.re.prefs.LogLevel.entries
    val logLevelNames = logLevelOptions.map { it.name }
    val currentLogLevelIndex = logLevelOptions.indexOf(settingsState.switches.logLevel).coerceAtLeast(0)

    val tunnelModeEntries = listOf(
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = stringResource(R.string.tunnel_mode_xray_tun),
                    summary = stringResource(R.string.tunnel_mode_xray_tun_summary),
                    selected = settingsState.switches.tunnelMode == TunnelMode.XrayTun,
                    onClick = { mainViewModel.setTunnelMode(TunnelMode.XrayTun) }
                ),
                DropdownItem(
                    text = stringResource(R.string.tunnel_mode_hev_socks5),
                    summary = stringResource(R.string.tunnel_mode_hev_socks5_summary),
                    selected = settingsState.switches.tunnelMode == TunnelMode.HevSocks5Tunnel,
                    onClick = { mainViewModel.setTunnelMode(TunnelMode.HevSocks5Tunnel) }
                )
            )
        )
    )

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
            SmallTitle(text = stringResource(R.string.general))
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.theme_title),
                    items = themeOptions,
                    selectedIndex = currentThemeIndex,
                    onSelectedIndexChange = { index ->
                        mainViewModel.setTheme(themeModes[index])
                    }
                )

                val currentIcon by mainViewModel.appIcon.collectAsStateWithLifecycle()
                val currentIconIndex = iconKeys.indexOf(currentIcon).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = stringResource(R.string.app_icon),
                    items = iconOptions,
                    selectedIndex = currentIconIndex,
                    onSelectedIndexChange = { index ->
                        mainViewModel.setAppIcon(iconKeys[index])
                    }
                )

                SwitchPreference(
                    title = stringResource(R.string.hide_from_recents_title),
                    checked = settingsState.switches.hideFromRecents,
                    onCheckedChange = { mainViewModel.setHideFromRecentsEnabled(it) }
                )

                val keepAwakeTitle = stringResource(R.string.keep_awake_title)
                val keepAwakeSummary = stringResource(R.string.keep_awake_summary)
                SwitchPreference(
                    title = "",
                    startAction = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = keepAwakeTitle,
                                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { activeHelpDialog = keepAwakeTitle to keepAwakeSummary },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Help,
                                    contentDescription = keepAwakeTitle,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    checked = settingsState.switches.keepAwake,
                    onCheckedChange = { mainViewModel.setKeepAwakeEnabled(it) }
                )
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.vpn_interface))
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.apps_title),
                    onClick = { mainViewModel.navigateToAppList() }
                )

                SwitchPreference(
                    title = stringResource(R.string.disable_vpn_title),
                    summary = stringResource(R.string.disable_vpn_summary),
                    checked = settingsState.switches.disableVpn,
                    onCheckedChange = { mainViewModel.setDisableVpnEnabled(it) }
                )

                OverlayDropdownPreference(
                    title = stringResource(R.string.tunnel_mode_title),
                    entries = tunnelModeEntries,
                    enabled = !vpnDisabled
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.tunnel_mtu_title),
                    currentValue = settingsState.tunnelMtu.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateTunnelMtu(newValue) },
                    label = stringResource(R.string.tunnel_mtu_title),
                    supportingText = stringResource(R.string.tunnel_mtu_summary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !vpnDisabled
                )

                SwitchPreference(
                    title = stringResource(R.string.ipv6),
                    summary = stringResource(R.string.ipv6_summary),
                    checked = settingsState.switches.ipv6Enabled,
                    onCheckedChange = { mainViewModel.setIpv6Enabled(it) },
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
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.inbound_settings))
            Card(modifier = Modifier.fillMaxWidth()) {
                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.socks_address),
                    currentValue = settingsState.socksAddress.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateSocksAddress(newValue) },
                    label = stringResource(R.string.socks_address),
                    supportingText = stringResource(
                        if (settingsState.switches.tunnelMode == TunnelMode.HevSocks5Tunnel) {
                            R.string.socks_address_summary_socks_tunnel
                        } else {
                            R.string.socks_address_summary
                        }
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !vpnDisabled
                )

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.socks_port),
                    currentValue = settingsState.socksPort.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateSocksPort(newValue) },
                    label = stringResource(R.string.socks_port),
                    supportingText = stringResource(
                        if (settingsState.switches.tunnelMode == TunnelMode.HevSocks5Tunnel) {
                            R.string.socks_port_summary_socks_tunnel
                        } else {
                            R.string.socks_port_summary
                        }
                    ),
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

                val httpProxyTitle = stringResource(R.string.http_proxy_title)
                val httpProxySummary = stringResource(R.string.http_proxy_summary)
                SwitchPreference(
                    title = "",
                    startAction = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = httpProxyTitle,
                                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = if (!vpnDisabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { activeHelpDialog = httpProxyTitle to httpProxySummary },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Help,
                                    contentDescription = httpProxyTitle,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    checked = settingsState.switches.httpProxyEnabled,
                    onCheckedChange = { mainViewModel.setHttpProxyEnabled(it) },
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

                val geoIntervalHours = settingsState.geoUpdateIntervalHours.value.toIntOrNull() ?: 0
                val geoSummaryText = if (geoIntervalHours <= 0) {
                    stringResource(R.string.geo_update_disabled)
                } else {
                    val lastUpdateTime = settingsState.lastGeoUpdateTime
                    val timeStr = if (lastUpdateTime > 0L) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(lastUpdateTime))
                    } else {
                        stringResource(R.string.geo_never_updated)
                    }
                    stringResource(R.string.geo_last_update_format, geoIntervalHours, timeStr)
                }

                EditableListItemWithMiuixBottomSheet(
                    headline = stringResource(R.string.geo_update_interval_title),
                    currentValue = settingsState.geoUpdateIntervalHours.value,
                    onValueConfirmed = { newValue -> mainViewModel.updateGeoUpdateInterval(newValue) },
                    label = stringResource(R.string.geo_update_interval_title),
                    supportingText = stringResource(R.string.geo_update_dialog_supporting_text),
                    customSummary = geoSummaryText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.network_settings))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(R.string.bypass_lan_title),
                    summary = stringResource(R.string.bypass_lan_summary),
                    checked = settingsState.switches.bypassLanEnabled,
                    onCheckedChange = { mainViewModel.setBypassLanEnabled(it) },
                    enabled = !vpnDisabled
                )

                val loglevelTitle = stringResource(R.string.loglevel_title)
                val loglevelSummary = stringResource(R.string.loglevel_summary)
                var isLogLevelDropdownExpanded by remember { mutableStateOf(false) }
                val currentLogLevelName = logLevelNames.getOrNull(currentLogLevelIndex) ?: ""
                BasicComponent(
                    title = "",
                    startAction = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = loglevelTitle,
                                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = if (!vpnDisabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { activeHelpDialog = loglevelTitle to loglevelSummary },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Help,
                                    contentDescription = loglevelTitle,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    endActions = {
                        Text(
                            text = currentLogLevelName,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = if (!vpnDisabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        DropdownArrowEndAction(
                            actionColor = if (!vpnDisabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        )
                        if (!vpnDisabled) {
                            val logLevelDropdownEntry = remember(logLevelNames, currentLogLevelIndex) {
                                DropdownEntry(
                                    logLevelNames.mapIndexed { index, name ->
                                        DropdownItem(
                                            text = name,
                                            selected = index == currentLogLevelIndex,
                                            onClick = { mainViewModel.setLogLevel(logLevelOptions[index]) }
                                        )
                                    }
                                )
                            }
                            OverlayDropdownPopup(
                                entry = logLevelDropdownEntry,
                                show = isLogLevelDropdownExpanded,
                                onDismiss = { isLogLevelDropdownExpanded = false },
                                onDismissFinished = {},
                                maxHeight = null,
                                dropdownColors = DropdownDefaults.dropdownColors(),
                                renderInRootScaffold = true,
                                collapseOnSelection = true
                            )
                        }
                    },
                    onClick = {
                        if (!vpnDisabled) {
                            isLogLevelDropdownExpanded = !isLogLevelDropdownExpanded
                        }
                    },
                    enabled = !vpnDisabled
                )
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.about))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = settingsState.info.appVersion,
                    summary = stringResource(R.string.version),
                    endActions = {
                        TextButton(
                            text = if (isCheckingForUpdates) "检查中..." else stringResource(R.string.check_for_updates),
                            onClick = { mainViewModel.checkForUpdates() },
                            enabled = !isCheckingForUpdates
                        )
                    }
                )

                BasicComponent(
                    title = settingsState.info.kernelVersion,
                    summary = stringResource(R.string.kernel)
                )

                ArrowPreference(
                    title = stringResource(R.string.source),
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, sourceUrl.toUri())
                        context.startActivity(browserIntent)
                    }
                )

                ArrowPreference(
                    title = stringResource(R.string.privacy_disclaimer_title),
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, privacyDisclaimerUrl.toUri())
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
    customSummary: String? = null,
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
        summary = customSummary,
        endActions = {
            if (customSummary == null) {
                Text(
                    text = currentValue,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        },
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
