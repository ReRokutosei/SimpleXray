package com.simplexray.re.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.system.measureTimeMillis

class TcpPingTest {

    @Test
    fun testPingBlocking_success() {
        val server = ServerSocket(0)
        try {
            val port = server.localPort
            val delay = TcpPing.pingBlocking("127.0.0.1", port, timeoutMs = 1000)
            assertTrue("Latency should be non-negative on successful connection, got $delay", delay >= 0L)
        } finally {
            server.close()
        }
    }

    @Test
    fun testPingBlocking_closedPort() {
        val server = ServerSocket(0)
        val port = server.localPort
        server.close() // Close immediately to get a free/closed port

        val delay = TcpPing.pingBlocking("127.0.0.1", port, timeoutMs = 300)
        assertEquals(-1L, delay)
    }

    @Test
    fun testPingBlocking_invalidInputs() {
        assertEquals(-1L, TcpPing.pingBlocking("", 80))
        assertEquals(-1L, TcpPing.pingBlocking("   ", 80))
        assertEquals(-1L, TcpPing.pingBlocking("127.0.0.1", 0))
        assertEquals(-1L, TcpPing.pingBlocking("127.0.0.1", -1))
        assertEquals(-1L, TcpPing.pingBlocking("127.0.0.1", 65536))
    }

    @Test
    fun testPingBlocking_unresolvableHost() {
        val elapsed = measureTimeMillis {
            val delay = TcpPing.pingBlocking(
                host = "nonexistent-domain-simplexray-unit-test.invalid",
                port = 80,
                timeoutMs = 500,
                dnsTimeoutMs = 500
            )
            assertEquals(-1L, delay)
        }
        assertTrue("Resolution failure should return within reasonable time, took ${elapsed}ms", elapsed < 3000)
    }

    @Test
    fun testPingSuspend_success() = runBlocking {
        val server = ServerSocket(0)
        try {
            val port = server.localPort
            val delay = TcpPing.ping("127.0.0.1", port, timeoutMs = 1000)
            assertTrue("Latency should be non-negative on successful ping, got $delay", delay >= 0L)
        } finally {
            server.close()
        }
    }

    @Test
    fun testPingSuspend_cancelsPromptly() = runBlocking {
        // 192.0.2.1 is TEST-NET-1 (RFC 5737), typically non-routable / blackholed
        val elapsed = measureTimeMillis {
            val job = launch {
                TcpPing.ping("192.0.2.1", 81, timeoutMs = 5000, dnsTimeoutMs = 5000)
            }
            delay(100)
            job.cancel()
            job.join()
        }
        assertTrue("Cancelled ping should exit promptly, took ${elapsed}ms", elapsed < 1500)
    }
}
