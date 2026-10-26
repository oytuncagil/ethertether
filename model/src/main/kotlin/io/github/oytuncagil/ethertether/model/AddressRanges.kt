// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

/** Special IPv4 ranges from the IANA special-purpose address registry (RFC 6890 and updates). */
object AddressRanges {
    private fun net(text: String, prefix: Int) = Ipv4Subnet.of(Ipv4Address.parse(text)!!, prefix)

    /** Ranges no host on a link may use: "this network", loopback, multicast and the reserved top block. */
    private val unusable = listOf(net("0.0.0.0", 8), net("127.0.0.0", 8), net("224.0.0.0", 4), net("240.0.0.0", 4))

    /** RFC 1918 private ranges and the RFC 3927 link-local range. */
    private val privateOrLinkLocal = listOf(
        net("10.0.0.0", 8), net("172.16.0.0", 12), net("192.168.0.0", 16), net("169.254.0.0", 16),
    )

    /** Shared address space (carrier-grade NAT), documentation and benchmarking ranges. */
    private val reserved = listOf(
        net("100.64.0.0", 10), net("192.0.0.0", 24), net("192.0.2.0", 24), net("198.18.0.0", 15),
        net("198.51.100.0", 24), net("203.0.113.0", 24),
    )

    /** True for addresses that can never be a host address on an Ethernet link. */
    fun isUnusableForHosts(address: Ipv4Address): Boolean = unusable.any { address in it }

    /** True for private (RFC 1918) and link-local (169.254.0.0/16) addresses. */
    fun isPrivateOrLinkLocal(address: Ipv4Address): Boolean = privateOrLinkLocal.any { address in it }

    /** True for carrier-grade NAT, documentation and benchmarking addresses. */
    fun isReserved(address: Ipv4Address): Boolean = reserved.any { address in it }
}
