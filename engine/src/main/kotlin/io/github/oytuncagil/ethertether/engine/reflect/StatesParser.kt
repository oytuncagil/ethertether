// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.reflect

import io.github.oytuncagil.ethertether.engine.ErroredIface
import io.github.oytuncagil.ethertether.engine.TetherIface
import io.github.oytuncagil.ethertether.engine.TetherStates
import io.github.oytuncagil.ethertether.engine.TetheringApiMissing

/**
 * Reads a TetherStatesParcel by its public fields. Android 11 holds String arrays; Android 12
 * and later hold TetheringInterface arrays (getType, getInterface). Both are handled by type.
 */
object StatesParser {
    /** The states held by [parcel]. */
    fun parse(parcel: Any): TetherStates {
        val errored = list(parcel, "erroredIfaceList")
        val errors = field(parcel, "lastErrorList") as? IntArray ?: IntArray(0)
        return TetherStates(
            available = list(parcel, "availableList"),
            tethered = list(parcel, "tetheredList"),
            localOnly = list(parcel, "localOnlyList"),
            errored = errored.mapIndexed { index, iface -> ErroredIface(iface, errors.getOrElse(index) { -1 }) },
        )
    }

    /** The "states" and "tetheredClients" fields of a TetheringCallbackStartedParcel; null without states. */
    fun parseStarted(started: Any): TetherStates? {
        val states = field(started, "states")?.let(::parse) ?: return null
        val clients = runCatching { field(started, "tetheredClients") as? List<*> }.getOrNull().orEmpty()
        return states.copy(ethernetClients = ethernetClientAddresses(clients))
    }

    /**
     * IPv4 addresses of Ethernet clients in a list of TetheredClient (getTetheringType,
     * getAddresses of AddressInfo with getAddress, a LinkAddress). MAC addresses are not read.
     * Anything unexpected yields no address instead of an error.
     */
    fun ethernetClientAddresses(clients: List<*>): List<String> = clients.filterNotNull().flatMap { client ->
        runCatching {
            val type = client.javaClass.getMethod("getTetheringType").invoke(client) as Int
            if (type != io.github.oytuncagil.ethertether.engine.TetheringConstants.TETHERING_ETHERNET) return@runCatching emptyList()
            val infos = client.javaClass.getMethod("getAddresses").invoke(client) as List<*>
            infos.filterNotNull().mapNotNull { info ->
                val link = info.javaClass.getMethod("getAddress").invoke(info) ?: return@mapNotNull null
                val inet = link.javaClass.getMethod("getAddress").invoke(link) as? java.net.InetAddress
                (inet as? java.net.Inet4Address)?.hostAddress
            }
        }.getOrDefault(emptyList())
    }.distinct()

    private fun list(parcel: Any, name: String): List<TetherIface> {
        val array = field(parcel, name) as? Array<*> ?: return emptyList()
        return array.filterNotNull().map(::iface)
    }

    private fun iface(element: Any): TetherIface {
        if (element is String) return TetherIface(element, type = null)
        val type = element.javaClass
        val name = type.getMethod("getInterface").invoke(element) as String
        val kind = type.getMethod("getType").invoke(element) as Int
        return TetherIface(name, kind)
    }

    private fun field(target: Any, name: String): Any? = try {
        target.javaClass.getField(name).get(target)
    } catch (e: NoSuchFieldException) {
        throw TetheringApiMissing("${target.javaClass.name}.$name", e)
    }
}
