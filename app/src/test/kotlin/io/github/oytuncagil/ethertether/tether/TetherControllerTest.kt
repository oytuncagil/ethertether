// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.backend.EngineUnavailable
import io.github.oytuncagil.ethertether.backend.UnavailableReason
import io.github.oytuncagil.ethertether.data.EventLog
import io.github.oytuncagil.ethertether.data.Settings
import io.github.oytuncagil.ethertether.engine.FailureReason
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.testing.FakeEngine
import io.github.oytuncagil.ethertether.testing.FakeProfiles
import io.github.oytuncagil.ethertether.testing.FakeSettings
import io.github.oytuncagil.ethertether.testing.idleStatus
import io.github.oytuncagil.ethertether.testing.noAdapterStatus
import io.github.oytuncagil.ethertether.testing.servingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TetherControllerTest {
    private val engine = FakeEngine()
    private val profiles = FakeProfiles()
    private var readiness: EngineReadiness = EngineReadiness.Ready
    private var foregroundStarts = 0
    private val alerts = mutableListOf<WatchdogEvent>()

    private var settingsStore = FakeSettings()

    private fun TestScope.controller(settings: Settings = Settings(loaded = true)) = TetherController(
        engine = engine,
        readinessCheck = { readiness },
        profiles = profiles,
        settings = FakeSettings(settings).also { settingsStore = it },
        log = EventLog(file = null) { 0L },
        foreground = { foregroundStarts++ },
        alerts = { event, _ -> alerts += event },
        scope = backgroundScope,
        restoreWaitMs = 1_000,
    )

    @Test
    fun startServesTheSelectedProfileAndStartsTheForegroundService() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        val state = controller.state.value
        assertTrue(state.serving && state.armed && state.active)
        assertEquals("preset-car", state.profileId)
        assertEquals("169.254.0.42", engine.started.single().phoneAddress)
        assertEquals(1, foregroundStarts)
        assertTrue(engine.watching)
    }

    @Test
    fun startWithoutAdapterIsArmedAndWaiting() = runTest {
        engine.adapterPresent = false
        val controller = controller()
        controller.start()
        runCurrent()
        assertTrue(controller.state.value.waitingForAdapter)
    }

    @Test
    fun startIsRefusedWhenShizukuIsNotReady() = runTest {
        readiness = EngineReadiness.ShizukuNotRunning
        val controller = controller()
        controller.start()
        assertTrue(engine.started.isEmpty())
        assertEquals(0, foregroundStarts)
        assertEquals(EngineReadiness.ShizukuNotRunning, controller.state.value.readiness)
    }

    @Test
    fun failedStartIsNotArmed() = runTest {
        engine.startOutcome = Outcome.Failed(FailureReason.RESULT_CODE, 12)
        val controller = controller()
        controller.start()
        assertFalse(controller.state.value.armed)
        assertEquals(Outcome.Failed(FailureReason.RESULT_CODE, 12), controller.state.value.lastReport?.outcome)
    }

    @Test
    fun unreachableEngineIsReported() = runTest {
        engine.unavailable = EngineUnavailable(UnavailableReason.BIND_TIMEOUT)
        val controller = controller()
        controller.start()
        assertEquals(UnavailableReason.BIND_TIMEOUT, controller.state.value.lastError?.reason)
        assertFalse(controller.state.value.armed)
    }

    @Test
    fun stopDisarmsWithoutAWatchdogAlert() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        controller.stop()
        runCurrent()
        assertFalse(controller.state.value.active)
        assertTrue(alerts.isEmpty())
        assertNull(controller.state.value.event)
    }

    @Test
    fun stoppedOutsideIsReportedAndDisarms() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        engine.push(idleStatus())
        runCurrent()
        assertEquals(WatchdogEvent.STOPPED_OUTSIDE, controller.state.value.event)
        assertEquals(listOf(WatchdogEvent.STOPPED_OUTSIDE), alerts)
        assertFalse(controller.state.value.armed)
    }

    @Test
    fun adapterRemovedWithoutKeepArmedIsReported() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        engine.push(noAdapterStatus())
        runCurrent()
        assertEquals(WatchdogEvent.ADAPTER_REMOVED, controller.state.value.event)
        assertFalse(controller.state.value.active)
        assertEquals(1, engine.started.size)
    }

    @Test
    fun adapterRemovedWithKeepArmedArmsAgainSilently() = runTest {
        val controller = controller(Settings(loaded = true, keepArmed = true))
        controller.start()
        runCurrent()
        engine.adapterPresent = false
        engine.push(noAdapterStatus())
        runCurrent()
        assertEquals(2, engine.started.size)
        assertTrue(controller.state.value.waitingForAdapter)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun rearmRetriesWithPausesThenGivesUp() = runTest {
        val controller = controller(Settings(loaded = true, keepArmed = true))
        controller.start()
        runCurrent()
        engine.startOutcome = Outcome.Failed(FailureReason.NO_RESULT)
        engine.push(noAdapterStatus())
        runCurrent()
        assertEquals(2, engine.started.size)
        assertTrue("the service must stay up while re-arming", controller.state.value.active)
        advanceTimeBy(2_001)
        assertEquals(3, engine.started.size)
        advanceTimeBy(4_001)
        assertEquals(4, engine.started.size)
        advanceTimeBy(8_001)
        assertEquals(WatchdogEvent.REARM_FAILED, controller.state.value.event)
        assertFalse(controller.state.value.active)
    }

    @Test
    fun stopDuringRearmEndsTheRetries() = runTest {
        val controller = controller(Settings(loaded = true, keepArmed = true))
        controller.start()
        runCurrent()
        engine.startOutcome = Outcome.Failed(FailureReason.NO_RESULT)
        engine.push(noAdapterStatus())
        runCurrent()
        controller.stop()
        advanceTimeBy(20_000)
        assertEquals(2, engine.started.size)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun alertsCanBeTurnedOff() = runTest {
        val controller = controller(Settings(loaded = true, alertOnExternalStop = false))
        controller.start()
        runCurrent()
        engine.push(idleStatus())
        runCurrent()
        assertEquals(WatchdogEvent.STOPPED_OUTSIDE, controller.state.value.event)
        assertTrue(alerts.isEmpty())
        controller.dismissEvent()
        assertNull(controller.state.value.event)
    }

    @Test
    fun toggleStartsThenStops() = runTest {
        val controller = controller()
        controller.toggle()
        runCurrent()
        assertTrue(controller.state.value.serving)
        controller.toggle()
        runCurrent()
        assertFalse(controller.state.value.active)
        assertEquals(1, engine.stops)
    }

    @Test
    fun startOfAnotherProfileRunsThatProfile() = runTest {
        val controller = controller()
        controller.start("preset-lab")
        assertEquals("192.168.50.1", engine.started.single().phoneAddress)
        assertEquals("preset-lab", controller.state.value.profileId)
    }

    @Test
    fun aCancelledStartNeverStaysStarting() = runTest {
        engine.startGate = CompletableDeferred()
        val controller = controller()
        val start = backgroundScope.launch { controller.start() }
        runCurrent()
        assertEquals(Operation.STARTING, controller.state.value.operation)
        start.cancel()
        runCurrent()
        assertNull(controller.state.value.operation)
        assertFalse(controller.state.value.armed)
    }

    @Test
    fun stopInterruptsAStartInProgress() = runTest {
        engine.startGate = CompletableDeferred()
        val controller = controller()
        val start = backgroundScope.launch { controller.start() }
        runCurrent()
        controller.stop()
        runCurrent()
        assertTrue("the interrupted start returns normally", start.isCompleted && !start.isCancelled)
        assertEquals(1, engine.stops)
        assertNull(controller.state.value.operation)
        assertFalse(controller.state.value.active)
    }

    @Test
    fun anUnexpectedErrorEndsTheOperationAndIsReported() = runTest {
        engine.startError = IllegalStateException("boom")
        val controller = controller()
        controller.start()
        assertNull(controller.state.value.operation)
        assertFalse(controller.state.value.armed)
        assertEquals(UnavailableReason.DIED, controller.state.value.lastError?.reason)
    }

    @Test
    fun losingShizukuWhileServingMakesTheStateUnknown() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        readiness = EngineReadiness.ShizukuNotRunning
        controller.onShizukuLost()
        val state = controller.state.value
        assertTrue(state.statusUnknown)
        assertNull(state.status)
        assertFalse(state.active)
        assertEquals(EngineReadiness.ShizukuNotRunning, state.readiness)
        readiness = EngineReadiness.Ready
        controller.refreshStatus()
        assertFalse("a status read again ends the unknown state", controller.state.value.statusUnknown)
    }

    @Test
    fun stopWithShizukuGoneMakesTheStateUnknown() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        readiness = EngineReadiness.ShizukuNotRunning
        engine.unavailable = EngineUnavailable(UnavailableReason.BIND_FAILED, "binder haven't been received")
        controller.stop()
        assertTrue(controller.state.value.statusUnknown)
        assertNull(controller.state.value.operation)
    }

    @Test
    fun losingShizukuWhileOffChangesNothing() = runTest {
        val controller = controller()
        readiness = EngineReadiness.ShizukuNotRunning
        controller.onShizukuLost()
        assertFalse(controller.state.value.statusUnknown)
    }

    @Test
    fun theArmedProfileIsSavedAndClearedOnStop() = runTest {
        val controller = controller()
        controller.start("preset-lab")
        runCurrent()
        assertEquals("preset-lab", settingsStore.settings.value.armedProfileId)
        controller.stop()
        runCurrent()
        assertNull(settingsStore.settings.value.armedProfileId)
    }

    @Test
    fun afterTheAppEndedAServingProfileIsWatchedAgainWithoutARestart() = runTest {
        engine.push(servingStatus())
        val controller = controller(Settings(loaded = true, armedProfileId = "preset-car"))
        runCurrent()
        val state = controller.state.value
        assertTrue(state.armed && state.serving)
        assertEquals("preset-car", state.profileId)
        assertTrue("serving is only watched, never restarted", engine.started.isEmpty())
        assertEquals(1, foregroundStarts)
    }

    @Test
    fun afterTheAppEndedAnArmedProfileThatNoLongerServesIsArmedAgain() = runTest {
        engine.adapterPresent = false
        engine.push(noAdapterStatus())
        val controller = controller(Settings(loaded = true, armedProfileId = "preset-car"))
        runCurrent()
        assertEquals(1, engine.started.size)
        assertTrue(controller.state.value.waitingForAdapter)
    }

    @Test
    fun noRestoreWithoutShizukuAndTheSavedProfileIsForgotten() = runTest {
        readiness = EngineReadiness.ShizukuNotRunning
        val controller = controller(Settings(loaded = true, armedProfileId = "preset-car"))
        advanceTimeBy(2_000)
        runCurrent()
        assertFalse(controller.state.value.armed)
        assertTrue(engine.started.isEmpty())
        assertNull(settingsStore.settings.value.armedProfileId)
    }

    @Test
    fun aDeletedProfileIsNotRestored() = runTest {
        val controller = controller(Settings(loaded = true, armedProfileId = "gone"))
        runCurrent()
        assertFalse(controller.state.value.armed)
        assertNull(settingsStore.settings.value.armedProfileId)
    }

    @Test
    fun afterARestartTheStartWaitsForShizukuWithoutPolling() = runTest {
        readiness = EngineReadiness.ShizukuNotRunning
        val controller = controller()
        val shizukuChanges = MutableStateFlow(0)
        val boot = async { controller.startAfterBoot(shizukuChanges, waitMs = 60_000) }
        advanceTimeBy(30_000)
        assertTrue(engine.started.isEmpty())
        readiness = EngineReadiness.Ready
        shizukuChanges.value = 1
        runCurrent()
        assertTrue(boot.await())
        assertTrue(controller.state.value.serving)
    }

    @Test
    fun afterARestartNothingStartsWhenShizukuNeverComes() = runTest {
        readiness = EngineReadiness.ShizukuNotRunning
        val controller = controller()
        val boot = async { controller.startAfterBoot(MutableStateFlow(0), waitMs = 60_000) }
        advanceTimeBy(61_000)
        assertFalse(boot.await())
        assertTrue(engine.started.isEmpty())
        assertFalse(controller.state.value.active)
    }

    @Test
    fun manyTogglesFromEverySurfaceNeverLeaveTheControllerBusy() = runTest {
        val controller = controller(Settings(loaded = true, keepArmed = true))
        val random = kotlin.random.Random(7)
        val jobs = (1..80).map { index ->
            backgroundScope.launch {
                when (random.nextInt(5)) {
                    0 -> controller.start()
                    1 -> controller.stop()
                    2 -> controller.toggle()
                    3 -> engine.push(if (random.nextBoolean()) noAdapterStatus() else idleStatus())
                    else -> controller.refreshStatus()
                }
            }.also { if (index % 10 == 0) runCurrent() }
        }
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(jobs.all { it.isCompleted })
        assertNull("never left busy", controller.state.value.operation)
        controller.stop()
        advanceTimeBy(60_000)
        runCurrent()
        val end = controller.state.value
        assertFalse("a final Stop leaves nothing serving, armed or re-arming", end.active)
        assertNull(settingsStore.settings.value.armedProfileId)
    }
}
