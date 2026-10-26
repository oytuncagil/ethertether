// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

/** Collects the raw steps and user notes of one engine operation. Not thread-safe. */
internal class OperationLog {
    private val steps = mutableListOf<EngineStep>()
    private val notes = linkedSetOf<EngineNote>()

    /** Records one raw step. */
    fun step(label: String, detail: String) {
        steps += EngineStep(label, detail)
    }

    /** Records a note for the user. */
    fun note(note: EngineNote) {
        notes += note
    }

    /** The steps recorded so far. */
    fun steps(): List<EngineStep> = steps.toList()

    /** The finished report with [outcome]. */
    fun report(outcome: Outcome): EngineReport {
        step("outcome", outcome.toString())
        return EngineReport(outcome, notes.toSet(), steps.toList())
    }
}

/** Readable text of a result code: "0 NO_ERROR", or "42 (unknown)". */
internal fun describeCode(code: Int): String = "$code ${TetherResultCode.fromCode(code)?.name ?: "(unknown)"}"

/** Short readable form of an exception for a raw step. */
internal fun Throwable.describe(): String = "${javaClass.simpleName}: ${message.orEmpty()}"

/** Maps an exception from the port to a failed outcome. */
internal fun Throwable.toFailure(): Outcome.Failed = when (this) {
    is TetheringApiMissing -> Outcome.Failed(FailureReason.API_MISSING, detail = what)
    is TetheringServiceDied -> Outcome.Failed(FailureReason.SERVICE_DIED, detail = describe())
    else -> Outcome.Failed(FailureReason.UNEXPECTED, detail = describe())
}
