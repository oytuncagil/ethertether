// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.model.TetherRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long the engine waits, in milliseconds.
 *
 * @property resultMs for the result code of one startTethering or stopTethering call
 * @property settleMs for Ethernet to leave the serving lists after a stop
 * @property confirmMs for Ethernet to serve, or fail, after an accepted start
 * @property firstStateMs for the initial state after the event callback was registered
 */
data class EngineTimeouts(
    val resultMs: Long = 5_000,
    val settleMs: Long = 4_000,
    val confirmMs: Long = 10_000,
    val firstStateMs: Long = 1_000,
)

/**
 * Starts, stops and reads Ethernet tethering through a [TetheringPort], following the rules
 * taken from the Android source: stop before every start, entitlement exemption
 * first with one retry without it on error 14, result 0 only means accepted, success is
 * confirmed through the tethering state. Operations run one at a time.
 */
class TetheringEngine(
    private val port: TetheringPort,
    private val interfaces: InterfaceReader,
    private val timeouts: EngineTimeouts = EngineTimeouts(),
) : AutoCloseable {
    private val mutableStates = MutableStateFlow<TetherStates?>(null)
    private val mutex = Mutex()

    /** Counts stop requests, so a stop ends the confirmation wait of a start in progress. */
    private val stopRequests = MutableStateFlow(0)
    private var registration: AutoCloseable? = null

    /** The latest state from the tethering event callback, null until the first event. */
    val states: StateFlow<TetherStates?> = mutableStates.asStateFlow()

    /** True when the tethering event callback is registered. */
    val eventsAvailable: Boolean @Synchronized get() = registration != null

    /** The device's API level. */
    val sdkInt: Int get() = port.sdkInt

    /** Registers the event callback if needed. Never throws; returns what happened as a step. */
    @Synchronized
    fun attachEvents(): EngineStep {
        if (registration != null) return EngineStep("registerTetheringEventCallback", "already registered")
        return try {
            registration = port.registerEvents { mutableStates.value = it }
            EngineStep("registerTetheringEventCallback", "registered")
        } catch (e: Exception) {
            // Some vendor frameworks fail inside the registration; the engine goes on without it.
            EngineStep("registerTetheringEventCallback", "unavailable: ${e.describe()}")
        }
    }

    /** Stops any Ethernet tethering, then starts it with [request] and confirms the outcome. */
    suspend fun start(request: TetherRequest): EngineReport = mutex.withLock {
        val log = OperationLog()
        log.step("attach", attachEvents().detail)
        try {
            StartOperation(this, request, log).run()
        } catch (e: Exception) {
            log.report(e.toFailure())
        }
    }

    /** Stops Ethernet tethering and waits until Android reports it stopped. */
    suspend fun stop(): EngineReport {
        // A start may be waiting for its confirmation; it gives up at once instead of holding the lock.
        stopRequests.value++
        return mutex.withLock { stopLocked() }
    }

    private suspend fun stopLocked(): EngineReport {
        val log = OperationLog()
        log.step("attach", attachEvents().detail)
        try {
            val code = awaitCode("stopTethering", log) { port.stopTethering(it) }
            val outcome = when {
                code == null -> Outcome.Failed(FailureReason.NO_RESULT)
                code != 0 -> Outcome.Failed(FailureReason.RESULT_CODE, code)
                !eventsAvailable -> Outcome.Stopped.also { log.note(EngineNote.EVENTS_UNAVAILABLE) }
                awaitNotServing(log) -> Outcome.Stopped
                else -> Outcome.Failed(FailureReason.NO_RESULT, detail = "still serving")
            }
            return log.report(outcome)
        } catch (e: Exception) {
            return log.report(e.toFailure())
        }
    }

    /**
     * The current Ethernet tethering state from both sources. Never throws. Right after the
     * callback was registered, it waits briefly for Android's initial state.
     */
    suspend fun status(): EngineStatus {
        attachEvents()
        val sdk = port.sdkInt
        if (eventsAvailable && mutableStates.value == null) {
            withTimeoutOrNull(timeouts.firstStateMs) { mutableStates.filterNotNull().first() }
        }
        val snapshot = mutableStates.value
        return EngineStatus(
            sdkInt = sdk,
            eventsAvailable = eventsAvailable,
            serving = snapshot?.ethernetServing(sdk)?.let { (iface, scope) -> iface.name to scope },
            error = snapshot?.ethernetError(sdk)?.let { it.iface.name to it.error },
            available = snapshot?.ethernetAvailable(sdk)?.name,
            interfaces = readInterfaces(),
            clients = if (snapshot?.ethernetServing(sdk) != null) snapshot.ethernetClients else emptyList(),
        )
    }

    /** Unregisters the event callback. */
    @Synchronized
    override fun close() {
        runCatching { registration?.close() }
        registration = null
    }

    /** Kernel view of the Ethernet interfaces; empty when unreadable. */
    internal fun readInterfaces(): List<InterfaceInfo> = runCatching { interfaces.ethernetInterfaces(port.sdkInt) }.getOrDefault(emptyList())

    /** Calls the port, waits for its result code and records it. Null on timeout. */
    internal suspend fun awaitCode(label: String, log: OperationLog, call: ((Int) -> Unit) -> Unit): Int? {
        val result = CompletableDeferred<Int>()
        call { result.complete(it) }
        val code = withTimeoutOrNull(timeouts.resultMs) { result.await() }
        log.step(label, code?.let(::describeCode) ?: "no result after ${timeouts.resultMs} ms")
        return code
    }

    /** Waits until no Ethernet interface serves. True when that happened in time. */
    internal suspend fun awaitNotServing(log: OperationLog): Boolean {
        val sdk = port.sdkInt
        val done = withTimeoutOrNull(timeouts.settleMs) {
            mutableStates.filterNotNull().first { it.ethernetServing(sdk) == null }
        } != null
        log.step("wait for stop", if (done) "Ethernet not serving" else "still serving after ${timeouts.settleMs} ms")
        return done
    }

    /**
     * Waits for Ethernet to serve or to show an error other than [staleError]. Null on timeout,
     * or at once when a stop is requested meanwhile.
     */
    internal suspend fun awaitServingOrError(staleError: ErroredIface?): TetherStates? {
        val sdk = port.sdkInt
        val stopsBefore = stopRequests.value
        return withTimeoutOrNull(timeouts.confirmMs) {
            combine(mutableStates, stopRequests) { states, stops -> states to stops }
                .first { (states, stops) ->
                    stops != stopsBefore || (states != null && (states.ethernetServing(sdk) != null || states.ethernetError(sdk)?.takeIf { it != staleError } != null))
                }
                .first
        }
    }

    /** The port, for [StartOperation]. */
    internal val tetheringPort: TetheringPort get() = port

    /** The latest state, for [StartOperation]. */
    internal val latestStates: TetherStates? get() = mutableStates.value

    /** The confirmation timeout, for messages. */
    internal val confirmMs: Long get() = timeouts.confirmMs
}
