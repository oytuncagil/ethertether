// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatusPublisherTest {
    private val request = TetherRequest(null, SharingScope.LOCAL_ONLY, false)

    private fun TestScope.setUp(port: FakeTetheringPort): Pair<TetheringEngine, StatusPublisher> {
        val engine = TetheringEngine(port, FakeInterfaces())
        return engine to StatusPublisher(engine, backgroundScope)
    }

    @Test
    fun sendsTheCurrentStatusThenEveryChange() = runTest {
        val port = FakeTetheringPort()
        val (engine, publisher) = setUp(port)
        val seen = mutableListOf<EngineStatus>()
        publisher.publishTo { seen += it }
        runCurrent()
        assertNull(seen.last().serving)
        engine.start(request)
        runCurrent()
        assertEquals("eth0" to ServingScope.LOCAL, seen.last().serving)
        port.unplug()
        runCurrent()
        assertNull(seen.last().serving)
        assertEquals("no repeated identical statuses", seen.size, seen.zipWithNext().count { (a, b) -> a != b } + 1)
    }

    @Test
    fun aNewReceiverReplacesTheOldOneAndNullStops() = runTest {
        val port = FakeTetheringPort()
        val (engine, publisher) = setUp(port)
        val first = mutableListOf<EngineStatus>()
        val second = mutableListOf<EngineStatus>()
        publisher.publishTo { first += it }
        runCurrent()
        publisher.publishTo { second += it }
        runCurrent()
        val firstCount = first.size
        engine.start(request)
        runCurrent()
        assertEquals(firstCount, first.size)
        publisher.publishTo(null)
        val secondCount = second.size
        port.unplug()
        runCurrent()
        assertEquals(secondCount, second.size)
    }

    @Test
    fun aThrowingReceiverIsDropped() = runTest {
        val port = FakeTetheringPort()
        val (engine, publisher) = setUp(port)
        var calls = 0
        publisher.publishTo { calls++; throw IllegalStateException("dead") }
        runCurrent()
        engine.start(request)
        runCurrent()
        assertEquals(1, calls)
    }
}
