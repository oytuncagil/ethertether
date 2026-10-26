// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.tether.Operation
import io.github.oytuncagil.ethertether.tether.TetherState
import io.github.oytuncagil.ethertether.tether.WatchdogEvent
import io.github.oytuncagil.ethertether.testing.noAdapterStatus
import io.github.oytuncagil.ethertether.testing.servingStatus
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()
    private val ready = TetherState(readiness = EngineReadiness.Ready)

    private fun show(tether: TetherState, profiles: ProfileList = ProfileList.initial, dark: Boolean = false) {
        val actions = HomeActions(
            onStart = { clicks += "start" },
            onStop = { clicks += "stop" },
            onSelect = { clicks += "select $it" },
            onGrant = { clicks += "grant" },
            onDismiss = { clicks += "dismiss" },
            onOpenSetup = { clicks += "setup" },
            onOpenProfiles = { clicks += "profiles" },
            onOpenLog = { clicks += "log" },
            onOpenSettings = { clicks += "settings" },
            onOpenAbout = { clicks += "about" },
            onOpenTetherSettings = { clicks += "tether settings" },
        )
        compose.setContent {
            EtherTetherTheme(darkTheme = dark, dynamicColor = false) { HomeScreen(HomeUiState(tether, profiles), actions) }
        }
    }

    @Test
    fun startNamesTheSelectedProfileAndStarts() {
        show(ready)
        compose.onNodeWithContentDescription("Ethernet tethering: Off", substring = true).assertExists()
        compose.onNodeWithContentDescription("Start with profile Car ENET link-local 169.254.0.42/16").assertIsEnabled().performClick()
        assertEquals(listOf("start"), clicks)
    }

    @Test
    fun withoutShizukuStartIsDisabledAndSetupIsOffered() {
        show(TetherState(readiness = EngineReadiness.ShizukuNotRunning))
        compose.onNodeWithContentDescription("Start with profile", substring = true).assertIsNotEnabled()
        compose.onNodeWithText("Shizuku is installed but not running.", substring = true).assertExists()
        compose.onNodeWithText("Set up Shizuku").performScrollTo().performClick()
        assertEquals(listOf("setup"), clicks)
    }

    @Test
    fun missingAccessOffersGrant() {
        show(TetherState(readiness = EngineReadiness.PermissionRequired))
        compose.onNodeWithText("Grant access").performScrollTo().performClick()
        assertEquals(listOf("grant"), clicks)
    }

    @Test
    fun servingShowsOnTheAddressesAndStop() {
        show(ready.copy(armed = true, status = servingStatus(), profileId = "preset-car"))
        compose.onNodeWithContentDescription("Ethernet tethering: On", substring = true).assertExists()
        compose.onNodeWithContentDescription("Phone address: 169.254.0.42/16", substring = true).assertExists()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(listOf("stop"), clicks)
    }

    @Test
    fun theProfileCannotChangeWhileActive() {
        show(ready.copy(armed = true, status = servingStatus(), profileId = "preset-car"))
        compose.onNodeWithText("Stop tethering to change the profile.").performScrollTo().assertExists()
        compose.onNodeWithText("Isolated lab network").performScrollTo().performClick()
        assertEquals(emptyList<String>(), clicks)
    }

    @Test
    fun aProfileIsPickedWithOneTap() {
        show(ready)
        compose.onNodeWithText("Car ENET link-local 169.254.0.42/16").performScrollTo()
        compose.onNodeWithText("Isolated lab network").performScrollTo().performClick()
        assertEquals(listOf("select preset-lab"), clicks)
    }

    @Test
    fun theSelectedProfileIsMarked() {
        show(ready, ProfileList.initial.select("preset-lab"))
        compose.onNodeWithContentDescription("Start with profile Isolated lab network").assertExists()
        compose.onNode(selectableRow("Isolated lab network")).assertIsSelected()
        compose.onNode(selectableRow("Car ENET link-local 169.254.0.42/16")).assertIsNotSelected()
    }

    @Test
    fun waitingForTheAdapterIsExplainedAndStoppable() {
        show(ready.copy(armed = true, status = noAdapterStatus()))
        compose.onNodeWithContentDescription("Ethernet tethering: Waiting for the adapter", substring = true).assertExists()
        compose.onNodeWithText("Plug in the adapter.", substring = true).assertExists()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(listOf("stop"), clicks)
    }

    @Test
    fun watchdogMessageCanBeDismissed() {
        show(ready.copy(event = WatchdogEvent.ADAPTER_REMOVED))
        compose.onNodeWithText("The adapter was removed", substring = true).assertExists()
        compose.onNodeWithText("Dismiss").performScrollTo().performClick()
        assertEquals(listOf("dismiss"), clicks)
    }

    // Changed in the phone test fixes: a start that takes long or hangs must always be stoppable.
    @Test
    fun stopIsOfferedWhileStarting() {
        show(ready.copy(operation = Operation.STARTING))
        compose.onNodeWithContentDescription("Ethernet tethering: Starting", substring = true).assertExists()
        compose.onNodeWithText("Stop").assertIsEnabled().performClick()
        assertEquals(listOf("stop"), clicks)
    }

    @Test
    fun theButtonIsDisabledWhileStopping() {
        show(ready.copy(operation = Operation.STOPPING, status = servingStatus()))
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.onNodeWithContentDescription("Start with profile", substring = true).assertIsNotEnabled()
    }

    @Test
    fun anUnknownStateAfterShizukuStoppedOffersSetupAndTetheringSettings() {
        show(TetherState(readiness = EngineReadiness.ShizukuNotRunning, statusUnknown = true))
        compose.onNodeWithContentDescription("Ethernet tethering: Unknown", substring = true).assertExists()
        compose.onNodeWithText("Shizuku stopped, so EtherTether cannot see or stop Ethernet tethering", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Set up Shizuku").performScrollTo().performClick()
        compose.onNodeWithText("Open tethering settings").performScrollTo().performClick()
        assertEquals(listOf("setup", "tether settings"), clicks)
    }

    @Test
    fun withoutProfilesStartIsDisabled() {
        show(ready, ProfileList(emptyList(), null))
        compose.onNodeWithText("No profiles yet. Create one under Manage profiles.").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Start with profile", substring = true).assertIsNotEnabled()
    }

    @Test
    fun theTopBarOpensTheOtherScreens() {
        show(ready)
        compose.onNodeWithContentDescription("Profiles").performClick()
        compose.onNodeWithContentDescription("Log").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("Manage profiles").performScrollTo().performClick()
        assertEquals(listOf("profiles", "log", "settings", "about", "profiles"), clicks)
    }

    @Test
    @Config(qualifiers = "night")
    fun rendersInDarkTheme() {
        show(ready.copy(armed = true, status = servingStatus()), dark = true)
        compose.onNodeWithContentDescription("Ethernet tethering: On", substring = true).assertExists()
    }

    private fun selectableRow(text: String) = hasText(text, substring = true) and isSelectable()
}
