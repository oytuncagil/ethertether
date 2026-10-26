// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.debug

import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineUnavailable
import io.github.oytuncagil.ethertether.backend.UnavailableReason
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.EngineStep
import io.github.oytuncagil.ethertether.engine.InterfaceInfo
import io.github.oytuncagil.ethertether.engine.RawStep
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.model.ValidationResult
import io.github.oytuncagil.ethertether.testing.FakeEngine
import io.github.oytuncagil.ethertether.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class DebugViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private val viewModel by lazy { DebugViewModel(engine) { "12:00:00" } }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsWithAValidLinkLocalRequest() {
        assertTrue(viewModel.state.value.validation is ValidationResult.Valid)
        assertEquals("169.254.0.42", viewModel.state.value.input.phoneAddress)
    }

    @Test
    fun startRecordsMessageNotesAndRawSteps() = runTest(dispatcher) {
        viewModel.start()
        assertTrue(viewModel.state.value.busy)
        advanceUntilIdle()
        val entry = viewModel.state.value.entries.single()
        assertEquals(UiText(R.string.outcome_serving_local, listOf("eth0")), entry.message?.title)
        assertEquals(listOf(UiText(R.string.note_exemption_refused)), entry.notes)
        assertEquals("outcome", entry.steps.single().label)
        assertEquals("12:00:00", entry.time)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun invalidInputIsNeverSent() = runTest(dispatcher) {
        viewModel.updateInput { it.copy(prefixLength = "8") }
        val validation = viewModel.state.value.validation as ValidationResult.Invalid
        assertEquals(16, validation.suggestedPrefix)
        viewModel.start()
        viewModel.step(RawStep.START_ONLY)
        advanceUntilIdle()
        assertTrue(engine.started.isEmpty() && engine.steps.isEmpty())
        assertEquals(UiText(R.string.debug_request_invalid), viewModel.state.value.entries.first().message?.title)
    }

    @Test
    fun stepsWithoutARequestDoNotNeedValidInput() = runTest(dispatcher) {
        viewModel.updateInput { it.copy(phoneAddress = "nonsense") }
        viewModel.step(RawStep.DIAGNOSE)
        advanceUntilIdle()
        assertEquals(RawStep.DIAGNOSE to null, engine.steps.single())
    }

    @Test
    fun anUnreachableEngineIsExplained() = runTest(dispatcher) {
        engine.unavailable = EngineUnavailable(UnavailableReason.BIND_TIMEOUT, "15000 ms")
        viewModel.start()
        advanceUntilIdle()
        val entry = viewModel.state.value.entries.single()
        assertEquals(UiText(R.string.unavailable_bind_timeout), entry.message?.title)
        assertEquals("BIND_TIMEOUT 15000 ms", entry.steps.single().detail)
    }

    @Test
    fun removedAdapterAfterServingIsReported() = runTest(dispatcher) {
        engine.statuses += EngineStatus(36, true, "eth0" to ServingScope.LOCAL, null, null, listOf(InterfaceInfo("eth0", true, true, 1000, emptyList())))
        engine.statuses += EngineStatus(36, true, null, null, null, emptyList())
        viewModel.status()
        advanceUntilIdle()
        viewModel.status()
        advanceUntilIdle()
        assertEquals(UiText(R.string.status_adapter_removed), viewModel.state.value.entries.first().message?.title)
    }

    @Test
    fun newestResultComesFirstAndClearEmptiesTheList() = runTest(dispatcher) {
        viewModel.stop()
        advanceUntilIdle()
        viewModel.step(RawStep.READ_STATES)
        advanceUntilIdle()
        assertNull(viewModel.state.value.entries.first().message)
        viewModel.clear()
        assertTrue(viewModel.state.value.entries.isEmpty())
    }
}
