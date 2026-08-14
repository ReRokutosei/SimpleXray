package com.simplexray.re.common

import kotlinx.coroutines.CancellationException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Lightweight TCP-connect latency probe (1 RTT, no dependency on the core).
 * Blocking by design: callers are expected to run it on an IO dispatcher
 * (e.g. via `async(Dispatchers.IO.limitedParallelism(n))`).
 */
object TcpPing {
    fun pingBlocking(host: String, port: Int, timeoutMs: Int = 1500): Long {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            return System.currentTimeMillis() - start
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return -1L
        }
    }
}
