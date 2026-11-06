package com.radiocompanion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** High-contrast dark scheme for night driving (default). */
private val CarDarkColors = darkColorScheme(
    primary = Color(0xFF00C2FF), onPrimary = Color(0xFF00131A),
    secondary = Color(0xFF3DDC97),
    background = Color(0xFF07090C), onBackground = Color(0xFFF2F5F8),
    surface = Color(0xFF12161C), onSurface = Color(0xFFF2F5F8),
    surfaceVariant = Color(0xFF1C222B), onSurfaceVariant = Color(0xFFA9B4C2),
    error = Color(0xFFFF5C5C),
)

/** Clean light scheme for daytime. Dark text on light surfaces, same accent hue, deepened for contrast. */
private val CarLightColors = lightColorScheme(
    primary = Color(0xFF006A8A), onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF0A7A55),
    background = Color(0xFFF4F6F9), onBackground = Color(0xFF0B0F14),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0B0F14),
    surfaceVariant = Color(0xFFE2E7ED), onSurfaceVariant = Color(0xFF3F4B57),
    error = Color(0xFFB3261E),
)

/**
 * Ramadan, night: deep midnight blue with lantern gold, the classic fanous palette.
 * Same contrast targets as the regular dark theme.
 */
private val RamadanDarkColors = darkColorScheme(
    primary = Color(0xFFE8C26A), onPrimary = Color(0xFF2A1E00),
    primaryContainer = Color(0xFF3B2F10), onPrimaryContainer = Color(0xFFFFE3A1),
    secondary = Color(0xFF6ED3C1), onSecondary = Color(0xFF00201B),
    secondaryContainer = Color(0xFF173A40), onSecondaryContainer = Color(0xFFBDEFE6),
    tertiary = Color(0xFFC9B6FF),
    background = Color(0xFF0A0F24), onBackground = Color(0xFFF4EEDC),
    surface = Color(0xFF121A36), onSurface = Color(0xFFF4EEDC),
    surfaceVariant = Color(0xFF1E2851), onSurfaceVariant = Color(0xFFC0C7E2),
    outline = Color(0xFF6B6447),
    error = Color(0xFFFF6B6B),
)

/** Ramadan, day: warm ivory and deep gold. */
private val RamadanLightColors = lightColorScheme(
    primary = Color(0xFF7F5A00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE08F), onPrimaryContainer = Color(0xFF281A00),
    secondary = Color(0xFF00695C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6EDE5), onSecondaryContainer = Color(0xFF00201B),
    background = Color(0xFFFBF5E6), onBackground = Color(0xFF1C1A14),
    surface = Color(0xFFFFFBF2), onSurface = Color(0xFF1C1A14),
    surfaceVariant = Color(0xFFF0E5C8), onSurfaceVariant = Color(0xFF4D4632),
    outline = Color(0xFFB5A577),
    error = Color(0xFFB3261E),
)

/** True while the Ramadan look is on: screens add the pattern and the crescent. */
val LocalRamadanTheme = staticCompositionLocalOf { false }

// Sized for reading at arm's length. Nothing important below ~18sp.
private val CarTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp),
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
)

object CarDimens {
    val MinTouch = 76.dp
    val PlayButton = 120.dp
    val TileHeight = 112.dp
    val Gutter = 20.dp
}

@Composable
fun CarRadioTheme(darkTheme: Boolean = true, ramadan: Boolean = false, content: @Composable () -> Unit) {
    val colors = when {
        ramadan && darkTheme -> RamadanDarkColors
        ramadan -> RamadanLightColors
        darkTheme -> CarDarkColors
        else -> CarLightColors
    }
    CompositionLocalProvider(LocalRamadanTheme provides ramadan) {
        MaterialTheme(colorScheme = colors, typography = CarTypography, content = content)
    }
}
