// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.tether.Operation
import io.github.oytuncagil.ethertether.ui.common.IconAction
import io.github.oytuncagil.ethertether.ui.common.ScreenColumn
import io.github.oytuncagil.ethertether.ui.common.ScreenScaffold
import io.github.oytuncagil.ethertether.ui.common.SectionHeading
import io.github.oytuncagil.ethertether.ui.common.displayName
import io.github.oytuncagil.ethertether.ui.common.resolve
import io.github.oytuncagil.ethertether.ui.common.summary
import io.github.oytuncagil.ethertether.ui.result.MessageView

/** Callbacks of the home screen. Defaults do nothing, for previews and tests. */
data class HomeActions(
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onGrant: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onOpenSetup: () -> Unit = {},
    val onOpenProfiles: () -> Unit = {},
    val onOpenLog: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onOpenTetherSettings: () -> Unit = {},
)

/** Home: the status card with the one main action, the message with the next step, the profile picker. */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    ScreenScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = {
            IconAction(R.drawable.ic_md_list, stringResource(R.string.profiles_title), actions.onOpenProfiles)
            IconAction(R.drawable.ic_log, stringResource(R.string.log_title), actions.onOpenLog)
            IconAction(R.drawable.ic_md_settings, stringResource(R.string.settings_title), actions.onOpenSettings)
            OverflowMenu(actions.onOpenAbout)
        },
    ) { padding ->
        ScreenColumn(padding) {
            StatusCard(state, actions)
            homeMessage(state.tether)?.let { MessageCard(it, actions) }
            ProfilePicker(state, actions)
        }
    }
}

@Composable
private fun OverflowMenu(onOpenAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconAction(R.drawable.ic_md_more_vert, stringResource(R.string.action_more), onClick = { open = true })
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.about_title)) }, onClick = {
            open = false
            onOpenAbout()
        })
    }
}

@Composable
private fun StatusCard(state: HomeUiState, actions: HomeActions) {
    val tether = state.tether
    val profile = tether.profileId?.let(state.profiles::find) ?: state.profiles.selected
    val title = stringResource(statusTitle(tether))
    val lines = statusLines(tether, profile?.displayName()).map { it.resolve() }
    val spoken = (listOf(stringResource(R.string.home_status_description, title)) + lines).joinToString(". ")
    val colors = if (tether.serving) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                Text(stringResource(R.string.tethering_title), style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.displaySmall)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            MainButton(state, actions)
        }
    }
}

@Composable
private fun MainButton(state: HomeUiState, actions: HomeActions) {
    val tether = state.tether
    val ready = tether.readiness == EngineReadiness.Ready
    val stopping = tether.operation == Operation.STOPPING
    // Stop stays available while starting, so a slow or stuck start can always be ended.
    val stop = tether.active && !stopping
    val label = stringResource(if (stop) R.string.action_stop else R.string.action_start)
    val profile = state.profiles.selected?.displayName().orEmpty()
    val description = if (stop) label else stringResource(R.string.home_start_description, profile)
    Button(
        onClick = if (stop) actions.onStop else actions.onStart,
        enabled = !stopping && (stop || (ready && state.profiles.selected != null)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageCard(home: HomeMessage, actions: HomeActions) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageView(home.message)
            home.notes.forEach { Text(it.resolve(), style = MaterialTheme.typography.bodyMedium) }
            if (home.action != null || home.secondary != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessageButton(home.action, actions)
                    MessageButton(home.secondary, actions)
                }
            }
        }
    }
}

@Composable
private fun MessageButton(action: HomeAction?, actions: HomeActions) {
    val modifier = Modifier.heightIn(min = 48.dp)
    when (action) {
        HomeAction.SETUP -> FilledTonalButton(onClick = actions.onOpenSetup, modifier = modifier) { Text(stringResource(R.string.action_open_setup)) }
        HomeAction.GRANT -> FilledTonalButton(onClick = actions.onGrant, modifier = modifier) { Text(stringResource(R.string.action_grant)) }
        HomeAction.DISMISS -> TextButton(onClick = actions.onDismiss, modifier = modifier) { Text(stringResource(R.string.action_dismiss)) }
        HomeAction.START -> FilledTonalButton(onClick = actions.onStart, modifier = modifier) { Text(stringResource(R.string.action_start)) }
        HomeAction.TETHER_SETTINGS -> OutlinedButton(onClick = actions.onOpenTetherSettings, modifier = modifier) {
            Text(stringResource(R.string.action_tether_settings))
        }
        null -> Unit
    }
}

@Composable
private fun ProfilePicker(state: HomeUiState, actions: HomeActions) {
    val list = state.profiles
    val locked = state.tether.active
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeading(stringResource(R.string.home_profile_title))
        if (locked) Text(stringResource(R.string.home_profile_locked), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.selectableGroup()) {
            list.profiles.forEach { profile ->
                val selected = profile.id == list.selected?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = selected, enabled = !locked, role = Role.RadioButton) { actions.onSelect(profile.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected, onClick = null, enabled = !locked)
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName(), style = MaterialTheme.typography.bodyLarge)
                        Text(profile.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (list.profiles.isEmpty()) Text(stringResource(R.string.home_no_profiles), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = actions.onOpenProfiles, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_manage_profiles))
        }
    }
}
