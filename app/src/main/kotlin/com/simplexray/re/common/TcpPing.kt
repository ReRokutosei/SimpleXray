package com.simplexray.re.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.coroutineContext

/**
 * Lightweight TCP-connect latency probe (1 RTT, no dependency on the core).
 * Supports bounded DNS resolution timeout and prompt coroutine cancellation.
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
        try {
            val addressFuture = dnsExecutor.submit(Callable { InetAddress.getByName(host) })
            val inetAddress = try {
                addressFuture.get(dnsTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                addressFuture.cancel(true)
                return -1L
            } catch (_: Exception) {
                return -1L
            }

            val startNs = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(inetAddress, port), timeoutMs)
            }
            return (System.nanoTime() - startNs) / 1_000_000L
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return -1L
        }
    }

    suspend fun ping(
        host: String,
        port: Int,
        timeoutMs: Int = 1500,
        dnsTimeoutMs: Long = 2000L
    ): Long {
        if (host.isBlank() || port !in 1..65535) return -1L
        return withContext(Dispatchers.IO) {
            val inetAddress = resolveAddressCancellable(host, dnsTimeoutMs) ?: return@withContext -1L
            connectTimedCancellable(inetAddress, port, timeoutMs)
        }
    }

    private suspend fun resolveAddressCancellable(host: String, dnsTimeoutMs: Long): InetAddress? {
        val future = dnsExecutor.submit(Callable { InetAddress.getByName(host) })
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                future.cancel(true)
            }
        }
        return try {
            future.get(dnsTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: CancellationException) {
            future.cancel(true)
            throw e
        } catch (_: InterruptedException) {
            future.cancel(true)
            if (!coroutineContext.isActive) throw CancellationException()
            null
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (_: Exception) {
            if (!coroutineContext.isActive) throw CancellationException()
            null
        } finally {
            cancelHandle?.dispose()
        }
    }

    private suspend fun connectTimedCancellable(
        inetAddress: InetAddress,
        port: Int,
        timeoutMs: Int,
    ): Long {
        val socket = Socket()
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                runCatching { socket.close() }
            }
        }
        return try {
            val startNs = System.nanoTime()
            socket.use { s ->
                s.connect(InetSocketAddress(inetAddress, port), timeoutMs)
            }
            (System.nanoTime() - startNs) / 1_000_000L
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!coroutineContext.isActive) throw CancellationException()
            -1L
        } finally {
            cancelHandle?.dispose()
        }
    }
}
