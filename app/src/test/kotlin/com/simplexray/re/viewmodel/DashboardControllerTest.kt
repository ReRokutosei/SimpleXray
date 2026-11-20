package com.simplexray.re.viewmodel

import com.simplexray.re.common.ConfigUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class DashboardControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writeConfig(name: String, host: String = "1.1.1.1", port: Int = 443): File {
        val file = tempFolder.newFile(name)
        file.writeText(
            """
            {
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "$host",
                        "port": $port,
                        "users": [{ "id": "00000000-0000-0000-0000-000000000000" }]
                      }
                    ]
                  }
                },
                {
                  "tag": "direct",
                  "protocol": "freedom"
                }
              ]
            }
            """.trimIndent()
        )
        return file
    }

    private suspend fun awaitCondition(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                delay(10)
            }
        }
    }

    @Test
    fun autoRefreshRespectsTtlAcrossVisibilityToggles() = runBlocking {
        val config = writeConfig("a.json")
        val selectedConfig = MutableStateFlow<File?>(config)
        val serviceEnabled = MutableStateFlow(true)
        val clock = AtomicLong(1_000L)
        val probes = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val controller = DashboardController(
                statsEndpoint = { "127.0.0.1" to 10085 },
                scope = scope,
                selectedConfig = selectedConfig,
                isServiceEnabled = serviceEnabled,
                ioDispatcher = Dispatchers.IO,
                pingDispatcher = Dispatchers.IO,
                clockElapsedMs = { clock.get() },
                probeEndpoint = {
                    probes.incrementAndGet()
                    42L
                },
                autoRefreshTtlMs = 60_000L,
                pollCoreStats = false,
            )

            // Hidden initially -> no probe
            delay(50)
            assertEquals(0, probes.get())

            // Become visible -> triggers initial probe
            controller.setDashboardVisible(true)
            awaitCondition { probes.get() == 1 }
            awaitCondition {
                controller.outboundNodes.value.firstOrNull { it.tag == "proxy" }?.latency is LatencyState.Measured
            }
            val measured = controller.outboundNodes.value.first { it.tag == "proxy" }.latency as LatencyState.Measured
            assertEquals(42L, measured.delayMs)

            // Rapid tab switching within TTL (30s later) -> must NOT re-probe
            clock.addAndGet(30_000L)
            controller.setDashboardVisible(false)
            delay(20)
            controller.setDashboardVisible(true)
            delay(80)
            assertEquals(1, probes.get())

            // Staying visible past TTL -> must NOT auto-poll
            clock.addAndGet(40_000L) // total 70s elapsed
            delay(80)
            assertEquals(1, probes.get())
            assertTrue(controller.outboundNodes.value.first { it.tag == "proxy" }.latency is LatencyState.Measured)

            // Returning to dashboard past TTL -> triggers auto-refresh
            controller.setDashboardVisible(false)
            delay(20)
            controller.setDashboardVisible(true)
            awaitCondition { probes.get() == 2 }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun manualRefreshBypassesTtlAndSingleNodeUpdatesOnlyTarget() = runBlocking {
        val config = writeConfig("b.json")
        val selectedConfig = MutableStateFlow<File?>(config)
        val serviceEnabled = MutableStateFlow(true)
        val clock = AtomicLong(1_000L)
        val probes = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val controller = DashboardController(
                statsEndpoint = { "127.0.0.1" to 10085 },
                scope = scope,
                selectedConfig = selectedConfig,
                isServiceEnabled = serviceEnabled,
                ioDispatcher = Dispatchers.IO,
                pingDispatcher = Dispatchers.IO,
                clockElapsedMs = { clock.get() },
                probeEndpoint = {
                    val count = probes.incrementAndGet()
                    count * 10L
                },
                autoRefreshTtlMs = 60_000L,
                pollCoreStats = false,
            )

            controller.setDashboardVisible(true)
            awaitCondition { probes.get() == 1 }

            // Manual full refresh immediately (within TTL) -> forces probe
            controller.refreshLatency(force = true)
            awaitCondition { probes.get() == 2 }
            awaitCondition {
                (controller.outboundNodes.value.first { it.tag == "proxy" }.latency as? LatencyState.Measured)?.delayMs == 20L
            }

            // Single node refresh -> forces probe for that node
            controller.refreshNodeLatency("proxy")
            awaitCondition { probes.get() == 3 }
            awaitCondition {
                (controller.outboundNodes.value.first { it.tag == "proxy" }.latency as? LatencyState.Measured)?.delayMs == 30L
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun networkInvalidationDefersWhenHiddenAndRefreshesWhenVisible() = runBlocking {
        val config = writeConfig("c.json")
        val selectedConfig = MutableStateFlow<File?>(config)
        val serviceEnabled = MutableStateFlow(true)
        val clock = AtomicLong(1_000L)
        val probes = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val controller = DashboardController(
                statsEndpoint = { "127.0.0.1" to 10085 },
                scope = scope,
                selectedConfig = selectedConfig,
                isServiceEnabled = serviceEnabled,
                ioDispatcher = Dispatchers.IO,
                pingDispatcher = Dispatchers.IO,
                clockElapsedMs = { clock.get() },
                probeEndpoint = {
                    probes.incrementAndGet()
                    25L
                },
                autoRefreshTtlMs = 60_000L,
                networkChangeDebounceMs = 30L,
                pollCoreStats = false,
            )

            controller.setDashboardVisible(true)
            awaitCondition { probes.get() == 1 }

            // Hidden when network switches -> no background probe
            controller.setDashboardVisible(false)
            delay(20)
            controller.invalidateLatencyCache()
            delay(100)
            assertEquals(1, probes.get())

            // Returning to dashboard -> immediately refreshes because cache was invalidated
            controller.setDashboardVisible(true)
            awaitCondition { probes.get() == 2 }

            // Visible when network switches -> debounced refresh triggers automatically
            controller.invalidateLatencyCache()
            awaitCondition { probes.get() == 3 }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun resetClearsLatencyAndSwitchingConfigTriggersRefresh() = runBlocking {
        val configA = writeConfig("d1.json", host = "1.1.1.1")
        val configB = writeConfig("d2.json", host = "8.8.8.8")
        val selectedConfig = MutableStateFlow<File?>(configA)
        val serviceEnabled = MutableStateFlow(true)
        val clock = AtomicLong(1_000L)
        val probedHosts = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val controller = DashboardController(
                statsEndpoint = { "127.0.0.1" to 10085 },
                scope = scope,
                selectedConfig = selectedConfig,
                isServiceEnabled = serviceEnabled,
                ioDispatcher = Dispatchers.IO,
                pingDispatcher = Dispatchers.IO,
                clockElapsedMs = { clock.get() },
                probeEndpoint = { ep: ConfigUtils.OutboundEndpoint ->
                    synchronized(probedHosts) { probedHosts.add(ep.host) }
                    15L
                },
                autoRefreshTtlMs = 60_000L,
                pollCoreStats = false,
            )

            controller.setDashboardVisible(true)
            awaitCondition { synchronized(probedHosts) { probedHosts.size == 1 } }
            assertEquals("1.1.1.1", synchronized(probedHosts) { probedHosts[0] })

            // Switch config while visible -> resets & probes new config
            selectedConfig.value = configB
            awaitCondition { synchronized(probedHosts) { probedHosts.size == 2 } }
            assertEquals("8.8.8.8", synchronized(probedHosts) { probedHosts[1] })

            // Stop service + reset -> latency returns to Idle, nodes remain listed
            serviceEnabled.value = false
            controller.reset()
            val proxy = controller.outboundNodes.value.first { it.tag == "proxy" }
            assertEquals(LatencyState.Idle, proxy.latency)

            // Restart service -> probes again automatically
            serviceEnabled.value = true
            awaitCondition { synchronized(probedHosts) { probedHosts.size == 3 } }
        } finally {
            scope.cancel()
        }
    }
}
