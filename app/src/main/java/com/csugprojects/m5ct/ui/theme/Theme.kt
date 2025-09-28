package com.csugprojects.m5ct.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Defines the Dark Material 3 Color Scheme using the green palette.
 */
private val DarkColorScheme = darkColorScheme(
    primary = GreenDarkPrimary,
    secondary = GreenDarkAccent,
    tertiary = GreenLight,
    background = BlackBase,
    surface = DarkGreySurface,
    onPrimary = Color.Black
)

/**
 * Defines the Light Material 3 Color Scheme using the green palette.
 */
private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    secondary = GreenLight,
    tertiary = GreenDarkAccent,
    background = WhiteBase,
    surface = GreenSurface,
    onPrimary = Color.White
)

/**
 * The main Composable function for applying the application's theme.
 * This handles system synchronization (dark/light mode) and dynamic colors.
 */
@Composable
fun M5CTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Selects the appropriate color scheme based on user preference and device support.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Ensures the Android status bar color matches the primary color of the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme.not()
        }
    }

    // Applies the final Material Theme configuration.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}