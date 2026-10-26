// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.oytuncagil.ethertether.EtherTetherApplication
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineApi
import io.github.oytuncagil.ethertether.backend.EngineUnavailable
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.EngineStep
import io.github.oytuncagil.ethertether.engine.RawStep
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.model.TetherMode
import io.github.oytuncagil.ethertether.model.TetherRequestValidator
import io.github.oytuncagil.ethertether.model.ValidationResult
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText
import io.github.oytuncagil.ethertether.ui.result.Message
import io.github.oytuncagil.ethertether.ui.result.message
import io.github.oytuncagil.ethertether.ui.result.text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One result on the debug screen: what ran, when, the plain-language result and the raw steps. */
data class DebugEntry(
    val id: Long,
    val label: UiText,
    val time: String,
    val message: Message?,
    val notes: List<UiText>,
    val steps: List<EngineStep>,
)

/** What the debug screen shows. [entries] are newest first. */
data class DebugUiState(
    val input: TetherInput = DEFAULT_INPUT,
    val validation: ValidationResult = TetherRequestValidator.validate(DEFAULT_INPUT),
    val busy: Boolean = false,
    val entries: List<DebugEntry> = emptyList(),
)

/** The request the debug screen starts with: the link-local example of the README. */
val DEFAULT_INPUT = TetherInput(TetherMode.STATIC, "169.254.0.42", "169.254.0.1", "16", SharingScope.LOCAL_ONLY, false)

/**
 * Runs engine operations and single steps for the debug screen and keeps every result.
 *
 * @param clock the time label of a new entry
 */
class DebugViewModel(
    private val engine: EngineApi,
    private val clock: () -> String = { SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date()) },
) : ViewModel() {
    private val mutableState = MutableStateFlow(DebugUiState())
    private var nextId = 0L
    private var lastStatus: EngineStatus? = null

    /** The latest screen state. */
    val state: StateFlow<DebugUiState> = mutableState.asStateFlow()

    /** Replaces the request input and validates it again. */
    fun updateInput(transform: (TetherInput) -> TetherInput) {
        mutableState.update { state ->
            val input = transform(state.input)
            state.copy(input = input, validation = TetherRequestValidator.validate(input))
        }
    }

    /** Full start: stop first, start, confirm. */
    fun start() {
        val input = validInputOrReport() ?: return
        run(UiText(R.string.debug_action_start)) { reportEntry(engine.start(input)) }
    }

    /** Full stop. */
    fun stop() = run(UiText(R.string.debug_action_stop)) { reportEntry(engine.stop()) }

    /** Reads the status from both sources. */
    fun status() = run(UiText(R.string.debug_action_status)) {
        val status = engine.status()
        val message = statusMessage(status, lastStatus)
        lastStatus = status
        val steps = listOf(EngineStep("status", status.toString())) + status.interfaces.map { EngineStep("interface", it.toString()) }
        val notes = if (status.eventsAvailable) emptyList() else listOf(UiText(R.string.status_events_missing))
        Triple(message, notes + status.interfaces.map { it.describe() }, steps)
    }

    /** Runs one raw [step]. Steps that send a request need a valid input. */
    fun step(step: RawStep) {
        val needsInput = step in INPUT_STEPS
        val input = if (needsInput) validInputOrReport() ?: return else null
        run(step.label()) { Triple(null, emptyList(), engine.runStep(step, input)) }
    }

    /** Removes all results. */
    fun clear() = mutableState.update { it.copy(entries = emptyList()) }

    private fun validInputOrReport(): TetherInput? {
        val state = mutableState.value
        if (state.validation is ValidationResult.Valid) return state.input
        add(UiText(R.string.debug_action_start), Message(UiText(R.string.debug_request_invalid), null, Tone.ACTION), emptyList(), emptyList())
        return null
    }

    private fun reportEntry(report: EngineReport): Triple<Message?, List<UiText>, List<EngineStep>> =
        Triple(report.outcome.message(), report.notes.map { it.text() }, report.steps)

    private fun run(label: UiText, block: suspend () -> Triple<Message?, List<UiText>, List<EngineStep>>) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val (message, notes, steps) = try {
                block()
            } catch (e: EngineUnavailable) {
                Triple(e.message(), emptyList(), listOf(EngineStep("engine", "${e.reason} ${e.detail}")))
            } catch (e: Exception) {
                val text = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                Triple(Message(UiText(R.string.failure_unexpected, listOf(text)), UiText(R.string.failure_unexpected_next), Tone.ACTION), emptyList(), emptyList())
            }
            add(label, message, notes, steps)
            mutableState.update { it.copy(busy = false) }
        }
    }

    private fun add(label: UiText, message: Message?, notes: List<UiText>, steps: List<EngineStep>) {
        val entry = DebugEntry(nextId++, label, clock(), message, notes, steps)
        mutableState.update { it.copy(entries = listOf(entry) + it.entries) }
    }

    /** Factory wiring the view model to the application's engine. */
    companion object {
        private val INPUT_STEPS = setOf(RawStep.BUILD_REQUEST, RawStep.START_ONLY, RawStep.START_ONLY_NO_EXEMPTION)

        /** Creates the factory for [application]. */
        fun factory(application: EtherTetherApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { DebugViewModel(application.engine) }
        }
    }
}

/** The button label of a raw step. */
fun RawStep.label(): UiText = UiText(
    when (this) {
        RawStep.DIAGNOSE -> R.string.debug_step_diagnose
        RawStep.READ_STATES -> R.string.debug_step_states
        RawStep.READ_INTERFACES -> R.string.debug_step_interfaces
        RawStep.BUILD_REQUEST -> R.string.debug_step_build
        RawStep.STOP_ONLY -> R.string.debug_step_stop
        RawStep.START_ONLY -> R.string.debug_step_start
        RawStep.START_ONLY_NO_EXEMPTION -> R.string.debug_step_start_no_exemption
    },
)
