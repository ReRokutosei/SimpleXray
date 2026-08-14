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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.formatBytes
import com.simplexray.re.common.formatNumber
import com.simplexray.re.common.formatUptime
import com.simplexray.re.viewmodel.MainViewModel
import com.simplexray.re.viewmodel.OutboundLatency
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LatencyGood = Color(0xFF4CAF50)
private val LatencyFair = Color(0xFFFF9800)
private const val LatencyStaleSeconds = 60L

@Composable
fun DashboardScreen(
    mainViewModel: MainViewModel,
    onSwitchVpnService: () -> Unit = {},
    paddingValues: PaddingValues = PaddingValues()
) {
    val coreStats by mainViewModel.coreStatsState.collectAsStateWithLifecycle()
    val outboundNodes by mainViewModel.outboundNodes.collectAsStateWithLifecycle()
    val outboundLatency by mainViewModel.outboundLatency.collectAsStateWithLifecycle()
    val isServiceEnabled by mainViewModel.isServiceEnabled.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            mainViewModel.refreshOutboundNodes()
            mainViewModel.testOutboundLatency()
            while (isActive) {
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
                SmallTitle(text = stringResource(id = R.string.traffic_info))
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

            if (outboundNodes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallTitle(
                            text = stringResource(id = R.string.outbound_nodes),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { mainViewModel.refreshLatency() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh_latency),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(outboundNodes, key = { it.tag }) { node ->
                    OutboundNodeCard(
                        node = node,
                        latency = outboundLatency[node.tag],
                        serviceEnabled = isServiceEnabled
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                SmallTitle(text = stringResource(id = R.string.core_runtime_status))
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

@Composable
private fun OutboundNodeCard(
    node: ConfigUtils.OutboundInfo,
    latency: OutboundLatency?,
    serviceEnabled: Boolean
) {
    val nowSeconds = System.currentTimeMillis() / 1000
    val stale = latency != null && nowSeconds - latency.lastTryTime > LatencyStaleSeconds

    val (text, color) = when {
        !serviceEnabled || latency == null || stale || !latency.alive ->
            "-ms" to MiuixTheme.colorScheme.onSurfaceVariantSummary
        latency.delayMs <= 100 ->
            "${latency.delayMs}ms" to LatencyGood
        latency.delayMs <= 300 ->
            "${latency.delayMs}ms" to LatencyFair
        else ->
            "${latency.delayMs}ms" to MiuixTheme.colorScheme.error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.tag,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = node.protocol,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
            Text(
                text = text,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
