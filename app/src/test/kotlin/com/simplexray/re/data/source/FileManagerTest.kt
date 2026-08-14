package com.simplexray.re.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileManagerTest {

    @Test
    fun testIsStandardGeoDatCaseInsensitive() {
        assertTrue(FileManager.isStandardGeoDat("geoip.dat"))
        assertTrue(FileManager.isStandardGeoDat("geosite.dat"))
        assertTrue(FileManager.isStandardGeoDat("Geoip.dat"))
        assertTrue(FileManager.isStandardGeoDat("GEOIP.DAT"))
        assertTrue(FileManager.isStandardGeoDat("GeoSite.Dat"))
    }

    @Test
    fun testIsStandardGeoDatRejectsThirdPartyNames() {
        assertFalse(FileManager.isStandardGeoDat("custom.dat"))
        assertFalse(FileManager.isStandardGeoDat("geoip2.dat"))
        assertFalse(FileManager.isStandardGeoDat("geosite-cn.dat"))
        assertFalse(FileManager.isStandardGeoDat(""))
    }
}
