// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The app theme. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Every setting of the app.
 *
 * @property loaded false only for the placeholder before the stored settings were read
 * @property keepArmed start again whenever the adapter is plugged in again
 * @property startAtBoot arm the selected profile after the phone starts (needs Shizuku running)
 * @property alertOnExternalStop notify when tethering stops without EtherTether stopping it
 * @property language a language tag ("en", "tr", "de"), or empty for the phone's language
 * @property armedProfileId the profile EtherTether kept armed or serving, so the state survives
 *   Android ending the app process; null when nothing was armed
 */
data class Settings(
    val loaded: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = true,
    val language: String = "",
    val keepArmed: Boolean = false,
    val startAtBoot: Boolean = false,
    val alertOnExternalStop: Boolean = true,
    val developerMode: Boolean = false,
    val onboardingDone: Boolean = false,
    val armedProfileId: String? = null,
)

/** Reads and changes [Settings]. Faked in tests. */
interface SettingsStore {
    /** The current settings; [Settings.loaded] is false until they were read. */
    val settings: StateFlow<Settings>

    /** Changes the settings with [transform]. */
    suspend fun update(transform: (Settings) -> Settings)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_v2",
    // Early builds kept the developer switch in SharedPreferences "settings".
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "settings")) },
)

/** [SettingsStore] in DataStore, private to the app and never backed up. */
class DataStoreSettings(context: Context, scope: CoroutineScope) : SettingsStore {
    private val store = context.applicationContext.settingsDataStore

    override val settings: StateFlow<Settings> = store.data
        .map(::read)
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { preferences ->
            val next = transform(read(preferences))
            preferences[THEME] = next.theme.name
            preferences[DYNAMIC] = next.dynamicColor
            preferences[LANGUAGE] = next.language
            preferences[KEEP_ARMED] = next.keepArmed
            preferences[BOOT] = next.startAtBoot
            preferences[ALERTS] = next.alertOnExternalStop
            preferences[DEVELOPER] = next.developerMode
            preferences[ONBOARDING] = next.onboardingDone
            next.armedProfileId?.let { preferences[ARMED] = it } ?: preferences.remove(ARMED)
        }
    }

    private fun read(preferences: Preferences) = Settings(
        loaded = true,
        theme = ThemeChoice.entries.firstOrNull { it.name == preferences[THEME] } ?: ThemeChoice.SYSTEM,
        dynamicColor = preferences[DYNAMIC] ?: true,
        language = preferences[LANGUAGE].orEmpty(),
        keepArmed = preferences[KEEP_ARMED] ?: false,
        startAtBoot = preferences[BOOT] ?: false,
        alertOnExternalStop = preferences[ALERTS] ?: true,
        developerMode = preferences[DEVELOPER] ?: false,
        onboardingDone = preferences[ONBOARDING] ?: false,
        armedProfileId = preferences[ARMED],
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val KEEP_ARMED = booleanPreferencesKey("keep_armed")
        val BOOT = booleanPreferencesKey("start_at_boot")
        val ALERTS = booleanPreferencesKey("alert_external_stop")

        /** Same key as in the old SharedPreferences, so the migrated value is picked up. */
        val DEVELOPER = booleanPreferencesKey("developer_mode")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val ARMED = stringPreferencesKey("armed_profile")
    }
}
