package com.reckon.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The full set of semantic colors the UI uses, in one place so we can swap light/dark. */
data class ReckonPalette(
    val paper: Color, val card: Color, val cardAlt: Color,
    val ink: Color, val inkSoft: Color, val inkFaint: Color,
    val line: Color, val lineSoft: Color,
    val accent: Color, val accentSoft: Color,
    val teal: Color, val tealSoft: Color,
    val red: Color, val redSoft: Color, val gold: Color
)

val LightPalette = ReckonPalette(
    paper = Color(0xFFF2EBDB), card = Color(0xFFFBF7EE), cardAlt = Color(0xFFFFFFFF),
    ink = Color(0xFF211C16), inkSoft = Color(0xFF6B6155), inkFaint = Color(0xFF9A8E7C),
    line = Color(0xFFE3D8C1), lineSoft = Color(0xFFEDE4D2),
    accent = Color(0xFFC0532B), accentSoft = Color(0xFFF1DACB),
    teal = Color(0xFF1E6F66), tealSoft = Color(0xFFD8E8E4),
    red = Color(0xFFA63A2A), redSoft = Color(0xFFF0D6CF), gold = Color(0xFFB0852C)
)

val DarkPalette = ReckonPalette(
    paper = Color(0xFF17140F), card = Color(0xFF221D16), cardAlt = Color(0xFF2B241C),
    ink = Color(0xFFEFE7D6), inkSoft = Color(0xFFB7AB96), inkFaint = Color(0xFF887C69),
    line = Color(0xFF3A332A), lineSoft = Color(0xFF2E2820),
    accent = Color(0xFFD9683E), accentSoft = Color(0xFF3C281D),
    teal = Color(0xFF5BB3A6), tealSoft = Color(0xFF1E3733),
    red = Color(0xFFE0705A), redSoft = Color(0xFF3C241E), gold = Color(0xFFCBA14A)
)

/** Active palette for the current composition; defaults to light. */
val LocalReckonPalette = staticCompositionLocalOf { LightPalette }

/*
 * Named color accessors. These are @Composable getters that read the active palette,
 * so existing references (Paper, Ink, Accent, …) automatically follow light/dark mode
 * without any call-site changes.
 */
val Paper: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.paper
val Card: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.card
val CardAlt: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.cardAlt
val Ink: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.ink
val InkSoft: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.inkSoft
val InkFaint: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.inkFaint
val LineC: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.line
val LineSoft: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.lineSoft
val Accent: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.accent
val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.accentSoft
val Teal: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.teal
val TealSoft: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.tealSoft
val RedC: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.red
val RedSoft: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.redSoft
val Gold: Color @Composable @ReadOnlyComposable get() = LocalReckonPalette.current.gold
