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
}
