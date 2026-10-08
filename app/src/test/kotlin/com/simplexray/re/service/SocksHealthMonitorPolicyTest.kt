package com.simplexray.re.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocksHealthMonitorPolicyTest {
    @Test
    fun `probes continue while the screen is interactive`() {
        assertFalse(shouldPauseSocksProbe(wakeLockHeld = false, interactive = true))
    }

    @Test
    fun `probes pause on a suspended screen without a wake lock`() {
        assertTrue(shouldPauseSocksProbe(wakeLockHeld = false, interactive = false))
    }

    @Test
    fun `probes continue on a suspended screen while the wake lock is held`() {
        assertFalse(shouldPauseSocksProbe(wakeLockHeld = true, interactive = false))
    }

    @Test
    fun `wake lock and interactive screen keeps probing`() {
        assertFalse(shouldPauseSocksProbe(wakeLockHeld = true, interactive = true))
    }
}
