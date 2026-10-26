// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.profiles

import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.data.EventLog
import io.github.oytuncagil.ethertether.model.Profile
import io.github.oytuncagil.ethertether.model.ProfileJson
import io.github.oytuncagil.ethertether.model.ProfileLimits
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.model.TetherMode
import io.github.oytuncagil.ethertether.testing.FakeProfiles
import io.github.oytuncagil.ethertether.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeProfiles()
    private val log = EventLog(file = null) { 0L }
    private var ids = 0
    private val viewModel by lazy { ProfilesViewModel(store, log) { "id-${++ids}" } }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.messages(): List<UiText> {
        val messages = mutableListOf<UiText>()
        // Unconfined: subscribed at once and handed every message immediately, since messages are not
        // replayed and advanceUntilIdle does not run background work.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.toList(messages) }
        return messages
    }

    @Test
    fun selectAndDelete() = runTest(dispatcher) {
        viewModel.select("preset-lab")
        advanceUntilIdle()
        assertEquals("preset-lab", store.profiles.value.selected?.id)
        viewModel.delete("preset-lab")
        advanceUntilIdle()
        assertNull(store.profiles.value.find("preset-lab"))
        assertEquals("preset-car", store.profiles.value.selected?.id)
        assertTrue(log.entries.value.any { it.message.contains("deleted profile preset-lab") })
    }

    @Test
    fun duplicateAddsACustomCopyAfterTheOriginal() = runTest(dispatcher) {
        viewModel.duplicate("preset-car", "Car (copy)")
        advanceUntilIdle()
        val list = store.profiles.value.profiles
        assertEquals(listOf("preset-car", "id-1", "preset-laptop", "preset-lab"), list.map { it.id })
        assertEquals("Car (copy)", list[1].name)
        assertNull(list[1].preset)
        assertEquals(list[0].input, list[1].input)
    }

    @Test
    fun duplicateReportsAFullList() = runTest(dispatcher) {
        val input = TetherInput(TetherMode.DHCP, "", "", "")
        store.update { ProfileList((1..ProfileLimits.MAX_PROFILES).map { Profile("p$it", "P$it", null, input) }, "p1") }
        val messages = messages()
        viewModel.duplicate("p1", "copy")
        advanceUntilIdle()
        assertEquals(listOf(UiText(R.string.profiles_full, listOf(ProfileLimits.MAX_PROFILES))), messages)
    }

    @Test
    fun exportThenImportRoundTrips() = runTest(dispatcher) {
        val text = viewModel.exportText { it.preset?.name ?: it.name }
        val messages = messages()
        viewModel.import(text)
        advanceUntilIdle()
        assertEquals(6, store.profiles.value.profiles.size)
        assertEquals(listOf(UiText(R.string.profiles_imported, listOf(UiText.plural(R.plurals.profiles_added, 3), UiText.plural(R.plurals.profiles_skipped, 0)))), messages)
    }

    @Test
    fun importRefusesOtherFilesAndNewerVersions() = runTest(dispatcher) {
        val messages = messages()
        viewModel.import("{\"hello\": 1}")
        viewModel.import(null)
        viewModel.import("{\"format\": \"${ProfileJson.FORMAT}\", \"version\": 999, \"profiles\": []}")
        advanceUntilIdle()
        assertEquals(
            listOf(UiText(R.string.profiles_import_not_a_file), UiText(R.string.profiles_import_not_a_file), UiText(R.string.profiles_import_newer)),
            messages,
        )
        assertEquals(3, store.profiles.value.profiles.size)
    }

    @Test
    fun exportResultIsReported() = runTest(dispatcher) {
        val messages = messages()
        viewModel.exported(true)
        viewModel.exported(false)
        advanceUntilIdle()
        assertEquals(listOf(UiText(R.string.profiles_exported), UiText(R.string.profiles_export_failed)), messages)
    }
}
