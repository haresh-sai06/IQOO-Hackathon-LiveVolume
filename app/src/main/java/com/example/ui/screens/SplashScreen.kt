package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LiveVolumeBrandMark
import com.example.ui.theme.LivePrimary
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSurface
import kotlinx.coroutines.delay

/**
 * Short branded splash shown once on cold start before Auth or Main.
 */
@Composable
fun SplashScreen(
  onFinished: () -> Unit,
  modifier: Modifier = Modifier
) {
  val alpha = remember { Animatable(0f) }
  val scale = remember { Animatable(0.88f) }

  LaunchedEffect(Unit) {
    alpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    scale.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    delay(1100)
    alpha.animateTo(0f, tween(280))
    onFinished()
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(LiveSurface, Color(0xFFE8EEFF), LiveSurface)
        )
      )
      .testTag("splash_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .alpha(alpha.value)
        .scale(scale.value)
        .padding(24.dp)
    ) {
      LiveVolumeBrandMark(size = 88.dp, filled = true)
      Spacer(modifier = Modifier.height(20.dp))
      Text(
        text = "LiveVolume",
        style = MaterialTheme.typography.headlineMedium.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = (-0.4).sp
        ),
        color = LivePrimary
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Volumetric calling for iQOO",
        style = MaterialTheme.typography.bodyMedium,
        color = LivePrimaryContainer.copy(alpha = 0.85f),
        textAlign = TextAlign.Center
      )
    }
  }
}
