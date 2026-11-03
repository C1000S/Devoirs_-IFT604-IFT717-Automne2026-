package ca.usherbrooke.reparecampus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = NavyLight,
    onPrimaryContainer = Color.White,
    secondary = Amber,
    onSecondary = Color(0xFF2A1A00),
    tertiary = Teal,
    background = SurfaceLight,
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE7EBF0),
    onSurfaceVariant = Grey,
    error = Red,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = NavyLight,
    onPrimary = Color.White,
    primaryContainer = Navy,
    onPrimaryContainer = Color.White,
    secondary = Amber,
    onSecondary = Color(0xFF2A1A00),
    tertiary = Teal,
    background = SurfaceDark,
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF1B2127),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF2A323B),
    onSurfaceVariant = Color(0xFFB9C1CB),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun RepareCampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
