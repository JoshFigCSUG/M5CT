package com.csugprojects.m5ct.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- 1. Color Schemes (Based on the Material 3 standard) ---

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,      // Main interactive color
    secondary = PurpleGrey80,  // Secondary container/element color
    tertiary = Pink80,       // Accent color
    background = BlackBase,    // Dark background for screens
    surface = DarkGreySurface  // Dark background for cards/containers
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = WhiteBase,
    surface = WhiteSurface
)

/**
 * The main Composable function for the application's theme.
 * This should wrap your entire application UI in MainActivity.kt.
 */
@Composable
fun M5CTTheme(
    // Default to system dark mode setting
    darkTheme: Boolean = isSystemInDarkTheme(),

    // Enable dynamic color (Android 12+), which matches system wallpaper
    dynamicColor: Boolean = true,

    content: @Composable () -> Unit
) {
    // Choose scheme based on settings and system availability
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Status Bar color synchronization
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme.not()
        }
    }

    // Apply the Material Theme to the entire application
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Assume Typography.kt is defined
        content = content
    )
}

// NOTE: The separate Color.kt and Type.kt files defining the constants are omitted
// but are required for the theme to fully compile and function.