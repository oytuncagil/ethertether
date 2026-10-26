// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.data.Settings
import io.github.oytuncagil.ethertether.data.ThemeChoice
import io.github.oytuncagil.ethertether.model.BackendKind
import io.github.oytuncagil.ethertether.model.BackendState
import io.github.oytuncagil.ethertether.model.BackendStatus
import io.github.oytuncagil.ethertether.model.KnownUids
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var settings = Settings(loaded = true)
    private val clicks = mutableListOf<String>()
    private val backends = listOf(
        BackendStatus(BackendKind.SHIZUKU, BackendState.Ready(KnownUids.SHELL)),
    )

    private fun show(
        current: Settings = settings,
        readiness: EngineReadiness = EngineReadiness.Ready,
        notificationsAllowed: Boolean = true,
        language: String = "",
        dark: Boolean = false,
        unusedAppRestricted: Boolean = false,
    ) {
        val actions = SettingsActions(
            onBack = { clicks += "back" },
            onChange = { settings = it(settings) },
            onLanguage = { clicks += "language $it" },
            onOpenSetup = { clicks += "setup" },
            onGrant = { clicks += "grant" },
            onAllowNotifications = { clicks += "notifications" },
            onOpenAppSettings = { clicks += "app settings" },
            onOpenUnusedAppSettings = { clicks += "unused app" },
            onOpenDebug = { clicks += "debug" },
            onShowIntro = { clicks += "intro" },
        )
        compose.setContent {
            EtherTetherTheme(darkTheme = dark, dynamicColor = false) {
                SettingsScreen(current, SettingsExtras(readiness, backends, notificationsAllowed, language, unusedAppRestricted), actions)
            }
        }
    }

    private fun switch(title: String) = compose.onNode(hasText(title) and isToggleable())

    @Test
    // Only Shizuku is supported, so Sui and root are not listed.
    fun accessShowsShizukuOnly() {
        show()
        compose.onNodeWithText("This version uses Shizuku, started with wireless debugging or ADB.").assertExists()
        compose.onNodeWithContentDescription("Shizuku: Ready, runs as shell", substring = true).assertExists()
        compose.onNodeWithText("Sui and root", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Set up Shizuku").assertDoesNotExist()
    }

    @Test
    fun setupIsOfferedWhenShizukuIsNotReady() {
        show(readiness = EngineReadiness.ShizukuNotRunning)
        compose.onNodeWithText("Set up Shizuku").performScrollTo().performClick()
        assertEquals(listOf("setup"), clicks)
    }

    @Test
    fun automationSwitchesChangeTheSettings() {
        show()
        switch("Keep armed").performScrollTo().assertIsOff().performClick()
        switch("Start after restart").performScrollTo().assertIsOff().performClick()
        assertEquals(true, settings.keepArmed)
        assertEquals(true, settings.startAtBoot)
        compose.onNodeWithText("Android allows this only while EtherTether shows its notification", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Open app settings").performScrollTo().performClick()
        assertEquals(listOf("app settings"), clicks)
    }

    @Test
    fun noUnusedAppHintWithoutAutomation() {
        show(unusedAppRestricted = true)
        compose.onNodeWithText("Open the setting").assertDoesNotExist()
    }

    @Test
    fun theUnusedAppHintAppearsWhenAutomationNeedsIt() {
        show(current = settings.copy(keepArmed = true), unusedAppRestricted = true)
        compose.onNodeWithText("Android can remove EtherTether's permissions", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Open the setting").performScrollTo().performClick()
        assertEquals(listOf("unused app"), clicks)
    }

    @Test
    fun noUnusedAppHintWhenAndroidKeepsThePermissions() {
        show(current = settings.copy(keepArmed = true, startAtBoot = true), unusedAppRestricted = false)
        compose.onNodeWithText("Open the setting").assertDoesNotExist()
    }

    @Test
    fun alertsCanBeTurnedOffAndBlockedNotificationsAreExplained() {
        show(notificationsAllowed = false)
        switch("Alert when tethering stops").performScrollTo().assertIsOn().performClick()
        assertEquals(false, settings.alertOnExternalStop)
        compose.onNodeWithText("Notifications are off for EtherTether", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Allow notifications").performScrollTo().performClick()
        assertEquals(listOf("notifications"), clicks)
    }

    @Test
    fun themeAndLanguageArePicked() {
        show(current = settings.copy(theme = ThemeChoice.DARK), language = "de")
        compose.onNode(hasText("Dark") and isSelectable()).performScrollTo().assertIsSelected()
        compose.onNode(hasText("Light") and isSelectable()).performScrollTo().performClick()
        assertEquals(ThemeChoice.LIGHT, settings.theme)
        compose.onNode(hasText("Deutsch") and isSelectable()).performScrollTo().assertIsSelected()
        compose.onNode(hasText("Türkçe") and isSelectable()).performScrollTo().performClick()
        compose.onNode(hasText("Phone language") and isSelectable()).performScrollTo().performClick()
        assertEquals(listOf("language tr", "language "), clicks)
    }

    @Test
    fun theDebugScreenNeedsTheDeveloperSwitch() {
        show()
        compose.onNodeWithText("Open the debug screen").assertDoesNotExist()
        switch("Developer switch").performScrollTo().assertIsOff().performClick()
        assertEquals(true, settings.developerMode)
    }

    @Test
    fun withTheDeveloperSwitchTheDebugScreenOpens() {
        show(current = settings.copy(developerMode = true))
        compose.onNodeWithText("Open the debug screen").performScrollTo().performClick()
        compose.onNodeWithText("Show the introduction again").performScrollTo().performClick()
        assertEquals(listOf("debug", "intro"), clicks)
    }

    @Test
    @Config(qualifiers = "night")
    fun rendersInDarkTheme() {
        show(dark = true)
        switch("Keep armed").performScrollTo().assertExists()
    }
}
