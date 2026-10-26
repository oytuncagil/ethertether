// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class EventLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_760_000_000_000L

    /** Runs the file work at once, so the test can read the file right after. */
    private val direct = Executor { it.run() }

    @Test
    fun aLineRoundTripsWithTabsAndLineBreaks() {
        val entry = LogEntry(now, LogLevel.WARNING, "engine", "a\tb\nc \\ d")
        assertEquals(entry, LogEntry.parse(entry.toLine()))
        assertEquals(1, entry.toLine().lines().size)
    }

    @Test
    fun foreignLinesAreIgnored() {
        assertNull(LogEntry.parse("hello"))
        assertNull(LogEntry.parse("yesterday\tINFO\tapp\tx"))
        assertNull(LogEntry.parse("2026-10-08T10:00:00.000Z\tLOUD\tapp\tx"))
    }

    @Test
    fun timesAreUtc() {
        assertEquals("1970-01-01T00:00:01.500Z", LogEntry.formatTime(1500))
    }

    @Test
    fun entriesSurviveARestartAndClearEmptiesTheFile() {
        val file = File(folder.root, "logs/events.log")
        val log = EventLog(file, direct) { now++ }
        log.info("app", "one")
        log.error("engine", "two")
        val again = EventLog(file, direct) { now }
        assertEquals(listOf("one", "two"), again.entries.value.map { it.message })
        assertEquals(LogLevel.ERROR, again.entries.value.last().level)
        again.clear()
        assertTrue(again.entries.value.isEmpty())
        assertTrue(EventLog(file, writer = direct).entries.value.isEmpty())
    }

    @Test
    fun onlyTheNewestEntriesAreKept() {
        val file = File(folder.root, "events.log")
        val log = EventLog(file, direct) { now++ }
        repeat(EventLog.MAX_ENTRIES + 50) { log.warn("t", "m$it") }
        assertEquals(EventLog.MAX_ENTRIES, log.entries.value.size)
        assertEquals("m50", log.entries.value.first().message)
        assertEquals(EventLog.MAX_ENTRIES, EventLog(file, writer = direct).entries.value.size)
    }

    @Test
    fun asTextHasOneLinePerEntry() {
        val log = EventLog(null, direct) { now }
        log.info("a", "x")
        log.info("b", "y")
        assertEquals(2, log.asText().lines().size)
        assertTrue(log.asText().endsWith("\tINFO\tb\ty"))
    }
}
