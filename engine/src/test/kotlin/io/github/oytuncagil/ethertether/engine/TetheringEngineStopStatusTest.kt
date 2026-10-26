// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TetheringEngineStopStatusTest {
    private val dhcpLocal = TetherRequest(null, SharingScope.LOCAL_ONLY, false)

    @Test
    fun stopConfirmsThatEthernetLeftTheServingLists() = runTest {
        val port = FakeTetheringPort()
        val engine = TetheringEngine(port, FakeInterfaces())
        engine.start(dhcpLocal)
        assertEquals(Outcome.Stopped, engine.stop().outcome)
        assertNull(engine.status().serving)
    }

    @Test
    fun stopErrorIsReported() = runTest {
        val port = FakeTetheringPort(stopCode = 5)
        assertEquals(Outcome.Failed(FailureReason.RESULT_CODE, 5), TetheringEngine(port, FakeInterfaces()).stop().outcome)
    }

    @Test
    fun stopWithoutAnswerTimesOut() = runTest {
        val port = FakeTetheringPort(answer = false)
        assertEquals(Outcome.Failed(FailureReason.NO_RESULT), TetheringEngine(port, FakeInterfaces()).stop().outcome)
    }

    @Test
    fun stopWithoutEventsIsUnconfirmed() = runTest {
        val port = FakeTetheringPort(eventsSupported = false)
        val report = TetheringEngine(port, FakeInterfaces()).stop()
        assertEquals(Outcome.Stopped, report.outcome)
        assertTrue(EngineNote.EVENTS_UNAVAILABLE in report.notes)
    }

    @Test
    fun stopWhenTheServiceDiedIsAFailure() = runTest {
        val port = object : TetheringPort by FakeTetheringPort() {
            override fun stopTethering(onResult: (Int) -> Unit) = throw TetheringServiceDied()
        }
        assertEquals(FailureReason.SERVICE_DIED, (TetheringEngine(port, FakeInterfaces()).stop().outcome as Outcome.Failed).reason)
    }

    @Test
    fun statusShowsServingScopeAndAdapter() = runTest {
        val port = FakeTetheringPort(sdkInt = 34)
        val engine = TetheringEngine(port, FakeInterfaces())
        engine.start(dhcpLocal)
        val status = engine.status()
        assertEquals("eth0" to ServingScope.LOCAL, status.serving)
        assertTrue(status.eventsAvailable)
        assertTrue(status.adapterPresent)
    }

    @Test
    fun removingTheAdapterLeavesNoAdapterInTheStatus() = runTest {
        val port = FakeTetheringPort()
        val engine = TetheringEngine(port, FakeInterfaces())
        engine.start(dhcpLocal)
        port.unplug()
        val status = engine.status()
        assertNull(status.serving)
        assertFalse(status.adapterPresent)
    }

    @Test
    fun statusFallsBackToTheKernelViewWithoutEvents() = runTest {
        val interfaces = FakeInterfaces(listOf(InterfaceInfo("eth0", true, false, null, emptyList())))
        val status = TetheringEngine(FakeTetheringPort(eventsSupported = false), interfaces).status()
        assertFalse(status.eventsAvailable)
        assertTrue(status.adapterPresent)
        assertEquals("eth0", status.interfaces.single().name)
    }

    @Test
    fun unreadableInterfacesDoNotBreakTheStatus() = runTest {
        val engine = TetheringEngine(FakeTetheringPort(), InterfaceReader { throw SecurityException("denied") })
        assertTrue(engine.status().interfaces.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun statusWaitsBrieflyForTheFirstStateOnly() = runTest {
        val silent = object : TetheringPort by FakeTetheringPort() {
            override fun registerEvents(onStates: (TetherStates) -> Unit): AutoCloseable = AutoCloseable {}
        }
        val engine = TetheringEngine(silent, FakeInterfaces())
        val started = testScheduler.currentTime
        assertNull(engine.status().serving)
        assertEquals(EngineTimeouts().firstStateMs, testScheduler.currentTime - started)
    }

    @Test
    fun registrationIsDoneOnceAndUndoneOnClose() {
        val engine = TetheringEngine(FakeTetheringPort(), FakeInterfaces())
        assertEquals("registered", engine.attachEvents().detail)
        assertEquals("already registered", engine.attachEvents().detail)
        engine.close()
        assertFalse(engine.eventsAvailable)
    }

    @Test
    fun androidElevenNamesAreMatchedByPattern() {
        val states = TetherStates(tethered = listOf(TetherIface("eth0", null), TetherIface("rndis0", null)))
        assertEquals("eth0", states.ethernetServing(30)?.first?.name)
        assertNull(TetherStates(tethered = listOf(TetherIface("usb0", null))).ethernetServing(34))
        assertEquals("usb0", TetherStates(tethered = listOf(TetherIface("usb0", null))).ethernetServing(35)?.first?.name)
    }

    @Test
    fun typedInterfacesAreMatchedByType() {
        val wifi = TetherIface("wlan1", 0)
        val ethernet = TetherIface("usb1", TetheringConstants.TETHERING_ETHERNET)
        assertEquals(ethernet, TetherStates(localOnly = listOf(wifi, ethernet)).ethernetServing(33)?.first)
    }
}
