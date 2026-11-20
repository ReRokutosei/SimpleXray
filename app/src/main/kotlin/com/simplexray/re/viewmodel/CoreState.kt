package com.simplexray.re.viewmodel

data class TrafficState(
    val uplink: Long,
    val downlink: Long
)

data class CoreStatsState(
    val uplink: Long = 0,
    val downlink: Long = 0,
    val numGoroutine: Int = 0,
    val numGC: Int = 0,
    val alloc: Long = 0,
    val totalAlloc: Long = 0,
    val sys: Long = 0,
    val mallocs: Long = 0,
    val frees: Long = 0,
    val liveObjects: Long = 0,
    val pauseTotalNs: Long = 0,
    val uptime: Int = 0
)

sealed interface LatencyState {
    data object Idle : LatencyState
    data object Testing : LatencyState
    data class Measured(val delayMs: Long, val elapsedMs: Long) : LatencyState
    data class Failed(val elapsedMs: Long) : LatencyState
}

data class OutboundNodeUiItem(
    val tag: String,
    val protocol: String,
    val testable: Boolean,
    val latency: LatencyState = LatencyState.Idle,
)
