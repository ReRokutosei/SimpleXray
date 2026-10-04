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
internal fun SettingsTunnelSection(
    mainViewModel: MainViewModel,
) {
    val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()
    val vpnDisabled = settingsState.switches.disableVpn
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
                ),
                DropdownItem(
                    text = stringResource(R.string.tunnel_mode_sing_tun),
                    summary = stringResource(R.string.tunnel_mode_sing_tun_summary),
                    selected = settingsState.switches.tunnelMode == TunnelMode.SingTun,
                    onClick = { mainViewModel.setTunnelMode(TunnelMode.SingTun) }
                ),
                DropdownItem(
                    text = stringResource(R.string.tunnel_mode_simpletun),
                    summary = stringResource(R.string.tunnel_mode_simpletun_summary),
                    selected = settingsState.switches.tunnelMode == TunnelMode.SimpleTun,
                    onClick = { mainViewModel.setTunnelMode(TunnelMode.SimpleTun) }
                )
            )
        )
    )

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

            val isSimpleTun = settingsState.switches.tunnelMode == TunnelMode.SimpleTun

            EditableListItemWithMiuixBottomSheet(
                headline = stringResource(R.string.tunnel_mtu_title),
                currentValue = if (isSimpleTun) "1500" else settingsState.tunnelMtu.value,
                onValueConfirmed = { newValue -> mainViewModel.updateTunnelMtu(newValue) },
                label = stringResource(R.string.tunnel_mtu_title),
                supportingText = stringResource(R.string.tunnel_mtu_summary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !vpnDisabled && !isSimpleTun
            )

            SwitchPreference(
                title = stringResource(R.string.ipv6),
                summary = stringResource(R.string.ipv6_summary),
                checked = settingsState.switches.ipv6Enabled,
                onCheckedChange = { mainViewModel.setIpv6Enabled(it) },
                enabled = !vpnDisabled && !isSimpleTun
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
