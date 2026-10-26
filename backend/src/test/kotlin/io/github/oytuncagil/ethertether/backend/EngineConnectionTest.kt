// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import android.os.Binder
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.engine.RawStep
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.engine.service.EngineCodec
import io.github.oytuncagil.ethertether.engine.service.IEngineListener
import io.github.oytuncagil.ethertether.engine.service.IEtherTetherService
import io.github.oytuncagil.ethertether.model.KnownUids
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.model.TetherMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/** A fake engine service; [alive] false makes its binder look dead. */
private class FakeService(
    val serviceUid: Int = KnownUids.SHELL,
    val protocol: Int = EngineCodec.PROTOCOL_VERSION,
    var failCalls: Boolean = false,
    /** When set, start blocks its thread until the latch opens, like a binder call that never answers. */
    val hang: CountDownLatch? = null,
) : IEtherTetherService {
    var alive = true
    var starts = 0
    private val binder = object : Binder() {
        override fun isBinderAlive() = alive
    }

    override fun asBinder(): IBinder = binder
    override fun destroy() = Unit
    override fun protocolVersion() = protocol
    override fun uid() = serviceUid

    override fun start(request: Bundle?): Bundle {
        if (failCalls) throw DeadObjectException()
        hang?.await()
        starts++
        return EngineCodec.encodeReport(EngineReport(Outcome.Waiting))
    }

    override fun stop(): Bundle = EngineCodec.encodeReport(EngineReport(Outcome.Stopped))
    override fun status(): Bundle = Bundle()
    override fun runStep(step: String?, request: Bundle?): Bundle = Bundle()

    var registered: IEngineListener? = null

    override fun setListener(listener: IEngineListener?) {
        registered = listener
    }
}

/** A binder that hands out [services] in order, or never answers when [answer] is false. */
private class FakeBinder(private val services: List<FakeService>, private val answer: Boolean = true) : UserServiceBinder {
    var binds = 0
    var unbinds = 0

    override fun bind(onConnected: (IBinder) -> Unit, onDisconnected: () -> Unit) {
        val service = services[minOf(binds, services.lastIndex)]
        binds++
        if (answer) onConnected(service.asBinder())
    }

    override fun unbind() {
        unbinds++
    }
}

@RunWith(AndroidJUnit4::class)
class EngineConnectionTest {
    private fun connection(binder: FakeBinder, services: List<FakeService>): EngineConnection {
        val byBinder = services.associateBy { it.asBinder() }
        return EngineConnection(binder, bindTimeoutMs = 1_000) { byBinder.getValue(it) }
    }

    private suspend fun expectUnavailable(reason: UnavailableReason, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: EngineUnavailable) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun bindsOnceAndReusesTheService() = runTest {
        val service = FakeService()
        val binder = FakeBinder(listOf(service))
        val connection = connection(binder, listOf(service))
        assertSame(service, connection.service())
        assertSame(service, connection.service())
        assertEquals(1, binder.binds)
        assertTrue(connection.isConnected)
    }

    @Test
    fun aDeadServiceIsReplacedOnTheNextCall() = runTest {
        val first = FakeService()
        val second = FakeService()
        val binder = FakeBinder(listOf(first, second))
        val connection = connection(binder, listOf(first, second))
        connection.service()
        first.alive = false
        assertSame(second, connection.service())
        assertEquals(2, binder.binds)
    }

    @Test
    fun resetAfterShizukuDiedBindsAgain() = runTest {
        val first = FakeService()
        val second = FakeService()
        val binder = FakeBinder(listOf(first, second))
        val connection = connection(binder, listOf(first, second))
        connection.service()
        connection.reset()
        assertFalse(connection.isConnected)
        assertSame(second, connection.service())
    }

    @Test
    fun noConnectionInTimeIsReported() = runTest {
        val service = FakeService()
        val connection = connection(FakeBinder(listOf(service), answer = false), listOf(service))
        expectUnavailable(UnavailableReason.BIND_TIMEOUT) { connection.service() }
    }

    @Test
    fun bindingThatThrowsIsReported() = runTest {
        val binder = object : UserServiceBinder {
            override fun bind(onConnected: (IBinder) -> Unit, onDisconnected: () -> Unit) = throw IllegalStateException("binder haven't been received")
            override fun unbind() = Unit
        }
        expectUnavailable(UnavailableReason.BIND_FAILED) { EngineConnection(binder).service() }
    }

    @Test
    fun aServiceNotRunningAsShellIsRefused() = runTest {
        val service = FakeService(serviceUid = KnownUids.ROOT)
        val connection = connection(FakeBinder(listOf(service)), listOf(service))
        expectUnavailable(UnavailableReason.WRONG_UID) { connection.service() }
        assertFalse(connection.isConnected)
    }

    @Test
    fun anotherProtocolVersionIsRefusedAndUnbound() = runTest {
        val service = FakeService(protocol = EngineCodec.PROTOCOL_VERSION + 1)
        val binder = FakeBinder(listOf(service))
        expectUnavailable(UnavailableReason.PROTOCOL_MISMATCH) { connection(binder, listOf(service)).service() }
        assertEquals(1, binder.unbinds)
    }

    @Test
    fun clientDecodesResults() = runTest {
        val service = FakeService()
        val client = EngineClient(connection(FakeBinder(listOf(service)), listOf(service)), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Waiting, client.start(TetherInput(TetherMode.DHCP)).outcome)
        assertEquals(Outcome.Stopped, client.stop().outcome)
        assertTrue(client.runStep(RawStep.DIAGNOSE, null).isEmpty())
        assertEquals(1, service.starts)
    }

    @Test
    fun aServiceDyingDuringACallIsReportedAndForgotten() = runTest {
        val dying = FakeService(failCalls = true)
        val fresh = FakeService()
        val binder = FakeBinder(listOf(dying, fresh))
        val connection = connection(binder, listOf(dying, fresh))
        val client = EngineClient(connection, StandardTestDispatcher(testScheduler))
        expectUnavailable(UnavailableReason.DIED) { client.start(TetherInput(TetherMode.DHCP)) }
        assertFalse(connection.isConnected)
        assertEquals(Outcome.Waiting, client.start(TetherInput(TetherMode.DHCP)).outcome)
        assertEquals(2, binder.binds)
    }

    @Test
    fun aCallThatNeverAnswersIsGivenUpAndForgotten() = runBlocking {
        val latch = CountDownLatch(1)
        val hanging = FakeService(hang = latch)
        val fresh = FakeService()
        val connection = connection(FakeBinder(listOf(hanging, fresh)), listOf(hanging, fresh))
        val client = EngineClient(connection, Dispatchers.IO, answerTimeoutMs = 300)
        val began = System.nanoTime()
        expectUnavailable(UnavailableReason.NO_ANSWER) { client.start(TetherInput(TetherMode.DHCP)) }
        assertTrue("gave up after the limit, not after the call", System.nanoTime() - began < 5_000_000_000L)
        assertFalse(connection.isConnected)
        assertEquals(Outcome.Waiting, client.start(TetherInput(TetherMode.DHCP)).outcome)
        latch.countDown()
    }

    @Test
    fun watchingRegistersAgainAfterEveryReconnectAndDeliversPushes() = runTest {
        val first = FakeService()
        val second = FakeService()
        val connection = connection(FakeBinder(listOf(first, second)), listOf(first, second))
        val client = EngineClient(connection, StandardTestDispatcher(testScheduler))
        client.watch()
        val listener = first.registered!!
        val status = EngineStatus(36, true, "eth0" to ServingScope.LOCAL, null, null, emptyList())
        listener.onStatus(EngineCodec.encodeStatus(status))
        assertEquals(status, client.statusUpdates.replayCache.single())
        first.alive = false
        client.stop()
        assertSame(listener, second.registered)
    }

    @Test
    fun withoutWatchNoListenerIsRegistered() = runTest {
        val service = FakeService()
        val client = EngineClient(connection(FakeBinder(listOf(service)), listOf(service)), StandardTestDispatcher(testScheduler))
        client.stop()
        assertEquals(null, service.registered)
    }
}
