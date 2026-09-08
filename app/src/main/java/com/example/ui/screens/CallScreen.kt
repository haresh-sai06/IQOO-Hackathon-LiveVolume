package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.VolumetricMeshMode
import com.example.ui.components.CallControlsOverlay
import com.example.ui.components.CameraPreview
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.components.SpatialAudioVisualizer
import com.example.ui.components.VolumetricVisualizer
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import com.example.util.DeviceSensorManager
import com.example.util.HapticType
import com.example.util.HapticsManager
import com.example.viewmodel.CallViewModel

/**
 * Modern, beautifully spaced video and volumetric 3D call interface.
 * Fixed spacing issues with status bar insets, PiP placement, and floating controls dock.
 */
@Composable
fun CallScreen(
  callerName: String = "Live Contact",
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CallViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val elapsedDurationFormatted by viewModel.elapsedDurationFormatted.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val callHistoryRepository = remember { CallHistoryRepository.getInstance(context) }
  val sensorManager = remember { DeviceSensorManager(context) }

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
      .background(Color(0xFF0F172A))
      .pointerInput(uiState.is3DMode) {
        if (uiState.is3DMode) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            val newY = (rotY + dragAmount.x * 0.12f).coerceIn(-18f, 18f)
            val newX = (rotX - dragAmount.y * 0.08f).coerceIn(-10f, 10f)
            rotY = newY
            rotX = newX
          }
        }
      }
      .testTag("call_screen")
  ) {
    // 1. Full-bleed 3D / Video Stream Surface
    Box(
      modifier = Modifier
        .fillMaxSize()
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

      // 3D Volumetric Visualizer Mesh
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

      // Smooth ambient scrim
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.35f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.45f)
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
                  LivePrimaryContainer.copy(alpha = 0.45f),
                  Color.Transparent
                )
              ),
              shape = CircleShape
            )
        )
      }
    }

    // 2. Top Header HUD Bar (Proper statusBarsPadding, no clipping or overlap)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .align(Alignment.TopCenter),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      // Left HUD: Caller Identity & Duration Pill
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .shadow(10.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.12f))
          .clip(RoundedCornerShape(99.dp))
          .background(Color.White.copy(alpha = 0.95f))
          .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .testTag("persistent_call_duration_timer")
      ) {
        LiveVolumeAvatar(
          avatarUrl = DataRepository.SARAH_AVATAR_URL,
          initials = "SC",
          size = 32.dp,
          showOnlineBadge = false
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
          Text(
            text = callerName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(LiveSuccess)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = elapsedDurationFormatted,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
              fontSize = 11.sp,
              modifier = Modifier.testTag("elapsed_call_duration_text")
            )
          }
        }
      }

      // Right HUD: Self Camera PiP (Picture-in-Picture) Window
      Box(
        modifier = Modifier
          .size(width = 80.dp, height = 112.dp)
          .shadow(14.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.18f))
          .clip(RoundedCornerShape(18.dp))
          .background(Color.White)
          .border(2.dp, Color.White, RoundedCornerShape(18.dp))
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
            .background(Color.White.copy(alpha = 0.92f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.FlipCameraIos,
            contentDescription = "Switch Camera",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(13.dp)
          )
        }
      }
    }

    // 3. Bottom Controls & Overlay Region (navigationBarsPadding, beautifully spaced)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Dynamic Binaural Spatial Audio HUD (clean & non-intrusive)
      if (uiState.is3DMode) {
        SpatialAudioVisualizer(
          azimuthDegrees = uiState.azimuth,
          audioLevel = uiState.audioLevel,
          modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
      }

      // 3D Optics Settings Sheet (expands when toggled)
      if (uiState.is3DMode && uiState.showOpticsSheet) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(20.dp))
            .padding(14.dp)
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

          Spacer(modifier = Modifier.height(8.dp))

          // Mesh Mode Selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
        Spacer(modifier = Modifier.height(8.dp))
      }

      // Mode Switch (2D / 3D Live) + Optics Row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // 2D / 3D Mode Capsule Switch
        Box(
          modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.12f))
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
                  if (uiState.is3DMode) {
                    HapticsManager.trigger(context, HapticType.MODE_SWITCH)
                    viewModel.toggle3DMode()
                  }
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
                  if (!uiState.is3DMode) {
                    HapticsManager.trigger(context, HapticType.MODE_SWITCH)
                    viewModel.toggle3DMode()
                  }
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
              .shadow(8.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.12f))
              .clip(RoundedCornerShape(99.dp))
              .background(if (uiState.showOpticsSheet) LivePrimaryContainer else Color.White.copy(alpha = 0.95f))
              .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
              .clickable {
                HapticsManager.trigger(context, HapticType.OPTICS_TOGGLE)
                viewModel.toggleOpticsSheet()
              }
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

      Spacer(modifier = Modifier.height(14.dp))

      // 5-Button Elevated Call Controls Dock
      CallControlsOverlay(
        isMuted = uiState.isMuted,
        isCameraOn = uiState.isCameraOn,
        isSpeakerOn = uiState.isSpeakerOn,
        onToggleMute = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          viewModel.toggleMute()
        },
        onToggleCamera = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          viewModel.toggleCamera()
        },
        onToggleSpeaker = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          viewModel.toggleSpeaker()
        },
        onSwitchCamera = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          viewModel.switchCamera()
        },
        onEndCall = {
          HapticsManager.trigger(context, HapticType.CALL_END)
          viewModel.endCall(callHistoryRepository)
          onEndCall()
        }
      )
    }
  }
}
