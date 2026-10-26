// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.data.EventLog
import io.github.oytuncagil.ethertether.data.ProfileStore
import io.github.oytuncagil.ethertether.model.ImportResult
import io.github.oytuncagil.ethertether.model.Profile
import io.github.oytuncagil.ethertether.model.ProfileJson
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.model.ProfileLimits
import io.github.oytuncagil.ethertether.ui.common.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The profile list screen: select, duplicate, delete, import and export.
 *
 * @param newId makes ids for new profiles; replaced in tests
 */
class ProfilesViewModel(
    private val store: ProfileStore,
    private val log: EventLog,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableMessages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)

    /** The profiles. */
    val profiles: StateFlow<ProfileList> = store.profiles

    /** Short messages for the snackbar. */
    val messages: SharedFlow<UiText> = mutableMessages

    /** Selects [id] for Start, the tile and the widget. */
    fun select(id: String) = viewModelScope.launch { store.update { it.select(id) } }

    /** Adds a copy of [id] named [name]. */
    fun duplicate(id: String, name: String) = viewModelScope.launch {
        val before = store.profiles.value.profiles.size
        store.update { it.duplicate(id, newId(), name) }
        if (store.profiles.value.profiles.size == before) mutableMessages.tryEmit(UiText(R.string.profiles_full, listOf(ProfileLimits.MAX_PROFILES)))
    }

    /** Deletes [id]. */
    fun delete(id: String) = viewModelScope.launch {
        store.update { it.delete(id) }
        log.info(TAG, "deleted profile $id")
    }

    /** The export text of every profile; [nameOf] resolves translated preset names. */
    fun exportText(nameOf: (Profile) -> String): String = ProfileJson.export(store.profiles.value.profiles, nameOf)

    /** Reports the result of writing an export. */
    fun exported(success: Boolean) {
        mutableMessages.tryEmit(UiText(if (success) R.string.profiles_exported else R.string.profiles_export_failed))
        log.info(TAG, "export ${if (success) "written" else "failed"}")
    }

    /** Imports [text], adding every valid profile, and reports the result. Null text means unreadable. */
    fun import(text: String?) = viewModelScope.launch {
        val result = text?.let { ProfileJson.import(it, newId) } ?: ImportResult.NotAProfileFile
        val message = when (result) {
            ImportResult.NotAProfileFile -> UiText(R.string.profiles_import_not_a_file)
            ImportResult.NewerVersion -> UiText(R.string.profiles_import_newer)
            is ImportResult.Read -> {
                val room = ProfileLimits.MAX_PROFILES - store.profiles.value.profiles.size
                store.update { it.addAll(result.profiles) }
                val added = minOf(room.coerceAtLeast(0), result.profiles.size)
                val skipped = result.skipped.values.sum() + (result.profiles.size - added)
                log.info(TAG, "import: added $added, skipped $skipped ${result.skipped}")
                UiText(R.string.profiles_imported, listOf(UiText.plural(R.plurals.profiles_added, added), UiText.plural(R.plurals.profiles_skipped, skipped)))
            }
        }
        mutableMessages.tryEmit(message)
    }

    private companion object {
        const val TAG = "profiles"
    }
}
