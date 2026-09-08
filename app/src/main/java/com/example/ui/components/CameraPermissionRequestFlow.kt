package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import com.example.util.AudioPermissionHelper

/**
 * Runtime permission request flow for device camera and audio to support video calling.
 */
@Composable
fun CameraPermissionRequestFlow(
  onPermissionsResult: (cameraGranted: Boolean, audioGranted: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  contentWhenGranted: @Composable () -> Unit
) {
  val context = LocalContext.current

  fun checkCameraPermission(ctx: Context) = ContextCompat.checkSelfPermission(
    ctx,
    Manifest.permission.CAMERA
  ) == PackageManager.PERMISSION_GRANTED

  var isCameraGranted by remember {
    mutableStateOf(checkCameraPermission(context))
  }
  var isAudioGranted by remember {
    mutableStateOf(AudioPermissionHelper.isAudioCaptureAuthorized(context))
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val cam = permissions[Manifest.permission.CAMERA] ?: isCameraGranted
    val aud = permissions[Manifest.permission.RECORD_AUDIO] ?: isAudioGranted
    isCameraGranted = cam
    isAudioGranted = aud
    onPermissionsResult(cam, aud)
  }

  LaunchedEffect(Unit) {
    val cam = checkCameraPermission(context)
    val aud = AudioPermissionHelper.isAudioCaptureAuthorized(context)
    isCameraGranted = cam
    isAudioGranted = aud
    onPermissionsResult(cam, aud)
  }

  if (isCameraGranted) {
    contentWhenGranted()
  } else {
    // Permission request prompt card
    Box(
      modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(20.dp))
        .padding(20.dp)
        .testTag("camera_permission_request_card")
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Color(0xFFF2F3FF)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = "Camera Permission",
            tint = LivePrimaryContainer,
            modifier = Modifier.size(28.dp)
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "Camera & Audio Access Needed",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "To enable real-time 3D holographic video calling and binaural spatial audio, LiveVolume requires camera and microphone authorization.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy feature reassurance row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF7F8FE))
            .padding(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = LivePrimaryContainer,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Processed 100% on-device. Zero video frames saved to cloud.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
          onClick = {
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
              )
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("request_camera_permission_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CameraAlt,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Grant Camera & Audio Access",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}
