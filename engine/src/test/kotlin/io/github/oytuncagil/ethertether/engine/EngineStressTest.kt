// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.model.Ipv4Address
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.StaticAddresses
import io.github.oytuncagil.ethertether.model.TetherRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Many operations in a row and at once, on every Android version shape: the engine always ends
 * in the state of its last operation, registers its event callback only once, and leaves nothing
 * registered after close.
 */
class EngineStressTest {
    private val addresses = StaticAddresses(Ipv4Address.parse("169.254.0.42")!!, Ipv4Address.parse("169.254.0.1")!!, 16)
    private val versions = 30..37

    private fun request(random: Random) = TetherRequest(
        if (random.nextBoolean()) addresses else null,
        if (random.nextBoolean()) SharingScope.LOCAL_ONLY else SharingScope.SHARE_INTERNET,
        allowScopeFallback = true,
    )

    @Test
    fun manyStartsStopsAndUnplugsEndInTheLastState() = runTest {
        val random = Random(42)
        for (sdk in versions) {
            val port = FakeTetheringPort(sdkInt = sdk)
            val engine = TetheringEngine(port, FakeInterfaces(port = port))
            repeat(60) { round ->
                when (random.nextInt(4)) {
                    0, 1 -> {
                        val outcome = engine.start(request(random)).outcome
                        val expected = if (port.adapterPresent) "Serving" else "Waiting"
                        assertTrue("API $sdk round $round: $outcome", outcome.javaClass.simpleName == expected)
                    }
                    2 -> {
                        engine.stop()
                        assertNull("API $sdk round $round", engine.status().serving)
                    }
                    else -> if (port.adapterPresent) port.unplug() else port.plugIn()
                }
                assertTrue("API $sdk: one event callback at most", port.registered <= 1)
            }
            engine.close()
            assertEquals("API $sdk: nothing left registered", 0, port.registered)
        }
    }

    @Test
    fun parallelRequestsRunOneAtATimeAndTheStateMatchesAndroid() = runTest {
        for (sdk in versions) {
            val port = FakeTetheringPort(sdkInt = sdk)
            val engine = TetheringEngine(port, FakeInterfaces(port = port))
            val random = Random(sdk)
            (1..40).map { index ->
                async { if (index % 3 == 0) engine.stop() else engine.start(request(random)) }
            }.awaitAll()
            val starts = port.calls.count { it.startsWith("start") }
            val stops = port.calls.count { it == "stop" }
            assertTrue("API $sdk: every start was preceded by a stop", stops >= starts)
            val androidServes = port.calls.last().startsWith("start")
            assertEquals("API $sdk", androidServes, engine.status().serving != null)
            assertTrue(port.registered <= 1)
        }
    }
}
