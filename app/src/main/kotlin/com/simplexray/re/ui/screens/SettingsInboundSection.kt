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
internal fun SettingsInboundSection(
    mainViewModel: MainViewModel,
    onShowHelp: (String, String) -> Unit,
) {
    val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()
    val vpnDisabled = settingsState.switches.disableVpn

        SmallTitle(text = stringResource(R.string.inbound_settings))
        Card(modifier = Modifier.fillMaxWidth()) {
            EditableListItemWithMiuixBottomSheet(
                headline = stringResource(R.string.socks_address),
                currentValue = settingsState.socksAddress.value,
                onValueConfirmed = { newValue -> mainViewModel.updateSocksAddress(newValue) },
                label = stringResource(R.string.socks_address),
                supportingText = stringResource(
                    if (settingsState.switches.tunnelMode != TunnelMode.XrayTun) {
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
                    if (settingsState.switches.tunnelMode != TunnelMode.XrayTun) {
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
                            onClick = { onShowHelp(httpProxyTitle, httpProxySummary) },
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
