// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

/** How the phone's address on the Ethernet link is chosen. */
enum class TetherMode {
    /** A fixed phone address and one fixed client address chosen by the user. */
    STATIC,

    /** Android's normal tethering: a random private subnet and DHCP. */
    DHCP,
}

/** Whether the phone's own internet connection is shared over the Ethernet link. */
enum class SharingScope {
    /** No internet is shared (CONNECTIVITY_SCOPE_LOCAL, Android 12 and later). */
    LOCAL_ONLY,

    /** The phone's connection is shared (CONNECTIVITY_SCOPE_GLOBAL). */
    SHARE_INTERNET,
}

/** The fixed addresses of a static request. Both lie in the same subnet. */
data class StaticAddresses(val phone: Ipv4Address, val client: Ipv4Address, val prefixLength: Int) {
    /** The subnet both addresses share. */
    val subnet: Ipv4Subnet get() = Ipv4Subnet.of(phone, prefixLength)
}

/**
 * A validated Ethernet tethering request. Build it with [TetherRequestValidator] so that only
 * values Android accepts reach the engine.
 *
 * @property addresses the fixed addresses, or null for [TetherMode.DHCP]
 * @property scope what is shared over the link
 * @property allowScopeFallback when the phone refuses [SharingScope.LOCAL_ONLY], start with the
 *   system default (internet shared) instead of failing; the fallback is always reported
 */
data class TetherRequest(
    val addresses: StaticAddresses?,
    val scope: SharingScope,
    val allowScopeFallback: Boolean,
) {
    /** The address mode of this request. */
    val mode: TetherMode get() = if (addresses == null) TetherMode.DHCP else TetherMode.STATIC
}
