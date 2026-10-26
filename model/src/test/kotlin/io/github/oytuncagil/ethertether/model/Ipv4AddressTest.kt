// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4AddressTest {
    @Test
    fun parsesAndFormatsRoundTrip() {
        val address = Ipv4Address.parse("169.254.0.42")!!
        assertEquals("169.254.0.42", address.toString())
        assertEquals(listOf(169, 254, 0, 42), address.octets.toList())
    }

    @Test
    fun trimsSurroundingSpaces() {
        assertEquals("10.0.0.1", Ipv4Address.parse(" 10.0.0.1 ").toString())
    }

    @Test
    fun handlesExtremes() {
        assertEquals("0.0.0.0", Ipv4Address.parse("0.0.0.0").toString())
        assertEquals("255.255.255.255", Ipv4Address.parse("255.255.255.255").toString())
    }

    @Test
    fun rejectsMalformedText() {
        val bad = listOf(
            "", "1.2.3", "1.2.3.4.5", "256.1.1.1", "1.2.3.-4", "a.b.c.d", "01.2.3.4",
            "1..3.4", "1.2.3.4 5", "1.2.3.1000", "+1.2.3.4", "1.2.3.4.",
        )
        bad.forEach { assertNull("Accepted '$it'", Ipv4Address.parse(it)) }
    }

    @Test
    fun detectsLinkLocalRange() {
        assertTrue(Ipv4Address.parse("169.254.0.42")!!.isLinkLocal)
        assertTrue(Ipv4Address.parse("169.254.255.255")!!.isLinkLocal)
        assertFalse(Ipv4Address.parse("169.253.0.1")!!.isLinkLocal)
        assertFalse(Ipv4Address.parse("192.168.42.1")!!.isLinkLocal)
    }
}
