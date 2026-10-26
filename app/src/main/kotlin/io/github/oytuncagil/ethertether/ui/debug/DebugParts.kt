// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.debug

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.ui.common.resolve
import io.github.oytuncagil.ethertether.ui.result.MessageView

/** One result: label and time, the plain-language message, notes, and the raw steps (selectable). */
@Composable
internal fun DebugEntryCard(entry: DebugEntry) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.debug_entry_title, entry.label.resolve(), entry.time), style = MaterialTheme.typography.titleMedium)
            entry.message?.let { MessageView(it) }
            entry.notes.forEach { Text(it.resolve(), style = MaterialTheme.typography.bodyMedium) }
            if (entry.steps.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        entry.steps.joinToString("\n") { "${it.label}: ${it.detail}" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/** All entries as plain text, oldest first, for the clipboard. */
fun entriesAsText(context: Context, entries: List<DebugEntry>): String = entries.reversed().joinToString("\n\n") { entry ->
    buildString {
        appendLine(context.getString(R.string.debug_entry_title, entry.label.resolve(context), entry.time))
        entry.message?.let { message ->
            appendLine(message.title.resolve(context))
            message.next?.let { appendLine(context.getString(R.string.debug_next, it.resolve(context))) }
        }
        entry.notes.forEach { appendLine(context.getString(R.string.debug_note, it.resolve(context))) }
        entry.steps.forEach { appendLine("${it.label}: ${it.detail}") }
    }.trimEnd()
}
