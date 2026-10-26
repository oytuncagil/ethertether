// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The app's own language. Android 13 and later keep it as the per-app language (also shown in
 * the phone's settings); on Android 11 and 12 the activity applies it to its own context.
 */
object AppLocales {
    /** The languages the app offers, as tags; empty means the phone's language. */
    val supported = listOf("", "en", "de", "tr")

    private const val PREFS = "locale"
    private const val KEY = "tag"

    /** Sets the app language to [tag] ("" for the phone's language). The activity recreates itself. */
    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, tag) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        }
    }

    /** The chosen tag, or "" for the phone's language. */
    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(',').substringBefore('-')
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    }

    /** On Android 11 and 12, [base] with the chosen language applied; otherwise [base] itself. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(config)
    }
}
