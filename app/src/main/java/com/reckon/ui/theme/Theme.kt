package com.reckon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun ReckonTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = Color.White,
            secondary = palette.teal, onSecondary = Color.White,
            error = palette.red, onError = Color.White,
            background = palette.paper, onBackground = palette.ink,
            surface = palette.card, onSurface = palette.ink,
            surfaceVariant = palette.cardAlt, onSurfaceVariant = palette.inkSoft,
            outline = palette.line
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = Color.White,
            secondary = palette.teal, onSecondary = Color.White,
            error = palette.red, onError = Color.White,
            background = palette.paper, onBackground = palette.ink,
            surface = palette.card, onSurface = palette.ink,
            surfaceVariant = palette.cardAlt, onSurfaceVariant = palette.inkSoft,
            outline = palette.line
        )
    }
    CompositionLocalProvider(LocalReckonPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content
        )
    }
}
