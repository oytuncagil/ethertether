// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import androidx.annotation.StringRes
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.tether.Operation
import io.github.oytuncagil.ethertether.tether.TetherState
import io.github.oytuncagil.ethertether.tether.WatchdogEvent
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText
import io.github.oytuncagil.ethertether.ui.result.Message
import io.github.oytuncagil.ethertether.ui.result.message
import io.github.oytuncagil.ethertether.ui.result.text

/** The button shown with the home message. */
enum class HomeAction {
    /** Opens the guided Shizuku setup. */
    SETUP,

    /** Shows Shizuku's permission dialog. */
    GRANT,

    /** Hides a watchdog message. */
    DISMISS,

    /** Opens Android's own tethering settings. */
    TETHER_SETTINGS,

    /** Starts the selected profile. */
    START,
}

/** The message on the home screen with its buttons and the notes of the last start. */
data class HomeMessage(
    val message: Message,
    val action: HomeAction?,
    val notes: List<UiText> = emptyList(),
    val secondary: HomeAction? = null,
)

/**
 * The one most important message for [state], or null when everything is fine. Order: a state
 * that Shizuku's stop made unknown, Shizuku not ready, a watchdog event, the engine unreachable, a failed start or stop, waiting for the
 * adapter, then the notes of a successful start.
 */
fun homeMessage(state: TetherState): HomeMessage? {
    val readiness = state.readiness
    if (state.statusUnknown) {
        val message = Message(UiText(R.string.home_unknown), UiText(R.string.home_unknown_next), Tone.ACTION)
        val action = when (readiness) {
            EngineReadiness.Ready, null -> null
            EngineReadiness.PermissionRequired -> HomeAction.GRANT
            else -> HomeAction.SETUP
        }
        return HomeMessage(message, action, secondary = HomeAction.TETHER_SETTINGS)
    }
    if (readiness != null && readiness != EngineReadiness.Ready) {
        val action = if (readiness == EngineReadiness.PermissionRequired) HomeAction.GRANT else HomeAction.SETUP
        return HomeMessage(readiness.message(), action)
    }
    if (state.event == WatchdogEvent.ADAPTER_REMOVED && state.status?.adapterPresent == true && !state.active) {
        // Removing the adapter ended tethering; now that it is back, offer to start again.
        val message = Message(UiText(R.string.home_adapter_back), UiText(R.string.home_adapter_back_next), Tone.ACTION)
        return HomeMessage(message, HomeAction.START, secondary = HomeAction.DISMISS)
    }
    state.event?.let { return HomeMessage(it.message(), HomeAction.DISMISS) }
    state.lastError?.let { return HomeMessage(it.message(), null) }
    val report = state.lastReport
    if (state.operation == null && report?.outcome is Outcome.Failed) return HomeMessage(report.outcome.message(), null, report.notes.map { it.text() })
    if (state.waitingForAdapter) {
        return HomeMessage(Message(UiText(R.string.home_waiting), UiText(R.string.home_waiting_next), Tone.ACTION), null)
    }
    val notes = report?.notes.orEmpty()
    if (state.serving && notes.isNotEmpty()) {
        return HomeMessage(Message(UiText(R.string.home_started_with_notes), null, Tone.OK), null, notes.map { it.text() })
    }
    return null
}

/** The message for a watchdog event. */
fun WatchdogEvent.message(): Message = when (this) {
    WatchdogEvent.ADAPTER_REMOVED -> Message(UiText(R.string.alert_adapter_removed), UiText(R.string.event_adapter_removed_next), Tone.ACTION)
    WatchdogEvent.STOPPED_OUTSIDE -> Message(UiText(R.string.alert_stopped_outside), UiText(R.string.event_stopped_outside_next), Tone.ACTION)
    WatchdogEvent.REARM_FAILED -> Message(UiText(R.string.alert_rearm_failed), UiText(R.string.event_rearm_failed_next), Tone.ACTION)
}

/** The big state word of the status card. */
@StringRes
fun statusTitle(state: TetherState): Int = when {
    state.operation == Operation.STARTING -> R.string.home_state_starting
    state.operation == Operation.STOPPING -> R.string.home_state_stopping
    state.statusUnknown -> R.string.home_state_unknown
    state.serving -> R.string.home_state_on
    state.waitingForAdapter || state.rearming -> R.string.home_state_waiting
    else -> R.string.home_state_off
}

/** The lines of the status card below the state word: profile, interface, addresses, device, adapter, Shizuku. */
fun statusLines(state: TetherState, profileName: String?): List<UiText> {
    val lines = mutableListOf<UiText>()
    val status = state.status
    if (profileName != null && (state.active)) lines += UiText(R.string.home_line_profile, listOf(profileName))
    status?.serving?.let { (iface, scope) ->
        val scopeText = UiText(if (scope == ServingScope.LOCAL) R.string.scope_local else R.string.scope_shared)
        lines += UiText(R.string.home_line_interface, listOf(iface, scopeText))
        val addresses = status.interfaces.firstOrNull { it.name == iface }?.ipv4.orEmpty()
        if (addresses.isNotEmpty()) lines += UiText(R.string.home_line_addresses, listOf(addresses.joinToString()))
        if (status.clients.isNotEmpty()) lines += UiText(R.string.home_line_clients, listOf(status.clients.joinToString()))
    }
    lines += adapterLine(state)
    otherAdapters(state)?.let { lines += UiText(R.string.home_line_other_adapter, listOf(it)) }
    state.readiness?.let { lines += UiText(if (it == EngineReadiness.Ready) R.string.home_line_shizuku_ready else R.string.home_line_shizuku_not_ready) }
    return lines
}

/** Further Ethernet adapters, which Android does not use while one is already chosen. */
private fun otherAdapters(state: TetherState): String? {
    val status = state.status ?: return null
    val used = status.serving?.first ?: status.available ?: status.interfaces.firstOrNull()?.name
    return status.interfaces.map { it.name }.filter { it != used }.distinct().takeIf { it.isNotEmpty() }?.joinToString()
}

private fun adapterLine(state: TetherState): UiText {
    val status = state.status ?: return UiText(R.string.home_line_adapter_unknown)
    if (!status.adapterPresent) return UiText(R.string.home_line_adapter_none)
    val name = status.serving?.first ?: status.available ?: status.interfaces.firstOrNull()?.name.orEmpty()
    val info = status.interfaces.firstOrNull { it.name == name }
    val speed = info?.speedMbps
    return when (info?.hasCarrier) {
        true -> if (speed != null) {
            UiText(R.string.home_line_adapter_cable_speed, listOf(name, speed))
        } else {
            UiText(R.string.home_line_adapter_cable, listOf(name))
        }
        false -> UiText(R.string.home_line_adapter_no_cable, listOf(name))
        null -> UiText(R.string.home_line_adapter, listOf(name))
    }
}
