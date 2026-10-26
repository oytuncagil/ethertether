// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.model.StaticAddresses

/**
 * One Ethernet tethering request exactly as it goes to the device's own request Builder.
 *
 * @property addresses the fixed phone and client addresses, or null for normal DHCP
 * @property scope a TetheringConstants.CONNECTIVITY_SCOPE_* value, or null to leave the
 *   Builder's default (Android 11 has no scope at all)
 * @property exemptFromEntitlement the value for setExemptFromEntitlementCheck
 */
data class PortRequest(
    val addresses: StaticAddresses?,
    val scope: Int?,
    val exemptFromEntitlement: Boolean,
)

/**
 * The hidden tethering service as the engine sees it. The real implementation reaches the
 * device's own classes through reflection; tests use fakes for every Android version shape.
 * Every call may throw [TetheringApiMissing] or [TetheringServiceDied].
 */
interface TetheringPort {
    /** The Android API level of the device. */
    val sdkInt: Int

    /** True when the device's request Builder has setConnectivityScope (Android 12 and later). */
    val supportsConnectivityScope: Boolean

    /** Sends startTethering; [onResult] receives the result code once, on any thread. */
    fun startTethering(request: PortRequest, onResult: (Int) -> Unit)

    /** Sends stopTethering for Ethernet; [onResult] receives the result code once, on any thread. */
    fun stopTethering(onResult: (Int) -> Unit)

    /**
     * Registers the tethering event callback. [onStates] receives the initial state right away
     * and every later change, on any thread. Closing the result unregisters it.
     */
    fun registerEvents(onStates: (TetherStates) -> Unit): AutoCloseable

    /** Human-readable lines about what was resolved on this device, for the debug screen. */
    fun describe(): List<String>

    /** A readable dump of the request parcel the device's Builder makes, for the debug screen. */
    fun describeRequest(request: PortRequest): String
}

/** A hidden class, method or field the engine needs does not exist on this device. */
class TetheringApiMissing(val what: String, cause: Throwable? = null) :
    Exception("Not found on this device: $what", cause)

/** The tethering service or the process hosting the call went away during a call. */
class TetheringServiceDied(cause: Throwable? = null) : Exception("The tethering service connection died", cause)
