// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.resolve

/** A message with its tone icon, read by TalkBack as one sentence: what happened, then the next step. */
@Composable
fun MessageView(message: Message, modifier: Modifier = Modifier) {
    val title = message.title.resolve()
    val next = message.next?.resolve()
    // One node for TalkBack, read from the visible texts: tone, what happened, the next step.
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ToneIcon(message.tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (next != null) {
                Text(next, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The icon of a [Tone], with its spoken label. */
@Composable
fun ToneIcon(tone: Tone) {
    val (icon, tint, description) = when (tone) {
        Tone.OK -> Triple(R.drawable.ic_status_ok, MaterialTheme.colorScheme.primary, R.string.cd_state_ok)
        Tone.ACTION -> Triple(R.drawable.ic_status_action, MaterialTheme.colorScheme.tertiary, R.string.cd_state_action)
        Tone.ABSENT -> Triple(R.drawable.ic_status_absent, MaterialTheme.colorScheme.outline, R.string.cd_state_absent)
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = stringResource(description),
        tint = tint,
        modifier = Modifier.size(28.dp),
    )
}
