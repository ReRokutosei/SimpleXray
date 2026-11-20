package com.simplexray.re.viewmodel

import android.os.SystemClock
import android.util.Log
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.CoreStatsClient
import com.simplexray.re.common.TcpPing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DashboardController"

internal class DashboardController(
    private val statsEndpoint: () -> Pair<String, Int>,
    private val scope: CoroutineScope,
    private val selectedConfig: StateFlow<File?>,
    private val isServiceEnabled: StateFlow<Boolean>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pingDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(8),
    private val clockElapsedMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val probeEndpoint: suspend (ConfigUtils.OutboundEndpoint) -> Long = { ep ->
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            TcpPing.ping(ep.host, ep.port)
        } ?: -1L
    },
    private val autoRefreshTtlMs: Long = DEFAULT_AUTO_REFRESH_TTL_MS,
    private val statsPollIntervalMs: Long = DEFAULT_STATS_POLL_INTERVAL_MS,
    private val networkChangeDebounceMs: Long = DEFAULT_NETWORK_CHANGE_DEBOUNCE_MS,
    private val pollCoreStats: Boolean = true,
) {
    private data class ConfigSnapshot(
        val path: String,
        val lastModified: Long,
        val length: Long,
        val nodes: List<ConfigUtils.OutboundInfo>,
        val endpointsByTag: Map<String, ConfigUtils.OutboundEndpoint>,
    )

    @Volatile
    private var coreStatsClient: CoreStatsClient? = null
    private val statsClientLock = Any()

    @Volatile
    private var cachedSnapshot: ConfigSnapshot? = null

    @Volatile
    private var lastBatchTestElapsedMs: Long? = null

    private var latencyTestJob: Job? = null
    private var networkRefreshJob: Job? = null
    private val singleNodeJobs = ConcurrentHashMap<String, Job>()

    private val _isDashboardVisible = MutableStateFlow(false)

    private val _coreStatsState = MutableStateFlow(CoreStatsState())
    val coreStatsState: StateFlow<CoreStatsState> = _coreStatsState.asStateFlow()

    private val _outboundNodes = MutableStateFlow<List<OutboundNodeUiItem>>(emptyList())
    val outboundNodes: StateFlow<List<OutboundNodeUiItem>> = _outboundNodes.asStateFlow()

    init {
        scope.launch {
            combine(_isDashboardVisible, isServiceEnabled, selectedConfig) { visible, enabled, file ->
                Triple(visible, enabled, file)
            }.collectLatest { (visible, enabled, _) ->
                if (!visible) return@collectLatest
                val snapshot = ensureSnapshotLoaded()
                if (!enabled) return@collectLatest
                if (snapshot != null && shouldAutoRefreshLatency(snapshot)) {
                    refreshLatency(force = false)
                }
                if (pollCoreStats) {
                    while (isActive) {
                        updateCoreStats()
                        delay(statsPollIntervalMs)
                    }
                }
            }
        }
    }

    fun setDashboardVisible(visible: Boolean) {
        _isDashboardVisible.value = visible
        if (!visible) {
            networkRefreshJob?.cancel()
            networkRefreshJob = null
        }
    }

    fun invalidateLatencyCache() {
        lastBatchTestElapsedMs = null
        if (!_isDashboardVisible.value || !isServiceEnabled.value) return
        networkRefreshJob?.cancel()
        networkRefreshJob = scope.launch {
            delay(networkChangeDebounceMs)
            if (_isDashboardVisible.value && isServiceEnabled.value && lastBatchTestElapsedMs == null) {
                refreshLatency(force = false)
            }
        }
    }

    fun reset() {
        latencyTestJob?.cancel()
        latencyTestJob = null
        networkRefreshJob?.cancel()
        networkRefreshJob = null
        singleNodeJobs.values.forEach { it.cancel() }
        singleNodeJobs.clear()
        lastBatchTestElapsedMs = null
        _coreStatsState.value = CoreStatsState()
        _outboundNodes.update { items ->
            items.map { item ->
                if (item.latency == LatencyState.Idle) item else item.copy(latency = LatencyState.Idle)
            }
        }
        close()
    }

    fun close() {
        synchronized(statsClientLock) {
            coreStatsClient?.close()
            coreStatsClient = null
        }
    }

    suspend fun updateCoreStats() = withContext(ioDispatcher) {
        if (!isServiceEnabled.value) return@withContext

        val client = synchronized(statsClientLock) {
            coreStatsClient ?: run {
                val (address, port) = statsEndpoint()
                CoreStatsClient.create(address, port).also {
                    Log.d(TAG, "=== [DEBUG gRPC] Connecting CoreStatsClient to $address:$port ===")
                    coreStatsClient = it
                }
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

    fun refreshLatency(force: Boolean = true) {
        if (!isServiceEnabled.value) return
        if (latencyTestJob?.isActive == true) return
        latencyTestJob = scope.launch {
            val snapshot = ensureSnapshotLoaded() ?: return@launch
            if (!force && !shouldAutoRefreshLatency(snapshot)) return@launch
            runBatchLatencyTest(snapshot)
        }
    }

    fun refreshNodeLatency(tag: String) {
        if (!isServiceEnabled.value) return
        if (latencyTestJob?.isActive == true || singleNodeJobs[tag]?.isActive == true) return
        var job: Job? = null
        job = scope.launch {
            val snapshot = ensureSnapshotLoaded() ?: return@launch
            val ep = snapshot.endpointsByTag[tag] ?: return@launch
            updateNodeLatency(tag, LatencyState.Testing)
            var result: LatencyState? = null
            try {
                result = withContext(pingDispatcher) {
                    measureEndpoint(ep)
                }
            } finally {
                updateNodeLatency(tag, result ?: LatencyState.Idle)
            }
        }
        singleNodeJobs[tag] = job
        job.invokeOnCompletion {
            singleNodeJobs.remove(tag, job)
        }
    }

    private suspend fun ensureSnapshotLoaded(): ConfigSnapshot? {
        val file = selectedConfig.value
        if (file == null) {
            if (cachedSnapshot != null || _outboundNodes.value.isNotEmpty()) {
                cancelLatencyJobs()
                cachedSnapshot = null
                lastBatchTestElapsedMs = null
                _outboundNodes.value = emptyList()
            }
            return null
        }

        val meta = withContext(ioDispatcher) {
            Triple(file.absolutePath, file.lastModified(), file.length())
        }
        val existing = cachedSnapshot
        if (existing != null &&
            existing.path == meta.first &&
            existing.lastModified == meta.second &&
            existing.length == meta.third
        ) {
            return existing
        }

        val content = withContext(ioDispatcher) {
            runCatching { file.readText() }.getOrNull()
        } ?: return existing

        val nodes = ConfigUtils.extractOutbounds(content)
        val endpointsByTag = ConfigUtils.extractOutboundEndpoints(content).associateBy { it.tag }
        val newSnapshot = ConfigSnapshot(
            path = meta.first,
            lastModified = meta.second,
            length = meta.third,
            nodes = nodes,
            endpointsByTag = endpointsByTag,
        )

        val switchedFile = existing != null && existing.path != newSnapshot.path
        if (switchedFile) {
            cancelLatencyJobs()
            lastBatchTestElapsedMs = null
        }

        val previousByTag = if (switchedFile) {
            emptyMap()
        } else {
            _outboundNodes.value.associateBy { it.tag }
        }
        val previousEndpoints = if (switchedFile) {
            emptyMap()
        } else {
            existing?.endpointsByTag.orEmpty()
        }

        cachedSnapshot = newSnapshot
        _outboundNodes.value = nodes.map { info ->
            val newEp = endpointsByTag[info.tag]
            val prevEp = previousEndpoints[info.tag]
            val preservedLatency = if (newEp != null && newEp == prevEp) {
                previousByTag[info.tag]?.latency ?: LatencyState.Idle
            } else {
                LatencyState.Idle
            }
            OutboundNodeUiItem(
                tag = info.tag,
                protocol = info.protocol,
                testable = newEp != null,
                latency = preservedLatency,
            )
        }
        Log.d(TAG, "Loaded ${nodes.size} outbound nodes (${endpointsByTag.size} TCP-testable) from ${file.name}")
        return newSnapshot
    }

    private fun shouldAutoRefreshLatency(snapshot: ConfigSnapshot): Boolean {
        if (!isServiceEnabled.value) return false
        if (snapshot.endpointsByTag.isEmpty()) return false
        val lastBatch = lastBatchTestElapsedMs ?: return true
        if (clockElapsedMs() - lastBatch >= autoRefreshTtlMs) return true
        return _outboundNodes.value.any { it.testable && it.latency is LatencyState.Idle }
    }

    private suspend fun runBatchLatencyTest(snapshot: ConfigSnapshot) {
        if (!isServiceEnabled.value) return
        val endpoints = snapshot.endpointsByTag.values.toList()
        if (endpoints.isEmpty()) return
        val tags = snapshot.endpointsByTag.keys

        singleNodeJobs.values.forEach { it.cancel() }
        singleNodeJobs.clear()

        _outboundNodes.update { items ->
            items.map { item ->
                if (item.tag in tags) item.copy(latency = LatencyState.Testing) else item
            }
        }

        var completed = false
        try {
            coroutineScope {
                endpoints.forEach { ep ->
                    launch(pingDispatcher) {
                        val state = measureEndpoint(ep)
                        updateNodeLatency(ep.tag, state)
                    }
                }
            }
            completed = true
            lastBatchTestElapsedMs = clockElapsedMs()
            Log.d(TAG, "Outbound latency (TCPing) updated: ${endpoints.size} entries")
        } finally {
            if (!completed) {
                _outboundNodes.update { items ->
                    items.map { item ->
                        if (item.tag in tags && item.latency is LatencyState.Testing) {
                            item.copy(latency = LatencyState.Idle)
                        } else {
                            item
                        }
                    }
                }
            }
        }
    }

    private suspend fun measureEndpoint(ep: ConfigUtils.OutboundEndpoint): LatencyState {
        val delay = probeEndpoint(ep)
        val now = clockElapsedMs()
        Log.d(TAG, "[tcping] tag=${ep.tag} delay=${if (delay >= 0) "${delay}ms" else "failed"}")
        return if (delay >= 0) {
            LatencyState.Measured(delayMs = delay, elapsedMs = now)
        } else {
            LatencyState.Failed(elapsedMs = now)
        }
    }

    private fun updateNodeLatency(tag: String, state: LatencyState) {
        _outboundNodes.update { items ->
            items.map { item ->
                if (item.tag == tag) item.copy(latency = state) else item
            }
        }
    }

    private fun cancelLatencyJobs() {
        latencyTestJob?.cancel()
        latencyTestJob = null
        networkRefreshJob?.cancel()
        networkRefreshJob = null
        singleNodeJobs.values.forEach { it.cancel() }
        singleNodeJobs.clear()
    }

    companion object {
        const val DEFAULT_AUTO_REFRESH_TTL_MS = 60_000L
        const val DEFAULT_STATS_POLL_INTERVAL_MS = 1_000L
        const val DEFAULT_NETWORK_CHANGE_DEBOUNCE_MS = 500L
        private const val PROBE_TIMEOUT_MS = 4_000L
    }
}
