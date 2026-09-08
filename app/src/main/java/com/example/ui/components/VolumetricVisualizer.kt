package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import com.example.model.VolumetricMeshMode
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D Point in simulated volumetric space
 */
private data class Vertex3D(
  val x: Float,
  val y: Float,
  val z: Float,
  val baseDepth: Float
)

/**
 * High-performance 3D Volumetric Canvas Visualizer.
 * Projects a real-time 3D facial/torso depth topology into 2D screen space
 * using perspective matrix calculations, responding to audio levels and camera tilt.
 */
@Composable
fun VolumetricVisualizer(
  rotationX: Float,
  rotationY: Float,
  meshMode: VolumetricMeshMode,
  depthIntensity: Float = 1.0f,
  audioLevel: Float = 0.0f,
  modifier: Modifier = Modifier
) {
  // Continuous holographic laser scanline cycle
  val infiniteTransition = rememberInfiniteTransition(label = "scanline")
  val scanPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(2800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "scanline_y"
  )

  // Pre-generate 3D grid vertices modeling a human head and shoulders depth topology
  val rows = 24
  val cols = 22
  val vertices = remember {
    val list = mutableListOf<Vertex3D>()
    for (r in 0 until rows) {
      val normY = (r.toFloat() / (rows - 1)) * 2f - 1f // -1 to 1 (top to bottom)
      for (c in 0 until cols) {
        val normX = (c.toFloat() / (cols - 1)) * 2f - 1f // -1 to 1 (left to right)

        // Head and nose volumetric protrusion model
        val headRadiusSq = (normX * normX * 1.5f) + ((normY + 0.15f) * (normY + 0.15f) * 1.2f)
        val isHead = headRadiusSq < 0.65f

        // Nose prominence near center
        val distToNose = sqrt((normX * normX) + ((normY + 0.05f) * (normY + 0.05f)))
        val noseProtrusion = (exp((-distToNose * distToNose * 18f).toDouble()).toFloat() * 1.3f)

        // Cheekbones and brow depth
        val cheekProtrusion = (exp(-((normX.times(normX) - 0.12f).let { it * it } + normY * normY) * 12.0).toFloat() * 0.6f)

        val baseZ = if (isHead) {
          (1.0f - headRadiusSq).coerceAtLeast(0f) * 1.1f + noseProtrusion + cheekProtrusion
        } else if (normY > 0.45f) {
          // Shoulders / chest plane sloping backward
          (0.45f - normY * 0.35f).coerceAtLeast(0f)
        } else {
          0.05f
        }

        list.add(
          Vertex3D(
            x = normX * 180f,
            y = normY * 240f,
            z = baseZ * 80f,
            baseDepth = baseZ
          )
        )
      }
    }
    list
  }

  Canvas(modifier = modifier.fillMaxSize()) {
    val width = size.width
    val height = size.height
    val centerX = width / 2f
    val centerY = height / 2f

    val radX = Math.toRadians(rotationX.toDouble()).toFloat()
    val radY = Math.toRadians(rotationY.toDouble()).toFloat()

    val cosX = cos(radX)
    val sinX = sin(radX)
    val cosY = cos(radY)
    val sinY = sin(radY)

    val focalLength = 480f
    val cameraDistance = 500f

    val projectedPoints = Array(vertices.size) { Offset.Zero }
    val projectedDepths = FloatArray(vertices.size)

    // Audio reactivity adds micro-vibration
    val audioDisplacement = audioLevel * 14f

    for (i in vertices.indices) {
      val v = vertices[i]
      val scaledZ = (v.z * depthIntensity) + (v.baseDepth * audioDisplacement)

      // 1. Rotate Y (Yaw / horizontal orbit)
      val x1 = v.x * cosY + scaledZ * sinY
      val z1 = -v.x * sinY + scaledZ * cosY

      // 2. Rotate X (Pitch / vertical orbit)
      val y2 = v.y * cosX - z1 * sinX
      val z2 = v.y * sinX + z1 * cosX

      // 3. Perspective projection
      val denom = z2 + cameraDistance
      val scale = if (denom > 10f) focalLength / denom else 1f
      val projX = centerX + x1 * scale
      val projY = centerY + y2 * scale

      projectedPoints[i] = Offset(projX, projY)
      projectedDepths[i] = (scaledZ / 80f).coerceIn(0f, 1f)
    }

    when (meshMode) {
      VolumetricMeshMode.POINT_CLOUD -> {
        // Render 3D floating glowing particles
        for (i in vertices.indices) {
          val pt = projectedPoints[i]
          val depth = projectedDepths[i]
          val pointRadius = (depth * 3.2f + 1.2f)

          val pointColor = when {
            depth > 0.65f -> Color(0xFF00F0FF).copy(alpha = 0.95f) // Glowing cyan highlight for near features
            depth > 0.35f -> Color(0xFF3B82F6).copy(alpha = 0.75f) // Electric blue for midground
            else -> Color(0xFF6366F1).copy(alpha = 0.35f)          // Deep indigo for background
          }

          drawCircle(
            color = pointColor,
            radius = pointRadius,
            center = pt
          )
        }
      }

      VolumetricMeshMode.HOLOGRAPHIC_MESH -> {
        // Draw interconnected wireframe polygons with holographic cyan glow
        val strokeColor = Color(0xFF00F5FF).copy(alpha = 0.40f)
        val nodeHighlight = Color(0xFFE0F2FE).copy(alpha = 0.85f)

        for (r in 0 until rows) {
          for (c in 0 until cols) {
            val idx = r * cols + c
            val p0 = projectedPoints[idx]

            // Connect horizontal neighbor
            if (c < cols - 1) {
              val pRight = projectedPoints[idx + 1]
              drawLine(
                color = strokeColor,
                start = p0,
                end = pRight,
                strokeWidth = 1.2f,
                cap = StrokeCap.Round
              )
            }

            // Connect vertical neighbor
            if (r < rows - 1) {
              val pDown = projectedPoints[idx + cols]
              drawLine(
                color = strokeColor,
                start = p0,
                end = pDown,
                strokeWidth = 1.2f,
                cap = StrokeCap.Round
              )
            }

            // Draw glowing vertex nodes on facial contours
            if (projectedDepths[idx] > 0.55f && (r + c) % 2 == 0) {
              drawCircle(
                color = nodeHighlight,
                radius = 2.0f,
                center = p0
              )
            }
          }
        }
      }

      VolumetricMeshMode.DEPTH_CONTOURS -> {
        // Render color-coded topographic elevation contours (Warm near -> Cool far)
        for (i in vertices.indices) {
          val pt = projectedPoints[i]
          val depth = projectedDepths[i]

          val contourColor = when {
            depth > 0.75f -> Color(0xFFF43F5E) // Red/Crimson = Closest
            depth > 0.50f -> Color(0xFFF59E0B) // Amber = Mid-Near
            depth > 0.25f -> Color(0xFF10B981) // Emerald = Mid
            else -> Color(0xFF3B82F6)          // Blue = Furthest
          }

          drawCircle(
            color = contourColor.copy(alpha = 0.85f),
            radius = (depth * 2.8f + 1.5f),
            center = pt
          )
        }
      }

      VolumetricMeshMode.PARALLAX_VIDEO -> {
        // Subtle depth grid overlay highlighting contours on top of video feed
        val gridColor = Color.White.copy(alpha = 0.22f)
        for (i in vertices.indices step 2) {
          val pt = projectedPoints[i]
          if (projectedDepths[i] > 0.3f) {
            drawCircle(
              color = gridColor,
              radius = 1.5f,
              center = pt
            )
          }
        }
      }
    }

    // Dynamic Holographic Scanner Beam
    if (meshMode == VolumetricMeshMode.HOLOGRAPHIC_MESH || meshMode == VolumetricMeshMode.POINT_CLOUD) {
      val scanY = centerY - 180f + (scanPhase * 360f)
      drawLine(
        brush = Brush.horizontalGradient(
          colors = listOf(
            Color.Transparent,
            Color(0xFF00F5FF).copy(alpha = 0.85f),
            Color.White,
            Color(0xFF00F5FF).copy(alpha = 0.85f),
            Color.Transparent
          )
        ),
        start = Offset(centerX - 160f, scanY),
        end = Offset(centerX + 160f, scanY),
        strokeWidth = 2.0f
      )
    }
  }
}
