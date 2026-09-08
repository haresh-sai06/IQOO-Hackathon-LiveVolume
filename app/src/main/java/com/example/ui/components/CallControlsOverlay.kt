package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LiveError
import com.example.ui.theme.LivePrimaryContainer

/**
 * Modern, beautifully spaced floating call control dock.
 * Contains evenly spaced touch targets for Mute, Camera, Speaker, Flip Camera, and End Call.
 */
@Composable
fun CallControlsOverlay(
  isMuted: Boolean,
  isCameraOn: Boolean,
  onToggleMute: () -> Unit,
  onSwitchCamera: () -> Unit,
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
  onToggleCamera: (() -> Unit)? = null,
  isSpeakerOn: Boolean = true,
  onToggleSpeaker: (() -> Unit)? = null
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .testTag("call_controls_overlay"),
    contentAlignment = Alignment.Center
  ) {
    // Floating glassmorphic dock
    Box(
      modifier = Modifier
        .shadow(16.dp, RoundedCornerShape(36.dp), spotColor = Color.Black.copy(alpha = 0.14f))
        .clip(RoundedCornerShape(36.dp))
        .background(Color.White.copy(alpha = 0.96f))
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(36.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // 1. Mute / Unmute
        OverlayIconButton(
          icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
          isActive = isMuted,
          activeBg = Color(0xFFFFECEB),
          activeTint = LiveError,
          defaultBg = Color(0xFFF2F4FD),
          defaultTint = Color(0xFF1E293B),
          testTag = "mute_button",
          contentDescription = if (isMuted) "Unmute" else "Mute",
          onClick = onToggleMute
        )

        // 2. Camera Video On / Off
        if (onToggleCamera != null) {
          OverlayIconButton(
            icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
            isActive = !isCameraOn,
            activeBg = Color(0xFFFFECEB),
            activeTint = LiveError,
            defaultBg = Color(0xFFF2F4FD),
            defaultTint = Color(0xFF1E293B),
            testTag = "camera_toggle_button",
            contentDescription = if (isCameraOn) "Turn Off Video" else "Turn On Video",
            onClick = onToggleCamera
          )
        }

        // 3. Speakerphone Toggle
        if (onToggleSpeaker != null) {
          OverlayIconButton(
            icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
            isActive = isSpeakerOn,
            activeBg = Color(0xFFEAEDFF),
            activeTint = LivePrimaryContainer,
            defaultBg = Color(0xFFF2F4FD),
            defaultTint = Color(0xFF64748B),
            testTag = "speaker_toggle_button",
            contentDescription = if (isSpeakerOn) "Speaker Active" else "Earpiece Active",
            onClick = onToggleSpeaker
          )
        }

        // 4. Flip Camera (Front / Rear)
        OverlayIconButton(
          icon = Icons.Default.Cameraswitch,
          isActive = false,
          activeBg = Color(0xFFF2F4FD),
          activeTint = LivePrimaryContainer,
          defaultBg = Color(0xFFF2F4FD),
          defaultTint = Color(0xFF1E293B),
          testTag = "camera_switch_button",
          contentDescription = "Switch Camera",
          onClick = onSwitchCamera
        )

        // 5. End Call Button (Primary Bold Red)
        Box(
          modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(LiveError)
            .clickable(onClick = onEndCall)
            .testTag("end_call_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CallEnd,
            contentDescription = "End Call",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun OverlayIconButton(
  icon: ImageVector,
  isActive: Boolean,
  activeBg: Color,
  activeTint: Color,
  defaultBg: Color,
  defaultTint: Color,
  testTag: String,
  contentDescription: String,
  onClick: () -> Unit
) {
  val bg = if (isActive) activeBg else defaultBg
  val tint = if (isActive) activeTint else defaultTint

  Box(
    modifier = Modifier
      .size(48.dp)
      .clip(CircleShape)
      .background(bg)
      .border(
        1.dp,
        if (isActive && activeBg != Color(0xFFFFECEB)) LivePrimaryContainer.copy(alpha = 0.3f) else Color(0xFFE2E7FF),
        CircleShape
      )
      .clickable(onClick = onClick)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(22.dp)
    )
  }
}
