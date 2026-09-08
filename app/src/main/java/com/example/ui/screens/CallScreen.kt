package com.example.ui.screens

import android.view.SurfaceView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.CallHistoryRepository
import com.example.data.repository.CallSignalingRepository
import com.example.model.CallSessionState
import com.example.model.DataRepository
import com.example.ui.components.CallControlsOverlay
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import com.example.util.HapticType
import com.example.util.HapticsManager
import com.example.util.InAppNotificationManager
import com.example.util.InAppNotificationType
import com.example.viewmodel.CallViewModel

/**
 * Modern, beautifully spaced clean video call interface powered by Agora Video RTC.
 * Provides real-time peer video rendering, self PiP, and active call controls.
 */
@Composable
fun CallScreen(
  callerName: String = "Live Contact",
  channelName: String? = null,
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CallViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val elapsedDurationFormatted by viewModel.elapsedDurationFormatted.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val callHistoryRepository = remember { CallHistoryRepository.getInstance(context) }
  val signalingRepo = remember { CallSignalingRepository.getInstance(context) }
  val activeSession by signalingRepo.activeSession.collectAsStateWithLifecycle()

  var localSurfaceView by remember { androidx.compose.runtime.mutableStateOf<SurfaceView?>(null) }
  val depthEstimator = remember { com.example.ml.DepthEstimator(context) }

  androidx.compose.runtime.DisposableEffect(Unit) {
    onDispose {
      depthEstimator.close()
    }
  }

  // Local depth capture and 3D Point Cloud streaming to remote peer (Phase 6)
  LaunchedEffect(localSurfaceView, uiState.isCameraOn) {
    val surface = localSurfaceView ?: return@LaunchedEffect
    if (!uiState.isCameraOn) return@LaunchedEffect

    val bmp = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
    val handler = android.os.Handler(android.os.Looper.getMainLooper())

    while (true) {
      kotlinx.coroutines.delay(200) // 5 FPS capture & streaming
      try {
        if (surface.holder.surface.isValid) {
          android.view.PixelCopy.request(surface, bmp, { copyResult ->
            if (copyResult == android.view.PixelCopy.SUCCESS) {
              val result = depthEstimator.estimateDepth(bmp)
              result?.pointCloud?.let { pc ->
                viewModel.sendLocalPointCloud(pc)
              }
            }
          }, handler)
        }
      } catch (e: Exception) {
        // safety
      }
    }
  }

  LaunchedEffect(callerName, channelName) {
    viewModel.initializeCall(callerName, channelName)
  }

  // React to remote user declining or ending the call
  LaunchedEffect(activeSession?.state) {
    if (activeSession?.state == CallSessionState.REJECTED) {
      InAppNotificationManager.postNotification(
        title = "Call Declined",
        message = "$callerName declined the call.",
        type = InAppNotificationType.GENERAL,
        context = context
      )
      onEndCall()
    } else if (activeSession?.state == CallSessionState.ENDED && activeSession?.callId?.isNotBlank() == true) {
      onEndCall()
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF0F172A))
      .testTag("call_screen")
  ) {
    // 1. Full-bleed Video Stream Surface (3D Hologram, 2D Agora Peer, or Waiting State)
    Box(modifier = Modifier.fillMaxSize()) {
      if (uiState.is3DMode) {
        // 3D Volumetric Mode: Live rotatable point cloud reconstruction of the remote caller!
        Box(modifier = Modifier.fillMaxSize()) {
          com.example.ui.components.VolumetricVisualizer(
            pointCloud = uiState.remotePointCloud,
            modifier = Modifier.fillMaxSize()
          )

          // 3D Volumetric Status Badge
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 120.dp)
              .clip(RoundedCornerShape(99.dp))
              .background(Color.Black.copy(alpha = 0.75f))
              .border(1.dp, LivePrimaryContainer.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
              .padding(horizontal = 16.dp, vertical = 6.dp)
          ) {
            Text(
              text = if (uiState.remotePointCloud != null)
                "Live 3D Volumetric Stream • Drag to orbit"
              else
                "Waiting for $callerName's 3D volumetric stream...",
              color = Color.White.copy(alpha = 0.9f),
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      } else if (uiState.remoteUid != null && !uiState.isRemoteVideoMuted) {
        // Live remote video stream rendered directly from Agora RTC
        AndroidView(
          factory = { ctx ->
            SurfaceView(ctx).apply {
              viewModel.agoraManager.setupRemoteVideo(this, uiState.remoteUid!!)
            }
          },
          update = { surfaceView ->
            uiState.remoteUid?.let { uid ->
              viewModel.agoraManager.setupRemoteVideo(surfaceView, uid)
            }
          },
          modifier = Modifier.fillMaxSize()
        )
      } else {
        // Modern waiting / connecting placeholder when remote peer is not yet joined
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color(0xFF0F172A),
                  Color(0xFF1E293B),
                  Color(0xFF020617)
                )
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            LiveVolumeAvatar(
              avatarUrl = DataRepository.SARAH_AVATAR_URL,
              initials = callerName.take(2).uppercase(),
              size = 92.dp,
              showOnlineBadge = false
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
              text = callerName,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.alpha(pulseAlpha)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(if (uiState.remoteUid != null) LiveSuccess else LivePrimaryContainer)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (uiState.remoteUid == null) "Waiting for $callerName to connect..." else "Remote video muted",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
              )
            }

            if (uiState.channelName.isNotBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(99.dp))
                  .background(Color.White.copy(alpha = 0.08f))
                  .padding(horizontal = 14.dp, vertical = 6.dp)
              ) {
                Text(
                  text = "Room: ${uiState.channelName}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.6f),
                  fontSize = 11.sp
                )
              }
            }
          }
        }
      }

      // Smooth ambient scrim: ensures HUD and bottom dock remain crisp and legible
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.45f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.15f),
                Color.Black.copy(alpha = 0.80f)
              )
            )
          )
      )
    }

    // 2. Top Header HUD Bar (statusBarsPadding, no clipping or overlap)
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
          initials = callerName.take(2).uppercase(),
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
                .background(if (uiState.remoteUid != null) LiveSuccess else LivePrimaryContainer)
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

      // Right HUD: Self Camera PiP (Picture-in-Picture) Window with Agora local video
      Box(
        modifier = Modifier
          .size(width = 88.dp, height = 124.dp)
          .shadow(14.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.18f))
          .clip(RoundedCornerShape(18.dp))
          .background(Color.Black)
          .border(2.dp, Color.White, RoundedCornerShape(18.dp))
          .clickable { viewModel.switchCamera() }
          .testTag("self_pip_window")
      ) {
        if (uiState.isCameraOn) {
          AndroidView(
            factory = { ctx ->
              SurfaceView(ctx).apply {
                setZOrderMediaOverlay(true)
                viewModel.agoraManager.setupLocalVideo(this)
                localSurfaceView = this
              }
            },
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.VideocamOff,
              contentDescription = "Camera Off",
              tint = Color.White.copy(alpha = 0.7f),
              modifier = Modifier.size(26.dp)
            )
          }
        }

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

    // 2D Video vs 3D Hologram Volumetric Toggle (Phase 6)
    Row(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(top = 74.dp)
        .shadow(8.dp, RoundedCornerShape(99.dp))
        .clip(RoundedCornerShape(99.dp))
        .background(Color.Black.copy(alpha = 0.7f))
        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
        .padding(3.dp),
      horizontalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(99.dp))
          .background(if (!uiState.is3DMode) LivePrimaryContainer else Color.Transparent)
          .clickable { viewModel.set3DMode(false) }
          .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "2D Video",
          color = if (!uiState.is3DMode) Color.White else Color.White.copy(alpha = 0.65f),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(99.dp))
          .background(if (uiState.is3DMode) LivePrimaryContainer else Color.Transparent)
          .clickable { viewModel.set3DMode(true) }
          .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "3D Hologram",
          color = if (uiState.is3DMode) Color.White else Color.White.copy(alpha = 0.65f),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // 3. Bottom Controls (navigationBarsPadding, elevated floating dock)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
          signalingRepo.endCall()
          viewModel.endCall(callHistoryRepository)
          onEndCall()
        }
      )
    }
  }
}
