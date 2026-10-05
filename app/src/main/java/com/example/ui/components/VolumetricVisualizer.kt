package com.example.ui.components

import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ml.PointCloud
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Live 3D point-cloud viewer with drag-orbit, pinch-zoom, inertia, and optional
 * Shared Presence desk grid (OpenGL preview — not ARCore world tracking).
 */
@Composable
fun VolumetricVisualizer(
  pointCloud: PointCloud?,
  modifier: Modifier = Modifier,
  initialYaw: Float = 18f,
  initialPitch: Float = 12f,
  initialDistance: Float = 1.85f,
  sharedPresenceEnabled: Boolean = false,
  surfaceFill: Boolean = true
) {
  var yaw by remember { mutableFloatStateOf(initialYaw) }
  var pitch by remember { mutableFloatStateOf(initialPitch) }
  var distance by remember { mutableFloatStateOf(initialDistance) }
  var yawVelocity by remember { mutableFloatStateOf(0f) }
  var pitchVelocity by remember { mutableFloatStateOf(0f) }

  val renderer = remember { PointCloudRenderer() }
  var glView by remember { mutableStateOf<GLSurfaceView?>(null) }

  LaunchedEffect(sharedPresenceEnabled, surfaceFill) {
    renderer.sharedPresenceEnabled = sharedPresenceEnabled
    renderer.surfaceFill = surfaceFill
    renderer.pointSize = if (surfaceFill) 20f else 14f
    glView?.requestRender()
  }

  LaunchedEffect(yaw, pitch, distance) {
    renderer.rotationYaw = yaw
    renderer.rotationPitch = pitch
    renderer.cameraDistance = distance
    glView?.requestRender()
  }

  LaunchedEffect(pointCloud) {
    pointCloud?.let { pc ->
      renderer.updatePointCloud(pc.vertexData, pc.pointCount)
      // Slightly larger points when sparse so the bust still reads as solid
      val densityBoost = when {
        pc.pointCount < 400 -> 1.35f
        pc.pointCount < 900 -> 1.15f
        else -> 1.0f
      }
      renderer.pointSize = (if (surfaceFill) 20f else 14f) * densityBoost
      glView?.requestRender()
    }
  }

  // Orbit inertia: keep spinning briefly after a fling-like drag
  LaunchedEffect(Unit) {
    while (isActive) {
      delay(16L)
      val vy = yawVelocity
      val vp = pitchVelocity
      if (kotlin.math.abs(vy) > 0.02f || kotlin.math.abs(vp) > 0.02f) {
        yaw = (yaw + vy) % 360f
        pitch = (pitch + vp).coerceIn(-85f, 85f)
        yawVelocity = vy * 0.92f
        pitchVelocity = vp * 0.92f
        if (kotlin.math.abs(yawVelocity) < 0.02f) yawVelocity = 0f
        if (kotlin.math.abs(pitchVelocity) < 0.02f) pitchVelocity = 0f
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
          val dyaw = pan.x * 0.42f
          val dpitch = -pan.y * 0.42f
          yaw = (yaw + dyaw) % 360f
          pitch = (pitch + dpitch).coerceIn(-85f, 85f)
          yawVelocity = dyaw * 0.55f
          pitchVelocity = dpitch * 0.55f
          distance = (distance / zoom).coerceIn(0.55f, 4.0f)
        }
      }
  ) {
    AndroidView(
      factory = { ctx ->
        GLSurfaceView(ctx).apply {
          setEGLContextClientVersion(2)
          setEGLConfigChooser(8, 8, 8, 8, 16, 0)
          holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
          setRenderer(renderer)
          // CONTINUOUSLY so inertia + live cloud frames keep painting smoothly
          renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
          glView = this
        }
      },
      modifier = Modifier.fillMaxSize()
    )
  }
}
