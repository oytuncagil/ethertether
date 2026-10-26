// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

/** An IPv4 network: [network] is already masked to [prefixLength] bits. */
data class Ipv4Subnet(val network: Ipv4Address, val prefixLength: Int) {
    init {
        require(prefixLength in 0..32) { "Prefix length must be 0 to 32, was $prefixLength" }
        require(network.bits and mask(prefixLength).inv() == 0) { "$network is not masked to /$prefixLength" }
    }

    /** The highest address of the subnet. */
    val broadcast: Ipv4Address get() = Ipv4Address(network.bits or mask(prefixLength).inv())

    /** True when [address] lies inside this subnet. */
    operator fun contains(address: Ipv4Address): Boolean =
        (address.bits and mask(prefixLength)) == network.bits

    /** True when the two subnets share at least one address. */
    fun overlaps(other: Ipv4Subnet): Boolean {
        val shorter = minOf(prefixLength, other.prefixLength)
        return (network.bits and mask(shorter)) == (other.network.bits and mask(shorter))
    }

    override fun toString(): String = "$network/$prefixLength"

    /** Construction helpers. */
    companion object {
        /** The subnet of [address] with [prefixLength] bits. */
        fun of(address: Ipv4Address, prefixLength: Int): Ipv4Subnet =
            Ipv4Subnet(Ipv4Address(address.bits and mask(prefixLength)), prefixLength)

        /** Netmask with the top [prefixLength] bits set. */
        fun mask(prefixLength: Int): Int = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
    }
}
