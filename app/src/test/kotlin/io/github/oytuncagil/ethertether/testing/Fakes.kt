// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.testing

import io.github.oytuncagil.ethertether.backend.EngineApi
import io.github.oytuncagil.ethertether.backend.EngineUnavailable
import io.github.oytuncagil.ethertether.data.ProfileStore
import io.github.oytuncagil.ethertether.data.Settings
import io.github.oytuncagil.ethertether.data.SettingsStore
import io.github.oytuncagil.ethertether.engine.EngineNote
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.EngineStep
import io.github.oytuncagil.ethertether.engine.InterfaceInfo
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.engine.RawStep
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A status with Ethernet serving on eth0. */
fun servingStatus(scope: ServingScope = ServingScope.LOCAL) = EngineStatus(
    sdkInt = 36,
    eventsAvailable = true,
    serving = "eth0" to scope,
    error = null,
    available = null,
    interfaces = listOf(InterfaceInfo("eth0", true, true, 1000, listOf("169.254.0.42/16"))),
)

/** A status with the adapter present but not serving. */
fun idleStatus() = EngineStatus(36, true, null, null, "eth0", listOf(InterfaceInfo("eth0", true, false, null, emptyList())))

/** A status without any adapter. */
fun noAdapterStatus() = EngineStatus(36, true, null, null, null, emptyList())

/**
 * An [EngineApi] that serves on start (or waits when [adapterPresent] is false) and pushes the
 * matching status. Tests change its fields to simulate failures.
 */
class FakeEngine : EngineApi {
    val started = mutableListOf<TetherInput>()
    val steps = mutableListOf<Pair<RawStep, TetherInput?>>()
    var stops = 0
    var unavailable: EngineUnavailable? = null
    var startOutcome: Outcome? = null

    /** When set, start waits for it, like a request that takes long or never answers. */
    var startGate: CompletableDeferred<Unit>? = null

    /** When set, start throws it, like an error nobody expected. */
    var startError: RuntimeException? = null
    var adapterPresent = true
    var statuses = ArrayDeque<EngineStatus>()
    var watching = false
    private val pushes = MutableSharedFlow<EngineStatus>(replay = 1, extraBufferCapacity = 16)
    var current: EngineStatus = idleStatus()
        private set

    override val statusUpdates: SharedFlow<EngineStatus> = pushes

    override suspend fun watch() {
        unavailable?.let { throw it }
        watching = true
    }

    /** Pushes [status] as the engine would. */
    fun push(status: EngineStatus) {
        current = status
        pushes.tryEmit(status)
    }

    override suspend fun start(input: TetherInput): EngineReport {
        unavailable?.let { throw it }
        started += input
        startError?.let { throw it }
        startGate?.await()
        startOutcome?.let { return EngineReport(it) }
        if (!adapterPresent) {
            push(noAdapterStatus())
            return EngineReport(Outcome.Waiting)
        }
        val scope = if (input.scope == SharingScope.LOCAL_ONLY) ServingScope.LOCAL else ServingScope.GLOBAL
        push(servingStatus(scope))
        return EngineReport(Outcome.Serving("eth0", scope, listOf("169.254.0.42/16")), setOf(EngineNote.ENTITLEMENT_EXEMPTION_REFUSED), listOf(EngineStep("outcome", "serving")))
    }

    override suspend fun stop(): EngineReport {
        unavailable?.let { throw it }
        stops++
        push(if (adapterPresent) idleStatus() else noAdapterStatus())
        return EngineReport(Outcome.Stopped)
    }

    override suspend fun status(): EngineStatus {
        unavailable?.let { throw it }
        return statuses.removeFirstOrNull() ?: current
    }

    override suspend fun runStep(step: RawStep, input: TetherInput?): List<EngineStep> {
        steps += step to input
        return listOf(EngineStep(step.name, "raw"))
    }
}

/** An in-memory [SettingsStore]. */
class FakeSettings(initial: Settings = Settings(loaded = true)) : SettingsStore {
    private val flow = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = flow

    override suspend fun update(transform: (Settings) -> Settings) = flow.update(transform)
}

/** An in-memory [ProfileStore]. */
class FakeProfiles(initial: ProfileList = ProfileList.initial) : ProfileStore {
    private val flow = MutableStateFlow(initial)
    override val profiles: StateFlow<ProfileList> = flow

    override suspend fun update(transform: (ProfileList) -> ProfileList) = flow.update(transform)
}
