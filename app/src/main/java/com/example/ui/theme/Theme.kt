package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = LivePrimaryContainer,
    onPrimary = LiveOnPrimary,
    primaryContainer = LivePrimary,
    onPrimaryContainer = LiveOnPrimaryContainer,
    secondary = LiveSecondaryContainer,
    onSecondary = LiveOnSecondary,
    surface = Color(0xFF131B2E),
    onSurface = Color(0xFFFAF8FF),
    surfaceVariant = Color(0xFF283044),
    onSurfaceVariant = Color(0xFFC3C6D7),
    error = LiveError,
    onError = LiveOnError
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LivePrimaryContainer,
    onPrimary = LiveOnPrimary,
    primaryContainer = LivePrimaryContainer,
    onPrimaryContainer = LiveOnPrimaryContainer,
    secondary = LiveSecondary,
    onSecondary = LiveOnSecondary,
    secondaryContainer = LiveSecondaryContainer,
    onSecondaryContainer = LiveOnSecondaryContainer,
    surface = LiveSurface,
    onSurface = LiveOnSurface,
    surfaceVariant = LiveSurfaceContainerHighest,
    onSurfaceVariant = LiveOnSurfaceVariant,
    outline = LiveOutline,
    outlineVariant = LiveOutlineVariant,
    error = LiveError,
    onError = LiveOnError,
    errorContainer = LiveErrorContainer,
    onErrorContainer = LiveOnErrorContainer,
    background = LiveSurface,
    onBackground = LiveOnSurface
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
