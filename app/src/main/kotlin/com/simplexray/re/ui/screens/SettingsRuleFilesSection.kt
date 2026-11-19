package com.simplexray.re.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close2
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Download
import top.yukonga.miuix.kmp.icon.glass.Help
import top.yukonga.miuix.kmp.icon.glass.Import
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onDeleteCustomDat: (String) -> Unit,
    onShowDatUrlImport: () -> Unit,
    onShowHelp: (String, String) -> Unit,
) {
    val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()
    var replacingDatFileName by remember { mutableStateOf<String?>(null) }

    val customDatPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            mainViewModel.importCustomDatFile(uri, replacingDatFileName)
        }
        replacingDatFileName = null
    }

    SmallTitle(text = stringResource(R.string.rule_files_category_title))
    Card(modifier = Modifier.fillMaxWidth()) {
        RuleFileRow(
            title = "geoip.dat",
            summary = geoipProgress
                ?: settingsState.info.geoipSummary.ifBlank { stringResource(R.string.rule_file_default) },
            downloadProgress = geoipProgress,
            showDelete = settingsState.files.isGeoipCustom,
            deleteContentDescription = stringResource(R.string.reset_file),
            onCancelDownload = { mainViewModel.cancelDownload("geoip.dat") },
            onDownloadClick = { onEditRuleFile("geoip.dat", settingsState.info.geoipUrl) },
            onImportClick = { geoipFilePickerLauncher.launch(arrayOf("*/*")) },
            onDeleteClick = onDeleteGeoip,
        )

        RuleFileRow(
            title = "geosite.dat",
            summary = geositeProgress
                ?: settingsState.info.geositeSummary.ifBlank { stringResource(R.string.rule_file_default) },
            downloadProgress = geositeProgress,
            showDelete = settingsState.files.isGeositeCustom,
            deleteContentDescription = stringResource(R.string.reset_file),
            onCancelDownload = { mainViewModel.cancelDownload("geosite.dat") },
            onDownloadClick = { onEditRuleFile("geosite.dat", settingsState.info.geositeUrl) },
            onImportClick = { geositeFilePickerLauncher.launch(arrayOf("*/*")) },
            onDeleteClick = onDeleteGeosite,
        )

        // The third-party dat list lives in its own composable: its size
        // changes while downloads are in progress, and keeping it in a
        // separate composition context prevents those changes from shifting
        // the Compose slots of the ArrowPreference rows below (previously
        // caused "Boolean cannot be cast to ComposableLambdaImpl").
        CustomDatFilesSection(
            mainViewModel = mainViewModel,
            customDatProgress = customDatProgress,
            onEditUrl = onEditRuleFile,
            onReplaceCustomDat = { datName ->
                replacingDatFileName = datName
                customDatPickerLauncher.launch(arrayOf("*/*"))
            },
            onDeleteCustomDat = onDeleteCustomDat,
        )

        // Wrap each ArrowPreference in its own keyed group: they are
        // @NonRestartableComposable and call the same BasicComponent
        // overload, so two adjacent rows would otherwise collide on the
        // same composable-lambda slot key during recomposition ("Boolean
        // cannot be cast to ComposableLambdaImpl").
        key("import-from-file") {
            ArrowPreference(
                title = "+ " + stringResource(R.string.import_from_file) + " (.dat)",
                onClick = {
                    replacingDatFileName = null
                    customDatPickerLauncher.launch(arrayOf("*/*"))
                }
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
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                sdf.format(Date(lastUpdateTime))
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

        val autoRestartEnabled = geoIntervalHours > 0
        val autoRestartTitle = stringResource(R.string.geo_update_auto_restart_title)
        val autoRestartHelp = stringResource(R.string.geo_update_auto_restart_help)
        SwitchPreference(
            title = "",
            startAction = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = autoRestartTitle,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface.copy(
                            alpha = if (autoRestartEnabled) 1f else 0.38f
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onShowHelp(autoRestartTitle, autoRestartHelp) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Glass.Help,
                            contentDescription = autoRestartTitle,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            checked = settingsState.switches.geoUpdateAutoRestart,
            onCheckedChange = { enabled ->
                mainViewModel.setGeoUpdateAutoRestartEnabled(enabled)
            },
            enabled = autoRestartEnabled
        )
    }
}

@Composable
private fun RuleFileRow(
    title: String,
    summary: String,
    downloadProgress: String?,
    showDelete: Boolean,
    deleteContentDescription: String,
    onCancelDownload: () -> Unit,
    onDownloadClick: () -> Unit,
    onImportClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloadProgress != null) {
                    IconButton(onClick = onCancelDownload) {
                        Icon(
                            imageVector = MiuixIcons.Glass.Close2,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            imageVector = MiuixIcons.Glass.Download,
                            contentDescription = stringResource(R.string.rule_file_update_url),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onImportClick) {
                        Icon(
                            imageVector = MiuixIcons.Glass.Import,
                            contentDescription = stringResource(R.string.import_file),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (showDelete) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = MiuixIcons.Glass.Delete,
                                contentDescription = deleteContentDescription,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun CustomDatFilesSection(
    mainViewModel: MainViewModel,
    customDatProgress: Map<String, String?>,
    onEditUrl: (datName: String, url: String) -> Unit,
    onReplaceCustomDat: (datName: String) -> Unit,
    onDeleteCustomDat: (datName: String) -> Unit,
) {
    val customDatFiles by mainViewModel.customDatFiles.collectAsStateWithLifecycle()

    customDatFiles.forEach { item ->
        val progress = customDatProgress[item.name]
        key(item.name) {
            RuleFileRow(
                title = item.name,
                summary = progress ?: item.summary,
                downloadProgress = progress,
                showDelete = true,
                deleteContentDescription = stringResource(R.string.delete),
                onCancelDownload = { mainViewModel.cancelDownload(item.name) },
                onDownloadClick = { onEditUrl(item.name, item.url) },
                onImportClick = { onReplaceCustomDat(item.name) },
                onDeleteClick = { onDeleteCustomDat(item.name) },
            )
        }
    }
}
