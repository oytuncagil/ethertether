// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.debug

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.engine.EngineStep
import io.github.oytuncagil.ethertether.engine.RawStep
import io.github.oytuncagil.ethertether.model.TetherRequestValidator
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText
import io.github.oytuncagil.ethertether.ui.result.Message
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun show(state: DebugUiState = DebugUiState(), readiness: EngineReadiness? = EngineReadiness.Ready) {
        val actions = DebugActions(
            onStart = { clicks += "start" },
            onStop = { clicks += "stop" },
            onStatus = { clicks += "status" },
            onStep = { clicks += it.name },
            onCopy = { clicks += "copy" },
        )
        compose.setContent { EtherTetherTheme(dynamicColor = false) { DebugScreen(state, readiness, actions) } }
    }

    @Test
    fun everyOperationAndStepHasAButton() {
        show()
        compose.onNodeWithText("Start").performScrollTo().performClick()
        compose.onNodeWithText("Stop").performScrollTo().performClick()
        compose.onNodeWithText("Status").performScrollTo().performClick()
        listOf("Diagnose", "Read states", "Read interfaces", "Build request", "Stop only", "Start only", "Start only, no exemption")
            .forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        assertEquals(listOf("start", "stop", "status") + RawStep.entries.map { it.name }, clicks)
    }

    @Test
    fun invalidPrefixShowsTheReasonAndTheSuggestion() {
        val input = DEFAULT_INPUT.copy(prefixLength = "8")
        show(DebugUiState(input, TetherRequestValidator.validate(input)))
        compose.onNodeWithText("Android's DHCP server accepts only /16 to /30. Use /16 for the largest network.").performScrollTo().assertExists()
        compose.onNodeWithText("Use /16").performScrollTo().assertExists()
    }

    @Test
    fun buttonsAreDisabledWhileRunning() {
        show(DebugUiState(busy = true))
        compose.onNodeWithText("Start").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Running").assertExists()
    }

    @Test
    fun resultsShowMessageAndRawSteps() {
        val entry = DebugEntry(
            id = 0,
            label = UiText(R.string.debug_action_start),
            time = "12:00:00",
            message = Message(UiText(R.string.code_14), UiText(R.string.code_14_next), Tone.ACTION),
            notes = listOf(UiText(R.string.note_exemption_refused)),
            steps = listOf(EngineStep("startTethering (scope=2, exempt=true)", "14 NO_CHANGE_TETHERING_PERMISSION")),
        )
        show(DebugUiState(entries = listOf(entry)))
        compose.onNodeWithText("Start at 12:00:00").performScrollTo().assertExists()
        compose.onNodeWithText("permission is missing (error 14)", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("startTethering (scope=2, exempt=true): 14 NO_CHANGE_TETHERING_PERMISSION").performScrollTo().assertExists()
        compose.onNodeWithText("Copy all").performScrollTo().performClick()
        assertEquals(listOf("copy"), clicks)
    }

    @Test
    fun readinessIsShownOnTop() {
        show(readiness = EngineReadiness.ShizukuNotRunning)
        compose.onNodeWithText("Shizuku is installed but not running.", substring = true).assertExists()
    }
}
