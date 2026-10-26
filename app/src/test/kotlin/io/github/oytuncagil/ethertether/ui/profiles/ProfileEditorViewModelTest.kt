// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.profiles

import io.github.oytuncagil.ethertether.model.Ipv4Address
import io.github.oytuncagil.ethertether.model.Ipv4Subnet
import io.github.oytuncagil.ethertether.model.ProfileLimits
import io.github.oytuncagil.ethertether.model.ValidationResult
import io.github.oytuncagil.ethertether.model.ValidationWarning
import io.github.oytuncagil.ethertether.testing.FakeProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeProfiles()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newProfile(networks: List<Ipv4Subnet> = emptyList()) =
        ProfileEditorViewModel(store, profileId = null, phoneNetworks = { networks }, newId = { "new" })

    @Test
    fun aNewProfileStartsEmptyAndCannotBeSaved() {
        val viewModel = newProfile()
        val state = viewModel.state.value
        assertEquals(null, state.existing)
        assertEquals("16", state.input.prefixLength)
        assertFalse(state.canSave)
    }

    @Test
    fun aValidNewProfileIsSavedAndSelected() = runTest(dispatcher) {
        val viewModel = newProfile()
        viewModel.setName("  Bench  ")
        viewModel.updateInput { it.copy(phoneAddress = "10.9.0.1", clientAddress = "10.9.0.2", prefixLength = "24") }
        assertTrue(viewModel.state.value.canSave)
        var done = false
        viewModel.save { done = true }
        advanceUntilIdle()
        assertTrue(done)
        val saved = store.profiles.value.find("new")
        assertEquals("Bench", saved?.name)
        assertEquals("new", store.profiles.value.selected?.id)
    }

    @Test
    fun anInvalidProfileIsNeverSaved() = runTest(dispatcher) {
        val viewModel = newProfile()
        viewModel.setName("Bad")
        viewModel.updateInput { it.copy(phoneAddress = "10.9.0.1", clientAddress = "10.9.0.2", prefixLength = "8") }
        assertTrue(viewModel.state.value.validation is ValidationResult.Invalid)
        var done = false
        viewModel.save { done = true }
        advanceUntilIdle()
        assertFalse(done)
        assertEquals(3, store.profiles.value.profiles.size)
    }

    @Test
    fun aPresetKeepsItsNameWhenTheFieldIsBlank() = runTest(dispatcher) {
        val viewModel = ProfileEditorViewModel(store, "preset-lab")
        assertTrue(viewModel.state.value.canSave)
        viewModel.updateInput { it.copy(clientAddress = "192.168.50.20") }
        viewModel.save {}
        advanceUntilIdle()
        val lab = store.profiles.value.find("preset-lab")
        assertEquals("192.168.50.20", lab?.input?.clientAddress)
        assertEquals("", lab?.name)
        assertEquals(3, store.profiles.value.profiles.size)
    }

    @Test
    fun theNameIsLimited() {
        val viewModel = newProfile()
        viewModel.setName("x".repeat(ProfileLimits.MAX_NAME + 10))
        assertEquals(ProfileLimits.MAX_NAME, viewModel.state.value.name.length)
    }

    @Test
    fun aSubnetThePhoneUsesIsWarned() {
        val home = Ipv4Subnet.of(Ipv4Address.parse("10.9.0.50")!!, 24)
        val viewModel = newProfile(listOf(home))
        viewModel.updateInput { it.copy(phoneAddress = "10.9.0.1", clientAddress = "10.9.0.2", prefixLength = "24") }
        val validation = viewModel.state.value.validation as ValidationResult.Valid
        assertEquals(setOf(ValidationWarning.OVERLAPS_PHONE_NETWORK), validation.warnings)
    }
}
