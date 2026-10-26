// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4SubnetTest {
    private fun ip(text: String) = Ipv4Address.parse(text)!!

    @Test
    fun subnetMathIsExact() {
        val subnet = Ipv4Subnet.of(ip("169.254.0.42"), 16)
        assertEquals("169.254.0.0", subnet.network.toString())
        assertEquals("169.254.255.255", subnet.broadcast.toString())
        assertTrue(ip("169.254.200.1") in subnet)
        assertFalse(ip("169.255.0.1") in subnet)
        assertEquals("0.0.0.0/0", Ipv4Subnet.of(ip("8.8.8.8"), 0).toString())
        assertEquals("255.255.255.255", Ipv4Subnet.of(ip("8.8.8.8"), 0).broadcast.toString())
    }

    @Test
    fun overlapWorksBothWays() {
        val big = Ipv4Subnet.of(ip("10.0.0.0"), 8)
        val small = Ipv4Subnet.of(ip("10.1.2.0"), 24)
        val other = Ipv4Subnet.of(ip("11.0.0.0"), 8)
        assertTrue(big.overlaps(small))
        assertTrue(small.overlaps(big))
        assertFalse(big.overlaps(other))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unmaskedNetworkIsRejected() {
        Ipv4Subnet(ip("10.0.0.1"), 24)
    }
}
