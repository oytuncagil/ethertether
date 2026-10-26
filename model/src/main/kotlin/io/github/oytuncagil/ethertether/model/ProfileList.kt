// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

/**
 * The user's profiles and which one is selected. Immutable: every change returns a new list.
 *
 * @property profiles in display order
 * @property selectedId the profile Start, the tile and the widget use; always one of [profiles]
 *   when the list is not empty
 */
data class ProfileList(val profiles: List<Profile>, val selectedId: String?) {
    /** The selected profile, or the first one, or null when the list is empty. */
    val selected: Profile? get() = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()

    /** The profile with [id], or null. */
    fun find(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** Selects [id] if it exists. */
    fun select(id: String): ProfileList = if (find(id) != null) copy(selectedId = id) else this

    /** Adds [profile] at the end, unless the list is full. */
    fun add(profile: Profile): ProfileList {
        if (profiles.size >= ProfileLimits.MAX_PROFILES) return this
        return copy(profiles = profiles + profile).fixSelection()
    }

    /** Replaces the profile with the same id. */
    fun update(profile: Profile): ProfileList = copy(profiles = profiles.map { if (it.id == profile.id) profile else it })

    /** Adds a copy of [id] right after it, with [newId] and [newName], as a custom profile. */
    fun duplicate(id: String, newId: String, newName: String): ProfileList {
        val source = find(id) ?: return this
        if (profiles.size >= ProfileLimits.MAX_PROFILES) return this
        val copy = source.copy(id = newId, name = ProfileLimits.cleanName(newName), preset = null)
        val index = profiles.indexOf(source)
        return copy(profiles = profiles.take(index + 1) + copy + profiles.drop(index + 1))
    }

    /** Removes [id]; the selection moves to the first profile when it was the selected one. */
    fun delete(id: String): ProfileList = copy(profiles = profiles.filterNot { it.id == id }).fixSelection()

    /** Adds [imported] at the end, as many as fit. */
    fun addAll(imported: List<Profile>): ProfileList {
        val room = (ProfileLimits.MAX_PROFILES - profiles.size).coerceAtLeast(0)
        return copy(profiles = profiles + imported.take(room)).fixSelection()
    }

    private fun fixSelection(): ProfileList =
        if (profiles.any { it.id == selectedId }) this else copy(selectedId = profiles.firstOrNull()?.id)

    /** Construction helpers. */
    companion object {
        /** The list a new installation starts with: the ready-made profiles, the first selected. */
        val initial: ProfileList = ProfileList(Presets.all, Presets.all.first().id)
    }
}
