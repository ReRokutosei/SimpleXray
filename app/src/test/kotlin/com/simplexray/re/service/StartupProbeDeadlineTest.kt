package com.simplexray.re.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupProbeDeadlineTest {
    @Test
    fun `time spent suspended does not consume the startup budget`() {
        var awakeMs = 1_000L
        val deadline = StartupProbeDeadline(timeoutMs = 15_000L) { awakeMs }

        // Deep sleep advances wall-clock time, but not awake/uptime time.
        assertFalse(deadline.isExpired)

        awakeMs += 14_999L
        assertFalse(deadline.isExpired)

        awakeMs += 1L
        assertTrue(deadline.isExpired)
    }
}
