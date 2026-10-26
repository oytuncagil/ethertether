// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * A string resource with its format arguments, resolved only when drawn. With a [quantity], [id]
 * is a plurals resource and the quantity picks the form the language needs.
 */
data class UiText(val id: Int, val args: List<Any> = emptyList(), val quantity: Int? = null) {
    /** Text helpers. */
    companion object {
        /** A plurals resource [id] for [count], with [count] as its only argument. */
        fun plural(@PluralsRes id: Int, count: Int) = UiText(id, listOf(count), count)
    }
}

/** How a status looks: usable, needs the user, or simply not there. */
enum class Tone { OK, ACTION, ABSENT }

/** The resolved text. Arguments that are themselves [UiText] are resolved first. */
@Composable
fun UiText.resolve(): String {
    val resolved = args.map { if (it is UiText) it.resolve() else it }
    return quantity?.let { pluralStringResource(id, it, *resolved.toTypedArray()) } ?: stringResource(id, *resolved.toTypedArray())
}

/** The resolved text outside of composition, for example for the clipboard. */
fun UiText.resolve(context: Context): String {
    val resolved = args.map { if (it is UiText) it.resolve(context) else it }
    return quantity?.let { context.resources.getQuantityString(id, it, *resolved.toTypedArray()) } ?: context.getString(id, *resolved.toTypedArray())
}
