// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.engine.RawStep
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.ui.common.RequestForm
import io.github.oytuncagil.ethertether.ui.common.resolve
import io.github.oytuncagil.ethertether.ui.result.MessageView
import io.github.oytuncagil.ethertether.ui.result.message

/** Callbacks of the debug screen. */
data class DebugActions(
    val onBack: () -> Unit = {},
    val onInput: ((TetherInput) -> TetherInput) -> Unit = {},
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onStatus: () -> Unit = {},
    val onStep: (RawStep) -> Unit = {},
    val onCopy: () -> Unit = {},
    val onClear: () -> Unit = {},
)

/** The engine debug screen behind the developer switch: request, operations, single steps, raw results. */
@Composable
fun DebugScreen(state: DebugUiState, readiness: EngineReadiness?, actions: DebugActions) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.debug_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.debug_intro), style = MaterialTheme.typography.bodyMedium)
            readiness?.let { MessageView(it.message()) }
            SectionTitle(R.string.debug_request_title)
            RequestForm(state.input, state.validation, actions.onInput)
            Operations(state.busy, actions)
            Results(state, actions)
            OutlinedButton(onClick = actions.onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Operations(busy: Boolean, actions: DebugActions) {
    SectionTitle(R.string.debug_full_title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = actions.onStart, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.debug_action_start))
        }
        Button(onClick = actions.onStop, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.debug_action_stop))
        }
        FilledTonalButton(onClick = actions.onStatus, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.debug_action_status))
        }
    }
    SectionTitle(R.string.debug_steps_title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RawStep.entries.forEach { step ->
            OutlinedButton(onClick = { actions.onStep(step) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(step.label().resolve())
            }
        }
    }
    if (busy) {
        val running = stringResource(R.string.debug_running)
        LinearProgressIndicator(Modifier.semantics { contentDescription = running })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Results(state: DebugUiState, actions: DebugActions) {
    SectionTitle(R.string.debug_results_title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = actions.onCopy, enabled = state.entries.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.debug_action_copy))
        }
        OutlinedButton(onClick = actions.onClear, enabled = state.entries.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.debug_action_clear))
        }
    }
    if (state.entries.isEmpty()) {
        Text(stringResource(R.string.debug_results_empty), style = MaterialTheme.typography.bodyMedium)
    }
    state.entries.forEach { DebugEntryCard(it) }
}

@Composable
internal fun SectionTitle(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}
