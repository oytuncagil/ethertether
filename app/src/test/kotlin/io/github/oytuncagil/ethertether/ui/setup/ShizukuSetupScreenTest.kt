// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.setup

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ShizukuSetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun show(readiness: EngineReadiness?, installed: Boolean, dark: Boolean = false) {
        val actions = SetupActions(
            onBack = { clicks += "back" },
            onRefresh = { clicks += "refresh" },
            onGrant = { clicks += "grant" },
            onOpenDownload = { clicks += "download" },
            onOpenShizuku = { clicks += "shizuku" },
            onOpenDeveloperOptions = { clicks += "developer" },
        )
        compose.setContent {
            EtherTetherTheme(darkTheme = dark, dynamicColor = false) {
                ShizukuSetupScreen(SetupUiState(readiness, installed), actions)
            }
        }
    }

    @Test
    fun withoutShizukuItLinksToTheOfficialPage() {
        show(EngineReadiness.ShizukuNotInstalled, installed = false)
        compose.onNodeWithText("Shizuku is not installed.", substring = true).assertExists()
        compose.onNodeWithText("Open the official Shizuku page").performScrollTo().performClick()
        assertEquals(listOf("download"), clicks)
    }

    @Test
    fun installedShizukuCanBeOpened() {
        show(EngineReadiness.ShizukuNotRunning, installed = true)
        compose.onNodeWithText("Shizuku is installed.").performScrollTo().assertExists()
        compose.onNodeWithText("Open Shizuku").performScrollTo().performClick()
        assertEquals(listOf("shizuku"), clicks)
    }

    @Test
    fun wirelessDebuggingStepsAreNumberedAndDeveloperOptionsOpen() {
        show(EngineReadiness.ShizukuNotRunning, installed = true)
        (1..6).forEach { compose.onNodeWithText("Step $it").performScrollTo().assertExists() }
        compose.onNodeWithText("Open Developer options").performScrollTo().performClick()
        assertEquals(listOf("developer"), clicks)
    }

    @Test
    fun grantAppearsOnlyWhenAccessCanBeRequested() {
        show(EngineReadiness.PermissionRequired, installed = true)
        compose.onNodeWithText("Grant access").performScrollTo().performClick()
        compose.onNodeWithText("Check again").performScrollTo().performClick()
        assertEquals(listOf("grant", "refresh"), clicks)
    }

    @Test
    fun readyStateNeedsNothing() {
        show(EngineReadiness.Ready, installed = true)
        compose.onNodeWithText("Shizuku is ready.", substring = true).assertExists()
        compose.onNodeWithText("Grant access").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "night")
    fun rendersInDarkThemeAndGoesBack() {
        show(EngineReadiness.RunsAsRoot, installed = true, dark = true)
        compose.onNodeWithText("Shizuku runs as root.", substring = true).assertExists()
        compose.onNodeWithText("Back").performScrollTo().performClick()
        assertEquals(listOf("back"), clicks)
    }
}
