// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.service

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.engine.EngineNote
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.EngineStep
import io.github.oytuncagil.ethertether.engine.FailureReason
import io.github.oytuncagil.ethertether.engine.InterfaceInfo
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.model.TetherMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Round trips through a real Parcel, as across the binder between app and user service. */
@RunWith(AndroidJUnit4::class)
class EngineCodecTest {
    private fun Bundle.throughParcel(): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(this)
            parcel.setDataPosition(0)
            return parcel.readBundle(javaClass.classLoader)!!
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun inputRoundTrips() {
        val input = TetherInput(TetherMode.STATIC, "169.254.0.42", "169.254.0.1", "/16", SharingScope.SHARE_INTERNET, true)
        assertEquals(input, EngineCodec.decodeInput(EngineCodec.encodeInput(input).throughParcel()))
    }

    @Test
    fun aBundleThatIsNoRequestDecodesToNull() {
        assertNull(EngineCodec.decodeInput(Bundle()))
        assertNull(EngineCodec.decodeInput(Bundle().apply { putString("mode", "BRIDGE"); putString("scope", "LOCAL_ONLY") }))
    }

    @Test
    fun everyOutcomeRoundTrips() {
        val outcomes = listOf(
            Outcome.Serving("eth0", ServingScope.LOCAL, listOf("169.254.0.42/16")),
            Outcome.Waiting,
            Outcome.Stopped,
            Outcome.Failed(FailureReason.RESULT_CODE, 12, ""),
            Outcome.Failed(FailureReason.API_MISSING, null, "Builder"),
        )
        outcomes.forEach { outcome ->
            val report = EngineReport(outcome, setOf(EngineNote.SCOPE_FALLBACK_USED), listOf(EngineStep("stopTethering", "0 NO_ERROR")))
            assertEquals(report, EngineCodec.decodeReport(EngineCodec.encodeReport(report).throughParcel()))
        }
    }

    @Test
    fun unknownFailureReasonReadsAsUnexpected() {
        val bundle = EngineCodec.encodeReport(EngineReport(Outcome.Failed(FailureReason.NO_RESULT)))
        bundle.getBundle("outcome")!!.putString("reason", "FROM_THE_FUTURE")
        assertEquals(FailureReason.UNEXPECTED, (EngineCodec.decodeReport(bundle).outcome as Outcome.Failed).reason)
    }

    @Test
    fun statusRoundTrips() {
        val status = EngineStatus(
            sdkInt = 36,
            eventsAvailable = true,
            serving = "eth0" to ServingScope.GLOBAL,
            error = "eth1" to 10,
            available = "eth0",
            interfaces = listOf(
                InterfaceInfo("eth0", true, true, 1000, listOf("169.254.0.42/16")),
                InterfaceInfo("eth1", false, null, null, emptyList()),
            ),
        )
        assertEquals(status, EngineCodec.decodeStatus(EngineCodec.encodeStatus(status).throughParcel()))
    }

    @Test
    fun emptyStatusRoundTrips() {
        val status = EngineStatus(30, false, null, null, null, emptyList())
        assertEquals(status, EngineCodec.decodeStatus(EngineCodec.encodeStatus(status).throughParcel()))
    }

    @Test
    fun stepsRoundTrip() {
        val steps = listOf(EngineStep("diagnose", "API 36"), EngineStep("diagnose", ""))
        assertEquals(steps, EngineCodec.decodeSteps(EngineCodec.encodeSteps(steps).throughParcel()))
    }
}
