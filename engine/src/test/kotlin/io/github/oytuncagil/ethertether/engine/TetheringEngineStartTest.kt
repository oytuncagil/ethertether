// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.model.Ipv4Address
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.StaticAddresses
import io.github.oytuncagil.ethertether.model.TetherRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TetheringEngineStartTest {
    private val addresses = StaticAddresses(Ipv4Address.parse("169.254.0.42")!!, Ipv4Address.parse("169.254.0.1")!!, 16)

    private fun request(scope: SharingScope = SharingScope.LOCAL_ONLY, fallback: Boolean = false, static: Boolean = true) =
        TetherRequest(if (static) addresses else null, scope, fallback)

    private fun engine(port: FakeTetheringPort, interfaces: FakeInterfaces = FakeInterfaces(port = port)) = TetheringEngine(port, interfaces)

    @Test
    fun localOnlyServesLocallyOnEveryVersionWithScopes() = runTest {
        for (sdk in 31..37) {
            val port = FakeTetheringPort(sdkInt = sdk)
            val report = engine(port).start(request())
            assertEquals("API $sdk", Outcome.Serving("eth0", ServingScope.LOCAL, emptyList()), report.outcome)
            assertEquals(TetheringConstants.CONNECTIVITY_SCOPE_LOCAL, port.requests.single().scope)
        }
    }

    @Test
    fun sharingSetsGlobalScopeOnTwelveAndLater() = runTest {
        val port = FakeTetheringPort(sdkInt = 33)
        val report = engine(port).start(request(SharingScope.SHARE_INTERNET))
        assertEquals(ServingScope.GLOBAL, (report.outcome as Outcome.Serving).scope)
        assertEquals(TetheringConstants.CONNECTIVITY_SCOPE_GLOBAL, port.requests.single().scope)
    }

    @Test
    fun androidElevenRefusesLocalOnlyWithoutFallback() = runTest {
        val port = FakeTetheringPort(sdkInt = 30)
        val report = engine(port).start(request(fallback = false))
        assertEquals(Outcome.Failed(FailureReason.SCOPE_UNSUPPORTED), report.outcome)
        assertTrue("nothing may be sent", port.calls.isEmpty())
    }

    @Test
    fun androidElevenFallsBackWhenAllowedAndReportsIt() = runTest {
        val port = FakeTetheringPort(sdkInt = 30)
        val report = engine(port).start(request(fallback = true))
        assertEquals(Outcome.Serving("eth0", ServingScope.GLOBAL, emptyList()), report.outcome)
        assertNull("Android 11 has no scope", port.requests.single().scope)
        assertTrue(EngineNote.SCOPE_FALLBACK_USED in report.notes)
    }

    @Test
    fun androidElevenSharingSendsNoScope() = runTest {
        val port = FakeTetheringPort(sdkInt = 30)
        val report = engine(port).start(request(SharingScope.SHARE_INTERNET))
        assertTrue(report.outcome is Outcome.Serving)
        assertNull(port.requests.single().scope)
    }

    @Test
    fun alwaysStopsBeforeStarting() = runTest {
        val port = FakeTetheringPort()
        engine(port).start(request())
        assertEquals("stop", port.calls.first())
        assertTrue(port.calls[1].startsWith("start"))
    }

    @Test
    fun secondStartOnAndroidSixteenDoesNotHitDuplicateRequest() = runTest {
        val port = FakeTetheringPort(sdkInt = 36)
        val engine = engine(port)
        engine.start(request())
        val second = engine.start(request(SharingScope.SHARE_INTERNET))
        assertEquals(ServingScope.GLOBAL, (second.outcome as Outcome.Serving).scope)
    }

    @Test
    fun exemptionFirstThenOneRetryWithoutItOnError14() = runTest {
        val port = FakeTetheringPort(refuseExemption = true)
        val report = engine(port).start(request())
        assertTrue(report.outcome is Outcome.Serving)
        assertEquals(listOf(true, false), port.requests.map { it.exemptFromEntitlement })
        assertTrue(EngineNote.ENTITLEMENT_EXEMPTION_REFUSED in report.notes)
    }

    @Test
    fun error14WithoutExemptionIsReportedNotRetriedAgain() = runTest {
        val port = FakeTetheringPort(startCode = 14, refuseExemption = true)
        val report = engine(port).start(request())
        assertEquals(Outcome.Failed(FailureReason.RESULT_CODE, 14), report.outcome)
        assertEquals(2, port.requests.size)
    }

    @Test
    fun refusedLocalScopeFallsBackOnlyWhenAllowed() = runTest {
        val allowed = FakeTetheringPort(refuseLocalScopeWith = 5)
        val withFallback = engine(allowed).start(request(fallback = true))
        assertEquals(ServingScope.GLOBAL, (withFallback.outcome as Outcome.Serving).scope)
        assertTrue(EngineNote.SCOPE_FALLBACK_USED in withFallback.notes)

        val strict = FakeTetheringPort(refuseLocalScopeWith = 5)
        val withoutFallback = engine(strict).start(request(fallback = false))
        assertEquals(Outcome.Failed(FailureReason.RESULT_CODE, 5), withoutFallback.outcome)
        assertTrue(strict.requests.all { it.scope == TetheringConstants.CONNECTIVITY_SCOPE_LOCAL })
    }

    @Test
    fun noScopeFallbackForErrorsAnotherScopeCannotFix() = runTest {
        for (code in listOf(3, 12, 16, 18)) {
            val port = FakeTetheringPort(refuseLocalScopeWith = code)
            val report = engine(port).start(request(fallback = true))
            assertEquals(Outcome.Failed(FailureReason.RESULT_CODE, code), report.outcome)
            assertEquals("code $code", 1, port.requests.size)
        }
    }

    @Test
    fun everyErrorCodeIsReportedAsItself() = runTest {
        for (code in (1..18) - 14) {
            val port = FakeTetheringPort(startCode = code)
            val report = engine(port).start(request(SharingScope.SHARE_INTERNET))
            assertEquals("code $code", Outcome.Failed(FailureReason.RESULT_CODE, code), report.outcome)
        }
    }

    @Test
    fun noAnswerTimesOut() = runTest {
        val port = FakeTetheringPort(answer = false)
        val report = engine(port).start(request())
        assertEquals(Outcome.Failed(FailureReason.NO_RESULT), report.outcome)
        assertTrue(EngineNote.STOP_BEFORE_START_INCOMPLETE in report.notes)
    }

    @Test
    fun acceptedWithoutAdapterIsWaiting() = runTest {
        val port = FakeTetheringPort(adapterPresent = false)
        assertEquals(Outcome.Waiting, engine(port).start(request()).outcome)
    }

    // Phone test 2026-10-09: without an adapter, a start waited the whole confirmation time.
    @Test
    fun withoutAnAdapterTheStartDoesNotWaitForAConfirmation() = runTest {
        val port = FakeTetheringPort(adapterPresent = false)
        assertEquals(Outcome.Waiting, engine(port).start(request()).outcome)
        assertTrue(currentTime < 1_000)
    }

    // Phone test 2026-10-09: a Stop right after a Start waited for the start's confirmation.
    @Test
    fun aStopEndsTheConfirmationWaitOfAStartInProgress() = runTest {
        val port = FakeTetheringPort(slowToServe = true)
        val engine = engine(port)
        val start = async { engine.start(request()) }
        advanceTimeBy(1_000)
        val stop = engine.stop()
        assertEquals(Outcome.Waiting, start.await().outcome)
        assertEquals(Outcome.Stopped, stop.outcome)
        assertTrue(currentTime < 2_000)
    }

    @Test
    fun interfaceErrorAfterAcceptIsAFailureWithItsCode() = runTest {
        val port = FakeTetheringPort(interfaceErrorAfterStart = 10)
        val report = engine(port).start(request())
        assertEquals(Outcome.Failed(FailureReason.INTERFACE_ERROR, 10, "eth0"), report.outcome)
    }

    @Test
    fun anOldErrorIsNotTakenForTheNewResult() = runTest {
        val stale = TetherStates(errored = listOf(ErroredIface(TetherIface("eth0", 5), 10)))
        val port = FakeTetheringPort(initial = stale)
        assertTrue(engine(port).start(request()).outcome is Outcome.Serving)
    }

    @Test
    fun ignoredLocalScopeIsReported() = runTest {
        val port = FakeTetheringPort(ignoreLocalScope = true)
        val report = engine(port).start(request())
        assertEquals(ServingScope.GLOBAL, (report.outcome as Outcome.Serving).scope)
        assertTrue(EngineNote.LOCAL_SCOPE_IGNORED in report.notes)
    }

    @Test
    fun servingReportsTheActualAddresses() = runTest {
        val interfaces = FakeInterfaces(listOf(InterfaceInfo("eth0", true, true, 1000, listOf("169.254.0.42/16"))))
        val report = engine(FakeTetheringPort(), interfaces).start(request())
        assertEquals(listOf("169.254.0.42/16"), (report.outcome as Outcome.Serving).ipv4)
    }

    @Test
    fun withoutEventsAStaticAddressOnTheInterfaceConfirmsTheStart() = runTest {
        val port = FakeTetheringPort(eventsSupported = false)
        val interfaces = FakeInterfaces(listOf(InterfaceInfo("eth0", true, true, null, listOf("169.254.0.42/16"))))
        val report = engine(port, interfaces).start(request())
        assertEquals(Outcome.Serving("eth0", ServingScope.LOCAL, listOf("169.254.0.42/16")), report.outcome)
        assertTrue(EngineNote.EVENTS_UNAVAILABLE in report.notes)
    }

    @Test
    fun withoutEventsAndWithoutTheAddressItIsWaiting() = runTest {
        val port = FakeTetheringPort(eventsSupported = false)
        assertEquals(Outcome.Waiting, engine(port).start(request()).outcome)
    }

    @Test
    fun dhcpModeSendsNoAddresses() = runTest {
        val port = FakeTetheringPort()
        engine(port).start(request(static = false))
        assertNull(port.requests.single().addresses)
    }

    @Test
    fun binderDeathAndMissingApisBecomeFailures() = runTest {
        val died = FakeTetheringPort(startThrows = TetheringServiceDied())
        assertEquals(FailureReason.SERVICE_DIED, (engine(died).start(request()).outcome as Outcome.Failed).reason)
        val missing = FakeTetheringPort(startThrows = TetheringApiMissing("TetheringRequest.Builder"))
        assertEquals(
            Outcome.Failed(FailureReason.API_MISSING, detail = "TetheringRequest.Builder"),
            engine(missing).start(request()).outcome,
        )
    }

    @Test
    fun everyReportEndsWithItsOutcomeStep() = runTest {
        val report = engine(FakeTetheringPort()).start(request())
        assertEquals("outcome", report.steps.last().label)
    }
}
