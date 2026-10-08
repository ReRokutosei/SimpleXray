package com.simplexray.re.service

import com.simplexray.re.R
import com.simplexray.re.prefs.FakeContext
import com.simplexray.re.prefs.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VpnStartPreferencesValidationTest {

    private lateinit var prefs: Preferences
    private val getString: (Int) -> String = { id ->
        when (id) {
            R.string.socks_address_loopback_only -> "loopback_only"
            R.string.socks_credentials_pair_required -> "pair_required"
            R.string.socks_credentials_no_line_breaks -> "no_line_breaks"
            else -> "error"
        }
    }

    @Before
    fun setUp() {
        prefs = Preferences(FakeContext())
    }

    @Test
    fun `valid default loopback configuration returns null`() {
        assertNull(validateStartPreferences(prefs, getString))
    }

    @Test
    fun `rejects non-loopback socks address`() {
        prefs.socksAddress = "0.0.0.0"
        assertEquals("loopback_only", validateStartPreferences(prefs, getString))

        prefs.socksAddress = "192.168.1.1"
        assertEquals("loopback_only", validateStartPreferences(prefs, getString))

        prefs.socksAddress = ""
        assertEquals("loopback_only", validateStartPreferences(prefs, getString))
    }

    @Test
    fun `allows ipv6 loopback address`() {
        prefs.socksAddress = "::1"
        assertNull(validateStartPreferences(prefs, getString))
    }

    @Test
    fun `requires both username and password or neither`() {
        prefs.socksUsername = "user"
        prefs.socksPassword = ""
        assertEquals("pair_required", validateStartPreferences(prefs, getString))

        prefs.socksUsername = ""
        prefs.socksPassword = "password"
        assertEquals("pair_required", validateStartPreferences(prefs, getString))

        prefs.socksUsername = "user"
        prefs.socksPassword = "password"
        assertNull(validateStartPreferences(prefs, getString))
    }

    @Test
    fun `rejects line breaks in username or password`() {
        prefs.socksUsername = "user\nname"
        prefs.socksPassword = "password"
        assertEquals("no_line_breaks", validateStartPreferences(prefs, getString))

        prefs.socksUsername = "username"
        prefs.socksPassword = "pass\rword"
        assertEquals("no_line_breaks", validateStartPreferences(prefs, getString))
    }
}
