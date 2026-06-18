package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Delta Emulator Palette
val DeltaPurple = Color(0xFF8A28F6)
val DeltaPurpleDark = Color(0xFF671DB8)
val DeltaPurpleLight = Color(0xFFAB5CFF)

val DarkBackground = Color(0xFF121212) // iOS pure black -> standard Android dark
val DarkSurface = Color(0xFF1C1C1E) // iOS grouped background dark
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFF8E8E93)

private val DeltArchDarkScheme = darkColorScheme(
    primary = DeltaPurpleLight,
    onPrimary = Color.White,
    primaryContainer = DeltaPurple,
    onPrimaryContainer = Color.White,
    secondary = DeltaPurpleLight,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = DeltaPurpleLight
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = DeltArchDarkScheme) {
        content()
    }
}
