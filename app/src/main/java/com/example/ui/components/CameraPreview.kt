package com.example.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * CameraX live camera preview component for video calling.
 *
 * @param isFrontCamera Whether the front-facing camera is active, or rear camera.
 * @param isCameraOn Whether camera capture is enabled.
 * @param modifier Compose Modifier.
 */
@Composable
fun CameraPreview(
  isFrontCamera: Boolean,
  isCameraOn: Boolean,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }
  var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
  var isBoundSuccessfully by remember { mutableStateOf(false) }

  // Re-bind camera whenever camera direction or power state changes
  LaunchedEffect(isFrontCamera, isCameraOn, previewViewInstance) {
    val previewView = previewViewInstance ?: return@LaunchedEffect

    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
      {
        try {
          val cameraProvider = cameraProviderFuture.get()
          cameraProviderInstance = cameraProvider

          cameraProvider.unbindAll()

          if (isCameraOn) {
            val cameraSelector = if (isFrontCamera) {
              CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
              CameraSelector.DEFAULT_BACK_CAMERA
            }

            val preview = Preview.Builder()
              .build()
              .also {
                it.surfaceProvider = previewView.surfaceProvider
              }

            // Check if selected camera is available before binding
            if (cameraProvider.hasCamera(cameraSelector)) {
              cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
              )
              isBoundSuccessfully = true
            } else {
              // Fallback to back camera if front camera is not available on test device
              val fallbackSelector = CameraSelector.DEFAULT_BACK_CAMERA
              if (cameraProvider.hasCamera(fallbackSelector)) {
                cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  fallbackSelector,
                  preview
                )
                isBoundSuccessfully = true
              }
            }
          } else {
            isBoundSuccessfully = false
          }
        } catch (e: Exception) {
          Log.e("CameraPreview", "Failed to bind CameraX lifecycle", e)
          isBoundSuccessfully = false
        }
      },
      ContextCompat.getMainExecutor(context)
    )
  }

  // Ensure camera resources unbind on dispose
  DisposableEffect(lifecycleOwner) {
    onDispose {
      try {
        cameraProviderInstance?.unbindAll()
      } catch (e: Exception) {
        Log.e("CameraPreview", "Error unbinding camera provider", e)
      }
    }
  }

  Box(
    modifier = modifier
      .testTag("camera_preview_container")
      .background(Color(0xFF0F121C)),
    contentAlignment = Alignment.Center
  ) {
    if (isCameraOn) {
      AndroidView(
        factory = { ctx ->
          PreviewView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
          }.also {
            previewViewInstance = it
          }
        },
        modifier = Modifier
          .fillMaxSize()
          .testTag("camera_preview_view")
      )
    }

    // Camera turned off placeholder / privacy shield
    AnimatedVisibility(
      visible = !isCameraOn,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFF141824)),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(Color(0xFF282E42)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.VideocamOff,
              contentDescription = "Camera Off",
              tint = Color.White.copy(alpha = 0.7f),
              modifier = Modifier.size(28.dp)
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Camera is Paused",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f)
          )

          Text(
            text = "Video privacy enabled",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
          )
        }
      }
    }
  }
}
