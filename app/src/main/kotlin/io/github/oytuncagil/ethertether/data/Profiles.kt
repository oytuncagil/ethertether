// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.oytuncagil.ethertether.model.ProfileJson
import io.github.oytuncagil.ethertether.model.ProfileList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Reads and changes the saved profiles. Faked in tests. */
interface ProfileStore {
    /** The current profiles; the ready-made ones on a new installation. */
    val profiles: StateFlow<ProfileList>

    /** Changes the profiles with [transform]. */
    suspend fun update(transform: (ProfileList) -> ProfileList)

    /** The stored profiles, read from storage (unlike [profiles], never the placeholder before loading). */
    suspend fun stored(): ProfileList = profiles.value
}

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profiles")

/** [ProfileStore] in DataStore, as the profile JSON format (one key). */
class DataStoreProfiles(context: Context, scope: CoroutineScope) : ProfileStore {
    private val store = context.applicationContext.profileDataStore

    override val profiles: StateFlow<ProfileList> = store.data
        .map(::read)
        .stateIn(scope, SharingStarted.Eagerly, ProfileList.initial)

    override suspend fun update(transform: (ProfileList) -> ProfileList) {
        store.edit { preferences -> preferences[KEY] = ProfileJson.store(transform(read(preferences))) }
    }

    override suspend fun stored(): ProfileList = read(store.data.first())

    private fun read(preferences: Preferences): ProfileList =
        preferences[KEY]?.let(ProfileJson::restore) ?: ProfileList.initial

    private companion object {
        val KEY = stringPreferencesKey("profiles_json")
    }
}
