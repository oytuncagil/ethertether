// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

/** The ready-made profiles. The app shows a translated name for each until the user renames it. */
enum class Preset {
    /** Car diagnostic link: fixed link-local 169.254.0.42/16, nothing shared. */
    CAR_LINK_LOCAL,

    /** Laptop over USB-C Ethernet: normal DHCP, the phone's internet shared. */
    LAPTOP_INTERNET,

    /** Isolated lab network: fixed 192.168.50.1/24, nothing shared. */
    ISOLATED_LAB,
}

/**
 * A saved tethering setup.
 *
 * @property id stable identifier, unique in the profile list
 * @property name the user's name; blank for a ready-made profile the user has not renamed
 * @property preset the ready-made profile this one comes from, or null for a custom profile
 * @property input the request fields, validated with [TetherRequestValidator] before use
 */
data class Profile(val id: String, val name: String, val preset: Preset?, val input: TetherInput) {
    /** The validation result of [input]. */
    fun validate(phoneNetworks: List<Ipv4Subnet> = emptyList()): ValidationResult =
        TetherRequestValidator.validate(input, phoneNetworks)
}

/** The three ready-made profiles. */
object Presets {
    /** The three ready-made profiles, in display order, with fixed ids. */
    val all: List<Profile> = listOf(
        Profile(
            id = "preset-car",
            name = "",
            preset = Preset.CAR_LINK_LOCAL,
            input = TetherInput(TetherMode.STATIC, "169.254.0.42", "169.254.0.1", "16", SharingScope.LOCAL_ONLY, false),
        ),
        Profile(
            id = "preset-laptop",
            name = "",
            preset = Preset.LAPTOP_INTERNET,
            input = TetherInput(TetherMode.DHCP, scope = SharingScope.SHARE_INTERNET),
        ),
        Profile(
            id = "preset-lab",
            name = "",
            preset = Preset.ISOLATED_LAB,
            input = TetherInput(TetherMode.STATIC, "192.168.50.1", "192.168.50.2", "24", SharingScope.LOCAL_ONLY, false),
        ),
    )
}

/** Limits that keep stored and imported profiles small and readable. */
object ProfileLimits {
    /** Longest profile name, in characters. */
    const val MAX_NAME = 60

    /** Most profiles in the list or in one import. */
    const val MAX_PROFILES = 100

    /** Largest import file, in characters. */
    const val MAX_IMPORT_CHARS = 256 * 1024

    /** Longest address or prefix field, in characters. */
    const val MAX_FIELD = 32

    /**
     * The name with surrounding spaces, control characters and bidirectional controls removed,
     * cut to [MAX_NAME]. A bidirectional control in a shared profile file could reorder the text
     * around the name, for example in the notification; the zero width joiner stays for emoji.
     */
    fun cleanName(name: String): String = name.filterNot { it.isISOControl() || it in BIDI_CONTROLS }.trim().take(MAX_NAME)

    /** U+061C, U+200E, U+200F, U+202A to U+202E and U+2066 to U+2069. */
    private val BIDI_CONTROLS: Set<Char> =
        (listOf(0x061C, 0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)).map { it.toChar() }.toSet()
}
