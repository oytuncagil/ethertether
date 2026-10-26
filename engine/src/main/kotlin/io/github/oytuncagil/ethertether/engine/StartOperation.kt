// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.engine.TetheringConstants.CONNECTIVITY_SCOPE_GLOBAL
import io.github.oytuncagil.ethertether.engine.TetheringConstants.CONNECTIVITY_SCOPE_LOCAL
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherRequest

/** One start: stop first, start with the scope plan and the exemption retry, then confirm. */
internal class StartOperation(
    private val engine: TetheringEngine,
    private val request: TetherRequest,
    private val log: OperationLog,
) {
    private val port = engine.tetheringPort

    suspend fun run(): EngineReport {
        val scopes = scopePlan() ?: return log.report(Outcome.Failed(FailureReason.SCOPE_UNSUPPORTED))
        stopFirst()
        var failure: Outcome = Outcome.Failed(FailureReason.NO_RESULT)
        for ((index, scope) in scopes.withIndex()) {
            if (index > 0) log.note(EngineNote.SCOPE_FALLBACK_USED)
            val before = engine.latestStates?.ethernetError(port.sdkInt)
            val code = startWithExemptionRetry(scope)
            if (code == 0) return log.report(confirm(scope, before))
            failure = if (code == null) Outcome.Failed(FailureReason.NO_RESULT) else Outcome.Failed(FailureReason.RESULT_CODE, code)
            if (code == null || code in NOT_SCOPE_RELATED) break
        }
        return log.report(failure)
    }

    /**
     * The scopes to try in order. Null when local-only is impossible and fallback is not allowed.
     * A null entry means "no scope at all" (Android 11, where Ethernet always shares).
     */
    private fun scopePlan(): List<Int?>? {
        val wantsLocal = request.scope == SharingScope.LOCAL_ONLY
        if (!port.supportsConnectivityScope) {
            if (!wantsLocal) return listOf(null)
            if (!request.allowScopeFallback) return null
            log.note(EngineNote.SCOPE_FALLBACK_USED)
            log.step("scope", "local-only not supported on API ${port.sdkInt}; fallback allowed, starting without scope")
            return listOf(null)
        }
        if (!wantsLocal) return listOf(CONNECTIVITY_SCOPE_GLOBAL)
        return if (request.allowScopeFallback) {
            listOf(CONNECTIVITY_SCOPE_LOCAL, CONNECTIVITY_SCOPE_GLOBAL)
        } else {
            listOf(CONNECTIVITY_SCOPE_LOCAL)
        }
    }

    private suspend fun stopFirst() {
        val code = engine.awaitCode("stopTethering (before start)", log) { port.stopTethering(it) }
        val settled = !engine.eventsAvailable || engine.awaitNotServing(log)
        if (code != 0 || !settled) log.note(EngineNote.STOP_BEFORE_START_INCOMPLETE)
    }

    private suspend fun startWithExemptionRetry(scope: Int?): Int? {
        val code = startOnce(scope, exempt = true)
        if (code != TetherResultCode.NO_CHANGE_TETHERING_PERMISSION.code) return code
        log.note(EngineNote.ENTITLEMENT_EXEMPTION_REFUSED)
        return startOnce(scope, exempt = false)
    }

    private suspend fun startOnce(scope: Int?, exempt: Boolean): Int? {
        val portRequest = PortRequest(request.addresses, scope, exempt)
        val label = "startTethering (scope=${scope ?: "none"}, exempt=$exempt)"
        return engine.awaitCode(label, log) { port.startTethering(portRequest, it) }
    }

    private suspend fun confirm(scope: Int?, staleError: ErroredIface?): Outcome {
        if (!engine.eventsAvailable) return confirmFromInterfaces(scope)
        val sdk = port.sdkInt
        // Without an Ethernet interface Android cannot serve yet: it keeps the request until an
        // adapter appears, and the pushed status shows it then. No need to wait for it here.
        if (engine.readInterfaces().isEmpty() && engine.latestStates?.ethernetServing(sdk) == null) {
            log.step("confirm", "no Ethernet interface; the request waits for the adapter")
            return Outcome.Waiting
        }
        val seen = engine.awaitServingOrError(staleError) ?: engine.latestStates
        val serving = seen?.ethernetServing(sdk)
        val error = seen?.ethernetError(sdk)
        log.step("confirm", seen?.toString() ?: "no tethering state after ${engine.confirmMs} ms")
        return when {
            serving != null -> servingOutcome(serving.first.name, serving.second, scope)
            error != null -> Outcome.Failed(FailureReason.INTERFACE_ERROR, error.error, error.iface.name)
            else -> Outcome.Waiting
        }
    }

    private fun servingOutcome(iface: String, actual: ServingScope, scope: Int?): Outcome {
        if (scope == CONNECTIVITY_SCOPE_LOCAL && actual == ServingScope.GLOBAL) log.note(EngineNote.LOCAL_SCOPE_IGNORED)
        val ipv4 = engine.readInterfaces().firstOrNull { it.name == iface }?.ipv4.orEmpty()
        return Outcome.Serving(iface, actual, ipv4)
    }

    /** Without the event callback, only a static address seen on an Ethernet interface proves success. */
    private fun confirmFromInterfaces(scope: Int?): Outcome {
        log.note(EngineNote.EVENTS_UNAVAILABLE)
        val phone = request.addresses?.let { "${it.phone}/${it.prefixLength}" }
        val match = phone?.let { address -> engine.readInterfaces().firstOrNull { address in it.ipv4 } }
        log.step("confirm (interfaces)", match?.toString() ?: "no Ethernet interface with the phone address")
        if (match == null) return Outcome.Waiting
        val assumed = if (scope == CONNECTIVITY_SCOPE_LOCAL) ServingScope.LOCAL else ServingScope.GLOBAL
        return Outcome.Serving(match.name, assumed, match.ipv4)
    }

    private companion object {
        /** Codes that a different scope cannot fix, so no scope fallback is tried after them. */
        val NOT_SCOPE_RELATED = setOf(
            TetherResultCode.UNSUPPORTED.code,
            TetherResultCode.DHCPSERVER_ERROR.code,
            TetherResultCode.NO_CHANGE_TETHERING_PERMISSION.code,
            TetherResultCode.UNKNOWN_TYPE.code,
            TetherResultCode.DUPLICATE_REQUEST.code,
        )
    }
}
