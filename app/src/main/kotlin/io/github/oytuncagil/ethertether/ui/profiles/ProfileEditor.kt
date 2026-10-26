// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.profiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.data.ProfileStore
import io.github.oytuncagil.ethertether.model.Ipv4Subnet
import io.github.oytuncagil.ethertether.model.Profile
import io.github.oytuncagil.ethertether.model.ProfileLimits
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.model.TetherMode
import io.github.oytuncagil.ethertether.model.TetherRequestValidator
import io.github.oytuncagil.ethertether.model.ValidationResult
import io.github.oytuncagil.ethertether.ui.common.RequestForm
import io.github.oytuncagil.ethertether.ui.common.ScreenColumn
import io.github.oytuncagil.ethertether.ui.common.ScreenScaffold
import io.github.oytuncagil.ethertether.ui.common.SectionHeading
import io.github.oytuncagil.ethertether.ui.common.nameRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** What the editor shows. [existing] is null for a new profile. */
data class EditorState(
    val existing: Profile?,
    val name: String,
    val input: TetherInput,
    val validation: ValidationResult,
) {
    /** True when the profile can be saved: valid request and a name (presets may keep theirs blank). */
    val canSave: Boolean get() = validation is ValidationResult.Valid && (name.isNotBlank() || existing?.preset != null)
}

/**
 * Creates or edits one profile.
 *
 * @param phoneNetworks the subnets the phone uses now, for the overlap warning
 */
class ProfileEditorViewModel(
    private val store: ProfileStore,
    profileId: String?,
    private val phoneNetworks: () -> List<Ipv4Subnet> = { emptyList() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val existing = profileId?.let(store.profiles.value::find)
    private val startInput = existing?.input ?: TetherInput(TetherMode.STATIC, "", "", TetherRequestValidator.MIN_PREFIX.toString())
    private val mutableState = MutableStateFlow(EditorState(existing, existing?.name.orEmpty(), startInput, validate(startInput)))

    /** The editor state. */
    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    /** Changes the name. */
    fun setName(name: String) = mutableState.update { it.copy(name = name.take(ProfileLimits.MAX_NAME)) }

    /** Changes the request fields. */
    fun updateInput(transform: (TetherInput) -> TetherInput) = mutableState.update {
        val input = transform(it.input)
        it.copy(input = input, validation = validate(input))
    }

    /** Saves when valid, then calls [done]. A new profile is selected right away. */
    fun save(done: () -> Unit) {
        val current = mutableState.value
        if (!current.canSave) return
        viewModelScope.launch {
            val name = ProfileLimits.cleanName(current.name)
            if (existing != null) {
                store.update { it.update(existing.copy(name = name, input = current.input)) }
            } else {
                val profile = Profile(newId(), name, null, current.input)
                store.update { it.add(profile).select(profile.id) }
            }
            done()
        }
    }

    private fun validate(input: TetherInput) = TetherRequestValidator.validate(input, phoneNetworks())
}

/** The profile editor screen. */
@Composable
fun ProfileEditorScreen(state: EditorState, onName: (String) -> Unit, onInput: ((TetherInput) -> TetherInput) -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    val title = stringResource(if (state.existing == null) R.string.editor_new_title else R.string.editor_edit_title)
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        ScreenColumn(padding) {
            val placeholder = state.existing?.preset?.let { stringResource(it.nameRes()) }
            OutlinedTextField(
                value = state.name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.editor_name)) },
                placeholder = placeholder?.let { { Text(it) } },
                isError = !state.canSave && state.name.isBlank() && state.existing?.preset == null,
                supportingText = {
                    val limit = stringResource(R.string.editor_name_hint, ProfileLimits.MAX_NAME)
                    // The placeholder shows only while the field has focus, so say it here too.
                    Text(placeholder?.let { stringResource(R.string.editor_name_preset_hint, it) + " " + limit } ?: limit)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeading(stringResource(R.string.debug_request_title))
            RequestForm(state.input, state.validation, onInput)
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
