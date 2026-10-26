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
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText

/** A plain-language message: what happened and, when there is one, the next step. */
data class Message(val title: UiText, val next: UiText?, val tone: Tone)

private val CODE_TEXTS = listOf(
    R.string.code_0 to R.string.code_0_next, R.string.code_1 to R.string.code_1_next,
    R.string.code_2 to R.string.code_2_next, R.string.code_3 to R.string.code_3_next,
    R.string.code_4 to R.string.code_4_next, R.string.code_5 to R.string.code_5_next,
    R.string.code_6 to R.string.code_6_next, R.string.code_7 to R.string.code_7_next,
    R.string.code_8 to R.string.code_8_next, R.string.code_9 to R.string.code_9_next,
    R.string.code_10 to R.string.code_10_next, R.string.code_11 to R.string.code_11_next,
    R.string.code_12 to R.string.code_12_next, R.string.code_13 to R.string.code_13_next,
    R.string.code_14 to R.string.code_14_next, R.string.code_15 to R.string.code_15_next,
    R.string.code_16 to R.string.code_16_next, R.string.code_17 to R.string.code_17_next,
    R.string.code_18 to R.string.code_18_next,
)

/** The message for a TETHER_ERROR_* [code]; unknown codes are named, never guessed. */
fun resultCodeMessage(code: Int): Message {
    val texts = CODE_TEXTS.getOrNull(code)
        ?: return Message(UiText(R.string.code_unknown, listOf(code)), UiText(R.string.code_unknown_next), Tone.ACTION)
    val tone = if (code == 0 || code == 17) Tone.OK else Tone.ACTION
    return Message(UiText(texts.first), UiText(texts.second), tone)
}

/** The message for an engine [Outcome]. */
fun Outcome.message(): Message = when (this) {
    is Outcome.Serving -> {
        val title = if (scope == ServingScope.LOCAL) R.string.outcome_serving_local else R.string.outcome_serving_global
        val next = if (ipv4.isEmpty()) UiText(R.string.outcome_serving_next_unknown) else UiText(R.string.outcome_serving_next, listOf(ipv4.joinToString()))
        Message(UiText(title, listOf(iface)), next, Tone.OK)
    }
    Outcome.Waiting -> Message(UiText(R.string.outcome_waiting), UiText(R.string.outcome_waiting_next), Tone.ACTION)
    Outcome.Stopped -> Message(UiText(R.string.outcome_stopped), null, Tone.OK)
    is Outcome.Failed -> failureMessage(this)
}

private fun failureMessage(failed: Outcome.Failed): Message {
    fun of(title: Int, next: Int, vararg args: Any) = Message(UiText(title, args.toList()), UiText(next), Tone.ACTION)
    return when (failed.reason) {
        FailureReason.RESULT_CODE -> resultCodeMessage(failed.code ?: -1)
        FailureReason.INTERFACE_ERROR -> {
            val code = resultCodeMessage(failed.code ?: -1)
            Message(UiText(R.string.outcome_interface_error, listOf(failed.detail)), code.title, Tone.ACTION)
        }
        FailureReason.NO_RESULT -> of(R.string.failure_no_result, R.string.failure_no_result_next)
        FailureReason.API_MISSING -> of(R.string.failure_api_missing, R.string.failure_api_missing_next, failed.detail)
        FailureReason.SERVICE_DIED -> of(R.string.failure_service_died, R.string.failure_service_died_next)
        FailureReason.SCOPE_UNSUPPORTED -> of(R.string.failure_scope_unsupported, R.string.failure_scope_unsupported_next)
        FailureReason.UNEXPECTED -> of(R.string.failure_unexpected, R.string.failure_unexpected_next, failed.detail)
    }
}

/** The sentence for an engine note. */
fun EngineNote.text(): UiText = UiText(
    when (this) {
        EngineNote.ENTITLEMENT_EXEMPTION_REFUSED -> R.string.note_exemption_refused
        EngineNote.SCOPE_FALLBACK_USED -> R.string.note_scope_fallback
        EngineNote.LOCAL_SCOPE_IGNORED -> R.string.note_local_scope_ignored
        EngineNote.EVENTS_UNAVAILABLE -> R.string.note_events_unavailable
        EngineNote.STOP_BEFORE_START_INCOMPLETE -> R.string.note_stop_incomplete
    },
)

/** The message when the engine could not be reached. */
fun EngineUnavailable.message(): Message {
    val (title, next) = when (reason) {
        UnavailableReason.BIND_TIMEOUT -> R.string.unavailable_bind_timeout to R.string.unavailable_bind_timeout_next
        UnavailableReason.BIND_FAILED -> R.string.unavailable_bind_failed to R.string.unavailable_bind_failed_next
        UnavailableReason.WRONG_UID -> R.string.unavailable_wrong_uid to R.string.unavailable_wrong_uid_next
        UnavailableReason.PROTOCOL_MISMATCH -> R.string.unavailable_protocol to R.string.unavailable_protocol_next
        UnavailableReason.DIED -> R.string.unavailable_died to R.string.unavailable_died_next
        UnavailableReason.NO_ANSWER -> R.string.unavailable_no_answer to R.string.unavailable_no_answer_next
    }
    val args = if (reason == UnavailableReason.WRONG_UID) listOf<Any>(detail) else emptyList()
    return Message(UiText(title, args), UiText(next), Tone.ACTION)
}

/** The message for the Shizuku readiness, with the next step. */
fun EngineReadiness.message(): Message {
    fun action(title: Int, next: Int, vararg args: Any) = Message(UiText(title, args.toList()), UiText(next), Tone.ACTION)
    return when (this) {
        EngineReadiness.ShizukuNotInstalled ->
            Message(UiText(R.string.readiness_not_installed), UiText(R.string.readiness_not_installed_next), Tone.ABSENT)
        EngineReadiness.ShizukuNotRunning -> action(R.string.readiness_not_running, R.string.readiness_not_running_next)
        EngineReadiness.SuiNotSupported -> action(R.string.readiness_sui, R.string.readiness_sui_next)
        is EngineReadiness.ShizukuTooOld -> action(R.string.readiness_too_old, R.string.readiness_too_old_next, version)
        EngineReadiness.PermissionRequired -> action(R.string.readiness_permission, R.string.readiness_permission_next)
        EngineReadiness.PermissionDenied -> action(R.string.readiness_denied, R.string.readiness_denied_next)
        EngineReadiness.RunsAsRoot -> action(R.string.readiness_root, R.string.readiness_root_next)
        is EngineReadiness.UnexpectedUid -> action(R.string.readiness_uid, R.string.readiness_root_next, uid)
        EngineReadiness.Ready -> Message(UiText(R.string.readiness_ready), null, Tone.OK)
    }
}
