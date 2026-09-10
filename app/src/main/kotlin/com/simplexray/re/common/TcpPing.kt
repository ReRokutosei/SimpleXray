package com.simplexray.re.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Lightweight TCP-connect latency probe (1 RTT, no dependency on the core).
 * Supports bounded DNS resolution timeout to prevent hanging on unresponsive DNS servers.
 */
object TcpPing {
    private val dnsExecutor by lazy {
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "tcping-dns").apply { isDaemon = true }
        }
    }

    fun pingBlocking(
        host: String,
        port: Int,
        timeoutMs: Int = 1500,
        dnsTimeoutMs: Long = 2000L
    ): Long {
        if (host.isBlank() || port !in 1..65535) return -1L
        val start = System.currentTimeMillis()
        try {
            val addressFuture = dnsExecutor.submit(Callable { InetAddress.getByName(host) })
            val inetAddress = try {
                addressFuture.get(dnsTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                addressFuture.cancel(true)
                return -1L
            } catch (e: Exception) {
                return -1L
            }

            Socket().use { socket ->
                socket.connect(InetSocketAddress(inetAddress, port), timeoutMs)
            }
            return System.currentTimeMillis() - start
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return -1L
        }
    }

    suspend fun ping(
        host: String,
        port: Int,
        timeoutMs: Int = 1500,
        dnsTimeoutMs: Long = 2000L
    ): Long = withContext(Dispatchers.IO) {
        pingBlocking(host, port, timeoutMs, dnsTimeoutMs)
    }
}
