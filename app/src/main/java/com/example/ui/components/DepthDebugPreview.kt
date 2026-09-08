package com.example.ui.components

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ml.DepthEstimator
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import java.util.concurrent.Executors

/**
 * Real-time on-device depth map debug preview screen.
 * Displays live CameraX feed side-by-side with the neural depth map and latency telemetry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepthDebugPreviewScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val depthEstimator = remember { DepthEstimator(context) }
  val latestDepth by depthEstimator.latestDepth.collectAsStateWithLifecycle()

  var isFrontCamera by remember { mutableStateOf(true) }
  var viewMode by remember { mutableStateOf(PreviewViewMode.POINT_CLOUD_3D) }
  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

  DisposableEffect(Unit) {
    onDispose {
      depthEstimator.close()
      cameraExecutor.shutdown()
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "Volumetric 3D Pipeline",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
            Icon(Icons.Default.FlipCameraIos, contentDescription = "Switch Camera")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF0F172A),
          titleContentColor = Color.White,
          navigationIconContentColor = Color.White,
          actionIconContentColor = Color.White
        )
      )
    },
    modifier = modifier.fillMaxSize()
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F172A))
        .padding(innerPadding)
        .padding(horizontal = 14.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Telemetry pill HUD
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(8.dp, RoundedCornerShape(16.dp))
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White.copy(alpha = 0.08f))
          .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = null,
              tint = LivePrimaryContainer,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Depth: ${latestDepth?.depthLatencyMs ?: 0}ms | Seg: ${latestDepth?.segLatencyMs ?: 0}ms",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }
          Text(
            text = "Total: ${latestDepth?.totalLatencyMs ?: 0}ms | Points: ${latestDepth?.pointCloud?.pointCount ?: 0}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(LiveSuccess.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = latestDepth?.delegateUsed ?: depthEstimator.delegateUsed,
            color = LiveSuccess,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Upper Half: Live CameraX Feed
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(Color.Black)
          .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
      ) {
        AndroidView(
          factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
              val cameraProvider = cameraProviderFuture.get()
              val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
              }

              val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                  it.setAnalyzer(cameraExecutor, depthEstimator.createAnalyzer())
                }

              val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
              } else {
                CameraSelector.DEFAULT_BACK_CAMERA
              }

              try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  cameraSelector,
                  preview,
                  imageAnalysis
                )
              } catch (e: Exception) {
                e.printStackTrace()
              }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
          },
          modifier = Modifier.fillMaxSize()
        )

        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text("Camera RGB Feed", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // 3-Way Mode Toggle Pill: 3D Orbit vs Silhouette vs Raw Depth
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(99.dp))
          .background(Color.White.copy(alpha = 0.1f))
          .padding(3.dp),
        horizontalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (viewMode == PreviewViewMode.POINT_CLOUD_3D) LivePrimaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { viewMode = PreviewViewMode.POINT_CLOUD_3D },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "3D Orbit",
            color = if (viewMode == PreviewViewMode.POINT_CLOUD_3D) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (viewMode == PreviewViewMode.SEGMENTED_2D) LivePrimaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { viewMode = PreviewViewMode.SEGMENTED_2D },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Silhouette",
            color = if (viewMode == PreviewViewMode.SEGMENTED_2D) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (viewMode == PreviewViewMode.RAW_DEPTH_2D) LivePrimaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { viewMode = PreviewViewMode.RAW_DEPTH_2D },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Raw Depth",
            color = if (viewMode == PreviewViewMode.RAW_DEPTH_2D) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Lower Half: 3D Point Cloud Orbit OR 2D Depth Maps
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFF020617))
          .border(1.dp, LivePrimaryContainer.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
      ) {
        when (viewMode) {
          PreviewViewMode.POINT_CLOUD_3D -> {
            VolumetricVisualizer(
              pointCloud = latestDepth?.pointCloud,
              modifier = Modifier.fillMaxSize()
            )

            // Orbit instruction badge
            Box(
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(99.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
              Text(
                text = "Drag to orbit 3D • Pinch to zoom",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          PreviewViewMode.SEGMENTED_2D -> {
            val bmp = latestDepth?.maskedDepthBitmap ?: latestDepth?.depthBitmap
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Masked Depth Map",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                  modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(LivePrimaryContainer)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Computing silhouette depth...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
              }
            }
          }

          PreviewViewMode.RAW_DEPTH_2D -> {
            val bmp = latestDepth?.rawDepthBitmap ?: latestDepth?.depthBitmap
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Raw Depth Map",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                  modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(LivePrimaryContainer)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Computing full scene depth...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
              }
            }
          }
        }

        // Top-left label badge
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          val label = when (viewMode) {
            PreviewViewMode.POINT_CLOUD_3D -> "3D Volumetric Point Cloud (Live)"
            PreviewViewMode.SEGMENTED_2D -> "Silhouette Depth (Background Zeroed)"
            PreviewViewMode.RAW_DEPTH_2D -> "Full Scene Depth (Raw Disparity)"
          }
          Text(text = label, color = LivePrimaryContainer, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

enum class PreviewViewMode {
  POINT_CLOUD_3D,
  SEGMENTED_2D,
  RAW_DEPTH_2D
}
