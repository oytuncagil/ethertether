// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.tether.TetherState
import io.github.oytuncagil.ethertether.testing.idleStatus
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun show(state: TetherState, dark: Boolean = false) {
        val actions = OnboardingActions(
            onOpenSetup = { clicks += "setup" },
            onGrant = { clicks += "grant" },
            onRefresh = { clicks += "refresh" },
            onFinish = { clicks += "finish" },
        )
        compose.setContent { EtherTetherTheme(darkTheme = dark, dynamicColor = false) { OnboardingScreen(state, actions) } }
    }

    private fun next(times: Int = 1) = repeat(times) {
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
    }

    @Test
    fun theFirstPageExplainsTheAppAndSkipEndsTheGuide() {
        show(TetherState())
        compose.onNodeWithText("Ethernet tethering, your way").assertExists()
        compose.onNodeWithText("Page 1 of 4").assertExists()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(listOf("finish"), clicks)
    }

    @Test
    fun theShizukuPageShowsTheStateWithStepsAndCheckAgain() {
        show(TetherState(readiness = EngineReadiness.ShizukuNotInstalled))
        next()
        compose.onNodeWithText("Shizuku gives the access").assertExists()
        compose.onNodeWithText("Shizuku is not installed.", substring = true).assertExists()
        compose.onNodeWithText("Show me the steps").performScrollTo().performClick()
        compose.onNodeWithText("Check again").performScrollTo().performClick()
        assertEquals(listOf("setup", "refresh"), clicks)
    }

    @Test
    fun theShizukuPageAsksForAccessWhenNeeded() {
        show(TetherState(readiness = EngineReadiness.PermissionRequired))
        next()
        compose.onNodeWithText("Grant access").performScrollTo().performClick()
        assertEquals(listOf("grant"), clicks)
    }

    // Only Shizuku is supported, so there is no Sui and root page.
    @Test
    fun theThirdPageIsTheAdapterTestWithoutASuiAndRootPage() {
        show(TetherState())
        next(2)
        compose.onNodeWithText("Test your adapter").assertExists()
        compose.onNodeWithText("Sui and root").assertDoesNotExist()
    }

    @Test
    fun theAdapterTestNeedsShizuku() {
        show(TetherState(readiness = EngineReadiness.ShizukuNotRunning))
        next(2)
        compose.onNodeWithText("The adapter can be checked once Shizuku is ready.").assertExists()
    }

    @Test
    fun theAdapterTestShowsTheAdapter() {
        show(TetherState(readiness = EngineReadiness.Ready, status = idleStatus()))
        next(2)
        compose.onNodeWithText("Test your adapter").assertExists()
        compose.onNodeWithText("Adapter: eth0, no cable").assertExists()
        compose.onNodeWithText("Check adapter").performScrollTo().performClick()
        assertEquals(listOf("refresh"), clicks)
    }

    @Test
    fun theLastPageFinishes() {
        show(TetherState(readiness = EngineReadiness.Ready))
        next(3)
        compose.onNodeWithText("Page 4 of 4").assertExists()
        compose.onNodeWithText("Start using EtherTether").performClick()
        assertEquals(listOf("finish"), clicks)
    }

    @Test
    @Config(qualifiers = "night")
    fun rendersInDarkTheme() {
        show(TetherState(), dark = true)
        compose.onNodeWithText("Ethernet tethering, your way").assertExists()
    }
}
