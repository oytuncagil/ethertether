// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import io.github.oytuncagil.ethertether.data.EventLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
class AdapterChangesTest {
    private val log = EventLog(file = null, writer = Executor { it.run() }) { 0L }

    @Test
    fun aUsbEventReadsTheStatusTwiceThenNothingRuns() = runTest {
        var reads = 0
        val changes = AdapterChanges(backgroundScope, log) { reads++ }
        changes.onUsbChange(attached = true)
        advanceTimeBy(1_400)
        assertEquals(0, reads)
        advanceTimeBy(200)
        assertEquals(1, reads)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(2, reads)
        advanceTimeBy(60_000)
        assertEquals(2, reads)
        assertEquals("usb device attached", log.entries.value.last().message)
    }

    @Test
    fun eventsInQuickSuccessionCountFromTheLatest() = runTest {
        var reads = 0
        val changes = AdapterChanges(this, log) { reads++ }
        changes.onUsbChange(attached = false)
        advanceTimeBy(1_000)
        changes.onUsbChange(attached = true)
        advanceTimeBy(1_000)
        assertEquals(0, reads)
        advanceUntilIdle()
        assertEquals(2, reads)
    }
}
