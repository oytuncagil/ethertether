// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// EtherTether's own palette: a deep teal for the link, warm amber for things that need action.
private val LightColors = lightColorScheme(
    primary = Color(0xFF00687A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFADECFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF4B6269),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE7EF),
    onSecondaryContainer = Color(0xFF061F24),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA3),
    onTertiaryContainer = Color(0xFF261900),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171C1E),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171C1E),
    surfaceVariant = Color(0xFFDBE4E7),
    onSurfaceVariant = Color(0xFF3F484B),
    // The roles below default to Material's purple-grey baseline when left out; these are the
    // matching tones of the same teal palette.
    // A tone darker than usual: 4.6:1 on the background, so short field values stay readable.
    outline = Color(0xFF697375),
    outlineVariant = Color(0xFFBFC8CB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2C3133),
    inverseOnSurface = Color(0xFFECF2F4),
    inversePrimary = Color(0xFF55D6F4),
    surfaceDim = Color(0xFFD5DBDD),
    surfaceBright = Color(0xFFF5FAFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4F7),
    surfaceContainer = Color(0xFFE9EFF1),
    surfaceContainerHigh = Color(0xFFE3E9EB),
    surfaceContainerHighest = Color(0xFFDEE3E5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF55D6F4),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF004E5C),
    onPrimaryContainer = Color(0xFFADECFF),
    secondary = Color(0xFFB2CBD3),
    onSecondary = Color(0xFF1D3439),
    secondaryContainer = Color(0xFF344A51),
    onSecondaryContainer = Color(0xFFCEE7EF),
    tertiary = Color(0xFFF6BE48),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF0F1416),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0F1416),
    onSurface = Color(0xFFDEE3E5),
    surfaceVariant = Color(0xFF3F484B),
    onSurfaceVariant = Color(0xFFBFC8CB),
    outline = Color(0xFF899295),
    outlineVariant = Color(0xFF3F484B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFDEE3E5),
    inverseOnSurface = Color(0xFF2C3133),
    inversePrimary = Color(0xFF00687A),
    surfaceDim = Color(0xFF0F1416),
    surfaceBright = Color(0xFF353A3C),
    surfaceContainerLowest = Color(0xFF0A0F11),
    surfaceContainerLow = Color(0xFF171C1E),
    surfaceContainer = Color(0xFF1B2022),
    surfaceContainerHigh = Color(0xFF252B2D),
    surfaceContainerHighest = Color(0xFF303638),
)

/**
 * EtherTether's Material 3 theme: the system's dynamic colors on Android 12 and later when
 * [dynamicColor] is on, our own palette otherwise, light or dark following the system.
 */
@Composable
fun EtherTetherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme(darkTheme, dynamicColor), content = content)
}

@Composable
private fun colorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return if (darkTheme) DarkColors else LightColors
}
