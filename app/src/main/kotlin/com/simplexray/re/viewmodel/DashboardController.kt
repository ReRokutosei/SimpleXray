package com.simplexray.re.viewmodel

import android.util.Log
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.CoreStatsClient
import com.simplexray.re.common.TcpPing
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DashboardController"

internal class DashboardController(
    private val prefs: Preferences,
    private val scope: CoroutineScope,
    private val selectedConfig: StateFlow<File?>,
    private val isVpnEnabled: () -> Boolean,
) {
    @Volatile
    private var coreStatsClient: CoreStatsClient? = null
    private val statsClientLock = Any()
    private var latencyTestJob: Job? = null
    private val singleNodeJobs = ConcurrentHashMap<String, Job>()
    private val pingDispatcher = Dispatchers.IO.limitedParallelism(8)

    private val _coreStatsState = MutableStateFlow(CoreStatsState())
    val coreStatsState: StateFlow<CoreStatsState> = _coreStatsState.asStateFlow()

    private val _outboundNodes = MutableStateFlow<List<ConfigUtils.OutboundInfo>>(emptyList())
    val outboundNodes: StateFlow<List<ConfigUtils.OutboundInfo>> = _outboundNodes.asStateFlow()

    private val _testableOutboundTags = MutableStateFlow<Set<String>>(emptySet())
    val testableOutboundTags: StateFlow<Set<String>> = _testableOutboundTags.asStateFlow()

    private val _outboundLatency = MutableStateFlow<Map<String, OutboundLatency>>(emptyMap())
    val outboundLatency: StateFlow<Map<String, OutboundLatency>> = _outboundLatency.asStateFlow()

    private val _testingTags = MutableStateFlow<Set<String>>(emptySet())
    val testingTags: StateFlow<Set<String>> = _testingTags.asStateFlow()

    fun reset() {
        latencyTestJob?.cancel()
        latencyTestJob = null
        singleNodeJobs.values.forEach { it.cancel() }
        singleNodeJobs.clear()
        _coreStatsState.value = CoreStatsState()
        _outboundLatency.value = emptyMap()
        _testingTags.value = emptySet()
        close()
    }

    fun close() {
        synchronized(statsClientLock) {
            coreStatsClient?.close()
            coreStatsClient = null
        }
    }

    suspend fun updateCoreStats() = withContext(Dispatchers.IO) {
        if (!isVpnEnabled()) return@withContext

        val client = synchronized(statsClientLock) {
            coreStatsClient ?: CoreStatsClient.create(prefs.apiAddress, prefs.apiPort).also {
                Log.d(TAG, "=== [DEBUG gRPC] Connecting CoreStatsClient to ${prefs.apiAddress}:${prefs.apiPort} ===")
                coreStatsClient = it
            }
        }

        // reset()/close() may close the client concurrently; tolerate the
        // resulting errors instead of crashing or leaking the channel.
        val stats = runCatching { client.getSystemStats() }.getOrNull()
        val traffic = runCatching { client.getTraffic() }.getOrNull()
        Log.d(TAG, "=== [DEBUG gRPC RESULT] uplink=${traffic?.uplink}, downlink=${traffic?.downlink}, sys=${stats?.sys} ===")

        if (stats == null && traffic == null) {
            Log.w(TAG, "=== [DEBUG gRPC FAILED] Both stats & traffic returned null, resetting client ===")
            synchronized(statsClientLock) {
                if (coreStatsClient === client) {
                    runCatching { client.close() }
                    coreStatsClient = null
                }
            }
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
        val testableTags = ConfigUtils.extractOutboundEndpoints(content).map { it.tag }.toSet()
        _outboundNodes.value = nodes
        _testableOutboundTags.value = testableTags
        _outboundLatency.update { current -> current.filterKeys { it in testableTags } }
        Log.d(TAG, "Refreshed ${nodes.size} outbound nodes (${testableTags.size} TCP-testable) from ${file.name}")
    }

    suspend fun testOutboundLatency() {
        if (!isVpnEnabled()) return
        val file = selectedConfig.value ?: return
        val content = withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrNull()
        } ?: return
        val endpoints = ConfigUtils.extractOutboundEndpoints(content)
        val tags = endpoints.map { it.tag }.toSet()
        _testableOutboundTags.value = tags
        if (endpoints.isEmpty()) {
            _outboundLatency.value = emptyMap()
            return
        }
        singleNodeJobs.values.forEach { it.cancel() }
        singleNodeJobs.clear()
        _outboundLatency.update { current -> current.filterKeys { it in tags } }
        _testingTags.update { it + tags }
        try {
            coroutineScope {
                endpoints.forEach { ep ->
                    launch(pingDispatcher) {
                        try {
                            probeEndpoint(ep)
                        } finally {
                            _testingTags.update { it - ep.tag }
                        }
                    }
                }
            }
            Log.d(TAG, "Outbound latency (TCPing) updated: ${endpoints.size} entries")
        } finally {
            _testingTags.update { it - tags }
        }
    }

    private suspend fun probeEndpoint(ep: ConfigUtils.OutboundEndpoint) {
        val delay = withTimeoutOrNull(4000L) {
            TcpPing.pingBlocking(ep.host, ep.port)
        } ?: -1L
        val now = System.currentTimeMillis() / 1000
        Log.d(TAG, "[tcping] tag=${ep.tag} delay=${if (delay >= 0) "${delay}ms" else "failed"}")
        _outboundLatency.update { current ->
            current + (ep.tag to OutboundLatency(
                alive = delay >= 0,
                delayMs = delay.coerceAtLeast(0),
                lastTryTime = now
            ))
        }
    }

    fun refreshLatency() {
        if (!isVpnEnabled()) return
        if (latencyTestJob?.isActive == true) return
        latencyTestJob = scope.launch { testOutboundLatency() }
    }

    fun refreshNodeLatency(tag: String) {
        if (!isVpnEnabled()) return
        if (latencyTestJob?.isActive == true || singleNodeJobs[tag]?.isActive == true) return
        var job: Job? = null
        job = scope.launch {
            val file = selectedConfig.value ?: return@launch
            val content = withContext(Dispatchers.IO) {
                runCatching { file.readText() }.getOrNull()
            } ?: return@launch
            val ep = ConfigUtils.extractOutboundEndpoints(content).firstOrNull { it.tag == tag }
                ?: return@launch
            _testingTags.update { it + tag }
            try {
                withContext(pingDispatcher) {
                    probeEndpoint(ep)
                }
            } finally {
                _testingTags.update { it - tag }
            }
        }
        singleNodeJobs[tag] = job
        job.invokeOnCompletion {
            singleNodeJobs.remove(tag, job)
        }
    }
}
