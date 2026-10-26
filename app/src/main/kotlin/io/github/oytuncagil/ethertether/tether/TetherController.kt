// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import io.github.oytuncagil.ethertether.backend.EngineApi
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.backend.EngineUnavailable
import io.github.oytuncagil.ethertether.backend.UnavailableReason
import io.github.oytuncagil.ethertether.data.EventLog
import io.github.oytuncagil.ethertether.data.LogLevel
import io.github.oytuncagil.ethertether.data.ProfileStore
import io.github.oytuncagil.ethertether.data.SettingsStore
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.model.Profile
import io.github.oytuncagil.ethertether.model.ValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps the foreground service running while tethering is active. */
fun interface ForegroundControl {
    /** Starts the foreground service if it is not running; it stops itself when tethering is inactive. */
    fun ensureRunning()
}

/** Tells the user about watchdog events outside the app. */
fun interface Alerts {
    /** Shows [event] for the profile with [profileId]. */
    fun show(event: WatchdogEvent, profileId: String?)
}

/**
 * The one place that starts and stops tethering for the app, the notification, the tile, the
 * widget and the boot receiver. It keeps the request armed when the user wants that and watches
 * for tethering that stops without EtherTether (the watchdog). Operations run one at a time.
 */
class TetherController(
    private val engine: EngineApi,
    private val readinessCheck: () -> EngineReadiness,
    private val profiles: ProfileStore,
    private val settings: SettingsStore,
    private val log: EventLog,
    private val foreground: ForegroundControl,
    private val alerts: Alerts,
    private val scope: CoroutineScope,
    private val rearmDelaysMs: List<Long> = listOf(2_000, 4_000, 8_000),
    private val restoreWaitMs: Long = 10_000,
) {
    private val mutableState = MutableStateFlow(TetherState())
    private val mutex = Mutex()
    private var watching = false

    /** Changes with every start and stop by the user, so a running re-arm knows it is outdated. */
    @Volatile
    private var generation = 0

    /** The start in progress, so Stop can interrupt it instead of waiting for it. */
    @Volatile
    private var runningStart: Job? = null

    /** The current state. */
    val state: StateFlow<TetherState> = mutableState.asStateFlow()

    init {
        scope.launch { engine.statusUpdates.collect(::onStatus) }
        scope.launch {
            restoreArmed()
            rememberArmed()
        }
    }

    /** Checks Shizuku again; returns the result. Call when Shizuku's binder came or went. */
    fun refreshReadiness(): EngineReadiness {
        val readiness = readinessCheck()
        mutableState.update { it.copy(readiness = readiness) }
        return readiness
    }

    /**
     * After a reboot Shizuku may need a while (or the user) to start: waits up to [waitMs] for it,
     * checking again on every change in [shizukuChanges] (no polling), then starts. False when
     * Shizuku did not become ready in time.
     */
    suspend fun startAfterBoot(shizukuChanges: Flow<Int>, waitMs: Long): Boolean {
        log.info(TAG, "boot: waiting for Shizuku")
        val ready = withTimeoutOrNull(waitMs) { shizukuChanges.first { refreshReadiness() == EngineReadiness.Ready } }
        if (ready == null) {
            log.warn(TAG, "boot: Shizuku did not become ready; not starting")
            return false
        }
        start()
        return true
    }

    /** Starts the profile with [profileId], or the selected profile. */
    suspend fun start(profileId: String? = null) {
        val list = profiles.profiles.value
        val profile = profileId?.let(list::find) ?: list.selected ?: return
        mutex.withLock {
            generation++
            interruptibleStart(profile, reason = "start")
        }
    }

    /** Stops tethering and disarms. A start in progress is interrupted first. */
    suspend fun stop() {
        generation++
        runningStart?.let {
            log.info(TAG, "stop: interrupting the start in progress")
            it.cancel()
        }
        mutex.withLock {
            mutableState.update { it.copy(armed = false, operation = Operation.STOPPING) }
            var report: EngineReport? = null
            try {
                report = call { engine.stop() }
            } finally {
                mutableState.update { it.copy(operation = null, lastReport = report ?: it.lastReport) }
            }
            log.info(TAG, "stop: ${report?.outcome ?: "engine unavailable"}")
            readStatus(clearError = false)
        }
    }

    /** Shizuku's binder died: re-check it, and stop trusting a state that can no longer be read. */
    fun onShizukuLost() {
        refreshReadiness()
        markUnknown()
    }

    /** Starts when off, stops when serving or armed: the tile and widget action. */
    suspend fun toggle() {
        if (state.value.active) stop() else start()
    }

    /** Reads the status once, for example when a screen opens. */
    suspend fun refreshStatus() = readStatus(clearError = true)

    /** Reads the status after the adapter may have changed, keeping a visible error. */
    suspend fun refreshAdapter() = readStatus(clearError = false)

    /** Reads the status; after a start or stop, [clearError] is false so its error stays visible. */
    private suspend fun readStatus(clearError: Boolean) {
        if (refreshReadiness() != EngineReadiness.Ready) return
        call(clearError) { engine.status() }?.let(::onStatus)
    }

    /** Forgets the last watchdog event, after the user saw it. */
    fun dismissEvent() = mutableState.update { it.copy(event = null) }

    /** [startLocked] in its own job, so [stop] can cancel it; false when it was interrupted. */
    private suspend fun interruptibleStart(profile: Profile, reason: String): Boolean = coroutineScope {
        val job = async { startLocked(profile, reason) }
        runningStart = job
        try {
            job.await()
        } catch (e: CancellationException) {
            // Interrupted by Stop: carry on unless the caller itself was cancelled.
            if (!isActive) throw e
            false
        } finally {
            runningStart = null
        }
    }

    /** Starts [profile]; true when Android accepted it (serving or waiting for the adapter). */
    private suspend fun startLocked(profile: Profile, reason: String): Boolean {
        if (refreshReadiness() != EngineReadiness.Ready) {
            log.warn(TAG, "$reason: Shizuku not ready (${state.value.readiness})")
            return false
        }
        if (profile.validate() !is ValidationResult.Valid) {
            log.warn(TAG, "$reason: profile ${profile.id} is not valid")
            return false
        }
        foreground.ensureRunning()
        mutableState.update { it.copy(operation = Operation.STARTING, event = null, profileId = profile.id) }
        var report: EngineReport? = null
        try {
            report = call { engine.start(profile.input) }
        } finally {
            // Whatever happened (an answer, an error, a time limit, an interruption), the app is
            // never left "starting". One update, so the service never sees an idle moment between.
            mutableState.update { it.copy(operation = null, armed = report.accepted(), lastReport = report ?: it.lastReport) }
        }
        val ok = report.accepted()
        log.add(if (ok) LogLevel.INFO else LogLevel.ERROR, TAG, "$reason ${profile.id}: ${report?.outcome ?: "engine unavailable"} ${report?.notes.orEmpty()}")
        report?.steps?.forEach { log.info(TAG_ENGINE, "${it.label}: ${it.detail}") }
        readStatus(clearError = false)
        return ok
    }

    private suspend fun <T> call(clearError: Boolean = true, block: suspend () -> T): T? = try {
        if (!watching) {
            engine.watch()
            watching = true
        }
        block().also { if (clearError) mutableState.update { it.copy(lastError = null) } }
    } catch (e: EngineUnavailable) {
        log.error(TAG, "engine unavailable: ${e.reason} ${e.detail}")
        mutableState.update { it.copy(lastError = e) }
        if (refreshReadiness() != EngineReadiness.Ready) markUnknown()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        // Not expected; reported like a lost engine instead of ending the operation half way.
        log.error(TAG, "unexpected error: $e")
        mutableState.update { it.copy(lastError = EngineUnavailable(UnavailableReason.DIED, e.toString())) }
        null
    }

    /** After Shizuku stopped, a serving or armed state can no longer be read or changed. */
    private fun markUnknown() {
        val current = mutableState.value
        if (!current.serving && !current.armed && !current.rearming && !current.statusUnknown) return
        log.warn(TAG, "Shizuku stopped: the tethering state is unknown until it runs again")
        generation++
        runningStart?.cancel()
        mutableState.update { it.copy(status = null, armed = false, statusUnknown = true) }
    }

    /** Applies a pushed or read status and runs the watchdog. */
    internal fun onStatus(status: EngineStatus) {
        val before = mutableState.value
        mutableState.update { it.copy(status = status, statusUnknown = false) }
        val stopped = before.status?.serving != null && status.serving == null
        if (!before.armed || before.operation != null || !stopped) return
        if (status.adapterPresent) {
            raise(WatchdogEvent.STOPPED_OUTSIDE, before.profileId)
            mutableState.update { it.copy(armed = false) }
            return
        }
        if (settings.settings.value.keepArmed) {
            log.info(TAG, "adapter removed; keep armed is on, arming again")
            val token = generation
            scope.launch { rearm(before.profileId, token) }
        } else {
            raise(WatchdogEvent.ADAPTER_REMOVED, before.profileId)
            mutableState.update { it.copy(armed = false) }
        }
    }

    /**
     * Android may end the app process while tethering is armed (vendor battery rules, a swipe
     * from recent apps). Android's request lives on, but EtherTether forgot it. When the app
     * starts again, the saved profile is armed again: serving is only watched; a request that
     * no longer serves is sent again, so keep armed and the notification work as before.
     */
    private suspend fun restoreArmed() {
        val saved = settings.settings.first { it.loaded }.armedProfileId ?: return
        val profile = profiles.stored().find(saved)
        if (profile == null || !awaitReady()) {
            log.warn(TAG, "restore: not restoring $saved, ${if (profile == null) "profile gone" else "Shizuku not ready"}")
            settings.update { it.copy(armedProfileId = null) }
            return
        }
        mutex.withLock {
            val status = call { engine.status() }
            if (status?.serving != null) {
                log.info(TAG, "restore: ${profile.id} still serving, watching it again")
                onStatus(status)
                mutableState.update { it.copy(armed = true, profileId = profile.id) }
                foreground.ensureRunning()
            } else {
                log.info(TAG, "restore: ${profile.id} armed before the app ended, arming again")
                generation++
                interruptibleStart(profile, reason = "restore")
            }
        }
    }

    /** Shizuku's binder arrives shortly after the app starts; waits for it up to [restoreWaitMs]. */
    private suspend fun awaitReady(): Boolean {
        repeat((restoreWaitMs / READY_STEP_MS).toInt()) {
            if (refreshReadiness() == EngineReadiness.Ready) return true
            delay(READY_STEP_MS)
        }
        return refreshReadiness() == EngineReadiness.Ready
    }

    /** Saves the armed profile whenever it changes, for [restoreArmed]. */
    private suspend fun rememberArmed() {
        state.map { if (it.armed || it.rearming) it.profileId else null }
            .distinctUntilChanged()
            .collect { id -> settings.update { it.copy(armedProfileId = id) } }
    }

    /** Starts [profileId] again, with growing pauses, until it is accepted or [token] is outdated. */
    private suspend fun rearm(profileId: String?, token: Int) {
        val profile = profileId?.let(profiles.profiles.value::find)
        mutableState.update { it.copy(rearming = true) }
        try {
            rearmAttempts(profile, profileId, token)
        } finally {
            mutableState.update { it.copy(rearming = false) }
        }
    }

    private suspend fun rearmAttempts(profile: Profile?, profileId: String?, token: Int) {
        for (wait in rearmDelaysMs) {
            if (profile == null) break
            val accepted = mutex.withLock {
                if (token != generation) return
                interruptibleStart(profile, reason = "re-arm")
            }
            if (accepted) return
            delay(wait)
        }
        mutex.withLock {
            if (token != generation) return
            mutableState.update { it.copy(armed = false) }
        }
        raise(WatchdogEvent.REARM_FAILED, profileId)
    }

    private fun raise(event: WatchdogEvent, profileId: String?) {
        log.warn(TAG, "watchdog: $event")
        mutableState.update { it.copy(event = event) }
        if (settings.settings.value.alertOnExternalStop) alerts.show(event, profileId)
    }

    private companion object {
        const val TAG = "tether"
        const val TAG_ENGINE = "engine"
        const val READY_STEP_MS = 500L
    }
}

/** True when the last report ended in [Outcome.Waiting] or [Outcome.Serving]. */
fun EngineReport?.accepted(): Boolean = this?.outcome is Outcome.Serving || this?.outcome == Outcome.Waiting
