// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.oytuncagil.ethertether.engine.reflect

/*
 * Fake classes with the shapes of the hidden tethering classes per Android version
 * (docs/RESEARCH.md, "Android tethering"). The reflection layer resolves them exactly like the real ones.
 */

class FakeRequestParcel(val fields: Map<String, Any?>)

interface FakeIntListener

interface FakeEventCallback

class FakeStopRequest

/** HiddenNames pointing at the fakes above. */
val FAKE_NAMES = HiddenNames(
    requestParcel = FakeRequestParcel::class.java.name,
    intResultListener = FakeIntListener::class.java.name,
    eventCallback = FakeEventCallback::class.java.name,
)

/** Android 11: three parameters, no attribution tag. */
class Connector11 {
    val calls = mutableListOf<List<Any?>>()

    fun startTethering(request: FakeRequestParcel, callerPkg: String, receiver: FakeIntListener) {
        calls += listOf(request, callerPkg, receiver)
    }

    fun stopTethering(type: Int, callerPkg: String, receiver: FakeIntListener) {
        calls += listOf(type, callerPkg, receiver)
    }

    fun registerTetheringEventCallback(callback: FakeEventCallback, callerPkg: String) {
        calls += listOf(callback, callerPkg)
    }
}

/** Android 12 to 15: the attribution tag is added. */
class Connector12 {
    val calls = mutableListOf<List<Any?>>()

    fun tether(iface: String, callerPkg: String, tag: String?, receiver: FakeIntListener) = Unit

    fun startTethering(request: FakeRequestParcel, callerPkg: String, tag: String?, receiver: FakeIntListener) {
        calls += listOf(request, callerPkg, tag, receiver)
    }

    fun stopTethering(type: Int, callerPkg: String, tag: String?, receiver: FakeIntListener) {
        calls += listOf(type, callerPkg, tag, receiver)
    }

    fun registerTetheringEventCallback(callback: FakeEventCallback, callerPkg: String) = Unit

    fun unregisterTetheringEventCallback(callback: FakeEventCallback, callerPkg: String) = Unit
}

/** Android 16 and 17: stopTetheringRequest inserted, which must not be mistaken for stopTethering. */
class Connector16 {
    val calls = mutableListOf<String>()

    fun startTethering(request: FakeRequestParcel, callerPkg: String, tag: String?, receiver: FakeIntListener) {
        calls += "startTethering"
    }

    fun stopTetheringRequest(request: FakeStopRequest, callerPkg: String, tag: String?, receiver: FakeIntListener) {
        calls += "stopTetheringRequest"
    }

    fun stopTethering(type: Int, callerPkg: String, tag: String?, receiver: FakeIntListener) {
        calls += "stopTethering"
    }
}

/** A connector of a future version with an unknown parameter. */
class ConnectorUnknown {
    fun startTethering(request: FakeRequestParcel, callerPkg: String, flags: Long, receiver: FakeIntListener) = Unit

    fun stopTethering(type: Int, callerPkg: String, receiver: FakeIntListener) = Unit
}

/** A connector without startTethering. */
class ConnectorBroken {
    fun stopTethering(type: Int, callerPkg: String, receiver: FakeIntListener) = Unit
}

/** A LinkAddress stand-in. */
data class FakeLinkAddress(val address: String, val prefix: Int)

/** TetheringRequest with getParcel. */
class FakeRequest(private val values: Map<String, Any?>) {
    fun getParcel(): FakeRequestParcel = FakeRequestParcel(values)
}

/** Builder of Android 11: no setConnectivityScope. */
open class Builder11(type: Int) {
    protected val values = mutableMapOf<String, Any?>("type" to type)

    fun setStaticIpv4Addresses(local: FakeLinkAddress, client: FakeLinkAddress): Builder11 {
        require(local.prefix == client.prefix) { "Invalid server or client addresses" }
        values["local"] = local
        values["client"] = client
        return this
    }

    fun setExemptFromEntitlementCheck(exempt: Boolean): Builder11 = apply { values["exempt"] = exempt }

    fun setShouldShowEntitlementUi(show: Boolean): Builder11 = apply { values["showUi"] = show }

    fun build(): FakeRequest = FakeRequest(values.toMap())
}

/** Builder of Android 12 and later. */
class Builder12(type: Int) : Builder11(type) {
    fun setConnectivityScope(scope: Int): Builder12 = apply { values["scope"] = scope }
}

/** A builder missing a required setter. */
class BuilderBroken(type: Int) {
    fun build(): FakeRequest = FakeRequest(emptyMap())
}

/** TetheringInterface of Android 12 and later. */
class FakeTetheringInterface(private val type: Int, private val name: String) {
    fun getType(): Int = type

    fun getInterface(): String = name
}

/** TetherStatesParcel of Android 11: String arrays. */
class StatesParcel11 {
    @JvmField var availableList: Array<String>? = arrayOf("eth1")

    @JvmField var tetheredList: Array<String>? = arrayOf("eth0", "rndis0")

    @JvmField var localOnlyList: Array<String>? = emptyArray()

    @JvmField var erroredIfaceList: Array<String>? = arrayOf("eth2")

    @JvmField var lastErrorList: IntArray? = intArrayOf(10)
}

/** TetherStatesParcel of Android 12 and later: TetheringInterface arrays. */
class StatesParcel12 {
    @JvmField var availableList: Array<FakeTetheringInterface>? = emptyArray()

    @JvmField var tetheredList: Array<FakeTetheringInterface>? = emptyArray()

    @JvmField var localOnlyList: Array<FakeTetheringInterface>? = arrayOf(FakeTetheringInterface(5, "eth0"))

    @JvmField var erroredIfaceList: Array<FakeTetheringInterface>? = null

    @JvmField var lastErrorList: IntArray? = null
}

/** TetheringCallbackStartedParcel with its states field. */
class StartedParcel(@JvmField var states: Any?)
