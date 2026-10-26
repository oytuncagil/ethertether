// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.reflect

import io.github.oytuncagil.ethertether.engine.TetheringApiMissing
import java.lang.reflect.Method

/**
 * The ITetheringConnector methods the engine calls, found by name and parameter types on the
 * device's own class. Android 11 has three parameters, 12 and later add an attribution tag,
 * and 16 inserted stopTetheringRequest; none of that is hard-coded.
 */
class ConnectorMethods(connectorClass: Class<*>, private val names: HiddenNames) {
    /** startTethering(TetheringRequestParcel, String callerPkg, [String tag,] IIntResultListener). */
    val start: Method = find(connectorClass, "startTethering") { it.firstOrNull()?.name == names.requestParcel }

    /** stopTethering(int type, String callerPkg, [String tag,] IIntResultListener). */
    val stop: Method = find(connectorClass, "stopTethering") { it.firstOrNull() == Int::class.javaPrimitiveType }

    /** registerTetheringEventCallback(ITetheringEventCallback, String callerPkg), or null. */
    val register: Method? = findOrNull(connectorClass, "registerTetheringEventCallback") { it.firstOrNull()?.name == names.eventCallback }

    /** unregisterTetheringEventCallback(ITetheringEventCallback, String callerPkg), or null. */
    val unregister: Method? = findOrNull(connectorClass, "unregisterTetheringEventCallback") { it.firstOrNull()?.name == names.eventCallback }

    /** Readable signatures for the debug screen. */
    fun describe(): List<String> = listOf(
        "startTethering${start.signature()}",
        "stopTethering${stop.signature()}",
        "registerTetheringEventCallback${register?.signature() ?: " not found"}",
    )

    private fun find(type: Class<*>, name: String, shape: (List<Class<*>>) -> Boolean): Method =
        findOrNull(type, name, shape) ?: throw TetheringApiMissing("ITetheringConnector.$name with a known parameter list")

    private fun findOrNull(type: Class<*>, name: String, shape: (List<Class<*>>) -> Boolean): Method? =
        type.methods.firstOrNull { it.name == name && shape(it.parameterTypes.toList()) }
}

/** "(int, String, String, IIntResultListener)" style signature. */
internal fun Method.signature(): String = parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }

/**
 * Fills a connector method's arguments by parameter type: the first String is the caller
 * package, later Strings (the attribution tag) are null, an int is the tethering type, and the
 * listener and parcel types get [listener] and [parcel]. Any other type is reported as missing
 * support instead of guessed.
 */
class ArgumentFiller(private val names: HiddenNames, private val callerPackage: String, private val tetheringType: Int) {
    /** The argument array for [method]. */
    fun fill(method: Method, listener: Any?, parcel: Any? = null): Array<Any?> {
        var strings = 0
        return method.parameterTypes.map { type ->
            when {
                type == Int::class.javaPrimitiveType -> tetheringType
                type == String::class.java -> if (strings++ == 0) callerPackage else null
                type.name == names.intResultListener || type.name == names.eventCallback -> listener
                type.name == names.requestParcel -> parcel
                else -> throw TetheringApiMissing("${method.name} with parameter ${type.name}")
            }
        }.toTypedArray()
    }
}
