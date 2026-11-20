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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.common.formatBytes
import com.simplexray.re.common.formatNumber
import com.simplexray.re.common.formatUptime
import com.simplexray.re.viewmodel.LatencyState
import com.simplexray.re.viewmodel.MainViewModel
import com.simplexray.re.viewmodel.OutboundNodeUiItem
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Pause
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.icon.glass.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun DashboardScreen(
    mainViewModel: MainViewModel,
    onSwitchVpnService: () -> Unit = {},
    paddingValues: PaddingValues = PaddingValues()
) {
    val coreStats by mainViewModel.coreStatsState.collectAsStateWithLifecycle()
    val outboundNodes by mainViewModel.outboundNodes.collectAsStateWithLifecycle()
    val isServiceEnabled by mainViewModel.isServiceEnabled.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mainViewModel.setDashboardVisible(true)
                Lifecycle.Event.ON_PAUSE -> mainViewModel.setDashboardVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mainViewModel.setDashboardVisible(false)
        }
    }

    val isWideScreen = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width >= 600.dp.roundToPx()
    }
    val topPadding = paddingValues.calculateTopPadding() + 12.dp
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPadding, bottom = bottomPadding)
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
                            imageVector = if (isServiceEnabled) MiuixIcons.Glass.Pause else MiuixIcons.Glass.Play,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp).size(24.dp)
                        )
                        Text(
                            text = if (isServiceEnabled) stringResource(R.string.stop_core) else stringResource(R.string.app_name),
                            fontSize = MiuixTheme.textStyles.body1.fontSize
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
                        val canRefreshAll = isServiceEnabled &&
                            outboundNodes.any { it.testable } &&
                            outboundNodes.none { it.latency is LatencyState.Testing }
                        IconButton(
                            onClick = { mainViewModel.refreshLatency() },
                            enabled = canRefreshAll,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Glass.Refresh,
                                contentDescription = stringResource(R.string.refresh_latency),
                                modifier = Modifier.size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(
                                    alpha = if (canRefreshAll) 1f else 0.38f
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(outboundNodes, key = { it.tag }) { node ->
                    OutboundNodeCard(
                        node = node,
                        serviceEnabled = isServiceEnabled,
                        onClick = { mainViewModel.refreshNodeLatency(node.tag) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
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
    node: OutboundNodeUiItem,
    serviceEnabled: Boolean,
    onClick: () -> Unit,
) {
    val goodColor = MiuixTheme.colorScheme.primary
    val fairColor = lerp(goodColor, MiuixTheme.colorScheme.error, 0.5f)
    val mutedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val errorColor = MiuixTheme.colorScheme.error
    val timeoutText = stringResource(R.string.latency_timeout)

    val state = node.latency
    val isTesting = serviceEnabled && state is LatencyState.Testing
    val (text, color) = when {
        !serviceEnabled || state is LatencyState.Idle -> "--" to mutedColor
        state is LatencyState.Failed -> timeoutText to errorColor
        state is LatencyState.Measured -> when {
            state.delayMs <= 100 -> "${state.delayMs}ms" to goodColor
            state.delayMs <= 300 -> "${state.delayMs}ms" to fairColor
            else -> "${state.delayMs}ms" to errorColor
        }
        else -> "--" to mutedColor
    }

    val clickable = serviceEnabled && node.testable && !isTesting
    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = if (clickable) PressFeedbackType.Sink else PressFeedbackType.None,
        showIndication = clickable,
        onClick = if (clickable) onClick else null,
    ) {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = node.protocol,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isTesting) {
                InfiniteProgressIndicator(
                    color = mutedColor,
                    size = 18.dp,
                    strokeWidth = 1.8.dp,
                    orbitingDotSize = 2.dp,
                )
            } else {
                Text(
                    text = text,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = color,
                    fontFamily = if (state is LatencyState.Failed && serviceEnabled) {
                        FontFamily.Default
                    } else {
                        FontFamily.Monospace
                    }
                )
            }
        }
    }
}
