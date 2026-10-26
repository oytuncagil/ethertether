// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.about

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.BuildConfig
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun show(info: AboutInfo, dark: Boolean = false) {
        compose.setContent {
            EtherTetherTheme(darkTheme = dark, dynamicColor = false) {
                AboutScreen(info, onBack = { clicks += "back" }, onLibraries = { clicks += "libraries" }, onOpenSource = { clicks += "source" })
            }
        }
    }

    @Test
    // The screen shows only the verdict, never the fingerprint itself.
    fun showsVersionCopyrightLicenseSourceAndTheOfficialVerdict() {
        show(AboutInfo("0.3.0", 3, "release", "AB:CD", "AB:CD"))
        compose.onNodeWithText("Version 0.3.0 (3), release build").assertExists()
        compose.onNodeWithText("Copyright 2026 Oytun Cagil Koltuk").assertExists()
        compose.onNodeWithText("Apache License 2.0 or the GNU General Public License 3.0 or later", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText(SOURCE_URL).performScrollTo().assertExists()
        compose.onNodeWithText("This application is signed with the official EtherTether release key.").performScrollTo().assertExists()
        compose.onNodeWithText("AB:CD", substring = true).assertDoesNotExist()
        compose.onNodeWithText("no internet permission", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun buttonsOpenLibrariesAndSource() {
        show(AboutInfo("0.3.0", 3, "release", null))
        compose.onNodeWithText("Open-source libraries").performScrollTo().performClick()
        compose.onNodeWithText("Open in the browser").performScrollTo().performClick()
        // An unreadable certificate gives no verdict, so the section is left out.
        compose.onNodeWithText("Signing certificate").assertDoesNotExist()
        assertEquals(listOf("libraries", "source"), clicks)
    }

    @Test
    fun theOfficialKeyIsRecognisedWhateverTheSpelling() {
        assertEquals(SigningStatus.OFFICIAL, signingStatus(AboutInfo("1.0.0", 10000, "release", "AB:CD:EF", "abcdef")))
        assertEquals(SigningStatus.OTHER, signingStatus(AboutInfo("1.0.0", 10000, "debug", "AB:CD:EF", "AB:CD:00")))
        assertEquals(SigningStatus.NOT_PUBLISHED, signingStatus(AboutInfo("1.0.0", 10000, "release", "AB:CD:EF", "")))
        assertEquals(SigningStatus.UNKNOWN, signingStatus(AboutInfo("1.0.0", 10000, "release", null, "AB:CD:EF")))
    }

    @Test
    fun aCopyWithAnotherKeySaysSo() {
        show(AboutInfo("0.3.0", 3, "debug", "AB:CD", "EF:01"))
        compose.onNodeWithText("This application is not signed with the official EtherTether release key.").performScrollTo().assertExists()
    }

    // The published release certificate (gradle.properties): 32 colon-separated pairs, as keytool prints it.
    @Test
    fun thePublishedReleaseFingerprintIsWellFormed() {
        assertTrue(BuildConfig.RELEASE_CERT_SHA256, Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(BuildConfig.RELEASE_CERT_SHA256))
    }

    @Test
    fun fingerprintIsUpperCaseHexWithColons() {
        val fingerprint = sha256Fingerprint("abc".toByteArray())
        assertEquals("BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD", fingerprint)
    }

    @Test
    fun aboutInfoReadsThisApp() {
        val info = aboutInfo(ApplicationProvider.getApplicationContext(), "debug", null)
        assertEquals("debug", info.buildType)
        assertTrue(info.versionName.isNotEmpty())
    }

    @Test
    fun theLibraryListNamesEveryShippedLibraryWithAnAllowedLicense() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val json = JSONObject(resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() })
        val libraries = json.getJSONArray("libraries")
        val ids = (0 until libraries.length()).map { libraries.getJSONObject(it).getString("uniqueId") }
        assertTrue(ids.contains("dev.rikka.shizuku:api"))
        val licenses = json.getJSONObject("licenses").keys().asSequence().toSet()
        assertTrue("licenses: $licenses", licenses.isNotEmpty() && licenses.all { it in setOf("Apache-2.0", "MIT", "BSD-3-Clause", "BSD-2-Clause") })
        (0 until libraries.length()).forEach { assertTrue(libraries.getJSONObject(it).getJSONArray("licenses").length() > 0) }
    }

    @Test
    @Config(qualifiers = "night")
    fun rendersInDarkTheme() {
        show(AboutInfo("0.3.0", 3, "debug", "AB:CD"), dark = true)
        compose.onNodeWithText("Version 0.3.0 (3), debug build").assertExists()
    }
}
