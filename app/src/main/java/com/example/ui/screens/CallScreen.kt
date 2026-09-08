package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterTiltShift
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.model.VolumetricMeshMode
import com.example.ui.components.SpatialAudioVisualizer
import com.example.ui.components.VolumetricVisualizer
import com.example.util.DeviceSensorManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.local.CallHistoryRepository
import com.example.model.DataRepository
import com.example.ui.components.CallControlsOverlay
import com.example.ui.components.CameraPreview
import com.example.ui.components.ConnectionStrengthIcon
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import com.example.viewmodel.CallViewModel
import com.example.viewmodel.ConnectionStatus

/**
 * Modern light-themed video and volumetric 3D call interface with dynamic
 * connection strength latency observation, CameraX feed, and binaural audio spatial status.
 */
@Composable
fun CallScreen(
  callerName: String = "Sarah Chen",
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CallViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val elapsedDurationFormatted by viewModel.elapsedDurationFormatted.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val callHistoryRepository = remember { CallHistoryRepository.getInstance(context) }
  val sensorManager = remember { DeviceSensorManager(context) }

  var showGestureHint by remember { mutableStateOf(true) }

  // 3D rotation angles
  var rotX by remember { mutableFloatStateOf(0f) }
  var rotY by remember { mutableFloatStateOf(0f) }

  val sensorPitch by sensorManager.pitch.collectAsStateWithLifecycle()
  val sensorRoll by sensorManager.roll.collectAsStateWithLifecycle()

  DisposableEffect(uiState.isGyroTrackingEnabled, uiState.is3DMode) {
    if (uiState.isGyroTrackingEnabled && uiState.is3DMode) {
      sensorManager.startTracking()
    }
    onDispose {
      sensorManager.stopTracking()
    }
  }

  LaunchedEffect(sensorPitch, sensorRoll, uiState.isGyroTrackingEnabled, uiState.is3DMode) {
    if (uiState.isGyroTrackingEnabled && uiState.is3DMode) {
      rotX = (rotX * 0.35f + sensorPitch * 0.65f).coerceIn(-12f, 12f)
      rotY = (rotY * 0.35f + sensorRoll * 0.65f).coerceIn(-18f, 18f)
    }
  }

  val animRotX by animateFloatAsState(
    targetValue = if (uiState.is3DMode) rotX else 0f,
    animationSpec = spring(stiffness = 600f),
    label = "rotX"
  )
  val animRotY by animateFloatAsState(
    targetValue = if (uiState.is3DMode) rotY else 0f,
    animationSpec = spring(stiffness = 600f),
    label = "rotY"
  )

  LaunchedEffect(animRotX, animRotY) {
    viewModel.updateOrientation(animRotX, animRotY)
  }

  LaunchedEffect(callerName) {
    viewModel.initializeCall(callerName)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFF3F5FC))
      .pointerInput(uiState.is3DMode) {
        if (uiState.is3DMode) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            showGestureHint = false
            val newY = (rotY + dragAmount.x * 0.12f).coerceIn(-18f, 18f)
            val newX = (rotX - dragAmount.y * 0.08f).coerceIn(-10f, 10f)
            rotY = newY
            rotX = newX
          }
        }
      }
      .testTag("call_screen")
  ) {
    // Main 3D / Video Stream Surface framed in an airy light card
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(24.dp))
        .border(2.dp, Color.White, RoundedCornerShape(24.dp))
        .graphicsLayer {
          rotationX = animRotX
          rotationY = animRotY
          cameraDistance = 16f * density
          scaleX = if (uiState.is3DMode) 1.04f else 1.0f
          scaleY = if (uiState.is3DMode) 1.04f else 1.0f
        }
    ) {
      AsyncImage(
        model = DataRepository.SARAH_VIDEO_FEED_URL,
        contentDescription = "Caller Video Feed",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // 3D Volumetric Surface (Holographic Mesh, Point Cloud, Depth Contours)
      if (uiState.is3DMode) {
        VolumetricVisualizer(
          rotationX = animRotX,
          rotationY = animRotY,
          meshMode = uiState.meshMode,
          depthIntensity = uiState.depthIntensity,
          audioLevel = uiState.audioLevel,
          modifier = Modifier.fillMaxSize()
        )
      }

      // Light ambient scrim for clean contrast
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.White.copy(alpha = 0.55f),
                Color.Transparent,
                Color.White.copy(alpha = 0.65f)
              )
            )
          )
      )

      // 3D Spatial horizon ring
      if (uiState.is3DMode) {
        Box(
          modifier = Modifier
            .size(310.dp)
            .align(Alignment.Center)
            .border(
              width = 2.dp,
              brush = Brush.radialGradient(
                colors = listOf(
                  LivePrimaryContainer.copy(alpha = 0.6f),
                  Color.Transparent
                )
              ),
              shape = CircleShape
            )
        )
      }
    }

    // Top Header HUD Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 20.dp)
        .align(Alignment.TopCenter),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      // Left HUD: Caller Identity & Duration
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .shadow(8.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.08f))
          .clip(RoundedCornerShape(99.dp))
          .background(Color.White.copy(alpha = 0.95f))
          .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        LiveVolumeAvatar(
          avatarUrl = DataRepository.SARAH_AVATAR_URL,
          initials = "SC",
          size = 36.dp,
          showOnlineBadge = false
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
          Text(
            text = callerName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                  when (uiState.connectionStatus) {
                    ConnectionStatus.CONNECTED -> LiveSuccess
                    ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
                    ConnectionStatus.RECONNECTING -> Color(0xFFF59E0B)
                    ConnectionStatus.DISCONNECTED -> Color(0xFFEF4444)
                  }
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "$elapsedDurationFormatted • ${uiState.connectionStatus.label}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp
            )
          }
        }
      }

      // Right HUD: Connection Strength Icon + 3D Badge + CameraX PiP
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Connection strength icon observing ViewModel connectivity state and latency
          ConnectionStrengthIcon(
            latencyMs = uiState.latencyMs,
            connectionStatus = uiState.connectionStatus,
            connectionQuality = uiState.connectionQuality,
            onClick = { viewModel.cycleConnectionQuality() }
          )

          // 3D / 2D Indicator Pill
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .shadow(6.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.08f))
              .clip(RoundedCornerShape(99.dp))
              .background(Color.White.copy(alpha = 0.95f))
              .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ViewInAr,
              contentDescription = "3D Mode",
              tint = LivePrimaryContainer,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (uiState.is3DMode) "3D" else "2D",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = LivePrimaryContainer
            )
          }
        }

        // Live CameraX Self PiP Window
        Box(
          modifier = Modifier
            .size(width = 84.dp, height = 116.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
            .clickable { viewModel.switchCamera() }
            .testTag("self_pip_window")
        ) {
          CameraPreview(
            isFrontCamera = uiState.isFrontCamera,
            isCameraOn = uiState.isCameraOn,
            modifier = Modifier.fillMaxSize()
          )

          // Flip camera indicator on self preview
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(4.dp)
              .size(24.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.FlipCameraIos,
              contentDescription = "Switch Camera",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }
    }

    // Persistent UI Element: Call Duration Timer (Coroutine-based timer in ViewModel)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 80.dp)
        .shadow(8.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.08f))
        .clip(RoundedCornerShape(99.dp))
        .background(Color.White.copy(alpha = 0.95f))
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
        .padding(horizontal = 14.dp, vertical = 6.dp)
        .testTag("persistent_call_duration_timer")
    ) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(LiveSuccess)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Icon(
        imageVector = Icons.Default.Timer,
        contentDescription = "Elapsed Call Duration",
        tint = LivePrimaryContainer,
        modifier = Modifier.size(15.dp)
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = elapsedDurationFormatted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("elapsed_call_duration_text")
      )
    }

    // Interactive Drag Gesture Helper Banner
    AnimatedVisibility(
      visible = showGestureHint && uiState.is3DMode,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 32.dp)
    ) {
      Row(
        modifier = Modifier
          .shadow(8.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.10f))
          .clip(RoundedCornerShape(99.dp))
          .background(Color.White.copy(alpha = 0.96f))
          .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.FilterTiltShift,
          contentDescription = null,
          tint = LivePrimaryContainer,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Drag to rotate 3D view",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
          onClick = { showGestureHint = false },
          modifier = Modifier.size(20.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    // Bottom Controls & Overlay Region
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Dynamic Binaural Spatial Audio HUD
      SpatialAudioVisualizer(
        azimuthDegrees = uiState.azimuth,
        audioLevel = uiState.audioLevel,
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      Spacer(modifier = Modifier.height(6.dp))

      // 3D Optics & Volumetric Options Card
      if (uiState.is3DMode) {
        AnimatedVisibility(visible = uiState.showOpticsSheet) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(Color.White.copy(alpha = 0.96f))
              .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(20.dp))
              .padding(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "3D Volumetric Engine",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clip(RoundedCornerShape(99.dp))
                  .background(if (uiState.isGyroTrackingEnabled) LivePrimaryContainer.copy(alpha = 0.12f) else Color(0xFFF1F5F9))
                  .clickable { viewModel.toggleGyroTracking() }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.ScreenRotation,
                  contentDescription = "Gyro",
                  tint = if (uiState.isGyroTrackingEnabled) LivePrimaryContainer else Color.Gray,
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (uiState.isGyroTrackingEnabled) "Gyro Active" else "Gyro Off",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (uiState.isGyroTrackingEnabled) LivePrimaryContainer else Color.Gray
                )
              }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Mesh Mode Selector
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              VolumetricMeshMode.entries.forEach { mode ->
                FilterChip(
                  selected = uiState.meshMode == mode,
                  onClick = { viewModel.setMeshMode(mode) },
                  label = { Text(mode.label, fontSize = 10.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LivePrimaryContainer,
                    selectedLabelColor = Color.White
                  )
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Depth Extrusion Intensity Slider
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Extrusion: ${String.format(java.util.Locale.US, "%.1fx", uiState.depthIntensity)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Slider(
                value = uiState.depthIntensity,
                onValueChange = { viewModel.setDepthIntensity(it) },
                valueRange = 0.5f..2.2f,
                modifier = Modifier.width(170.dp),
                colors = SliderDefaults.colors(thumbColor = LivePrimaryContainer, activeTrackColor = LivePrimaryContainer)
              )
            }
          }
        }
      }

      // Mode switch + 3D Optics HUD row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // 2D / 3D Mode Pill Switch
        Box(
          modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
            .padding(4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(if (!uiState.is3DMode) LivePrimaryContainer else Color.Transparent)
                .clickable {
                  if (uiState.is3DMode) viewModel.toggle3DMode()
                }
                .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
              Text(
                text = "2D",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (!uiState.is3DMode) FontWeight.Bold else FontWeight.Medium,
                color = if (!uiState.is3DMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(if (uiState.is3DMode) LivePrimaryContainer else Color.Transparent)
                .clickable {
                  if (!uiState.is3DMode) viewModel.toggle3DMode()
                }
                .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Layers,
                  contentDescription = null,
                  tint = if (uiState.is3DMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "3D Live",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (uiState.is3DMode) FontWeight.Bold else FontWeight.Medium,
                  color = if (uiState.is3DMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // 3D Optics Toggle Button
        if (uiState.is3DMode) {
          Box(
            modifier = Modifier
              .shadow(6.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.08f))
              .clip(RoundedCornerShape(99.dp))
              .background(if (uiState.showOpticsSheet) LivePrimaryContainer else Color.White.copy(alpha = 0.95f))
              .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
              .clickable { viewModel.toggleOpticsSheet() }
              .padding(horizontal = 14.dp, vertical = 8.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "3D Optics",
                tint = if (uiState.showOpticsSheet) Color.White else LivePrimaryContainer,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Optics",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (uiState.showOpticsSheet) Color.White else LivePrimaryContainer
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Light-themed User interface overlay containing buttons for 'End Call', 'Mute', and 'Camera Switch'
      CallControlsOverlay(
        isMuted = uiState.isMuted,
        isCameraOn = uiState.isCameraOn,
        onToggleMute = { viewModel.toggleMute() },
        onSwitchCamera = { viewModel.switchCamera() },
        onEndCall = {
          viewModel.endCall(callHistoryRepository)
          onEndCall()
        },
        onToggleCamera = { viewModel.toggleCamera() }
      )
    }
  }
}
