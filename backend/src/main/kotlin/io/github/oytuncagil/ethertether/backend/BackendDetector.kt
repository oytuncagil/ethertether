// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import io.github.oytuncagil.ethertether.model.BackendKind
import io.github.oytuncagil.ethertether.model.BackendState
import io.github.oytuncagil.ethertether.model.BackendStatus

/**
 * Finds out whether one privileged backend can be used. Detection is read-only: it never
 * requests a permission, never starts a process and never runs su.
 */
interface BackendDetector {
    /** The backend this detector checks. */
    val kind: BackendKind

    /** Returns the current state; may throw, which [BackendDetectors] reports as failed. */
    fun detect(): BackendState
}

/** Runs all detectors in a fixed order: Shizuku, Sui, root. */
class BackendDetectors(private val detectors: List<BackendDetector>) {
    /** One status per detector; a detector that throws is reported as failed, not hidden. */
    fun detectAll(): List<BackendStatus> = detectors.map { detector ->
        val state = runCatching { detector.detect() }
            .getOrElse { BackendState.Failed("${it.javaClass.simpleName}: ${it.message}") }
        BackendStatus(detector.kind, state)
    }
}
