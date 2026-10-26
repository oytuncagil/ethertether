// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** How serious a log entry is. */
enum class LogLevel { INFO, WARNING, ERROR }

/** One log entry. [time] is in milliseconds since the epoch. */
data class LogEntry(val time: Long, val level: LogLevel, val tag: String, val message: String) {
    /** One line: time, level, tag, message, separated by tabs; tabs and line breaks in the message are escaped. */
    fun toLine(): String = listOf(formatTime(time), level.name, tag, escape(message)).joinToString("\t")

    /** Parsing and formatting. */
    companion object {
        /** The entry in [line], or null when the line is not one this log wrote. */
        fun parse(line: String): LogEntry? {
            val parts = line.split('\t', limit = 4)
            if (parts.size != 4) return null
            val time = runCatching { format().parse(parts[0])?.time }.getOrNull() ?: return null
            val level = LogLevel.entries.firstOrNull { it.name == parts[1] } ?: return null
            return LogEntry(time, level, parts[2], unescape(parts[3]))
        }

        /** UTC time with milliseconds, the same on every phone. */
        fun formatTime(time: Long): String = format().format(Date(time))

        private fun format() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }

        private fun escape(text: String) = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")

        private fun unescape(text: String): String {
            val out = StringBuilder()
            var index = 0
            while (index < text.length) {
                val char = text[index]
                if (char == '\\' && index + 1 < text.length) {
                    when (text[index + 1]) {
                        't' -> out.append('\t')
                        'n' -> out.append('\n')
                        else -> out.append(text[index + 1])
                    }
                    index += 2
                } else {
                    out.append(char)
                    index++
                }
            }
            return out.toString()
        }
    }
}

/**
 * What the app did, kept on the phone in one small text file and never uploaded. It leaves the
 * phone only when the user exports it. Thread-safe. The file is read and written on [writer], in
 * order, never on the caller's thread (which is often the main thread).
 *
 * @param file the log file, created when needed
 * @param writer runs the file work; tests pass one that runs it at once
 * @param clock the current time in milliseconds (last, so a trailing lambda is the clock)
 */
class EventLog(
    private val file: File?,
    private val writer: Executor = Executors.newSingleThreadExecutor { Thread(it, "event-log").apply { isDaemon = true } },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableEntries = MutableStateFlow(emptyList<LogEntry>())

    init {
        // Entries added before the file was read are kept after the stored ones.
        writer.execute { mutableEntries.update { (load() + it).takeLast(MAX_ENTRIES) } }
    }

    /** All entries, oldest first. */
    val entries: StateFlow<List<LogEntry>> = mutableEntries.asStateFlow()

    /** Adds an entry. */
    fun add(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(clock(), level, tag, message)
        mutableEntries.update { (it + entry).takeLast(MAX_ENTRIES) }
        writer.execute { persist(entry) }
    }

    /** Info entry. */
    fun info(tag: String, message: String) = add(LogLevel.INFO, tag, message)

    /** Warning entry. */
    fun warn(tag: String, message: String) = add(LogLevel.WARNING, tag, message)

    /** Error entry. */
    fun error(tag: String, message: String) = add(LogLevel.ERROR, tag, message)

    /** Removes every entry, also from the file. */
    fun clear() {
        mutableEntries.value = emptyList()
        writer.execute { runCatching { file?.writeText("") } }
    }

    /** The whole log as text, one entry per line, for copy and export. */
    fun asText(entries: List<LogEntry> = mutableEntries.value): String = entries.joinToString("\n") { it.toLine() }

    private fun load(): List<LogEntry> {
        val lines = runCatching { file?.takeIf { it.exists() }?.readLines() }.getOrNull().orEmpty()
        return lines.mapNotNull(LogEntry::parse).takeLast(MAX_ENTRIES)
    }

    private fun persist(entry: LogEntry) {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            target.appendText(entry.toLine() + "\n")
            // Rewrite the file now and then so it never grows beyond twice the kept entries.
            if (target.length() > MAX_FILE_BYTES) target.writeText(asText() + "\n")
        }
    }

    /** Limits. */
    companion object {
        /** Entries kept. */
        const val MAX_ENTRIES = 1000

        private const val MAX_FILE_BYTES = 400_000L
    }
}
