package com.simplexray.re.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUpdateDuePolicyTest {

    @Test
    fun `disabled auto-update interval never triggers update`() {
        assertFalse(GeoUpdateWorker.isGeoUpdateDue(intervalHours = 0, lastUpdateMs = 0L))
        assertFalse(GeoUpdateWorker.isGeoUpdateDue(intervalHours = -1, lastUpdateMs = 0L))
        assertFalse(GeoUpdateWorker.isGeoUpdateDue(intervalHours = 0, lastUpdateMs = 1_000L, nowMs = 100_000_000L))
    }

    @Test
    fun `first time run with zero lastUpdate timestamp triggers update`() {
        assertTrue(GeoUpdateWorker.isGeoUpdateDue(intervalHours = 24, lastUpdateMs = 0L, nowMs = 1_000_000L))
    }

    @Test
    fun `does not trigger update before interval expires`() {
        val now = 100_000_000L
        val intervalHours = 12
        val intervalMs = intervalHours * 60L * 60L * 1000L

        // 1 second before expiration
        val lastUpdate = now - (intervalMs - 1000L)
        assertFalse(GeoUpdateWorker.isGeoUpdateDue(intervalHours, lastUpdate, now))
    }

    @Test
    fun `triggers update when interval has exactly or more elapsed`() {
        val now = 100_000_000L
        val intervalHours = 12
        val intervalMs = intervalHours * 60L * 60L * 1000L

        // Exactly elapsed
        assertTrue(GeoUpdateWorker.isGeoUpdateDue(intervalHours, now - intervalMs, now))

        // Overdue by 1 hour
        assertTrue(GeoUpdateWorker.isGeoUpdateDue(intervalHours, now - intervalMs - 3600_000L, now))
    }
}
