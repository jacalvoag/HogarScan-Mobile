package com.example.hogarscan.ui.theme

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
    primary = HogarColors.Primary, onPrimary = Color.White,
    secondary = HogarColors.Brand, onSecondary = HogarColors.Ink,
    secondaryContainer = HogarColors.Soft, onSecondaryContainer = HogarColors.Primary,
    background = HogarColors.Canvas, onBackground = HogarColors.Ink,
    surface = HogarColors.Surface, onSurface = HogarColors.Ink,
    surfaceVariant = HogarColors.Soft, onSurfaceVariant = HogarColors.Muted,
    outline = HogarColors.Line, error = HogarColors.DangerInk,
    errorContainer = HogarColors.Danger, onErrorContainer = HogarColors.DangerInk
)
private val DarkColors = darkColorScheme(
    primary = HogarColors.Brand, onPrimary = HogarColors.Primary,
    background = Color(0xFF151B10), onBackground = Color(0xFFE5ECD9),
    surface = Color(0xFF202719), onSurface = Color(0xFFE5ECD9),
    surfaceVariant = Color(0xFF424B38), onSurfaceVariant = Color(0xFFD2DCC4),
    secondary = Color(0xFFC0D2A5), onSecondary = Color(0xFF26351A)
)

@Composable
fun HogarScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
