// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.data.LogEntry
import io.github.oytuncagil.ethertether.data.LogLevel
import io.github.oytuncagil.ethertether.ui.common.ScreenScaffold

/** Which entries the log screen shows. */
data class LogFilter(val problemsOnly: Boolean = false, val text: String = "") {
    /** True when [entry] passes this filter. */
    fun matches(entry: LogEntry): Boolean {
        if (problemsOnly && entry.level == LogLevel.INFO) return false
        if (text.isBlank()) return true
        return entry.message.contains(text, ignoreCase = true) || entry.tag.contains(text, ignoreCase = true)
    }
}

/** Callbacks of the log screen. Copy and export get the entries the filter shows. */
data class LogActions(
    val onBack: () -> Unit = {},
    val onCopy: (List<LogEntry>) -> Unit = {},
    val onExport: (List<LogEntry>) -> Unit = {},
    val onClear: () -> Unit = {},
)

/** The on-phone log: newest first, a filter, copy, export through the share sheet and clear. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(entries: List<LogEntry>, actions: LogActions) {
    var filter by remember { mutableStateOf(LogFilter()) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = entries.filter(filter::matches)
    ScreenScaffold(title = stringResource(R.string.log_title), onBack = actions.onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.log_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !filter.problemsOnly, onClick = { filter = filter.copy(problemsOnly = false) }, label = { Text(stringResource(R.string.log_filter_all)) }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = filter.problemsOnly, onClick = { filter = filter.copy(problemsOnly = true) }, label = { Text(stringResource(R.string.log_filter_problems)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
            OutlinedTextField(
                value = filter.text,
                onValueChange = { filter = filter.copy(text = it.take(100)) },
                label = { Text(stringResource(R.string.log_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { actions.onExport(shown) }, enabled = shown.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.log_export)) }
                OutlinedButton(onClick = { actions.onCopy(shown) }, enabled = shown.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.log_copy)) }
                OutlinedButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.log_clear)) }
            }
            Text(pluralStringResource(R.plurals.log_count, entries.size, shown.size, entries.size), style = MaterialTheme.typography.labelMedium)
            if (shown.isEmpty()) Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown.asReversed(), key = { "${it.time}-${it.hashCode()}" }) { entry ->
                    LogRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.log_clear_title)) },
            text = { Text(stringResource(R.string.log_clear_text)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; actions.onClear() }) { Text(stringResource(R.string.log_clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    val level = stringResource(
        when (entry.level) {
            LogLevel.INFO -> R.string.log_level_info
            LogLevel.WARNING -> R.string.log_level_warning
            LogLevel.ERROR -> R.string.log_level_error
        },
    )
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("${LogEntry.formatTime(entry.time)}  $level  ${entry.tag}", style = MaterialTheme.typography.labelSmall, color = color)
        SelectionContainer { Text(entry.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
    }
}
