package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class ThemePreset(
  val id: String,
  val displayName: String,
  val description: String,
  val primary: Color,
  val primaryContainer: Color,
  val subtle: Color,
  val border: Color
) {
  LIVE_BLUE(
    id = "blue",
    displayName = "Live Blue",
    description = "Default holographic cobalt palette",
    primary = Color(0xFF004AC6),
    primaryContainer = Color(0xFF2563EB),
    subtle = Color(0xFFF2F3FF),
    border = Color(0xFFDAE2FD)
  ),
  HOLO_VIOLET(
    id = "violet",
    displayName = "Holo Violet",
    description = "Cybernetic neon amethyst palette",
    primary = Color(0xFF7C3AED),
    primaryContainer = Color(0xFF8B5CF6),
    subtle = Color(0xFFF5F3FF),
    border = Color(0xFFDDD6FE)
  ),
  EMERALD_MATRIX(
    id = "emerald",
    displayName = "Emerald Matrix",
    description = "Biometric green precision palette",
    primary = Color(0xFF059669),
    primaryContainer = Color(0xFF10B981),
    subtle = Color(0xFFECFDF5),
    border = Color(0xFFA7F3D0)
  ),
  SOLAR_AMBER(
    id = "amber",
    displayName = "Solar Amber",
    description = "High-contrast warm photon palette",
    primary = Color(0xFFD97706),
    primaryContainer = Color(0xFFF59E0B),
    subtle = Color(0xFFFFFBEB),
    border = Color(0xFFFDE68A)
  )
}

object ThemeManager {
  var currentTheme by mutableStateOf(ThemePreset.LIVE_BLUE)
}
