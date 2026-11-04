package com.radiocompanion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
fun CarRadioTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) CarDarkColors else CarLightColors,
        typography = CarTypography,
        content = content,
    )
}
