// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawStepsTest {
    private val request = TetherRequest(null, SharingScope.LOCAL_ONLY, false)

    private fun steps(port: FakeTetheringPort = FakeTetheringPort()) = RawSteps(TetheringEngine(port, FakeInterfaces()))

    @Test
    fun startOnlyDoesNotStopFirst() = runTest {
        val port = FakeTetheringPort()
        val result = steps(port).run(RawStep.START_ONLY, request)
        assertEquals(listOf("start(scope=2, exempt=true)"), port.calls)
        assertEquals("0 NO_ERROR", result.single().detail)
    }

    @Test
    fun startWithoutExemptionSendsFalse() = runTest {
        val port = FakeTetheringPort()
        steps(port).run(RawStep.START_ONLY_NO_EXEMPTION, request)
        assertEquals(false, port.requests.single().exemptFromEntitlement)
    }

    @Test
    fun stopOnlyShowsTheRawCode() = runTest {
        val result = steps(FakeTetheringPort(stopCode = 7)).run(RawStep.STOP_ONLY, null)
        assertEquals("7 UNTETHER_IFACE_ERROR", result.single().detail)
    }

    @Test
    fun stepsThatNeedARequestSayWhenItIsMissing() = runTest {
        val result = steps().run(RawStep.BUILD_REQUEST, null)
        assertEquals("error", result.single().label)
    }

    @Test
    fun everyStepRunsWithoutThrowing() = runTest {
        RawStep.entries.forEach { step ->
            assertTrue(step.name, steps().run(step, request).isNotEmpty())
        }
    }

    @Test
    fun unknownCodesAreNamedAsUnknown() {
        assertEquals("42 (unknown)", describeCode(42))
    }
}
