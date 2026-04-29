package com.example.ivan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary              = Blue700,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFDBEAFE),
    onPrimaryContainer   = Blue900,
    secondary            = Violet600,
    onSecondary          = Color.White,
    secondaryContainer   = Violet100,
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary             = Indigo500,
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFE0E7FF),
    onTertiaryContainer  = Color(0xFF1E1B4B),
    background           = Surface0,
    onBackground         = Color(0xFF0F172A),
    surface              = Surface0,
    onSurface            = Color(0xFF0F172A),
    surfaceVariant       = Surface1,
    onSurfaceVariant     = Color(0xFF475569),
    outline              = Color(0xFFCBD5E1),
)

private val DarkColors = darkColorScheme(
    primary              = Blue200,
    onPrimary            = Color(0xFF1E3A8A),
    primaryContainer     = Blue800,
    onPrimaryContainer   = Color(0xFFBFDBFE),
    secondary            = Violet300,
    onSecondary          = Color(0xFF2E1065),
    secondaryContainer   = Violet900,
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary             = Indigo300,
    onTertiary           = Color(0xFF1E1B4B),
    tertiaryContainer    = Color(0xFF312E81),
    onTertiaryContainer  = Color(0xFFE0E7FF),
    background           = Dark0,
    onBackground         = Color(0xFFE2E8F0),
    surface              = Dark1,
    onSurface            = Color(0xFFE2E8F0),
    surfaceVariant       = Dark2,
    onSurfaceVariant     = Color(0xFF94A3B8),
    outline              = Color(0xFF334155),
)

@Composable
fun IvanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
