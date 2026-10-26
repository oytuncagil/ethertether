// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import androidx.annotation.StringRes
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText
import io.github.oytuncagil.ethertether.model.BackendKind
import io.github.oytuncagil.ethertether.model.BackendState
import io.github.oytuncagil.ethertether.model.BackendStatus
import io.github.oytuncagil.ethertether.model.KnownUids

/** Everything the screen needs to draw one backend row. */
data class BackendPresentation(
    @param:StringRes val name: Int,
    val state: UiText,
    val hint: UiText,
    val tone: Tone,
    val canGrant: Boolean,
)

/** Maps a detector result to plain-language texts with the next step. */
fun BackendStatus.present(): BackendPresentation {
    val (stateText, hint, tone) = describe(kind, state)
    val canGrant = state is BackendState.PermissionRequired && kind != BackendKind.ROOT
    return BackendPresentation(kind.label(), stateText, hint, tone, canGrant)
}

private fun BackendKind.label(): Int = when (this) {
    BackendKind.SHIZUKU -> R.string.backend_shizuku
    BackendKind.SUI -> R.string.backend_sui
    BackendKind.ROOT -> R.string.backend_root
}

private fun describe(kind: BackendKind, state: BackendState): Triple<UiText, UiText, Tone> = when (state) {
    BackendState.NotFound -> Triple(UiText(R.string.state_not_installed), notFoundHint(kind), Tone.ABSENT)
    BackendState.NotRunning ->
        Triple(UiText(R.string.state_not_running), UiText(R.string.hint_shizuku_not_running), Tone.ACTION)
    BackendState.PermissionRequired ->
        Triple(UiText(R.string.state_permission_required), UiText(R.string.hint_permission_required), Tone.ACTION)
    BackendState.Available -> Triple(UiText(R.string.state_su_found), UiText(R.string.hint_root_available), Tone.OK)
    is BackendState.Ready -> describeReady(kind, state.uid)
    is BackendState.Failed ->
        Triple(UiText(R.string.state_failed), UiText(R.string.hint_failed, listOf(state.reason)), Tone.ACTION)
}

private fun notFoundHint(kind: BackendKind): UiText = when (kind) {
    BackendKind.SHIZUKU -> UiText(R.string.hint_shizuku_not_installed)
    BackendKind.SUI -> UiText(R.string.hint_sui_not_installed)
    BackendKind.ROOT -> UiText(R.string.hint_root_not_found)
}

private fun describeReady(kind: BackendKind, uid: Int): Triple<UiText, UiText, Tone> = when {
    uid == KnownUids.SHELL -> Triple(UiText(R.string.state_ready_shell), UiText(R.string.hint_ready), Tone.OK)
    // Only the shell identity is supported, so Shizuku running as root needs action.
    uid == KnownUids.ROOT && kind == BackendKind.SHIZUKU ->
        Triple(UiText(R.string.state_ready_root), UiText(R.string.hint_shizuku_ready_root), Tone.ACTION)
    uid == KnownUids.ROOT -> Triple(UiText(R.string.state_ready_root), UiText(R.string.hint_ready), Tone.OK)
    else -> Triple(UiText(R.string.state_ready_uid, listOf(uid)), UiText(R.string.hint_ready), Tone.ACTION)
}
