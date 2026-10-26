// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.reflect

import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import io.github.oytuncagil.ethertether.engine.PortRequest
import io.github.oytuncagil.ethertether.engine.TetherStates
import io.github.oytuncagil.ethertether.engine.TetheringApiMissing
import io.github.oytuncagil.ethertether.engine.TetheringConstants
import io.github.oytuncagil.ethertether.engine.TetheringPort
import io.github.oytuncagil.ethertether.engine.TetheringServiceDied
import io.github.oytuncagil.ethertether.model.Ipv4Address
import java.net.InetAddress

/**
 * The real [TetheringPort]: the device's ITetheringConnector, reached through ServiceManager.
 * It runs inside the Shizuku user service, so every call carries the shell identity (uid 2000)
 * with the package com.android.shell, the pair the tethering service accepts.
 */
class ReflectiveTetheringPort(private val names: HiddenNames = HiddenNames()) : TetheringPort {
    private val connector: Any by lazy(::resolveConnector)
    private val methods by lazy { ConnectorMethods(connector.javaClass, names) }
    private val filler = ArgumentFiller(names, TetheringConstants.SHELL_PACKAGE, TetheringConstants.TETHERING_ETHERNET)
    private val callbacks = BinderCallbacks(names)
    private val requests by lazy { RequestFactory(loadClass(names.requestBuilder), ::linkAddress) }

    override val sdkInt: Int get() = Build.VERSION.SDK_INT

    override val supportsConnectivityScope: Boolean get() = requests.supportsConnectivityScope

    override fun startTethering(request: PortRequest, onResult: (Int) -> Unit) {
        val parcel = requests.parcel(request)
        call(methods.start, callbacks.resultListener(onResult), parcel)
    }

    override fun stopTethering(onResult: (Int) -> Unit) {
        call(methods.stop, callbacks.resultListener(onResult))
    }

    override fun registerEvents(onStates: (TetherStates) -> Unit): AutoCloseable {
        val register = methods.register ?: throw TetheringApiMissing("ITetheringConnector.registerTetheringEventCallback")
        val callback = callbacks.eventCallback(onStates)
        call(register, callback)
        return AutoCloseable { methods.unregister?.let { runCatching { call(it, callback) } } }
    }

    override fun describe(): List<String> = buildList {
        add("API level $sdkInt (${Build.VERSION.RELEASE}), ${Build.MANUFACTURER} ${Build.MODEL}")
        add("connector: " + runCatching { connector.javaClass.name }.getOrElse { "not available: ${it.message}" })
        runCatching { methods.describe() }.onSuccess(::addAll).onFailure { add("connector methods: ${it.message}") }
        runCatching { requests.describe() }.onSuccess(::addAll).onFailure { add("Builder: ${it.message}") }
        add(callbacks.describe())
    }

    override fun describeRequest(request: PortRequest): String = FieldDump.of(requests.parcel(request))

    private fun call(method: java.lang.reflect.Method, listener: Any, parcel: Any? = null) {
        try {
            invoke(method, connector, *filler.fill(method, listener, parcel))
        } catch (e: DeadObjectException) {
            throw TetheringServiceDied(e)
        }
    }

    private fun resolveConnector(): Any {
        val binder = serviceBinder() ?: throw TetheringApiMissing("system service \"${TetheringConstants.SERVICE_NAME}\"")
        val asInterface = loadClass(names.connectorStub).getMethod("asInterface", IBinder::class.java)
        return invoke(asInterface, null, binder) ?: throw TetheringApiMissing("ITetheringConnector.Stub.asInterface result")
    }

    private fun serviceBinder(): IBinder? {
        val getService = loadClass(names.serviceManager).getMethod("getService", String::class.java)
        return invoke(getService, null, TetheringConstants.SERVICE_NAME) as IBinder?
    }

    private fun linkAddress(address: Ipv4Address, prefixLength: Int): Any {
        val inet = InetAddress.getByAddress(ByteArray(4) { address.octets[it].toByte() })
        val constructor = loadClass(names.linkAddress).getConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
        return constructor.newInstance(inet, prefixLength)
    }

    private fun loadClass(name: String): Class<*> = try {
        Class.forName(name)
    } catch (e: ClassNotFoundException) {
        throw TetheringApiMissing(name, e)
    }
}
