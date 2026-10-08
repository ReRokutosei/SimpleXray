package com.simplexray.re.service

import com.simplexray.re.prefs.FakeContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class SocksHealthMonitorTest {

    private class TestTarget : SocksHealthMonitor.Target {
        var pid = 1234
        var ready = true
        var stopping = false
        var failed = false
        var restartCount = 0

        override fun xrayPid(): Int = pid
        override fun isXrayReady(): Boolean = ready
        override fun isStopping(): Boolean = stopping
        override fun hasStartupFailed(): Boolean = failed
        override fun restartXray() {
            restartCount++
        }
    }

    @Test
    fun `restarts core on 3 consecutive probe failures`() {
        val target = TestTarget()
        val monitor = SocksHealthMonitor(
            context = FakeContext(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            target = target,
            tag = "Test",
            shouldPauseProbe = { false },
            probeSocket = { _, _ -> false },
            nowElapsedRealtimeMs = { 0L },
            postRecovery = { it() },
        )

        // 1st failure
        monitor.tickOnce()
        assertEquals(0, target.restartCount)

        // 2nd failure
        monitor.tickOnce()
        assertEquals(0, target.restartCount)

        // 3rd failure -> triggers recovery
        monitor.tickOnce()
        assertEquals(1, target.restartCount)
    }

    @Test
    fun `successful probe resets consecutive failure counter`() {
        val target = TestTarget()
        var successful = false
        val monitor = SocksHealthMonitor(
            context = FakeContext(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            target = target,
            tag = "Test",
            shouldPauseProbe = { false },
            probeSocket = { _, _ -> successful },
            nowElapsedRealtimeMs = { 0L },
            postRecovery = { it() },
        )

        // 2 failures
        monitor.tickOnce()
        monitor.tickOnce()
        assertEquals(0, target.restartCount)

        // 1 success resets failures to 0
        successful = true
        monitor.tickOnce()
        assertEquals(0, target.restartCount)

        // Fail again: needs 3 new failures before restarting
        successful = false
        monitor.tickOnce()
        monitor.tickOnce()
        assertEquals(0, target.restartCount)

        monitor.tickOnce() // 3rd failure
        assertEquals(1, target.restartCount)
    }

    @Test
    fun `recovery respects cooldown period`() {
        val target = TestTarget()
        var now = 10_000L
        val monitor = SocksHealthMonitor(
            context = FakeContext(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            target = target,
            tag = "Test",
            shouldPauseProbe = { false },
            probeSocket = { _, _ -> false },
            nowElapsedRealtimeMs = { now },
            postRecovery = { it() },
        )

        // 1st recovery at 10s
        monitor.tickOnce()
        monitor.tickOnce()
        monitor.tickOnce()
        assertEquals(1, target.restartCount)

        // 3 more failures at 25s (cooldown 30s has not elapsed: 25000 - 10000 = 15000 < 30000)
        now = 25_000L
        monitor.tickOnce()
        monitor.tickOnce()
        monitor.tickOnce()
        assertEquals(1, target.restartCount)

        // 3 more failures at 45s (45000 - 10000 = 35000 >= 30000 cooldown satisfied)
        now = 45_000L
        monitor.tickOnce()
        monitor.tickOnce()
        monitor.tickOnce()
        assertEquals(2, target.restartCount)
    }

    @Test
    fun `paused probe while sleeping resets failure counter without probing`() {
        val target = TestTarget()
        var probesRun = 0
        var sleeping = false
        val monitor = SocksHealthMonitor(
            context = FakeContext(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            target = target,
            tag = "Test",
            shouldPauseProbe = { sleeping },
            probeSocket = { _, _ ->
                probesRun++
                false
            },
            nowElapsedRealtimeMs = { 0L },
            postRecovery = { it() },
        )

        // 2 failures while active
        monitor.tickOnce()
        monitor.tickOnce()
        assertEquals(2, probesRun)
        assertEquals(0, target.restartCount)

        // Enter sleep without wake lock -> pauses probes and resets failures
        sleeping = true
        monitor.tickOnce()
        monitor.tickOnce()
        assertEquals(2, probesRun) // no additional probes during sleep
        assertEquals(0, target.restartCount)

        // Wake up -> should require 3 fresh failures to trigger recovery
        sleeping = false
        monitor.tickOnce()
        monitor.tickOnce()
        assertEquals(4, probesRun)
        assertEquals(0, target.restartCount)

        monitor.tickOnce() // 3rd failure
        assertEquals(5, probesRun)
        assertEquals(1, target.restartCount)
    }

    @Test
    fun `skips probing when target is not ready or stopping`() {
        val target = TestTarget()
        var probesRun = 0
        val monitor = SocksHealthMonitor(
            context = FakeContext(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            target = target,
            tag = "Test",
            shouldPauseProbe = { false },
            probeSocket = { _, _ ->
                probesRun++
                false
            },
            nowElapsedRealtimeMs = { 0L },
            postRecovery = { it() },
        )

        target.ready = false
        monitor.tickOnce()
        assertEquals(0, probesRun)

        target.ready = true
        target.stopping = true
        monitor.tickOnce()
        assertEquals(0, probesRun)

        target.stopping = false
        target.failed = true
        monitor.tickOnce()
        assertEquals(0, probesRun)
    }
}
