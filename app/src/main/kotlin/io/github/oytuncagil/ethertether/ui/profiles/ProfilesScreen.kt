// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.model.Profile
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.ui.common.IconAction
import io.github.oytuncagil.ethertether.ui.common.ScreenColumn
import io.github.oytuncagil.ethertether.ui.common.ScreenScaffold
import io.github.oytuncagil.ethertether.ui.common.displayName
import io.github.oytuncagil.ethertether.ui.common.summary

/** Callbacks of the profile list. */
data class ProfilesActions(
    val onBack: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onEdit: (String) -> Unit = {},
    val onNew: () -> Unit = {},
    val onDuplicate: (String, String) -> Unit = { _, _ -> },
    val onDelete: (String) -> Unit = {},
    val onImport: () -> Unit = {},
    val onExport: () -> Unit = {},
)

/** All profiles: the selected one marked, each with edit, duplicate, delete and select. */
@Composable
fun ProfilesScreen(list: ProfileList, actions: ProfilesActions, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    var deleting by remember { mutableStateOf<Profile?>(null) }
    ScreenScaffold(
        title = stringResource(R.string.profiles_title),
        onBack = actions.onBack,
        snackbar = snackbar,
        actions = { ImportExportMenu(actions) },
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = actions.onNew,
                icon = { Icon(painterResource(R.drawable.ic_md_add), contentDescription = null) },
                text = { Text(stringResource(R.string.profiles_new)) },
            )
        },
    ) { padding ->
        ScreenColumn(padding) {
            if (list.profiles.isEmpty()) Text(stringResource(R.string.home_no_profiles))
            list.profiles.forEach { profile -> ProfileCard(profile, profile.id == list.selected?.id, actions) { deleting = profile } }
            Text(stringResource(R.string.profiles_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    deleting?.let { profile -> DeleteDialog(profile, onConfirm = { actions.onDelete(profile.id); deleting = null }, onCancel = { deleting = null }) }
}

@Composable
private fun ImportExportMenu(actions: ProfilesActions) {
    var open by remember { mutableStateOf(false) }
    IconAction(R.drawable.ic_md_more_vert, stringResource(R.string.action_more)) { open = true }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.profiles_import)) }, onClick = { open = false; actions.onImport() })
        DropdownMenuItem(text = { Text(stringResource(R.string.profiles_export)) }, onClick = { open = false; actions.onExport() })
    }
}

@Composable
private fun ProfileCard(profile: Profile, selected: Boolean, actions: ProfilesActions, onDelete: () -> Unit) {
    val name = profile.displayName()
    val copyName = stringResource(R.string.profiles_copy_name, name)
    val selectedText = stringResource(R.string.profiles_selected)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) { if (selected) stateDescription = selectedText }) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(profile.summary(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected) Text(selectedText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!selected) CardAction(R.string.profiles_select, name) { actions.onSelect(profile.id) }
                CardAction(R.string.profiles_edit, name) { actions.onEdit(profile.id) }
                CardAction(R.string.profiles_duplicate, name) { actions.onDuplicate(profile.id, copyName) }
                CardAction(R.string.profiles_delete, name, onDelete)
            }
        }
    }
}

@Composable
private fun CardAction(label: Int, profileName: String, onClick: () -> Unit) {
    val text = stringResource(label)
    val description = stringResource(R.string.profiles_action_description, text, profileName)
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description }) { Text(text) }
}

@Composable
private fun DeleteDialog(profile: Profile, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.profiles_delete_title)) },
        text = { Text(stringResource(R.string.profiles_delete_text, profile.displayName())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.profiles_delete)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}
