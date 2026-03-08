package com.example.twinmind.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = BackgroundLight,
    onPrimaryContainer = TealDark,
    secondary = OrangeAccent,
    onSecondary = SurfaceWhite,
    secondaryContainer = CardOrangeLight,
    onSecondaryContainer = OrangeAccent,
    tertiary = TealLight,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceWhite,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = RecordingRed,
    onError = SurfaceWhite,
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = SurfaceWhite,
    primaryContainer = TealDark,
    onPrimaryContainer = SurfaceWhite,
    secondary = OrangeAccent,
    onSecondary = SurfaceWhite,
    background = TextPrimary,
    onBackground = SurfaceWhite,
    surface = TealDark,
    onSurface = SurfaceWhite,
    surfaceVariant = TealDark,
    onSurfaceVariant = TextMuted,
    outline = TextSecondary,
    error = RecordingRed,
    onError = SurfaceWhite,
)

@Composable
fun TwinmindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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