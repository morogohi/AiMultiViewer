package com.aimultiviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0B7285)
private val TealLight = Color(0xFF3BA8B8)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = TealLight
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    secondary = Teal
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
