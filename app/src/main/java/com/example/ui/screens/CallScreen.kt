package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LiveSuccess
import com.example.util.HapticType
import com.example.util.HapticsManager
import com.example.viewmodel.CallViewModel

/**
 * Modern, beautifully spaced clean video call interface.
 * Fixed spacing with status bar insets, clean PiP placement, and floating controls dock.
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

  LaunchedEffect(callerName) {
    viewModel.initializeCall(callerName)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF0F172A))
      .testTag("call_screen")
  ) {
    // 1. Full-bleed Video Stream Surface
    Box(modifier = Modifier.fillMaxSize()) {
      AsyncImage(
        model = DataRepository.SARAH_VIDEO_FEED_URL,
        contentDescription = "Caller Video Feed",
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
      )

      // Smooth ambient scrim: keeps top and bottom legible and masks any baked-in photo elements
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.50f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.25f),
                Color.Black.copy(alpha = 0.85f)
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
          .size(width = 84.dp, height = 118.dp)
          .shadow(14.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.18f))
          .clip(RoundedCornerShape(18.dp))
          .background(Color.Black)
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
          viewModel.endCall(callHistoryRepository)
          onEndCall()
        }
      )
    }
  }
}
