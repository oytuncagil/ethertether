// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.data.LogEntry
import io.github.oytuncagil.ethertether.data.LogLevel
import io.github.oytuncagil.ethertether.data.Settings
import io.github.oytuncagil.ethertether.model.BackendKind
import io.github.oytuncagil.ethertether.model.BackendState
import io.github.oytuncagil.ethertether.model.BackendStatus
import io.github.oytuncagil.ethertether.model.KnownUids
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.model.TetherRequestValidator
import io.github.oytuncagil.ethertether.tether.TetherState
import io.github.oytuncagil.ethertether.tether.WatchdogEvent
import io.github.oytuncagil.ethertether.testing.noAdapterStatus
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
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the main screens, compared with the images in app/src/test/screenshots on every
 * test run (Roborazzi). After an intended change, record them again with
 * `./gradlew recordRoborazziDebug` and review the images in the pull request.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val ready = TetherState(readiness = EngineReadiness.Ready)
    private val serving = ready.copy(armed = true, status = servingStatus().copy(clients = listOf("169.254.0.1")), profileId = "preset-car")
    private val backends = listOf(
        BackendStatus(BackendKind.SHIZUKU, BackendState.Ready(KnownUids.SHELL)),
    )

    private fun shoot(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { EtherTetherTheme(darkTheme = dark, dynamicColor = false) { content() } }
        // A tiny tolerance (0.05 % of the pixels) for anti-aliasing differences between machines;
        // a changed word is still far above it.
        compose.onRoot().captureRoboImage(roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0005f)))
    }

    @Composable
    private fun Home(state: TetherState) = HomeScreen(HomeUiState(state, ProfileList.initial), HomeActions())

    @Composable
    private fun SettingsPage() =
        SettingsScreen(Settings(loaded = true, keepArmed = true), SettingsExtras(EngineReadiness.Ready, backends, true, ""), SettingsActions())

    @Test
    fun homeOff() = shoot { Home(ready.copy(status = noAdapterStatus())) }

    @Test
    fun homeServing() = shoot { Home(serving) }

    @Test
    @Config(qualifiers = PHONE_NIGHT)
    fun homeServingDark() = shoot(dark = true) { Home(serving) }

    @Test
    fun homeWaiting() = shoot { Home(ready.copy(armed = true, status = noAdapterStatus())) }

    @Test
    fun homeWithoutShizuku() = shoot { Home(TetherState(readiness = EngineReadiness.ShizukuNotRunning)) }

    @Test
    fun homeAdapterRemoved() = shoot { Home(ready.copy(event = WatchdogEvent.ADAPTER_REMOVED, status = noAdapterStatus())) }

    @Test
    fun homeUnknownAfterShizukuStopped() = shoot { Home(TetherState(readiness = EngineReadiness.ShizukuNotRunning, statusUnknown = true)) }

    @Test
    @Config(qualifiers = "tr-$PHONE")
    fun homeServingTurkish() = shoot { Home(serving) }

    @Test
    @Config(qualifiers = "de-$PHONE")
    fun homeServingGerman() = shoot { Home(serving) }

    @Test
    fun onboarding() = shoot { OnboardingScreen(TetherState(readiness = EngineReadiness.ShizukuNotRunning), OnboardingActions()) }

    @Test
    fun profiles() = shoot { ProfilesScreen(ProfileList.initial, ProfilesActions()) }

    @Test
    @Config(qualifiers = PHONE_NIGHT)
    fun profilesDark() = shoot(dark = true) { ProfilesScreen(ProfileList.initial, ProfilesActions()) }

    @Test
    fun editor() = shoot {
        val profile = ProfileList.initial.find("preset-car")!!
        ProfileEditorScreen(EditorState(profile, "", profile.input, TetherRequestValidator.validate(profile.input)), {}, {}, {}, {})
    }

    @Test
    fun log() = shoot {
        LogScreen(
            listOf(
                LogEntry(1_791_450_000_000, LogLevel.INFO, "tether", "start preset-car: Serving"),
                LogEntry(1_791_450_060_000, LogLevel.WARNING, "tether", "watchdog: ADAPTER_REMOVED"),
            ),
            LogActions(),
        )
    }

    @Test
    fun settings() = shoot { SettingsPage() }

    @Test
    @Config(qualifiers = "tr-$PHONE")
    fun settingsTurkish() = shoot { SettingsPage() }

    @Test
    @Config(qualifiers = "de-$PHONE")
    fun settingsGerman() = shoot { SettingsPage() }

    @Test
    fun about() = shoot { AboutScreen(AboutInfo("0.1.0", 1, "release", "AB:CD:EF", "AB:CD:EF"), {}, {}, {}) }
}

/** A common phone size at a density that keeps the images small. */
private const val PHONE = "w411dp-h891dp-hdpi"

/** The same phone at night (Android's qualifier order puts night before the density). */
private const val PHONE_NIGHT = "w411dp-h891dp-night-hdpi"
