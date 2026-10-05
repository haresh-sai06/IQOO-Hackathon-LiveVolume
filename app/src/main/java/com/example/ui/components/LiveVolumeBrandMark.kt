package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LivePrimary
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSecondaryContainer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Local LiveVolume brand mark — person silhouette + orbiting node.
 * Drawn in Compose so auth/splash never depend on a remote URL.
 */
@Composable
fun LiveVolumeBrandMark(
  modifier: Modifier = Modifier,
  size: Dp = 56.dp,
  filled: Boolean = true
) {
  val shape = RoundedCornerShape(size * 0.30f)
  Box(
    modifier = modifier
      .size(size)
      .clip(shape)
      .then(
        if (filled) {
          Modifier.background(
            Brush.linearGradient(
              colors = listOf(LivePrimary, LivePrimaryContainer)
            )
          )
        } else {
          Modifier
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E7FF), shape)
        }
      )
      .testTag("livevolume_brand_mark"),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.size(size * 0.72f)) {
      val strokeColor = if (filled) Color.White else LivePrimaryContainer
      val accent = if (filled) LiveSecondaryContainer else LivePrimary
      val stroke = Stroke(width = size.toPx() * 0.055f, cap = StrokeCap.Round)
      val cx = this.size.width / 2f
      val cy = this.size.height / 2f
      val headR = this.size.minDimension * 0.16f

      // Head
      drawCircle(
        color = strokeColor,
        radius = headR,
        center = Offset(cx, cy - this.size.height * 0.18f),
        style = stroke
      )

      // Torso arch
      val torsoTop = cy + this.size.height * 0.02f
      val torsoBottom = cy + this.size.height * 0.32f
      val torsoHalf = this.size.width * 0.28f
      drawArc(
        color = strokeColor,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - torsoHalf, torsoTop),
        size = Size(torsoHalf * 2f, (torsoBottom - torsoTop) * 1.6f),
        style = stroke
      )

      // Orbit ellipse
      val orbitW = this.size.width * 0.92f
      val orbitH = this.size.height * 0.42f
      drawOval(
        color = accent.copy(alpha = 0.95f),
        topLeft = Offset(cx - orbitW / 2f, cy - orbitH / 2f + this.size.height * 0.06f),
        size = Size(orbitW, orbitH),
        style = Stroke(width = size.toPx() * 0.045f, cap = StrokeCap.Round)
      )

      // Satellite node on the orbit (upper-right)
      val angle = Math.toRadians(318.0)
      val nodeX = cx + cos(angle).toFloat() * (orbitW / 2f) * 0.92f
      val nodeY = cy + this.size.height * 0.06f + sin(angle).toFloat() * (orbitH / 2f) * 0.92f
      drawCircle(
        color = if (filled) Color.White else accent,
        radius = this.size.minDimension * 0.08f,
        center = Offset(nodeX, nodeY)
      )
    }
  }
}
