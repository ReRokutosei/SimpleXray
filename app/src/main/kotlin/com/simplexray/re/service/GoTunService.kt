package com.simplexray.re.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log

/**
 * Small process-isolated host for a single Go TUN runtime.
 *
 * The VPN fd is owned by TProxyService, while this service owns the native Go
 * backend. Keeping one concrete implementation per process prevents two
 * independent Go runtimes from sharing signal/TLS state.
 */
abstract class GoTunService : Service() {
    private var tunFd: ParcelFileDescriptor? = null
    private var running = false

    protected abstract val tag: String
    protected abstract fun startNative(
        tunFd: Int,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        username: String,
        password: String
    ): Boolean

    protected abstract fun stopNative(): Boolean

    private fun startBackend(
        pfd: ParcelFileDescriptor,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        username: String,
        password: String
    ): Boolean {
        if (running) {
            return true
        }
        tunFd = pfd
        val ok = runCatching {
            startNative(
                tunFd = pfd.fd,
                socksHost = socksHost,
                socksPort = socksPort,
                mtu = mtu,
                username = username,
                password = password
            )
        }.onFailure { Log.e(tag, "Native backend start failed", it) }.getOrDefault(false)
        running = ok
        if (!ok) {
            closeFd()
        }
        return ok
    }

    private fun stopBackend(): Boolean {
        if (running) {
            val result = runCatching { stopNative() }
                .onFailure { Log.w(tag, "Native backend stop failed", it) }
            running = false
            closeFd()
            requestProcessExit()
            return result.getOrDefault(false)
        }
        closeFd()
        requestProcessExit()
        return true
    }

    private fun requestProcessExit() {
        stopSelf()
        // Android may retain a stopped service process as cached. Explicitly
        // terminate this backend-only process so its Go runtime cannot remain
        // resident between benchmark/backend switches.
        Handler(Looper.getMainLooper()).post {
            Process.killProcess(Process.myPid())
        }
    }

    private fun closeFd() {
        runCatching { tunFd?.close() }
        tunFd = null
    }

    override fun onDestroy() {
        if (running) {
            runCatching { stopNative() }
                .onFailure { Log.w(tag, "Native backend stop during destroy failed", it) }
            running = false
        }
        closeFd()
        super.onDestroy()
    }

    private val binder = object : IGoTunBackend.Stub() {
        override fun start(
            tunFd: ParcelFileDescriptor,
            socksHost: String,
            socksPort: Int,
            mtu: Int,
            username: String,
            password: String
        ): Boolean = startBackend(tunFd, socksHost, socksPort, mtu, username, password)

        override fun stop(): Boolean = stopBackend()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        stopBackend()
        return false
    }

    companion object {
    }
}
