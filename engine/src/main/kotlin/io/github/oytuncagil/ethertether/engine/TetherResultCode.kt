// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

/**
 * The TETHER_ERROR_* result codes of TetheringManager, with their numeric values. Codes 0 to 16
 * exist since Android 11; 17 and 18 were added in Android 16. The app explains each one with
 * the next step.
 */
enum class TetherResultCode(val code: Int, val sinceSdk: Int = 30) {
    /** Request accepted; the outcome follows through the tethering event callback. */
    NO_ERROR(0),
    /** Interface not tracked by tethering. */
    UNKNOWN_IFACE(1),
    /** A system service tethering needs is missing. */
    SERVICE_UNAVAIL(2),
    /** Tethering not supported or not allowed (setting or user restriction). */
    UNSUPPORTED(3),
    /** Interface not available, for example no adapter. */
    UNAVAIL_IFACE(4),
    /** Generic internal error. */
    INTERNAL_ERROR(5),
    /** Setting up the tethered interface failed. */
    TETHER_IFACE_ERROR(6),
    /** Tearing down the tethered interface failed. */
    UNTETHER_IFACE_ERROR(7),
    /** Enabling forwarding or NAT failed. */
    ENABLE_FORWARDING_ERROR(8),
    /** Disabling forwarding failed. */
    DISABLE_FORWARDING_ERROR(9),
    /** IPv4 address configuration failed. */
    IFACE_CFG_ERROR(10),
    /** Carrier entitlement check failed. */
    PROVISIONING_FAILED(11),
    /** DHCP server could not start, for example a prefix outside 16 to 30. */
    DHCPSERVER_ERROR(12),
    /** Carrier entitlement result unknown. */
    ENTITLEMENT_UNKNOWN(13),
    /** Permission missing, or the caller package does not match the uid. */
    NO_CHANGE_TETHERING_PERMISSION(14),
    /** Event callback registration without access permission. */
    NO_ACCESS_TETHERING_PERMISSION(15),
    /** Invalid tethering type. */
    UNKNOWN_TYPE(16),
    /** No matching request to stop (Android 16 and later). */
    UNKNOWN_REQUEST(17, sinceSdk = 36),
    /** A request of this type is already pending or serving (Android 16 and later). */
    DUPLICATE_REQUEST(18, sinceSdk = 36),
    ;

    /** Lookup by numeric value. */
    companion object {
        private val byCode = entries.associateBy { it.code }

        /** The code with this numeric value, or null for a value this app does not know. */
        fun fromCode(code: Int): TetherResultCode? = byCode[code]
    }
}
