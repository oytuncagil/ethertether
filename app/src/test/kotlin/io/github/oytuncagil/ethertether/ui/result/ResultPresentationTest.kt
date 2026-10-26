// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.result

import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.backend.EngineUnavailable
import io.github.oytuncagil.ethertether.backend.UnavailableReason
import io.github.oytuncagil.ethertether.engine.EngineNote
import io.github.oytuncagil.ethertether.engine.FailureReason
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.engine.TetherResultCode
import io.github.oytuncagil.ethertether.model.ValidationError
import io.github.oytuncagil.ethertether.model.ValidationWarning
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ResultPresentationTest {
    @Test
    fun everyKnownResultCodeHasItsOwnTextAndNextStep() {
        val messages = TetherResultCode.entries.map { resultCodeMessage(it.code) }
        assertEquals(TetherResultCode.entries.size, messages.map { it.title }.toSet().size)
        messages.forEach { assertNotNull(it.next) }
        assertEquals(UiText(R.string.code_14), resultCodeMessage(14).title)
        assertEquals(UiText(R.string.code_12_next), resultCodeMessage(12).next)
    }

    @Test
    fun unknownCodesAreNamedWithTheirNumber() {
        assertEquals(UiText(R.string.code_unknown, listOf(19)), resultCodeMessage(19).title)
        assertEquals(UiText(R.string.code_unknown, listOf(-1)), resultCodeMessage(-1).title)
    }

    @Test
    fun onlySuccessCodesLookOk() {
        assertEquals(Tone.OK, resultCodeMessage(0).tone)
        assertEquals(Tone.ACTION, resultCodeMessage(4).tone)
    }

    @Test
    fun everyFailureReasonHasAMessageWithANextStep() {
        FailureReason.entries.forEach { reason ->
            val message = Outcome.Failed(reason, code = 12, detail = "x").message()
            assertNotNull(reason.name, message.next)
            assertEquals(Tone.ACTION, message.tone)
        }
    }

    @Test
    fun interfaceErrorExplainsTheCode() {
        val message = Outcome.Failed(FailureReason.INTERFACE_ERROR, 10, "eth0").message()
        assertEquals(UiText(R.string.outcome_interface_error, listOf("eth0")), message.title)
        assertEquals(UiText(R.string.code_10), message.next)
    }

    @Test
    fun servingNamesScopeInterfaceAndAddresses() {
        val local = Outcome.Serving("eth0", ServingScope.LOCAL, listOf("169.254.0.42/16")).message()
        assertEquals(UiText(R.string.outcome_serving_local, listOf("eth0")), local.title)
        assertEquals(UiText(R.string.outcome_serving_next, listOf("169.254.0.42/16")), local.next)
        assertEquals(UiText(R.string.outcome_serving_global, listOf("eth1")), Outcome.Serving("eth1", ServingScope.GLOBAL, emptyList()).message().title)
        assertNull(Outcome.Stopped.message().next)
        assertNotNull(Outcome.Waiting.message().next)
    }

    @Test
    fun everyNoteHasItsOwnSentence() {
        assertEquals(EngineNote.entries.size, EngineNote.entries.map { it.text() }.toSet().size)
    }

    @Test
    fun everyUnavailableReasonHasANextStep() {
        UnavailableReason.entries.forEach { assertNotNull(EngineUnavailable(it, "uid 0").message().next) }
        assertEquals(UiText(R.string.unavailable_wrong_uid, listOf("uid 0")), EngineUnavailable(UnavailableReason.WRONG_UID, "uid 0").message().title)
    }

    @Test
    fun everyReadinessStateHasAMessage() {
        val states = listOf(
            EngineReadiness.ShizukuNotInstalled, EngineReadiness.ShizukuNotRunning, EngineReadiness.SuiNotSupported,
            EngineReadiness.ShizukuTooOld(10), EngineReadiness.PermissionRequired, EngineReadiness.PermissionDenied,
            EngineReadiness.RunsAsRoot, EngineReadiness.UnexpectedUid(1000), EngineReadiness.Ready,
        )
        states.forEach { state ->
            val message = state.message()
            if (state == EngineReadiness.Ready) assertEquals(Tone.OK, message.tone) else assertNotNull(state.toString(), message.next)
        }
    }

    @Test
    fun everyValidationProblemHasItsOwnText() {
        assertEquals(ValidationError.entries.size, ValidationError.entries.map { it.text() }.toSet().size)
        assertEquals(ValidationWarning.entries.size, ValidationWarning.entries.map { it.text() }.toSet().size)
    }
}
