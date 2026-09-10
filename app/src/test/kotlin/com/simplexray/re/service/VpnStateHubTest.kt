package com.simplexray.re.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VpnStateHubTest {

    @Before
    fun setUp() {
        VpnStateHub.updateState(VpnRunningState.Disconnected)
    }

    @Test
    fun testInitialState() {
        assertEquals(VpnRunningState.Disconnected, VpnStateHub.state.value)
    }

    @Test
    fun testStateTransitions() {
        VpnStateHub.updateState(VpnRunningState.Connecting)
        assertEquals(VpnRunningState.Connecting, VpnStateHub.state.value)

        VpnStateHub.updateState(VpnRunningState.Connected)
        assertEquals(VpnRunningState.Connected, VpnStateHub.state.value)

        val failReason = "Startup probe failed"
        VpnStateHub.updateState(VpnRunningState.Failed(failReason))
        val failedState = VpnStateHub.state.value
        assertTrue(failedState is VpnRunningState.Failed)
        assertEquals(failReason, (failedState as VpnRunningState.Failed).message)

        VpnStateHub.updateState(VpnRunningState.Disconnected)
        assertEquals(VpnRunningState.Disconnected, VpnStateHub.state.value)
    }

    @Test
    fun testEmitLogDeliversToSubscriber() = runBlocking {
        val collectedLogs = mutableListOf<String>()
        val job: Job = launch {
            VpnStateHub.logFlow.collect { line ->
                collectedLogs.add(line)
            }
        }

        // Give the collector a moment to subscribe
        delay(20)

        VpnStateHub.emitLog("line 1")
        VpnStateHub.emitLog("line 2")
        VpnStateHub.emitLog("line 3")

        delay(50)
        job.cancel()

        assertEquals(listOf("line 1", "line 2", "line 3"), collectedLogs)
    }

    @Test
    fun testEmitLogNeverBlocksEvenWithoutSubscribers() {
        // Emit 600 lines (more than extraBufferCapacity of 500)
        for (i in 1..600) {
            VpnStateHub.emitLog("bulk line $i")
        }
        assertTrue(true)
    }
}
