package com.example.ivan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Light scheme ──────────────────────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary              = Indigo600,
    onPrimary            = Color.White,
    primaryContainer     = Indigo50,
    onPrimaryContainer   = Indigo900,
    secondary            = Violet500,
    onSecondary          = Color.White,
    secondaryContainer   = Violet50,
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary             = Blue500,
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFDBEAFE),
    onTertiaryContainer  = Color(0xFF1E3A8A),
    background           = Gray50,
    onBackground         = Color(0xFF111827),
    surface              = Color.White,
    onSurface            = Color(0xFF111827),
    surfaceVariant       = Gray100,
    onSurfaceVariant     = Color(0xFF6B7280),
    outline              = Gray200,
    outlineVariant       = Color(0xFFF3F4F6),
)

// ── Dark scheme ───────────────────────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary              = Indigo700,
    onPrimary            = Color(0xFF1E1B4B),
    primaryContainer     = Indigo800,
    onPrimaryContainer   = Color(0xFFC7D2FE),
    secondary            = Violet300,
    onSecondary          = Color(0xFF2E1065),
    secondaryContainer   = Violet900,
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary             = Blue300,
    onTertiary           = Color(0xFF1E3A8A),
    tertiaryContainer    = Color(0xFF1D4ED8),
    onTertiaryContainer  = Color(0xFFDBEAFE),
    background           = Dark900,
    onBackground         = Color(0xFFE5E7EB),
    surface              = Dark800,
    onSurface            = Color(0xFFE5E7EB),
    surfaceVariant       = Dark700,
    onSurfaceVariant     = Color(0xFF9CA3AF),
    outline              = Dark600,
    outlineVariant       = Dark700,
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
            // Статус-бар всегда чёрный (пункт 5 плана)
            window.statusBarColor = Color.Black.toArgb()
            // Иконки статус-бара всегда светлые (белые) — видно на чёрном фоне
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
