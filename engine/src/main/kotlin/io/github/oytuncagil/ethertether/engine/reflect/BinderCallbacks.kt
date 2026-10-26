// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.reflect

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import io.github.oytuncagil.ethertether.engine.TetherStates
import io.github.oytuncagil.ethertether.engine.TetheringApiMissing
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Callback objects for the hidden oneway AIDL interfaces. The AIDL stubs are hidden abstract
 * classes, so each callback is a plain [Binder] behind a [Proxy] of the hidden interface. The
 * transaction codes are read from the device's own stub (its TRANSACTION_* fields), never
 * hard-coded.
 */
internal class BinderCallbacks(private val names: HiddenNames) {
    /** An IIntResultListener that passes the first result code to [onResult]. */
    fun resultListener(onResult: (Int) -> Unit): Any {
        val code = transactionCode(names.intResultListenerStub, "onResult")
        val delivered = AtomicBoolean(false)
        val binder = object : Binder() {
            override fun onTransact(transaction: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (transaction != code) return super.onTransact(transaction, data, reply, flags)
                data.enforceInterface(names.intResultListener)
                val result = data.readInt()
                if (delivered.compareAndSet(false, true)) onResult(result)
                return true
            }
        }
        return proxy(names.intResultListener, binder)
    }

    /** An ITetheringEventCallback that passes the initial and every changed state to [onStates]. */
    fun eventCallback(onStates: (TetherStates) -> Unit): Any {
        val started = transactionCode(names.eventCallbackStub, "onCallbackStarted")
        val changed = transactionCode(names.eventCallbackStub, "onTetherStatesChanged")
        val clientsChanged = runCatching { transactionCode(names.eventCallbackStub, "onTetherClientsChanged") }.getOrNull()
        val startedCreator = creator(names.callbackStartedParcel)
        val statesCreator = creator(names.statesParcel)
        val clientCreator = runCatching { creator(names.tetheredClient) }.getOrNull()
        var latest = TetherStates()
        fun deliver(states: TetherStates) {
            latest = states
            onStates(states)
        }
        val binder = object : Binder() {
            override fun onTransact(transaction: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (transaction !in FIRST_CALL_TRANSACTION..LAST_CALL_TRANSACTION) {
                    return super.onTransact(transaction, data, reply, flags)
                }
                data.enforceInterface(names.eventCallback)
                when (transaction) {
                    started -> readTyped(data, startedCreator)?.let(StatesParser::parseStarted)?.let(::deliver)
                    changed -> readTyped(data, statesCreator)?.let(StatesParser::parse)
                        ?.let { deliver(it.copy(ethernetClients = latest.ethernetClients)) }
                    clientsChanged -> clientCreator?.let { creator ->
                        val clients = data.createTypedArrayList(creator).orEmpty()
                        deliver(latest.copy(ethernetClients = StatesParser.ethernetClientAddresses(clients)))
                    }
                }
                // Other events (upstream, clients, offload) are not needed and are ignored.
                return true
            }
        }
        return proxy(names.eventCallback, binder)
    }

    /** "onResult=1, onCallbackStarted=1, onTetherStatesChanged=5" style text for the debug screen. */
    fun describe(): String {
        fun code(stub: String, method: String) = runCatching { transactionCode(stub, method).toString() }.getOrDefault("not found")
        return "transaction codes: onResult=${code(names.intResultListenerStub, "onResult")}, " +
            "onCallbackStarted=${code(names.eventCallbackStub, "onCallbackStarted")}, " +
            "onTetherStatesChanged=${code(names.eventCallbackStub, "onTetherStatesChanged")}"
    }

    private fun transactionCode(stubName: String, method: String): Int = try {
        val field = Class.forName(stubName).getDeclaredField("TRANSACTION_$method")
        field.isAccessible = true
        field.getInt(null)
    } catch (e: ReflectiveOperationException) {
        throw TetheringApiMissing("$stubName.TRANSACTION_$method", e)
    }

    private fun creator(className: String): Parcelable.Creator<*> = try {
        Class.forName(className).getField("CREATOR").get(null) as Parcelable.Creator<*>
    } catch (e: ReflectiveOperationException) {
        throw TetheringApiMissing("$className.CREATOR", e)
    }

    /** A nullable AIDL parcelable argument: an int flag, then the parcelable when the flag is not 0. */
    private fun readTyped(data: Parcel, creator: Parcelable.Creator<*>): Any? =
        if (data.readInt() == 0) null else creator.createFromParcel(data)

    private fun proxy(interfaceName: String, binder: Binder): Any {
        binder.attachInterface(null, interfaceName)
        val type = try {
            Class.forName(interfaceName)
        } catch (e: ClassNotFoundException) {
            throw TetheringApiMissing(interfaceName, e)
        }
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> binder as IBinder
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "EtherTether $interfaceName"
                // Local calls never happen: the system always calls through the binder.
                else -> null
            }
        }
    }
}
