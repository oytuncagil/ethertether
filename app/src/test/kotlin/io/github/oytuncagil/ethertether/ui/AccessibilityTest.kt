// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TextContrastCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import org.hamcrest.Matcher
import org.hamcrest.Matchers.nullValue
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.data.LogEntry
import io.github.oytuncagil.ethertether.data.LogLevel
import io.github.oytuncagil.ethertether.data.Settings
import io.github.oytuncagil.ethertether.model.BackendKind
import io.github.oytuncagil.ethertether.model.BackendState
import io.github.oytuncagil.ethertether.model.BackendStatus
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.model.TetherRequestValidator
import io.github.oytuncagil.ethertether.tether.TetherState
import io.github.oytuncagil.ethertether.tether.WatchdogEvent
import io.github.oytuncagil.ethertether.testing.servingStatus
import io.github.oytuncagil.ethertether.ui.about.AboutInfo
import io.github.oytuncagil.ethertether.ui.about.AboutScreen
import io.github.oytuncagil.ethertether.ui.home.HomeActions
import io.github.oytuncagil.ethertether.ui.home.HomeScreen
import io.github.oytuncagil.ethertether.ui.home.HomeUiState
import io.github.oytuncagil.ethertether.ui.log.LogActions
import io.github.oytuncagil.ethertether.ui.log.LogScreen
import io.github.oytuncagil.ethertether.ui.onboarding.OnboardingActions
import io.github.oytuncagil.ethertether.ui.onboarding.OnboardingScreen
import io.github.oytuncagil.ethertether.ui.profiles.EditorState
import io.github.oytuncagil.ethertether.ui.profiles.ProfileEditorScreen
import io.github.oytuncagil.ethertether.ui.profiles.ProfilesActions
import io.github.oytuncagil.ethertether.ui.profiles.ProfilesScreen
import io.github.oytuncagil.ethertether.ui.settings.SettingsActions
import io.github.oytuncagil.ethertether.ui.settings.SettingsExtras
import io.github.oytuncagil.ethertether.ui.settings.SettingsScreen
import io.github.oytuncagil.ethertether.ui.setup.SetupActions
import io.github.oytuncagil.ethertether.ui.setup.SetupUiState
import io.github.oytuncagil.ethertether.ui.setup.ShizukuSetupScreen
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Runs Google's Accessibility Test Framework over every screen, in light and dark: missing
 * labels, small touch targets, text contrast, duplicate descriptions. Warnings fail the test too.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A common phone size, so nothing overlaps only because the default test screen is small.
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val ready = TetherState(readiness = EngineReadiness.Ready)

    private fun check(dark: Boolean = false, disabledControls: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { EtherTetherTheme(darkTheme = dark, dynamicColor = false) { content() } }
        // WCAG 1.4.3 exempts inactive controls from text contrast, but the framework cannot tell
        // them apart; screens with a disabled control are checked for contrast in their enabled state.
        val suppressions: Matcher<in AccessibilityViewCheckResult> =
            if (disabledControls) matchesCheck(TextContrastCheck::class.java) else nullValue(AccessibilityViewCheckResult::class.java)
        // Compose's own enableAccessibilityChecks does not run under Robolectric; Roborazzi's does.
        compose.onRoot().checkRoboAccessibility(
            RoborazziATFAccessibilityCheckOptions(
                RoborazziATFAccessibilityChecker(AccessibilityCheckPreset.LATEST, suppressions),
                RoborazziATFAccessibilityChecker.CheckLevel.Warning,
            ),
        )
    }

    @Test
    fun homeOff() = check { HomeScreen(HomeUiState(ready, ProfileList.initial), HomeActions()) }

    @Test
    fun homeServing() = check { HomeScreen(HomeUiState(ready.copy(armed = true, status = servingStatus(), profileId = "preset-car"), ProfileList.initial), HomeActions()) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun homeServingDark() = check(dark = true) {
        HomeScreen(HomeUiState(ready.copy(armed = true, status = servingStatus(), profileId = "preset-car"), ProfileList.initial), HomeActions())
    }

    @Test
    fun homeWithoutShizuku() = check(disabledControls = true) { HomeScreen(HomeUiState(TetherState(readiness = EngineReadiness.ShizukuNotInstalled), ProfileList.initial), HomeActions()) }

    @Test
    fun homeWithWatchdogMessage() = check { HomeScreen(HomeUiState(ready.copy(event = WatchdogEvent.ADAPTER_REMOVED), ProfileList.initial), HomeActions()) }

    @Test
    fun homeUnknownAfterShizukuStopped() =
        check(disabledControls = true) { HomeScreen(HomeUiState(TetherState(readiness = EngineReadiness.ShizukuNotRunning, statusUnknown = true), ProfileList.initial), HomeActions()) }

    @Test
    fun onboarding() = check { OnboardingScreen(TetherState(readiness = EngineReadiness.ShizukuNotRunning), OnboardingActions()) }

    @Test
    fun setup() = check { ShizukuSetupScreen(SetupUiState(EngineReadiness.ShizukuNotRunning, true), SetupActions()) }

    @Test
    fun profiles() = check { ProfilesScreen(ProfileList.initial, ProfilesActions()) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun profilesDark() = check(dark = true) { ProfilesScreen(ProfileList.initial, ProfilesActions()) }

    @Test
    fun editor() = check {
        val profile = ProfileList.initial.find("preset-car")!!
        ProfileEditorScreen(EditorState(profile, "", profile.input, TetherRequestValidator.validate(profile.input)), {}, {}, {}, {})
    }

    @Test
    fun log() = check {
        LogScreen(listOf(LogEntry(0, LogLevel.INFO, "app", "started"), LogEntry(1, LogLevel.ERROR, "engine", "failed")), LogActions())
    }

    @Test
    fun settings() = check {
        val backends = listOf(BackendStatus(BackendKind.SHIZUKU, BackendState.NotRunning))
        SettingsScreen(Settings(loaded = true, developerMode = true), SettingsExtras(EngineReadiness.ShizukuNotRunning, backends, false, ""), SettingsActions())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun settingsDark() = check(dark = true) {
        SettingsScreen(Settings(loaded = true), SettingsExtras(EngineReadiness.Ready, emptyList(), true, "de"), SettingsActions())
    }

    @Test
    fun about() = check { AboutScreen(AboutInfo("0.3.0", 3, "release", "AB:CD", "AB:CD"), {}, {}, {}) }
}
