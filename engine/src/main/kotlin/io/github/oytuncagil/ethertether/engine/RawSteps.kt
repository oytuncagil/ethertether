// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherRequest

/** The single steps the debug screen can run, each showing its raw result. */
enum class RawStep {
    /** What was found on this device: API level, connector methods, Builder setters, codes. */
    DIAGNOSE,

    /** Registers the event callback and shows the latest tethering state. */
    READ_STATES,

    /** Ethernet interfaces as the kernel reports them. */
    READ_INTERFACES,

    /** Builds the request parcel with the device's Builder and shows it; sends nothing. */
    BUILD_REQUEST,

    /** Sends stopTethering only. */
    STOP_ONLY,

    /** Sends startTethering only, with the exemption, without stopping first. */
    START_ONLY,

    /** Sends startTethering only, without the exemption, without stopping first. */
    START_ONLY_NO_EXEMPTION,
}

/** Runs one [RawStep] against [engine]. Never throws; failures are steps too. */
class RawSteps(private val engine: TetheringEngine) {
    private val port get() = engine.tetheringPort

    /** Runs [step]; [request] is needed for the build and start steps. */
    suspend fun run(step: RawStep, request: TetherRequest?): List<EngineStep> {
        val log = OperationLog()
        try {
            when (step) {
                RawStep.DIAGNOSE -> port.describe().forEach { log.step("diagnose", it) }
                RawStep.READ_STATES -> {
                    log.step("attach", engine.attachEvents().detail)
                    log.step("states", engine.states.value?.toString() ?: "no state received yet")
                }
                RawStep.READ_INTERFACES -> readInterfaces(log)
                RawStep.BUILD_REQUEST -> log.step("parcel", port.describeRequest(portRequest(request, exempt = true)))
                RawStep.STOP_ONLY -> engine.awaitCode("stopTethering", log) { port.stopTethering(it) }
                RawStep.START_ONLY -> startOnly(request, exempt = true, log)
                RawStep.START_ONLY_NO_EXEMPTION -> startOnly(request, exempt = false, log)
            }
        } catch (e: Exception) {
            log.step("error", e.describe())
        }
        return log.steps()
    }

    private fun readInterfaces(log: OperationLog) {
        val list = engine.readInterfaces()
        if (list.isEmpty()) log.step("interfaces", "no Ethernet interface")
        list.forEach { log.step("interface", it.toString()) }
    }

    private suspend fun startOnly(request: TetherRequest?, exempt: Boolean, log: OperationLog) {
        val portRequest = portRequest(request, exempt)
        engine.awaitCode("startTethering (scope=${portRequest.scope ?: "none"}, exempt=$exempt)", log) {
            port.startTethering(portRequest, it)
        }
    }

    private fun portRequest(request: TetherRequest?, exempt: Boolean): PortRequest {
        requireNotNull(request) { "This step needs a valid request" }
        val scope = when {
            !port.supportsConnectivityScope -> null
            request.scope == SharingScope.LOCAL_ONLY -> TetheringConstants.CONNECTIVITY_SCOPE_LOCAL
            else -> TetheringConstants.CONNECTIVITY_SCOPE_GLOBAL
        }
        return PortRequest(request.addresses, scope, exempt)
    }
}
