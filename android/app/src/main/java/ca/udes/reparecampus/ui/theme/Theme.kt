package ca.udes.reparecampus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5F8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF52606D),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDCBE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF94CCFF),
    onPrimary = Color(0xFF003352),
    primaryContainer = Color(0xFF004B74),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFFB9C8DA),
    tertiary = Color(0xFFFFB870),
    tertiaryContainer = Color(0xFF693C00),
)

@Composable
fun RepareCampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
