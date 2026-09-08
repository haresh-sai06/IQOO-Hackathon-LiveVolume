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
  var showSegmentedOnly by remember { mutableStateOf(true) }
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
            "Depth & Silhouette Pipeline",
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
        .padding(horizontal = 16.dp, vertical = 8.dp),
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
              fontSize = 12.sp
            )
          }
          Text(
            text = "Total: ${latestDepth?.totalLatencyMs ?: 0}ms",
            color = Color.White.copy(alpha = 0.7f),
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

      Spacer(modifier = Modifier.height(10.dp))

      // Upper Half: Live CameraX Feed
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .background(Color.Black)
          .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
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
          Text("Live Camera Feed", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Toggle Pill: Segmented vs Raw Depth
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(99.dp))
          .background(Color.White.copy(alpha = 0.1f))
          .padding(4.dp),
        horizontalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (showSegmentedOnly) LivePrimaryContainer else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable { showSegmentedOnly = true },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Segmented (Person)",
            color = if (showSegmentedOnly) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (!showSegmentedOnly) LivePrimaryContainer else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable { showSegmentedOnly = false },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Raw Depth (Full)",
            color = if (!showSegmentedOnly) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Lower Half: Depth Map Visualization (Masked or Raw)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .background(Color(0xFF020617))
          .border(1.dp, LivePrimaryContainer.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
      ) {
        val depthBmp = if (showSegmentedOnly) {
          latestDepth?.maskedDepthBitmap ?: latestDepth?.depthBitmap
        } else {
          latestDepth?.rawDepthBitmap ?: latestDepth?.depthBitmap
        }

        if (depthBmp != null) {
          Image(
            bitmap = depthBmp.asImageBitmap(),
            contentDescription = if (showSegmentedOnly) "Masked Depth Map" else "Raw Depth Map",
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
            Text(
              "Estimating depth & segmenting silhouette...",
              color = Color.White.copy(alpha = 0.7f),
              fontSize = 12.sp
            )
          }
        }

        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = if (showSegmentedOnly) "Silhouette Depth (Background Zeroed)" else "Full Scene Monocular Depth",
            color = LivePrimaryContainer,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }
  }
}
