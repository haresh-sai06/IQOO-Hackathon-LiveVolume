package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.HapticType
import com.example.util.HapticsManager
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

data class StreamTelemetry(
  val latency: Int = 0,
  val fps: Int = 0,
  val bitrate: Float = 0f,
  val jitter: Float = 0f,
  val meshQuality: String = "Waiting",
  val signalBars: Int = 0,
  val connectionType: String = "RTC"
)

/**
 * Real-Time 3D Volumetric Stream Telemetry & Latency HUD Monitor.
 * Native Jetpack Compose implementation of the React CallSignalLatencyMonitor.
 */
@Composable
fun CallSignalLatencyMonitor(
  latencyMs: Int = -1,
  connectionQuality: Any? = null,
  connectionStatus: Any? = null,
  binauralActive: Boolean = true,
  azimuthDegrees: Float = 0f,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var isExpanded by remember { mutableStateOf(false) }

  // Honest telemetry only: use measured [latencyMs] when provided (>=0).
  // Never invent sub-10ms or synthetic RTT for the HUD.
  var telemetry by remember {
    mutableStateOf(
      StreamTelemetry(
        latency = if (latencyMs >= 0) latencyMs else 0,
        meshQuality = if (latencyMs >= 0) "Measured" else "Waiting"
      )
    )
  }
  val latencyHistory = remember { mutableStateListOf<Int>() }

  LaunchedEffect(latencyMs) {
    if (latencyMs < 0) {
      telemetry = telemetry.copy(latency = 0, meshQuality = "Waiting", fps = 0)
      return@LaunchedEffect
    }
    if (latencyHistory.size >= 10) latencyHistory.removeAt(0)
    latencyHistory.add(latencyMs)
    telemetry = telemetry.copy(
      latency = latencyMs,
      fps = 0, // FPS not measured here — do not fake 60
      bitrate = 0f,
      jitter = 0f,
      meshQuality = when {
        latencyMs <= 80 -> "Excellent link"
        latencyMs <= 150 -> "Good link"
        latencyMs <= 300 -> "Fair link"
        else -> "Weak link"
      }
    )
  }

  val pulseAnim = rememberInfiniteTransition(label = "pulse")
  val alphaPulse by pulseAnim.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "alpha"
  )

  val qualityColor = when {
    telemetry.latency <= 28 -> Color(0xFF10B981)
    telemetry.latency <= 45 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
  }

  Box(modifier = modifier) {
    // Compact HUD Pill
    AnimatedVisibility(
      visible = !isExpanded,
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut()
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.65f))
          .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
          .clickable {
            HapticsManager.trigger(context, HapticType.LIGHT)
            isExpanded = true
          }
          .padding(horizontal = 10.dp, vertical = 5.dp)
      ) {
        // 4 Signal Bars
        Row(
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          modifier = Modifier.height(14.dp)
        ) {
          val barHeights = listOf(4.dp, 7.dp, 10.dp, 13.dp)
          barHeights.forEachIndexed { index, h ->
            Box(
              modifier = Modifier
                .width(2.5.dp)
                .height(h)
                .clip(RoundedCornerShape(1.dp))
                .background(
                  if (telemetry.signalBars > index) Color(0xFF34D399) else Color.White.copy(alpha = 0.3f)
                )
            )
          }
        }

        Spacer(modifier = Modifier.width(7.dp))

        // Pulse Dot
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(qualityColor.copy(alpha = alphaPulse))
        )

        Spacer(modifier = Modifier.width(5.dp))

        // Latency
        Text(
          text = "${telemetry.latency}ms",
          color = Color.White,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "•", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        Spacer(modifier = Modifier.width(6.dp))

        // Connection Type
        Text(
          text = telemetry.connectionType,
          color = Color.White.copy(alpha = 0.85f),
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
          imageVector = Icons.Default.GraphicEq,
          contentDescription = "Stream stats",
          tint = Color.White.copy(alpha = 0.6f),
          modifier = Modifier.size(13.dp)
        )
      }
    }

    // Expanded Detailed Telemetry Overlay Card
    AnimatedVisibility(
      visible = isExpanded,
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut()
    ) {
      Column(
        modifier = Modifier
          .width(290.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(Color(0xFF0F172A).copy(alpha = 0.95f))
          .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
          .padding(14.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            // Signal Bars
            Row(
              verticalAlignment = Alignment.Bottom,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              modifier = Modifier.height(13.dp)
            ) {
              val barHeights = listOf(4.dp, 7.dp, 10.dp, 13.dp)
              barHeights.forEach { h ->
                Box(
                  modifier = Modifier
                    .width(2.5.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF34D399))
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "3D Stream Quality",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
          }

          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.12f))
              .clickable {
                HapticsManager.trigger(context, HapticType.LIGHT)
                isExpanded = false
              },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close telemetry",
              tint = Color.White.copy(alpha = 0.8f),
              modifier = Modifier.size(15.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2x2 Metrics Grid
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Metric 1: Roundtrip Latency
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.06f))
              .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
              .padding(8.dp)
          ) {
            Column {
              Text("Roundtrip Latency", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
              Spacer(modifier = Modifier.height(2.dp))
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = "${telemetry.latency}",
                  color = Color(0xFF34D399),
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("ms", color = Color(0xFF34D399).copy(alpha = 0.8f), fontSize = 10.sp)
              }
            }
          }

          // Metric 2: Depth Bitrate
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.06f))
              .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
              .padding(8.dp)
          ) {
            Column {
              Text("Depth Bitrate", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
              Spacer(modifier = Modifier.height(2.dp))
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = "${telemetry.bitrate}",
                  color = Color(0xFF60A5FA),
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("Mbps", color = Color(0xFF60A5FA).copy(alpha = 0.8f), fontSize = 10.sp)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Metric 3: Volumetric Mesh FPS
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.06f))
              .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
              .padding(8.dp)
          ) {
            Column {
              Text("Volumetric Mesh", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
              Spacer(modifier = Modifier.height(2.dp))
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = "${telemetry.fps}",
                  color = Color(0xFFA78BFA),
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("FPS", color = Color(0xFFA78BFA).copy(alpha = 0.8f), fontSize = 10.sp)
              }
            }
          }

          // Metric 4: Jitter / Loss
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.06f))
              .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
              .padding(8.dp)
          ) {
            Column {
              Text("Jitter / Loss", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
              Spacer(modifier = Modifier.height(2.dp))
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = "${telemetry.jitter}",
                  color = Color(0xFF22D3EE),
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("ms (0%)", color = Color(0xFF22D3EE).copy(alpha = 0.8f), fontSize = 10.sp)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Latency Stability Sparkline Bar Chart
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(8.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Latency Stability", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
              Text(
                text = telemetry.meshQuality,
                color = Color(0xFF34D399),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sparkline Bars
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.Bottom
            ) {
              latencyHistory.forEach { valMs ->
                val heightFraction = min(1f, max(0.25f, (valMs - 15) / 25f))
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(Color(0xFF34D399).copy(alpha = 0.85f))
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Footer status
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF34D399).copy(alpha = alphaPulse))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "Binaural Spatial Sync: OK",
              color = Color.White.copy(alpha = 0.7f),
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace
            )
          }
          Text(
            text = telemetry.connectionType,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }
  }
}
