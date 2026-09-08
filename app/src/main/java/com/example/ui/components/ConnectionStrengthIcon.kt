package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.ConnectionQualityLevel
import com.example.viewmodel.ConnectionStatus

/**
 * Connection strength indicator observing latency and connection status from the ViewModel.
 * Dynamically changes colors from green (< 45ms) to yellow/amber (85-150ms) to red (> 150ms).
 */
@Composable
fun ConnectionStrengthIcon(
  latencyMs: Int,
  connectionStatus: ConnectionStatus,
  connectionQuality: ConnectionQualityLevel,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null
) {
  val animatedColor by animateColorAsState(
    targetValue = connectionQuality.color,
    label = "connection_strength_color"
  )

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .clip(RoundedCornerShape(99.dp))
      .background(Color.White.copy(alpha = 0.94f))
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
      .clickable(enabled = onClick != null) { onClick?.invoke() }
      .padding(horizontal = 10.dp, vertical = 5.dp)
      .testTag("connection_strength_indicator")
  ) {
    // 4-Bar Signal Graphic
    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      modifier = Modifier.height(14.dp)
    ) {
      val barCount = 4
      val activeBars = connectionQuality.bars

      for (i in 1..barCount) {
        val barHeight = (4 + i * 2.5f).dp
        val isBarActive = i <= activeBars
        val barColor = if (isBarActive) animatedColor else Color(0xFFD8DCED)

        Box(
          modifier = Modifier
            .width(3.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(1.dp))
            .background(barColor)
        )
      }
    }

    Spacer(modifier = Modifier.width(6.dp))

    // Latency text and status color dot
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(animatedColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (connectionStatus == ConnectionStatus.CONNECTED) "${latencyMs} ms" else connectionStatus.label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = animatedColor,
          fontSize = 10.sp
        )
      }
    }
  }
}
