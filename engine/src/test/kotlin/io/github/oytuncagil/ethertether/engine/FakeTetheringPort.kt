// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

/**
 * A [TetheringPort] that behaves like the tethering service of one Android version shape,
 * following the rules in docs/RESEARCH.md ("Android tethering"). Each test switches on the behaviour it needs.
 */
class FakeTetheringPort(
    override val sdkInt: Int = 36,
    var adapterPresent: Boolean = true,
    var refuseExemption: Boolean = false,
    var refuseLocalScopeWith: Int? = null,
    var startCode: Int = 0,
    var stopCode: Int = 0,
    var interfaceErrorAfterStart: Int? = null,
    var ignoreLocalScope: Boolean = false,
    var answer: Boolean = true,
    var eventsSupported: Boolean = true,
    var startThrows: Exception? = null,
    var slowToServe: Boolean = false,
    initial: TetherStates = TetherStates(),
) : TetheringPort {
    /** "stop", "start(scope=2, exempt=true)" and so on, in call order. */
    val calls = mutableListOf<String>()

    /** Every request startTethering received. */
    val requests = mutableListOf<PortRequest>()

    private var listener: ((TetherStates) -> Unit)? = null
    private var states = initial

    /** Android 11 (API 30) has no setConnectivityScope. */
    override val supportsConnectivityScope: Boolean get() = sdkInt >= TetheringConstants.MIN_SDK_CONNECTIVITY_SCOPE

    private val iface get() = TetherIface("eth0", if (sdkInt >= 31) TetheringConstants.TETHERING_ETHERNET else null)

    override fun startTethering(request: PortRequest, onResult: (Int) -> Unit) {
        startThrows?.let { throw it }
        calls += "start(scope=${request.scope}, exempt=${request.exemptFromEntitlement})"
        requests += request
        if (!answer) return
        val code = when {
            refuseExemption && request.exemptFromEntitlement -> TetherResultCode.NO_CHANGE_TETHERING_PERMISSION.code
            request.scope == TetheringConstants.CONNECTIVITY_SCOPE_LOCAL && refuseLocalScopeWith != null -> refuseLocalScopeWith!!
            sdkInt >= 36 && serving() -> TetherResultCode.DUPLICATE_REQUEST.code
            else -> startCode
        }
        onResult(code)
        if (code == 0 && adapterPresent && !slowToServe) serve(request)
    }

    override fun stopTethering(onResult: (Int) -> Unit) {
        calls += "stop"
        if (!answer) return
        onResult(stopCode)
        if (stopCode == 0) emit(states.copy(tethered = emptyList(), localOnly = emptyList(), available = listOfNotNull(iface.takeIf { adapterPresent })))
    }

    /** How many event callbacks are registered right now; more than one would be a leak. */
    var registered = 0
        private set

    override fun registerEvents(onStates: (TetherStates) -> Unit): AutoCloseable {
        if (!eventsSupported) throw TetheringApiMissing("ITetheringEventCallback\$Stub.TRANSACTION_onCallbackStarted")
        registered++
        listener = onStates
        onStates(states)
        return AutoCloseable {
            registered--
            listener = null
        }
    }

    override fun describe(): List<String> = listOf("fake API $sdkInt")

    override fun describeRequest(request: PortRequest): String = "FakeParcel($request)"

    /** Simulates the adapter being plugged in again: Ethernet is available, not serving. */
    fun plugIn() {
        adapterPresent = true
        emit(TetherStates(available = listOf(iface)))
    }

    /** Simulates the adapter being unplugged: Ethernet leaves every list. */
    fun unplug() {
        adapterPresent = false
        emit(TetherStates())
    }

    private fun serving() = states.ethernetServing(sdkInt) != null

    private fun serve(request: PortRequest) {
        interfaceErrorAfterStart?.let { error ->
            emit(TetherStates(errored = listOf(ErroredIface(iface, error))))
            return
        }
        val local = request.scope == TetheringConstants.CONNECTIVITY_SCOPE_LOCAL && !ignoreLocalScope
        emit(if (local) TetherStates(localOnly = listOf(iface)) else TetherStates(tethered = listOf(iface)))
    }

    private fun emit(newStates: TetherStates) {
        states = newStates
        listener?.invoke(newStates)
    }
}

/**
 * An [InterfaceReader] returning fixed interfaces, or, when it follows a [port], the kernel view
 * of that port's adapter (eth0 while it is plugged in).
 */
class FakeInterfaces(var list: List<InterfaceInfo> = emptyList(), private val port: FakeTetheringPort? = null) : InterfaceReader {
    override fun ethernetInterfaces(sdkInt: Int): List<InterfaceInfo> = when {
        port == null -> list
        port.adapterPresent -> listOf(InterfaceInfo("eth0", true, null, null, emptyList()))
        else -> emptyList()
    }
}
