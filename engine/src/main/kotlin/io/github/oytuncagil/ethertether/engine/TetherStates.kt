// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

/**
 * One interface in a tethering state list.
 *
 * @property name the Linux interface name, for example "eth0"
 * @property type the TETHERING_* type, or null on Android 11, whose lists hold names only
 */
data class TetherIface(val name: String, val type: Int?)

/** One interface whose tethering failed, with the TETHER_ERROR_* code Android reported. */
data class ErroredIface(val iface: TetherIface, val error: Int)

/**
 * A snapshot of the tethering service's TetherStatesParcel.
 *
 * @property available interfaces that could be tethered
 * @property tethered interfaces serving with the phone's connection shared (global scope)
 * @property localOnly interfaces serving without internet sharing (local scope)
 * @property errored interfaces that failed, each with its last error
 * @property ethernetClients IPv4 addresses of devices on Ethernet tethering, when Android reports them
 */
data class TetherStates(
    val available: List<TetherIface> = emptyList(),
    val tethered: List<TetherIface> = emptyList(),
    val localOnly: List<TetherIface> = emptyList(),
    val errored: List<ErroredIface> = emptyList(),
    val ethernetClients: List<String> = emptyList(),
) {
    /** The Ethernet interface that serves now and the scope it serves with, or null. */
    fun ethernetServing(sdkInt: Int): Pair<TetherIface, ServingScope>? {
        tethered.firstOrNull { it.isEthernet(sdkInt) }?.let { return it to ServingScope.GLOBAL }
        localOnly.firstOrNull { it.isEthernet(sdkInt) }?.let { return it to ServingScope.LOCAL }
        return null
    }

    /** The Ethernet interface in the error list, or null. */
    fun ethernetError(sdkInt: Int): ErroredIface? = errored.firstOrNull { it.iface.isEthernet(sdkInt) }

    /** The Ethernet interface that is available but not serving, or null. */
    fun ethernetAvailable(sdkInt: Int): TetherIface? = available.firstOrNull { it.isEthernet(sdkInt) }
}

/** The scope an interface actually serves with, as the tethering service reports it. */
enum class ServingScope {
    /** In tetheredList: the phone's connection is shared. */
    GLOBAL,

    /** In localOnlyList: nothing is shared. */
    LOCAL,
}

/**
 * True when this interface is Ethernet. Android 12 and later report the type; Android 11
 * reports names only, so the name is matched with the default Ethernet pattern of the version.
 */
fun TetherIface.isEthernet(sdkInt: Int): Boolean =
    if (type != null) type == TetheringConstants.TETHERING_ETHERNET else EthernetNames.matches(name, sdkInt)

/** Default Ethernet interface name patterns of the Android framework. */
object EthernetNames {
    /** First API level whose default pattern also accepts usbN names (Android 15). */
    const val SDK_USB_NAMES = 35

    private val ethOnly = Regex("eth\\d+")
    private val ethOrUsb = Regex("(eth|usb)\\d+")

    /** True when [name] matches the default Ethernet pattern of [sdkInt]. OEMs may differ. */
    fun matches(name: String, sdkInt: Int): Boolean =
        (if (sdkInt >= SDK_USB_NAMES) ethOrUsb else ethOnly).matches(name)
}
