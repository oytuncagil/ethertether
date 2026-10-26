// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

/** An IPv4 address held as its 32-bit value, so arithmetic on subnets stays exact. */
@JvmInline
value class Ipv4Address(val bits: Int) {
    /** The four octets, most significant first, each 0 to 255. */
    val octets: IntArray get() = IntArray(4) { (bits ushr (24 - 8 * it)) and 0xFF }

    /** True for 169.254.0.0/16, the IPv4 link-local range. */
    val isLinkLocal: Boolean get() = (bits ushr 16) == LINK_LOCAL_HIGH

    /** Dotted-quad text, for example "169.254.0.42". */
    override fun toString(): String = octets.joinToString(".")

    /** Parsing helpers. */
    companion object {
        private const val LINK_LOCAL_HIGH = (169 shl 8) or 254

        /**
         * Parses strict dotted-quad text: exactly four decimal octets 0 to 255, no leading
         * zeros (they are octal in some parsers), no spaces inside. Returns null when invalid.
         */
        fun parse(text: String): Ipv4Address? {
            val parts = text.trim().split(".")
            if (parts.size != 4) return null
            var bits = 0
            for (part in parts) {
                val octet = parseOctet(part) ?: return null
                bits = (bits shl 8) or octet
            }
            return Ipv4Address(bits)
        }

        private fun parseOctet(part: String): Int? {
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
            if (part.length > 1 && part[0] == '0') return null
            return part.toInt().takeIf { it <= 255 }
        }
    }
}
