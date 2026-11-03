package com.radiocompanion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CarDarkColors = darkColorScheme(
    primary = Color(0xFF00C2FF), onPrimary = Color(0xFF00131A),
    secondary = Color(0xFF3DDC97),
    background = Color(0xFF07090C), onBackground = Color(0xFFF2F5F8),
    surface = Color(0xFF12161C), onSurface = Color(0xFFF2F5F8),
    surfaceVariant = Color(0xFF1C222B), onSurfaceVariant = Color(0xFFA9B4C2),
    error = Color(0xFFFF5C5C),
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
fun CarRadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CarDarkColors, typography = CarTypography, content = content)
}
