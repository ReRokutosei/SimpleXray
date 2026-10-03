package com.simplexray.re.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun newerStableBeatsPrerelease() {
        assertTrue(ReleaseVersion.compare("2.3.0", "2.3.0-alpha.5") > 0)
        assertTrue(ReleaseVersion.compare("2.3.0-alpha.5", "2.3.0") < 0)
    }

    @Test
    fun prereleaseOrderingFollowsSemver() {
        assertTrue(ReleaseVersion.compare("2.3.0-alpha.5", "2.3.0-alpha.4") > 0)
        assertTrue(ReleaseVersion.compare("2.3.0-beta.1", "2.3.0-alpha.5") > 0)
        assertTrue(ReleaseVersion.compare("2.3.0-rc.1", "2.3.0-beta.2") > 0)
    }

    @Test
    fun numericPrereleaseIdentifiersCompareNumerically() {
        assertTrue(ReleaseVersion.compare("2.3.0-alpha.10", "2.3.0-alpha.9") > 0)
        assertTrue(ReleaseVersion.compare("2.3.0-alpha.2", "2.3.0-alpha") > 0)
    }

    @Test
    fun coreVersionsCompareNumerically() {
        assertTrue(ReleaseVersion.compare("2.10.0", "2.9.9") > 0)
        assertTrue(ReleaseVersion.compare("v2.3.1", "2.3.0") > 0)
        assertTrue(ReleaseVersion.compare("2.3", "2.3.0") == 0)
    }

    @Test
    fun buildMetadataIsIgnored() {
        assertEquals(0, ReleaseVersion.compare("2.3.0+build.7", "2.3.0"))
    }
}
