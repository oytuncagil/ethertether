// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.backend.EngineUnavailable
import io.github.oytuncagil.ethertether.backend.UnavailableReason
import io.github.oytuncagil.ethertether.engine.EngineNote
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.FailureReason
import io.github.oytuncagil.ethertether.engine.InterfaceInfo
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.tether.Operation
import io.github.oytuncagil.ethertether.tether.TetherState
import io.github.oytuncagil.ethertether.tether.WatchdogEvent
import io.github.oytuncagil.ethertether.testing.idleStatus
import io.github.oytuncagil.ethertether.testing.noAdapterStatus
import io.github.oytuncagil.ethertether.testing.servingStatus
import io.github.oytuncagil.ethertether.ui.common.UiText
import io.github.oytuncagil.ethertether.ui.result.message
import io.github.oytuncagil.ethertether.ui.result.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePresentationTest {
    private val ready = TetherState(readiness = EngineReadiness.Ready)

    @Test
    fun nothingToSayWhenReadyAndIdle() {
        assertNull(homeMessage(ready.copy(status = idleStatus())))
    }

    @Test
    fun shizukuComesFirstWithTheMatchingButton() {
        val missing = homeMessage(TetherState(readiness = EngineReadiness.ShizukuNotInstalled, event = WatchdogEvent.STOPPED_OUTSIDE))
        assertEquals(HomeAction.SETUP, missing?.action)
        assertEquals(UiText(R.string.readiness_not_installed), missing?.message?.title)
        assertEquals(HomeAction.GRANT, homeMessage(TetherState(readiness = EngineReadiness.PermissionRequired))?.action)
    }

    @Test
    fun watchdogEventsCanBeDismissed() {
        WatchdogEvent.entries.forEach { event ->
            val message = homeMessage(ready.copy(event = event))
            assertEquals(HomeAction.DISMISS, message?.action)
            assertEquals(event.message(), message?.message)
        }
    }

    @Test
    fun unreachableEngineIsExplained() {
        val message = homeMessage(ready.copy(lastError = EngineUnavailable(UnavailableReason.BIND_TIMEOUT)))
        assertEquals(UiText(R.string.unavailable_bind_timeout), message?.message?.title)
        assertNull(message?.action)
    }

    @Test
    fun failedStartShowsTheResultAndItsNotes() {
        val report = EngineReport(Outcome.Failed(FailureReason.RESULT_CODE, 12), setOf(EngineNote.ENTITLEMENT_EXEMPTION_REFUSED))
        val message = homeMessage(ready.copy(lastReport = report))
        assertEquals(report.outcome.message(), message?.message)
        assertEquals(listOf(EngineNote.ENTITLEMENT_EXEMPTION_REFUSED.text()), message?.notes)
    }

    @Test
    fun failureIsHiddenWhileANewOperationRuns() {
        val report = EngineReport(Outcome.Failed(FailureReason.NO_RESULT))
        assertNull(homeMessage(ready.copy(lastReport = report, operation = Operation.STARTING)))
    }

    @Test
    fun waitingForTheAdapterTellsWhatToDo() {
        val message = homeMessage(ready.copy(armed = true, status = noAdapterStatus()))
        assertEquals(UiText(R.string.home_waiting), message?.message?.title)
        assertEquals(UiText(R.string.home_waiting_next), message?.message?.next)
    }

    @Test
    fun servingWithNotesShowsThem() {
        val report = EngineReport(Outcome.Serving("eth0", ServingScope.LOCAL, emptyList()), setOf(EngineNote.ENTITLEMENT_EXEMPTION_REFUSED))
        val message = homeMessage(ready.copy(armed = true, status = servingStatus(), lastReport = report))
        assertEquals(UiText(R.string.home_started_with_notes), message?.message?.title)
        assertEquals(1, message?.notes?.size)
    }

    @Test
    fun statusTitleFollowsTheState() {
        assertEquals(R.string.home_state_off, statusTitle(ready))
        assertEquals(R.string.home_state_starting, statusTitle(ready.copy(operation = Operation.STARTING)))
        assertEquals(R.string.home_state_stopping, statusTitle(ready.copy(operation = Operation.STOPPING)))
        assertEquals(R.string.home_state_on, statusTitle(ready.copy(status = servingStatus())))
        assertEquals(R.string.home_state_waiting, statusTitle(ready.copy(armed = true, status = noAdapterStatus())))
        assertEquals(R.string.home_state_waiting, statusTitle(ready.copy(rearming = true)))
    }

    @Test
    fun servingLinesNameProfileInterfaceAddressesClientsAndAdapter() {
        val status = servingStatus(ServingScope.GLOBAL).copy(clients = listOf("169.254.0.43"))
        val lines = statusLines(ready.copy(armed = true, status = status), "Car")
        assertEquals(
            listOf(
                UiText(R.string.home_line_profile, listOf("Car")),
                UiText(R.string.home_line_interface, listOf("eth0", UiText(R.string.scope_shared))),
                UiText(R.string.home_line_addresses, listOf("169.254.0.42/16")),
                UiText(R.string.home_line_clients, listOf("169.254.0.43")),
                UiText(R.string.home_line_adapter_cable_speed, listOf("eth0", 1000)),
                UiText(R.string.home_line_shizuku_ready),
            ),
            lines,
        )
    }

    @Test
    fun adapterLineCoversEveryCase() {
        val shizuku = UiText(R.string.home_line_shizuku_ready)
        assertEquals(listOf(UiText(R.string.home_line_adapter_unknown), shizuku), statusLines(ready, null))
        assertEquals(listOf(UiText(R.string.home_line_adapter_none), shizuku), statusLines(ready.copy(status = noAdapterStatus()), null))
        assertEquals(listOf(UiText(R.string.home_line_adapter_no_cable, listOf("eth0")), shizuku), statusLines(ready.copy(status = idleStatus()), null))
        val cableNoSpeed = idleStatus().copy(interfaces = listOf(InterfaceInfo("eth0", true, true, null, emptyList())))
        assertEquals(listOf(UiText(R.string.home_line_adapter_cable, listOf("eth0")), shizuku), statusLines(ready.copy(status = cableNoSpeed), "Car"))
    }

    @Test
    fun theShizukuLineFollowsReadinessAndIsLeftOutBeforeTheFirstCheck() {
        assertEquals(UiText(R.string.home_line_shizuku_not_ready), statusLines(TetherState(readiness = EngineReadiness.ShizukuNotRunning), null).last())
        assertEquals(listOf(UiText(R.string.home_line_adapter_unknown)), statusLines(TetherState(), null))
    }

    @Test
    fun anUnknownStateComesFirstWithTheTetheringSettings() {
        val message = homeMessage(TetherState(readiness = EngineReadiness.ShizukuNotRunning, statusUnknown = true, event = WatchdogEvent.STOPPED_OUTSIDE))
        assertEquals(UiText(R.string.home_unknown), message?.message?.title)
        assertEquals(HomeAction.SETUP, message?.action)
        assertEquals(HomeAction.TETHER_SETTINGS, message?.secondary)
        assertEquals(HomeAction.GRANT, homeMessage(TetherState(readiness = EngineReadiness.PermissionRequired, statusUnknown = true))?.action)
        assertNull(homeMessage(ready.copy(statusUnknown = true))?.action)
        assertEquals(R.string.home_state_unknown, statusTitle(TetherState(statusUnknown = true)))
    }

    @Test
    fun whenTheRemovedAdapterIsBackStartIsOffered() {
        val back = homeMessage(ready.copy(event = WatchdogEvent.ADAPTER_REMOVED, status = idleStatus()))
        assertEquals(UiText(R.string.home_adapter_back), back?.message?.title)
        assertEquals(HomeAction.START, back?.action)
        assertEquals(HomeAction.DISMISS, back?.secondary)
        val gone = homeMessage(ready.copy(event = WatchdogEvent.ADAPTER_REMOVED, status = noAdapterStatus()))
        assertEquals(WatchdogEvent.ADAPTER_REMOVED.message(), gone?.message)
    }

    @Test
    fun aSecondAdapterIsNamedAsNotUsed() {
        val two = servingStatus().copy(interfaces = servingStatus().interfaces + InterfaceInfo("eth1", true, null, null, emptyList()))
        val lines = statusLines(ready.copy(armed = true, status = two), "Car")
        assertTrue(lines.contains(UiText(R.string.home_line_other_adapter, listOf("eth1"))))
        assertFalse(statusLines(ready.copy(armed = true, status = servingStatus()), "Car").any { it.id == R.string.home_line_other_adapter })
    }
}
