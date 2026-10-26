// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

/**
 * What the kernel reports about one network interface, independent of the tethering service.
 *
 * @property name the Linux interface name
 * @property isUp the administrative state (IFF_UP)
 * @property hasCarrier true when a cable is connected and the link is up, null when unreadable
 * @property speedMbps the link speed, null when unreadable or the link is down
 * @property ipv4 the IPv4 addresses with prefix, for example "169.254.0.42/16"
 */
data class InterfaceInfo(
    val name: String,
    val isUp: Boolean,
    val hasCarrier: Boolean?,
    val speedMbps: Int?,
    val ipv4: List<String>,
)

/** Reads interfaces from the kernel. The fallback when the tethering event callback is missing. */
fun interface InterfaceReader {
    /** All interfaces whose names match the default Ethernet pattern of [sdkInt]. */
    fun ethernetInterfaces(sdkInt: Int): List<InterfaceInfo>
}
