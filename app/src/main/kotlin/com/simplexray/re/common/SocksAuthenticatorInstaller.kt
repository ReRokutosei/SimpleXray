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
            val isMatchingHost = requestingHost.isNullOrEmpty() ||
                requestingHost.equals(prefs.socksAddress, ignoreCase = true) ||
                requestingHost == "127.0.0.1" || requestingHost == "localhost"
            val isMatchingPort = requestingPort == -1 || requestingPort == prefs.socksPort

            return if (isProxy || (isMatchingHost && isMatchingPort)) {
                PasswordAuthentication(user, pass.toCharArray())
            } else {
                null
            }
        }
    }
}
