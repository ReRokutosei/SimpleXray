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
internal fun SettingsNetworkSection(
    mainViewModel: MainViewModel,
    onShowHelp: (String, String) -> Unit,
) {
    val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()
    val vpnDisabled = settingsState.switches.disableVpn
    val logLevelOptions = com.simplexray.re.prefs.LogLevel.entries
    val logLevelNames = logLevelOptions.map { it.name }
    val currentLogLevelIndex = logLevelOptions.indexOf(settingsState.switches.logLevel).coerceAtLeast(0)

        SmallTitle(text = stringResource(R.string.network_settings))
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.bypass_lan_title),
                summary = stringResource(R.string.bypass_lan_summary),
                checked = settingsState.switches.bypassLanEnabled,
                onCheckedChange = { mainViewModel.setBypassLanEnabled(it) },
                enabled = !vpnDisabled
            )

            val errorLogTitle = stringResource(R.string.error_log_title)
            val loglevelSummary = stringResource(R.string.loglevel_summary)
            var isLogLevelDropdownExpanded by remember { mutableStateOf(false) }
            val currentLogLevelName = logLevelNames.getOrNull(currentLogLevelIndex) ?: ""
            BasicComponent(
                title = "",
                startAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = errorLogTitle,
                            fontSize = MiuixTheme.textStyles.headline1.fontSize,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onShowHelp(errorLogTitle, loglevelSummary) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Help,
                                contentDescription = errorLogTitle,
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
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    DropdownArrowEndAction(
                        actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
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
                },
                onClick = {
                    isLogLevelDropdownExpanded = !isLogLevelDropdownExpanded
                }
            )

            SwitchPreference(
                title = stringResource(R.string.access_log_title),
                checked = settingsState.switches.accessLog,
                onCheckedChange = { mainViewModel.setAccessLog(it) }
            )

            SwitchPreference(
                title = stringResource(R.string.dns_log_title),
                checked = settingsState.switches.dnsLog,
                onCheckedChange = { mainViewModel.setDnsLog(it) }
            )
        }
    
}
