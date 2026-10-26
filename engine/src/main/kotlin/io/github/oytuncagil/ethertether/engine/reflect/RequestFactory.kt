// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.reflect

import io.github.oytuncagil.ethertether.engine.PortRequest
import io.github.oytuncagil.ethertether.engine.TetheringApiMissing
import io.github.oytuncagil.ethertether.engine.TetheringConstants
import io.github.oytuncagil.ethertether.model.Ipv4Address
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Builds the request parcel with the device's own TetheringRequest.Builder and getParcel(),
 * so the parcel layout always matches the device (never a hand-built parcel).
 *
 * @param builderClass the device's TetheringManager.TetheringRequest.Builder
 * @param linkAddress makes an android.net.LinkAddress from an address and a prefix length
 */
class RequestFactory(
    private val builderClass: Class<*>,
    private val linkAddress: (Ipv4Address, Int) -> Any,
) {
    private val scopeSetter: Method? = setter("setConnectivityScope", Int::class.javaPrimitiveType!!)

    /** True when the device's Builder has setConnectivityScope (Android 12 and later). */
    val supportsConnectivityScope: Boolean get() = scopeSetter != null

    /** The TetheringRequestParcel for [request]. */
    fun parcel(request: PortRequest): Any {
        val builder = construct()
        request.addresses?.let { addresses ->
            val local = linkAddress(addresses.phone, addresses.prefixLength)
            val client = linkAddress(addresses.client, addresses.prefixLength)
            invoke(required("setStaticIpv4Addresses", 2), builder, local, client)
        }
        invoke(required("setExemptFromEntitlementCheck", 1), builder, request.exemptFromEntitlement)
        invoke(required("setShouldShowEntitlementUi", 1), builder, false)
        request.scope?.let { scope ->
            val setter = scopeSetter ?: throw TetheringApiMissing("TetheringRequest.Builder.setConnectivityScope")
            invoke(setter, builder, scope)
        }
        val built = invoke(required("build", 0), builder) ?: throw TetheringApiMissing("TetheringRequest.Builder.build result")
        val getParcel = built.javaClass.methods.firstOrNull { it.name == "getParcel" && it.parameterCount == 0 }
            ?: throw TetheringApiMissing("TetheringRequest.getParcel")
        return invoke(getParcel, built) ?: throw TetheringApiMissing("TetheringRequest.getParcel result")
    }

    /** Builder setters found on this device, for the debug screen. */
    fun describe(): List<String> {
        val wanted = listOf("setStaticIpv4Addresses", "setExemptFromEntitlementCheck", "setShouldShowEntitlementUi", "setConnectivityScope")
        return wanted.map { name -> "Builder.$name: " + if (builderClass.methods.any { it.name == name }) "found" else "not found" }
    }

    private fun construct(): Any = try {
        builderClass.getConstructor(Int::class.javaPrimitiveType).newInstance(TetheringConstants.TETHERING_ETHERNET)
    } catch (e: NoSuchMethodException) {
        throw TetheringApiMissing("TetheringRequest.Builder(int)", e)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

    private fun setter(name: String, parameter: Class<*>): Method? =
        builderClass.methods.firstOrNull { it.name == name && it.parameterTypes.contentEquals(arrayOf(parameter)) }

    private fun required(name: String, parameterCount: Int): Method =
        builderClass.methods.firstOrNull { it.name == name && it.parameterCount == parameterCount }
            ?: throw TetheringApiMissing("TetheringRequest.Builder.$name")
}

/** Invokes [method], unwrapping the exception the target threw (for example IllegalArgumentException). */
internal fun invoke(method: Method, target: Any?, vararg args: Any?): Any? = try {
    method.invoke(target, *args)
} catch (e: InvocationTargetException) {
    throw e.targetException
}
