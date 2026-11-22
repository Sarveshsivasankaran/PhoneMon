package com.phonebox.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDeep = Color(0xFF060913)
val BgSurface = Color(0xFF0D1322)
val BgCard = Color(0xFF131B30)
val CyanGlow = Color(0xFF00F2FE)
val BlueNeon = Color(0xFF4FACFE)
val VioletAccent = Color(0xFF7F00FF)
val TextPrimary = Color(0xFFEBF4FF)
val TextMuted = Color(0xFF7E8DA6)
val BorderGlow = Color(0xFF1E2C4D)

private val DarkColorScheme = darkColorScheme(
    primary = CyanGlow,
    secondary = BlueNeon,
    tertiary = VioletAccent,
    background = BgDeep,
    surface = BgSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

