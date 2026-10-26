// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.data.EventLog
import io.github.oytuncagil.ethertether.tether.TetherController
import io.github.oytuncagil.ethertether.tether.WatchdogEvent
import io.github.oytuncagil.ethertether.testing.FakeEngine
import io.github.oytuncagil.ethertether.testing.FakeProfiles
import io.github.oytuncagil.ethertether.testing.FakeSettings
import io.github.oytuncagil.ethertether.testing.idleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private val profiles = FakeProfiles()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): HomeViewModel {
        val controller = TetherController(
            engine = engine,
            readinessCheck = { EngineReadiness.Ready },
            profiles = profiles,
            settings = FakeSettings(),
            log = EventLog(file = null) { 0L },
            foreground = {},
            alerts = { _, _ -> },
            scope = backgroundScope,
        )
        return HomeViewModel(controller, profiles)
    }

    @Test
    fun startAndStopReachTheEngineAndTheStateFollows() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.tether.serving)
        viewModel.stop()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.tether.active)
        assertEquals(1, engine.started.size)
        assertEquals(1, engine.stops)
    }

    @Test
    fun selectChangesTheProfileStartUses() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.select("preset-laptop")
        advanceUntilIdle()
        assertEquals("preset-laptop", viewModel.state.value.profiles.selected?.id)
        viewModel.start()
        advanceUntilIdle()
        assertEquals("preset-laptop", viewModel.state.value.tether.profileId)
    }

    @Test
    fun refreshReadsReadinessAndStatus() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(EngineReadiness.Ready, viewModel.state.value.tether.readiness)
        assertEquals(idleStatus(), viewModel.state.value.tether.status)
    }

    @Test
    fun dismissClearsTheWatchdogMessage() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()
        engine.push(idleStatus())
        // The controller collects pushes in the background scope, which advanceUntilIdle skips.
        runCurrent()
        advanceUntilIdle()
        assertEquals(WatchdogEvent.STOPPED_OUTSIDE, viewModel.state.value.tether.event)
        viewModel.dismissEvent()
        advanceUntilIdle()
        assertNull(viewModel.state.value.tether.event)
    }
}
