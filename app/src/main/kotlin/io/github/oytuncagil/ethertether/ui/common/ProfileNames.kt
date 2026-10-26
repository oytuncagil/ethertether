// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.model.Preset
import io.github.oytuncagil.ethertether.model.Profile
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherMode

/** The translated name of a ready-made profile. */
@StringRes
fun Preset.nameRes(): Int = when (this) {
    Preset.CAR_LINK_LOCAL -> R.string.preset_car
    Preset.LAPTOP_INTERNET -> R.string.preset_laptop
    Preset.ISOLATED_LAB -> R.string.preset_lab
}

/** The name to show: the user's name, else the translated preset name. */
fun Profile.displayName(context: Context): String =
    name.ifBlank { preset?.let { context.getString(it.nameRes()) } ?: context.getString(R.string.profile_unnamed) }

/** The name to show, in composition. */
@Composable
fun Profile.displayName(): String =
    name.ifBlank { preset?.let { stringResource(it.nameRes()) } ?: stringResource(R.string.profile_unnamed) }

/** One line describing the profile, for example "Fixed 169.254.0.42/16, client 169.254.0.1, local only". */
@Composable
fun Profile.summary(): String {
    val scope = stringResource(if (input.scope == SharingScope.LOCAL_ONLY) R.string.scope_local else R.string.scope_shared)
    return if (input.mode == TetherMode.STATIC) {
        stringResource(R.string.profile_summary_static, "${input.phoneAddress}/${input.prefixLength.removePrefix("/")}", input.clientAddress, scope)
    } else {
        stringResource(R.string.profile_summary_dhcp, scope)
    }
}
