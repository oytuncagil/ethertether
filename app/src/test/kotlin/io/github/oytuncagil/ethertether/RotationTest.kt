// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The whole app survives the activity being recreated, as on rotation, a language or theme change:
 * the screen stays where the user was and nothing restarts.
 */
@RunWith(AndroidJUnit4::class)
// The real application, as on a phone (the other tests use a plain one, see robolectric.properties).
@Config(application = EtherTetherApplication::class)
class RotationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { shown(text) }

    /** Home, past the introduction if this run still shows it (stored settings outlive a test). */
    private fun toHome() {
        compose.waitUntil(5_000) { shown("Skip") || shown("Manage profiles") }
        if (shown("Skip")) compose.onNodeWithText("Skip").performClick()
        waitFor("Manage profiles")
    }

    @Test
    fun theScreenStaysWhereItWasAfterRecreation() {
        toHome()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Keep armed")
        compose.activityRule.scenario.recreate()
        waitFor("Keep armed")
        compose.onNodeWithText("Privileged access").assertExists()
    }

    @Test
    fun theIntroductionDoesNotComeBackAfterRecreation() {
        toHome()
        compose.activityRule.scenario.recreate()
        waitFor("Manage profiles")
        compose.onNodeWithText("Skip").assertDoesNotExist()
    }
}
