package com.alphonso.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SanctuaryDarkColorScheme = darkColorScheme(
    primary = LiturgicalGoldLight,
    onPrimary = SanctuaryNavy,
    primaryContainer = LiturgicalGoldDark,
    onPrimaryContainer = SacredIvory,
    secondary = MarianBlueLight,
    onSecondary = Color.White,
    secondaryContainer = MarianBlueDark,
    onSecondaryContainer = SacredIvory,
    tertiary = RubySacrificeLight,
    onTertiary = Color.White,
    background = SanctuaryNavy,
    onBackground = SacredIvory,
    surface = SanctuaryDarkSurface,
    onSurface = SacredIvory,
    surfaceVariant = SanctuaryCardSurface,
    onSurfaceVariant = SacredParchment,
    outline = BorderSubtle,
    error = RubySacrificeLight,
    onError = Color.White
)

private val SanctuaryLightColorScheme = lightColorScheme(
    primary = MarianBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EDF8),
    onPrimaryContainer = MarianBlueDark,
    secondary = LiturgicalGold,
    onSecondary = SanctuaryNavy,
    secondaryContainer = Color(0xFFFFF3D6),
    onSecondaryContainer = LiturgicalGoldDark,
    tertiary = RubySacrifice,
    onTertiary = Color.White,
    background = SacredParchment,
    onBackground = SanctuaryNavy,
    surface = Color.White,
    onSurface = SanctuaryNavy,
    surfaceVariant = Color(0xFFF0EBE1),
    onSurfaceVariant = SanctuaryNavy,
    outline = Color(0xFFD3CABE),
    error = RubySacrifice,
    onError = Color.White
)

@Composable
fun SanctuaryTheme(
    darkTheme: Boolean = true, // Default to deep sacred dark atmosphere
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SanctuaryDarkColorScheme else SanctuaryLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun AlphonsoTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SanctuaryTheme(darkTheme = darkTheme, content = content)
}