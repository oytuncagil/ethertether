// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One profile in a file: the request fields and a name. Ids are never exported. */
@Serializable
internal data class ProfileEntry(
    val name: String = "",
    val mode: String,
    val phoneAddress: String = "",
    val clientAddress: String = "",
    val prefixLength: String = "",
    val scope: String,
    val allowScopeFallback: Boolean = false,
    // Only in the app's own storage:
    val id: String? = null,
    val preset: String? = null,
)

/** The whole file. */
@Serializable
internal data class ProfileFile(
    val format: String,
    val version: Int,
    @SerialName("selected") val selectedId: String? = null,
    val profiles: List<ProfileEntry>,
)

/** Why a profile in an import was skipped. */
enum class ImportProblem {
    /** The name is missing after cleaning. */
    NAME_MISSING,

    /** The mode or scope is unknown, or a field is too long. */
    FIELD_INVALID,

    /** The fields do not form a valid request (see [TetherRequestValidator]). */
    REQUEST_INVALID,

    /** The import has more profiles than allowed; the rest were skipped. */
    TOO_MANY,
}

/** The outcome of reading an import file. */
sealed interface ImportResult {
    /** [profiles] are valid; [skipped] counts each problem that made a profile unusable. */
    data class Read(val profiles: List<Profile>, val skipped: Map<ImportProblem, Int>) : ImportResult

    /** The file is not an EtherTether profile file, is too large, or is not readable JSON. */
    data object NotAProfileFile : ImportResult

    /** The file is from a newer version of the format. */
    data object NewerVersion : ImportResult
}

/**
 * The profile file format: a small JSON object with a format tag and a version. The same
 * format stores the app's own list (with ids and presets). Every imported profile is
 * validated; nothing in a file is executed or trusted.
 */
object ProfileJson {
    /** The format tag at the top of every file. */
    const val FORMAT = "ethertether-profiles"

    /** The format version written by this app. */
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** The export text for [profiles]; [nameOf] gives the display name (translated presets). */
    fun export(profiles: List<Profile>, nameOf: (Profile) -> String): String =
        json.encodeToString(ProfileFile.serializer(), ProfileFile(FORMAT, VERSION, null, profiles.map { entry(it, nameOf(it), keepIds = false) }))

    /** Reads an import; [newId] gives each imported profile a fresh id. */
    fun import(text: String, newId: () -> String): ImportResult {
        val file = parse(text) ?: return ImportResult.NotAProfileFile
        if (file.version > VERSION) return ImportResult.NewerVersion
        val skipped = mutableMapOf<ImportProblem, Int>()
        fun skip(problem: ImportProblem, count: Int = 1) { skipped[problem] = (skipped[problem] ?: 0) + count }
        val profiles = mutableListOf<Profile>()
        file.profiles.forEachIndexed { index, entry ->
            if (index >= ProfileLimits.MAX_PROFILES) return@forEachIndexed skip(ImportProblem.TOO_MANY)
            when (val checked = check(entry)) {
                is Checked.Bad -> skip(checked.problem)
                is Checked.Good -> profiles += Profile(newId(), checked.name, null, checked.input)
            }
        }
        return ImportResult.Read(profiles, skipped)
    }

    /** The app's own storage text for [list], with ids and presets. */
    fun store(list: ProfileList): String =
        json.encodeToString(ProfileFile.serializer(), ProfileFile(FORMAT, VERSION, list.selectedId, list.profiles.map { entry(it, it.name, keepIds = true) }))

    /** Reads the app's own storage; null when it is unreadable (the app then starts fresh). */
    fun restore(text: String): ProfileList? {
        val file = parse(text) ?: return null
        val profiles = file.profiles.mapNotNull { entry ->
            val id = entry.id ?: return@mapNotNull null
            val preset = Preset.entries.firstOrNull { it.name == entry.preset }
            val input = toInput(entry) ?: return@mapNotNull null
            Profile(id, ProfileLimits.cleanName(entry.name), preset, input)
        }.distinctBy { it.id }.take(ProfileLimits.MAX_PROFILES)
        return ProfileList(profiles, file.selectedId).let { it.select(it.selected?.id ?: "") }
    }

    private fun parse(text: String): ProfileFile? {
        if (text.length > ProfileLimits.MAX_IMPORT_CHARS) return null
        val file = try {
            json.decodeFromString(ProfileFile.serializer(), text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return file.takeIf { it.format == FORMAT }
    }

    private sealed interface Checked {
        data class Good(val name: String, val input: TetherInput) : Checked

        data class Bad(val problem: ImportProblem) : Checked
    }

    private fun check(entry: ProfileEntry): Checked {
        val name = ProfileLimits.cleanName(entry.name)
        if (name.isEmpty()) return Checked.Bad(ImportProblem.NAME_MISSING)
        val input = toInput(entry) ?: return Checked.Bad(ImportProblem.FIELD_INVALID)
        if (TetherRequestValidator.validate(input) !is ValidationResult.Valid) return Checked.Bad(ImportProblem.REQUEST_INVALID)
        return Checked.Good(name, input)
    }

    private fun toInput(entry: ProfileEntry): TetherInput? {
        val mode = TetherMode.entries.firstOrNull { it.name == entry.mode } ?: return null
        val scope = SharingScope.entries.firstOrNull { it.name == entry.scope } ?: return null
        val fields = listOf(entry.phoneAddress, entry.clientAddress, entry.prefixLength)
        if (fields.any { it.length > ProfileLimits.MAX_FIELD }) return null
        return TetherInput(mode, entry.phoneAddress.trim(), entry.clientAddress.trim(), entry.prefixLength.trim(), scope, entry.allowScopeFallback)
    }

    private fun entry(profile: Profile, name: String, keepIds: Boolean) = ProfileEntry(
        name = name,
        mode = profile.input.mode.name,
        phoneAddress = profile.input.phoneAddress,
        clientAddress = profile.input.clientAddress,
        prefixLength = profile.input.prefixLength,
        scope = profile.input.scope.name,
        allowScopeFallback = profile.input.allowScopeFallback,
        id = profile.id.takeIf { keepIds },
        preset = profile.preset?.name?.takeIf { keepIds },
    )
}
