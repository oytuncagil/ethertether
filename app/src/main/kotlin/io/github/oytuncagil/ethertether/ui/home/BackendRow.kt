// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.ui.common.resolve
import io.github.oytuncagil.ethertether.ui.result.ToneIcon

/** One backend: icon, name, state and the next step, plus Grant access when it helps. */
@Composable
internal fun BackendRow(row: BackendPresentation, onGrant: () -> Unit) {
    val name = stringResource(row.name)
    val stateText = row.state.resolve()
    val hint = row.hint.resolve()
    val spoken = stringResource(R.string.backend_row_description, name, stateText, hint)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            ) {
                ToneIcon(row.tone)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(stateText, style = MaterialTheme.typography.bodyMedium)
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (row.canGrant) GrantButton(name, onGrant)
        }
    }
}

@Composable
private fun GrantButton(name: String, onGrant: () -> Unit) {
    val description = stringResource(R.string.action_grant_for, name)
    FilledTonalButton(
        onClick = onGrant,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.action_grant))
    }
}
