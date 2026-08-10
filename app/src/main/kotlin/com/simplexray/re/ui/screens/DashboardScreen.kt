package com.simplexray.re.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.simplexray.re.R
import com.simplexray.re.common.formatBytes
import com.simplexray.re.common.formatNumber
import com.simplexray.re.common.formatUptime
import com.simplexray.re.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DashboardScreen(
    mainViewModel: MainViewModel,
    onSwitchVpnService: () -> Unit = {},
    paddingValues: PaddingValues = PaddingValues()
) {
    val coreStats by mainViewModel.coreStatsState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                mainViewModel.updateCoreStats()
                delay(1000)
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val bottomPadding = paddingValues.calculateBottomPadding().coerceAtLeast(12.dp)

    Box(
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
                SmallTitle(text = "流量信息")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StatRow(
                            label = stringResource(id = R.string.stats_uplink),
                            value = formatBytes(coreStats.uplink)
                        )
                        StatRow(
                            label = stringResource(id = R.string.stats_downlink),
                            value = formatBytes(coreStats.downlink)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SmallTitle(text = "核心运行状态")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StatRow(
                            label = stringResource(id = R.string.stats_num_goroutine),
                            value = formatNumber(coreStats.numGoroutine.toLong())
                        )
                        StatRow(
                            label = stringResource(id = R.string.stats_num_gc),
                            value = formatNumber(coreStats.numGC.toLong())
                        )
                        StatRow(
                            label = stringResource(id = R.string.stats_alloc),
                            value = formatBytes(coreStats.alloc)
                        )
                        StatRow(
                            label = stringResource(id = R.string.stats_uptime),
                            value = formatUptime(coreStats.uptime)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                val isServiceEnabled by mainViewModel.isServiceEnabled.collectAsState()
                SmallTitle(text = stringResource(id = R.string.core_control))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = Color.Transparent),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Button(
                        onClick = onSwitchVpnService,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = if (isServiceEnabled) ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError
                        ) else ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isServiceEnabled) R.drawable.pause else R.drawable.play
                            ),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (isServiceEnabled) stringResource(R.string.cancel) else stringResource(R.string.app_name),
                            fontSize = MiuixTheme.textStyles.body1.fontSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            color = MiuixTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}
