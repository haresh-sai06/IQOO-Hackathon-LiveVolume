package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val activeTheme = ThemeManager.currentTheme

  val darkColorScheme = darkColorScheme(
    primary = activeTheme.primaryContainer,
    onPrimary = LiveOnPrimary,
    primaryContainer = activeTheme.primary,
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

  val lightColorScheme = lightColorScheme(
    primary = activeTheme.primaryContainer,
    onPrimary = LiveOnPrimary,
    primaryContainer = activeTheme.primary,
    onPrimaryContainer = LiveOnPrimaryContainer,
    secondary = activeTheme.primary,
    onSecondary = LiveOnSecondary,
    secondaryContainer = activeTheme.subtle,
    onSecondaryContainer = activeTheme.primary,
    surface = LiveSurface,
    onSurface = LiveOnSurface,
    surfaceVariant = activeTheme.border,
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

  val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
