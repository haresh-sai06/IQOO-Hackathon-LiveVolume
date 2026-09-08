package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * On-screen binaural acoustic balance HUD widget displaying real-time
 * left/right ear spatial levels and virtual sound source azimuth localization.
 */
@Composable
fun SpatialAudioVisualizer(
  azimuthDegrees: Float,
  audioLevel: Float,
  modifier: Modifier = Modifier
) {
  // Approximate ILD (Interaural Level Difference) based on azimuth
  val thetaRad = Math.toRadians(azimuthDegrees.toDouble()).toFloat()
  val shadowFactor = (cos(thetaRad) * 0.5f + 0.5f).coerceIn(0.40f, 1.0f)
  val leftGain = if (azimuthDegrees <= 0) 1.0f else shadowFactor
  val rightGain = if (azimuthDegrees >= 0) 1.0f else shadowFactor

  val rawLeft = (audioLevel * leftGain).coerceIn(0.08f, 1.0f)
  val rawRight = (audioLevel * rightGain).coerceIn(0.08f, 1.0f)

  val leftLevelAnim by animateFloatAsState(targetValue = rawLeft, animationSpec = tween(80), label = "left")
  val rightLevelAnim by animateFloatAsState(targetValue = rawRight, animationSpec = tween(80), label = "right")

  Row(
    modifier = modifier
      .clip(RoundedCornerShape(99.dp))
      .background(Color.White.copy(alpha = 0.94f))
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
      .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Icon(
      imageVector = Icons.Default.Headset,
      contentDescription = "Binaural Headset",
      tint = LivePrimaryContainer,
      modifier = Modifier.size(15.dp)
    )

    // Left Ear Meter Bar
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "L",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(2.dp))
      Box(
        modifier = Modifier
          .width(5.dp)
          .height(18.dp)
          .clip(RoundedCornerShape(99.dp))
          .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.BottomCenter
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(leftLevelAnim)
            .clip(RoundedCornerShape(99.dp))
            .background(if (leftLevelAnim > 0.7f) Color(0xFF10B981) else LivePrimaryContainer)
        )
      }
    }

    // Right Ear Meter Bar
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "R",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(2.dp))
      Box(
        modifier = Modifier
          .width(5.dp)
          .height(18.dp)
          .clip(RoundedCornerShape(99.dp))
          .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.BottomCenter
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(rightLevelAnim)
            .clip(RoundedCornerShape(99.dp))
            .background(if (rightLevelAnim > 0.7f) Color(0xFF10B981) else LivePrimaryContainer)
        )
      }
    }

    // Azimuth Direction Indicator
    val headingLabel = when {
      azimuthDegrees < -15f -> "${(-azimuthDegrees).roundToInt()}° L"
      azimuthDegrees > 15f -> "${azimuthDegrees.roundToInt()}° R"
      else -> "Center"
    }

    Text(
      text = "HRTF • $headingLabel",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}
