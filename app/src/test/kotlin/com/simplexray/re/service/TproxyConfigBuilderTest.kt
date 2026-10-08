package com.simplexray.re.service

import com.simplexray.re.prefs.FakeContext
import com.simplexray.re.prefs.Preferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TproxyConfigBuilderTest {

    @Test
    fun escapesSingleQuotesInCredentials() {
        val prefs = Preferences(FakeContext())
        prefs.socksUsername = "user'one"
        prefs.socksPassword = "pa'ss"

        val config = TproxyConfigBuilder.build(prefs)

        assertTrue(config.contains("username: 'user''one'"))
        assertTrue(config.contains("password: 'pa''ss'"))
        assertFalse(config.contains("password: 'pa'ss'"))
    }

    @Test
    fun generatesAuthenticationFieldsWhenCredentialsPresent() {
        val prefs = Preferences(FakeContext())
        prefs.socksUsername = "alice"
        prefs.socksPassword = "secret"
        prefs.tunnelMtu = 1420
        prefs.socksPort = 20808

        val config = TproxyConfigBuilder.build(prefs)

        assertTrue(config.contains("mtu: 1420"))
        assertTrue(config.contains("port: 20808"))
        assertTrue(config.contains("address: '127.0.0.1'"))
        assertTrue(config.contains("username: 'alice'"))
        assertTrue(config.contains("password: 'secret'"))
    }

    @Test
    fun omitsAuthenticationFieldsWhenCredentialsEmpty() {
        val prefs = Preferences(FakeContext())
        prefs.socksUsername = ""
        prefs.socksPassword = ""

        val config = TproxyConfigBuilder.build(prefs)

        assertFalse(config.contains("username:"))
        assertFalse(config.contains("password:"))
    }
}
