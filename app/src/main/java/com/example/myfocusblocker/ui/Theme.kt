package com.example.myfocusblocker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF5B6CFF)
private val IndigoDark = Color(0xFF3D4BCC)

private val DarkColors = darkColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Color(0xFF7C4DFF),
    background = Color(0xFF0E0F1A),
    surface = Color(0xFF171827),
    surfaceVariant = Color(0xFF1F2133),
    onBackground = Color(0xFFE9EAF5),
    onSurface = Color(0xFFE9EAF5),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    secondary = Color(0xFF6A3DE8),
)

@Composable
fun MyFocusTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
