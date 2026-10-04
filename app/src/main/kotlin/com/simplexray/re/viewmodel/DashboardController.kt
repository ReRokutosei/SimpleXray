package com.simplexray.re.viewmodel

import android.util.Log
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.CoreStatsClient
import com.simplexray.re.common.TcpPing
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val TAG = "DashboardController"

internal class DashboardController(
    private val prefs: Preferences,
    private val scope: CoroutineScope,
    private val selectedConfig: StateFlow<File?>,
    private val isVpnEnabled: () -> Boolean,
) {
    private var coreStatsClient: CoreStatsClient? = null
    private var latencyTestJob: Job? = null

    private val _coreStatsState = MutableStateFlow(CoreStatsState())
    val coreStatsState: StateFlow<CoreStatsState> = _coreStatsState.asStateFlow()

    private val _outboundNodes = MutableStateFlow<List<ConfigUtils.OutboundInfo>>(emptyList())
    val outboundNodes: StateFlow<List<ConfigUtils.OutboundInfo>> = _outboundNodes.asStateFlow()

    private val _outboundLatency = MutableStateFlow<Map<String, OutboundLatency>>(emptyMap())
    val outboundLatency: StateFlow<Map<String, OutboundLatency>> = _outboundLatency.asStateFlow()

    fun reset() {
        _coreStatsState.value = CoreStatsState()
        coreStatsClient?.close()
        coreStatsClient = null
    }

    fun close() {
        coreStatsClient?.close()
        coreStatsClient = null
    }

    suspend fun updateCoreStats() = withContext(Dispatchers.IO) {
        if (!isVpnEnabled()) return@withContext
        if (coreStatsClient == null) {
            Log.d(TAG, "=== [DEBUG gRPC] Connecting CoreStatsClient to ${prefs.apiAddress}:${prefs.apiPort} ===")
            coreStatsClient = CoreStatsClient.create(prefs.apiAddress, prefs.apiPort)
        }

        val stats = coreStatsClient?.getSystemStats()
        val traffic = coreStatsClient?.getTraffic()
        Log.d(TAG, "=== [DEBUG gRPC RESULT] uplink=${traffic?.uplink}, downlink=${traffic?.downlink}, sys=${stats?.sys} ===")

        if (stats == null && traffic == null) {
            Log.w(TAG, "=== [DEBUG gRPC FAILED] Both stats & traffic returned null, resetting client ===")
            coreStatsClient?.close()
            coreStatsClient = null
            return@withContext
        }

        _coreStatsState.value = CoreStatsState(
            uplink = traffic?.uplink ?: 0,
            downlink = traffic?.downlink ?: 0,
            numGoroutine = stats?.numGoroutine ?: 0,
            numGC = stats?.numGC ?: 0,
            alloc = stats?.alloc ?: 0,
            totalAlloc = stats?.totalAlloc ?: 0,
            sys = stats?.sys ?: 0,
            mallocs = stats?.mallocs ?: 0,
            frees = stats?.frees ?: 0,
            liveObjects = stats?.liveObjects ?: 0,
            pauseTotalNs = stats?.pauseTotalNs ?: 0,
            uptime = stats?.uptime ?: 0
        )
        Log.d(TAG, "Core stats updated")
    }

    suspend fun refreshOutboundNodes() {
        val file = selectedConfig.value ?: return
        val content = withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrNull()
        } ?: return
        val nodes = ConfigUtils.extractOutbounds(content)
        _outboundNodes.value = nodes
        Log.d(TAG, "Refreshed ${nodes.size} outbound nodes from ${file.name}")
    }

    suspend fun testOutboundLatency() {
        val file = selectedConfig.value ?: return
        val content = withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrNull()
        } ?: return
        val endpoints = ConfigUtils.extractOutboundEndpoints(content)
        if (endpoints.isEmpty()) {
            _outboundLatency.value = emptyMap()
            return
        }
        val now = System.currentTimeMillis() / 1000
        val io = Dispatchers.IO.limitedParallelism(8)
        val results = coroutineScope {
            endpoints.map { ep ->
                async(io) {
                    val delay = withTimeoutOrNull(4000L) {
                        TcpPing.pingBlocking(ep.host, ep.port)
                    } ?: -1L
                    ep.tag to delay
                }
            }.awaitAll()
        }
        _outboundLatency.value = results.associate { (tag, delay) ->
            Log.d(TAG, "[tcping] tag=$tag delay=${if (delay >= 0) "${delay}ms" else "failed"}")
            tag to OutboundLatency(
                alive = delay >= 0,
                delayMs = delay.coerceAtLeast(0),
                lastTryTime = now
            )
        }
        Log.d(TAG, "Outbound latency (TCPing) updated: ${results.size} entries")
    }

    fun refreshLatency() {
        if (latencyTestJob?.isActive == true) return
        latencyTestJob = scope.launch { testOutboundLatency() }
    }
}
