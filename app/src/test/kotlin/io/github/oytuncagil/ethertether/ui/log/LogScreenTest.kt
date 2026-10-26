// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.log

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.data.LogEntry
import io.github.oytuncagil.ethertether.data.LogLevel
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LogScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val entries = listOf(
        LogEntry(1_000, LogLevel.INFO, "app", "started 0.1.0"),
        LogEntry(2_000, LogLevel.WARNING, "tether", "watchdog: STOPPED_OUTSIDE"),
        LogEntry(3_000, LogLevel.ERROR, "engine", "start failed with 12"),
    )
    private var copied: List<LogEntry>? = null
    private var exported: List<LogEntry>? = null
    private var cleared = 0

    private fun show(list: List<LogEntry> = entries, dark: Boolean = false) {
        val actions = LogActions(onCopy = { copied = it }, onExport = { exported = it }, onClear = { cleared++ })
        compose.setContent { EtherTetherTheme(darkTheme = dark, dynamicColor = false) { LogScreen(list, actions) } }
    }

    @Test
    fun filterMatchesLevelAndText() {
        val problems = LogFilter(problemsOnly = true)
        assertFalse(problems.matches(entries[0]))
        assertTrue(problems.matches(entries[1]))
        assertTrue(LogFilter(text = "ENGINE").matches(entries[2]))
        assertTrue(LogFilter(text = "watchdog").matches(entries[1]))
        assertFalse(LogFilter(text = "nothing").matches(entries[0]))
    }

    @Test
    fun allEntriesAreShownWithTheirCount() {
        show()
        compose.onNodeWithText("3 of 3 entries shown").assertExists()
        compose.onNodeWithText("start failed with 12").assertExists()
        compose.onNodeWithText("The log stays on this phone", substring = true).assertExists()
    }

    @Test
    fun problemsOnlyHidesInfoAndExportGetsWhatIsShown() {
        show()
        compose.onNodeWithText("Warnings and errors").performClick()
        compose.onNodeWithText("2 of 3 entries shown").assertExists()
        compose.onNodeWithText("started 0.1.0").assertDoesNotExist()
        compose.onNodeWithText("Export").performClick()
        assertEquals(entries.drop(1), exported)
    }

    @Test
    fun searchNarrowsAndCopyGetsWhatIsShown() {
        show()
        compose.onNodeWithText("Search").performTextInput("engine")
        compose.onNodeWithText("1 of 3 entries shown").assertExists()
        compose.onNodeWithText("Copy").performClick()
        assertEquals(listOf(entries[2]), copied)
    }

    @Test
    fun clearAsksFirst() {
        show()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Clear the log?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, cleared)
        compose.onNodeWithText("Clear").performClick()
        compose.onNode(hasText("Clear") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun oneEntryUsesTheSingular() {
        show(entries.take(1))
        compose.onNodeWithText("1 of 1 entry shown").assertExists()
    }

    @Test
    fun anEmptyLogDisablesTheButtons() {
        show(emptyList())
        compose.onNodeWithText("No entries.").assertExists()
        compose.onNodeWithText("Export").assertIsNotEnabled()
        compose.onNodeWithText("Copy").assertIsNotEnabled()
        compose.onNodeWithText("Clear").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "night")
    fun rendersInDarkTheme() {
        show(dark = true)
        compose.onNodeWithText("start failed with 12").assertExists()
    }
}
