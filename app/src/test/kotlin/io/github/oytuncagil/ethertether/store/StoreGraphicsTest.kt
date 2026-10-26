// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.store

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.oytuncagil.ethertether.R
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
import io.github.oytuncagil.ethertether.testing.servingStatus
import io.github.oytuncagil.ethertether.ui.home.HomeActions
import io.github.oytuncagil.ethertether.ui.home.HomeScreen
import io.github.oytuncagil.ethertether.ui.home.HomeUiState
import io.github.oytuncagil.ethertether.ui.log.LogActions
import io.github.oytuncagil.ethertether.ui.log.LogScreen
import io.github.oytuncagil.ethertether.ui.profiles.EditorState
import io.github.oytuncagil.ethertether.ui.profiles.ProfileEditorScreen
import io.github.oytuncagil.ethertether.ui.profiles.ProfilesActions
import io.github.oytuncagil.ethertether.ui.profiles.ProfilesScreen
import io.github.oytuncagil.ethertether.ui.settings.SettingsActions
import io.github.oytuncagil.ethertether.ui.settings.SettingsExtras
import io.github.oytuncagil.ethertether.ui.settings.SettingsScreen
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The store images in fastlane/metadata/android: phone screenshots of the real screens with the
 * ready-made profiles as demo data, the feature graphic and the icon. They are made only on
 * request, with scripts/store_graphics.sh; normal test runs skip them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class StoreGraphicsTest(private val locale: String, private val tagline: String) {
    @get:Rule
    val compose = createComposeRule()

    private val ready = TetherState(readiness = EngineReadiness.Ready)
    private val serving = ready.copy(armed = true, status = servingStatus().copy(clients = listOf("169.254.0.1")), profileId = "preset-car")

    @Before
    fun onlyOnRequest() = assumeTrue(System.getProperty("ethertether.storeGraphics") == "true")

    private fun image(name: String) = File(System.getProperty("ethertether.fastlaneDir"), "$locale/images/$name").path

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { EtherTetherTheme(darkTheme = dark, dynamicColor = false) { content() } }
        compose.onRoot().captureRoboImage(image(name))
    }

    @Test
    fun screenshot1Home() = shoot("phoneScreenshots/1_home.png") { HomeScreen(HomeUiState(serving, ProfileList.initial), HomeActions()) }

    @Test
    fun screenshot2Profiles() = shoot("phoneScreenshots/2_profiles.png") { ProfilesScreen(ProfileList.initial, ProfilesActions()) }

    @Test
    fun screenshot3Editor() = shoot("phoneScreenshots/3_editor.png") {
        val profile = ProfileList.initial.find("preset-car")!!
        ProfileEditorScreen(EditorState(profile, "", profile.input, TetherRequestValidator.validate(profile.input)), {}, {}, {}, {})
    }

    @Test
    fun screenshot4Settings() = shoot("phoneScreenshots/4_settings.png") {
        val backends = listOf(
            BackendStatus(BackendKind.SHIZUKU, BackendState.Ready(KnownUids.SHELL)),
        )
        SettingsScreen(Settings(loaded = true, keepArmed = true), SettingsExtras(EngineReadiness.Ready, backends, true, ""), SettingsActions())
    }

    @Test
    fun screenshot5Log() = shoot("phoneScreenshots/5_log.png") {
        LogScreen(
            listOf(
                LogEntry(1_791_450_000_000, LogLevel.INFO, "tether", "start preset-car: Serving(iface=eth0, scope=LOCAL)"),
                LogEntry(1_791_450_060_000, LogLevel.WARNING, "tether", "watchdog: ADAPTER_REMOVED"),
                LogEntry(1_791_450_062_000, LogLevel.INFO, "tether", "re-arm preset-car: Waiting"),
            ),
            LogActions(),
        )
    }

    @Test
    @Config(qualifiers = "+night")
    fun screenshot6HomeDark() = shoot("phoneScreenshots/6_home_dark.png", dark = true) { HomeScreen(HomeUiState(serving, ProfileList.initial), HomeActions()) }

    @Test
    @Config(qualifiers = "+w1024dp-h500dp-mdpi")
    fun featureGraphic() = shoot("featureGraphic.png") { FeatureGraphic(tagline) }
}

/** The adaptive icon's layers drawn flat, full bleed: stores apply their own mask. */
@Composable
internal fun FlatIcon(modifier: Modifier = Modifier) {
    Box(modifier.background(colorResource(R.color.launcher_background))) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** 1024 x 500: the icon, the name and a one-line tagline. */
@Composable
private fun FeatureGraphic(tagline: String) {
    Row(
        Modifier.fillMaxSize().background(colorResource(R.color.launcher_background)).padding(horizontal = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlatIcon(Modifier.size(260.dp))
        Spacer(Modifier.width(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("EtherTether", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Text(tagline, color = Color.White, fontSize = 34.sp, lineHeight = 42.sp)
        }
    }
}

@Config(qualifiers = "en-rUS-w411dp-h822dp-xxhdpi")
class StoreGraphicsEnglishTest : StoreGraphicsTest("en-US", "Ethernet tethering with your own fixed address") {
    @Test
    @Config(qualifiers = "+w512dp-h512dp-mdpi")
    fun icon() {
        assumeTrue(System.getProperty("ethertether.storeGraphics") == "true")
        compose.setContent { FlatIcon(Modifier.fillMaxSize()) }
        compose.onRoot().captureRoboImage(File(System.getProperty("ethertether.fastlaneDir"), "en-US/images/icon.png").path)
    }
}

@Config(qualifiers = "tr-rTR-w411dp-h822dp-xxhdpi")
class StoreGraphicsTurkishTest : StoreGraphicsTest("tr-TR", "Kendi sabit adresinizle Ethernet paylaşımı")

@Config(qualifiers = "de-rDE-w411dp-h822dp-xxhdpi")
class StoreGraphicsGermanTest : StoreGraphicsTest("de-DE", "Ethernet-Tethering mit eigener fester Adresse")
