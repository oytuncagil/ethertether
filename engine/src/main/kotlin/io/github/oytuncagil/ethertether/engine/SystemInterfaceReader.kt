// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Reads Ethernet interfaces through [NetworkInterface], plus carrier and speed from sysfs when
 * readable. Used as the fallback status source and for the actual addresses of an interface.
 *
 * @param sysfsRoot the sysfs network directory; replaced in tests
 */
class SystemInterfaceReader(private val sysfsRoot: File = File("/sys/class/net")) : InterfaceReader {
    override fun ethernetInterfaces(sdkInt: Int): List<InterfaceInfo> {
        val all = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        return all.filter { EthernetNames.matches(it.name, sdkInt) }.map(::info).sortedBy { it.name }
    }

    private fun info(nif: NetworkInterface): InterfaceInfo {
        val ipv4 = nif.interfaceAddresses
            .filter { it.address is Inet4Address }
            .map { "${it.address.hostAddress}/${it.networkPrefixLength}" }
        val carrier = readSysfs(nif.name, "carrier")?.let { it == "1" }
        val speed = readSysfs(nif.name, "speed")?.toIntOrNull()?.takeIf { it > 0 }
        return InterfaceInfo(nif.name, runCatching { nif.isUp }.getOrDefault(false), carrier, speed, ipv4)
    }

    /** One sysfs attribute, trimmed; null when missing or unreadable (EINVAL while the link is down). */
    internal fun readSysfs(name: String, attribute: String): String? =
        runCatching { File(File(sysfsRoot, name), attribute).readText().trim() }.getOrNull()
}
