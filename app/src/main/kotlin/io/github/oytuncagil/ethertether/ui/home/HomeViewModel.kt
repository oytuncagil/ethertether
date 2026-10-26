// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oytuncagil.ethertether.data.ProfileStore
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.tether.TetherController
import io.github.oytuncagil.ethertether.tether.TetherState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the home screen shows. */
data class HomeUiState(val tether: TetherState = TetherState(), val profiles: ProfileList = ProfileList.initial)

/** The home screen's view of the controller and the profiles. */
class HomeViewModel(private val controller: TetherController, private val profiles: ProfileStore) : ViewModel() {
    /** The latest screen state. */
    val state: StateFlow<HomeUiState> = combine(controller.state, profiles.profiles, ::HomeUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(controller.state.value, profiles.profiles.value))

    /** Starts the selected profile. */
    fun start() = launch { controller.start() }

    /** Stops tethering. */
    fun stop() = launch { controller.stop() }

    /** Selects the profile Start uses. */
    fun select(id: String) = launch { profiles.update { it.select(id) } }

    /** Hides the watchdog message. */
    fun dismissEvent() = controller.dismissEvent()

    /** Reads readiness and status again. */
    fun refresh() = launch { controller.refreshStatus() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
