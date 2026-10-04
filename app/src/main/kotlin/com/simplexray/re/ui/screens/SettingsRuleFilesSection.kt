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
internal fun SettingsRuleFilesSection(
    mainViewModel: MainViewModel,
    geoipProgress: String?,
    geositeProgress: String?,
    customDatProgress: Map<String, String?>,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    onEditRuleFile: (String, String) -> Unit,
    onDeleteGeoip: () -> Unit,
    onDeleteGeosite: () -> Unit,
    onShowDatUrlImport: () -> Unit,
) {
    val context = LocalContext.current
    val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()

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
                            IconButton(onClick = { onEditRuleFile("geoip.dat", settingsState.info.geoipUrl) }) {
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
                                IconButton(onClick = { onDeleteGeoip() }) {
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
                            IconButton(onClick = { onEditRuleFile("geosite.dat", settingsState.info.geositeUrl) }) {
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
                                IconButton(onClick = { onDeleteGeosite() }) {
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
                onEditUrl = onEditRuleFile
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
                    onClick = { onShowDatUrlImport() }
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
