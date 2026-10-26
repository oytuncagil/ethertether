// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

/** Why an engine operation failed. The app maps each to plain language with the next step. */
enum class FailureReason {
    /** startTethering or stopTethering returned a TETHER_ERROR_* code other than 0. */
    RESULT_CODE,

    /** The request was accepted, then the Ethernet interface went to the error list. */
    INTERFACE_ERROR,

    /** The tethering service did not answer in time. */
    NO_RESULT,

    /** A hidden class or method the engine needs does not exist on this device. */
    API_MISSING,

    /** The connection to the tethering service or the engine process died. */
    SERVICE_DIED,

    /** Local-only was asked for on a version without scopes, and fallback was not allowed. */
    SCOPE_UNSUPPORTED,

    /** Anything else; the detail holds the exception text. */
    UNEXPECTED,
}

/** The end state of a start or stop. */
sealed interface Outcome {
    /** Android confirmed that [iface] serves with [scope]; [ipv4] are its addresses if readable. */
    data class Serving(val iface: String, val scope: ServingScope, val ipv4: List<String>) : Outcome

    /**
     * Android accepted the request, but no Ethernet interface serves yet. Usually no adapter is
     * plugged in: Android keeps the request and serves the first adapter that appears.
     */
    data object Waiting : Outcome

    /** No Ethernet interface serves any more. */
    data object Stopped : Outcome

    /** The operation failed; [code] is the TETHER_ERROR_* value when there is one. */
    data class Failed(val reason: FailureReason, val code: Int? = null, val detail: String = "") : Outcome
}

/** Something the user should know about how the result was reached. */
enum class EngineNote {
    /** The phone refused the entitlement exemption (error 14); the request went without it. */
    ENTITLEMENT_EXEMPTION_REFUSED,

    /** Local-only was refused or unsupported; tethering started with internet sharing instead. */
    SCOPE_FALLBACK_USED,

    /** Local-only was requested, but Android reports the interface as sharing internet. */
    LOCAL_SCOPE_IGNORED,

    /** The tethering event callback is unavailable, so the result could not be confirmed. */
    EVENTS_UNAVAILABLE,

    /** Stopping before the start did not complete cleanly; the start went ahead anyway. */
    STOP_BEFORE_START_INCOMPLETE,
}

/** One raw step of an operation, shown on the debug screen and written to the log. */
data class EngineStep(val label: String, val detail: String)

/** The result of a start or stop: the outcome, notes for the user, and every raw step. */
data class EngineReport(
    val outcome: Outcome,
    val notes: Set<EngineNote> = emptySet(),
    val steps: List<EngineStep> = emptyList(),
)

/**
 * The current tethering state of Ethernet.
 *
 * @property sdkInt the device's API level
 * @property eventsAvailable true when the state comes from the tethering event callback
 * @property serving the Ethernet interface serving now and its scope, or null
 * @property error the Ethernet interface in the error list, or null
 * @property available the Ethernet interface tethering could use, or null
 * @property interfaces Ethernet interfaces as the kernel reports them (the fallback source)
 * @property clients IPv4 addresses of connected devices, when Android reports them
 */
data class EngineStatus(
    val sdkInt: Int,
    val eventsAvailable: Boolean,
    val serving: Pair<String, ServingScope>?,
    val error: Pair<String, Int>?,
    val available: String?,
    val interfaces: List<InterfaceInfo>,
    val clients: List<String> = emptyList(),
) {
    /** True when an Ethernet adapter is present, by either source. */
    val adapterPresent: Boolean get() = serving != null || available != null || error != null || interfaces.isNotEmpty()
}
