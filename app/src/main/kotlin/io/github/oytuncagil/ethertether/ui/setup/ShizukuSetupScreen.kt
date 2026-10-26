// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.ui.result.MessageView
import io.github.oytuncagil.ethertether.ui.result.message

/** What the setup screen shows. [readiness] is null while the first check runs. */
data class SetupUiState(val readiness: EngineReadiness?, val shizukuInstalled: Boolean)

/** Callbacks of the setup screen. */
data class SetupActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onGrant: () -> Unit = {},
    val onOpenDownload: () -> Unit = {},
    val onOpenShizuku: () -> Unit = {},
    val onOpenDeveloperOptions: () -> Unit = {},
)

/** The steps of starting Shizuku with wireless debugging, in order. */
private val START_STEPS = listOf(
    R.string.setup_step_wifi,
    R.string.setup_step_developer,
    R.string.setup_step_pairing,
    R.string.setup_step_code,
    R.string.setup_step_enter,
    R.string.setup_step_start,
)

/**
 * Guided Shizuku setup: what Shizuku is, the official download page, starting it
 * with wireless debugging on the phone alone, granting access, and checking again.
 */
@Composable
fun ShizukuSetupScreen(state: SetupUiState, actions: SetupActions) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Heading(R.string.setup_title, MaterialTheme.typography.headlineMedium)
            StatusCard(state, actions)
            Section(R.string.setup_what_title) { Body(R.string.setup_what_body) }
            Section(R.string.setup_install_title) { InstallContent(state.shizukuInstalled, actions) }
            Section(R.string.setup_start_title) { StartContent(actions.onOpenDeveloperOptions) }
            Section(R.string.setup_grant_title) { Body(R.string.setup_grant_body) }
            Section(R.string.setup_reboot_title) { Body(R.string.setup_reboot_body) }
            OutlinedButton(onClick = actions.onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@Composable
private fun StatusCard(state: SetupUiState, actions: SetupActions) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.setup_status_title), style = MaterialTheme.typography.titleMedium)
            state.readiness?.let { MessageView(it.message()) }
            if (state.readiness == EngineReadiness.PermissionRequired) {
                Button(onClick = actions.onGrant, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_grant))
                }
            }
            FilledTonalButton(onClick = actions.onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_refresh))
            }
        }
    }
}

@Composable
private fun InstallContent(installed: Boolean, actions: SetupActions) {
    if (installed) {
        Body(R.string.setup_install_done)
        FilledTonalButton(onClick = actions.onOpenShizuku, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_open_shizuku))
        }
    } else {
        Body(R.string.setup_install_body)
        Button(onClick = actions.onOpenDownload, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_open_download))
        }
    }
}

@Composable
private fun StartContent(onOpenDeveloperOptions: () -> Unit) {
    Body(R.string.setup_start_intro)
    START_STEPS.forEachIndexed { index, step ->
        Column {
            Text(stringResource(R.string.setup_step_number, index + 1), style = MaterialTheme.typography.labelLarge)
            Body(step)
        }
    }
    FilledTonalButton(onClick = onOpenDeveloperOptions, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.action_open_developer_options))
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(title, MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Heading(@StringRes text: Int, style: TextStyle) {
    Text(stringResource(text), style = style, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}
