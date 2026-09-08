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

/**
 * Rebuilt Volumetric Visualizer (Phase 5).
 * Renders real-time 3D point cloud reconstructed from real masked depth and RGB video frames.
 * Supports smooth drag-to-orbit and pinch-to-zoom interactive touch controls.
 *
 * @param pointCloud Live 3D point cloud data generated from neural depth & silhouette segmentation.
 * @param modifier Compose layout modifier.
 */
@Composable
fun VolumetricVisualizer(
  pointCloud: PointCloud?,
  modifier: Modifier = Modifier,
  initialYaw: Float = 0f,
  initialPitch: Float = 0f,
  initialDistance: Float = 1.6f
) {
  var yaw by remember { mutableFloatStateOf(initialYaw) }
  var pitch by remember { mutableFloatStateOf(initialPitch) }
  var distance by remember { mutableFloatStateOf(initialDistance) }

  val renderer = remember { PointCloudRenderer() }
  var glView by remember { mutableStateOf<GLSurfaceView?>(null) }

  // Update renderer orbit angles whenever touch gesture state changes
  LaunchedEffect(yaw, pitch, distance) {
    renderer.rotationYaw = yaw
    renderer.rotationPitch = pitch
    renderer.cameraDistance = distance
    glView?.requestRender()
  }

  // Update renderer geometry whenever new point cloud frame is received
  LaunchedEffect(pointCloud) {
    pointCloud?.let { pc ->
      renderer.updatePointCloud(pc.vertexData, pc.pointCount)
      glView?.requestRender()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
          // Drag-to-orbit: pan.x controls yaw, pan.y controls pitch
          yaw = (yaw + pan.x * 0.4f) % 360f
          pitch = (pitch - pan.y * 0.4f).coerceIn(-85f, 85f)
          // Pinch-to-zoom: adjusts camera orbit radius
          distance = (distance / zoom).coerceIn(0.6f, 3.8f)
        }
      }
  ) {
    AndroidView(
      factory = { ctx ->
        GLSurfaceView(ctx).apply {
          setEGLContextClientVersion(2)
          setRenderer(renderer)
          renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
          glView = this
        }
      },
      modifier = Modifier.fillMaxSize()
    )
  }
}
