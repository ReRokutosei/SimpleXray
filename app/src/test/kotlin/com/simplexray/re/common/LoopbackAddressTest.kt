package com.simplexray.re.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopbackAddressTest {

    @Test
    fun acceptsIpv4AndIpv6LoopbackLiterals() {
        assertTrue(isLoopbackAddress("127.0.0.1"))
        assertTrue(isLoopbackAddress("127.255.255.254"))
        assertTrue(isLoopbackAddress("::1"))
        assertTrue(isLoopbackAddress("[::1]"))
    }

    @Test
    fun rejectsNonLoopbackAndNonLiterals() {
        assertFalse(isLoopbackAddress("192.168.1.2"))
        assertFalse(isLoopbackAddress("0.0.0.0"))
        assertFalse(isLoopbackAddress("::"))
        assertFalse(isLoopbackAddress("localhost"))
        assertFalse(isLoopbackAddress(""))
    }
}
