package com.simplexray.re.common

import android.content.Context
import com.simplexray.re.prefs.Preferences
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs the process-wide [Authenticator] used when Java networking code needs
 * to authenticate against the local SOCKS inbound.
 *
 * The JVM authenticator is global, so installation is guarded by an atomic flag.
 */
object SocksAuthenticatorInstaller {
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (installed.compareAndSet(false, true)) {
            Authenticator.setDefault(AppSocksAuthenticator(context.applicationContext))
        }
    }

    private class AppSocksAuthenticator(private val appContext: Context) : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            val prefs = Preferences(appContext)
            val user = prefs.socksUsername
            val pass = prefs.socksPassword

            if (user.isEmpty() && pass.isEmpty()) {
                return null
            }

            val isProxy = requestorType == RequestorType.PROXY
            val host = requestingHost?.removePrefix("[")?.removeSuffix("]")
            val isMatchingHost = host != null && (
                host == "127.0.0.1" ||
                    host.equals("localhost", ignoreCase = true) ||
                    host.equals(prefs.socksAddress, ignoreCase = true)
                )
            val isMatchingPort = requestingPort == prefs.socksPort

            // Only answer for our own local SOCKS listener. The previous
            // `isProxy || ...` form leaked the saved credentials to any upstream
            // proxy that happened to request authentication.
            return if (isProxy && isMatchingHost && isMatchingPort) {
                PasswordAuthentication(user, pass.toCharArray())
            } else {
                null
            }
        }
    }
}
