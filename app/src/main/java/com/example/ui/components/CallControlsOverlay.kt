package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LiveError
import com.example.ui.theme.LivePrimaryContainer

/**
 * Light-themed user interface overlay for video screen containing buttons for 'End Call', 'Mute', and 'Camera Switch'.
 */
@Composable
fun CallControlsOverlay(
  isMuted: Boolean,
  isCameraOn: Boolean,
  onToggleMute: () -> Unit,
  onSwitchCamera: () -> Unit,
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
  onToggleCamera: (() -> Unit)? = null
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 20.dp)
      .testTag("call_controls_overlay"),
    contentAlignment = Alignment.Center
  ) {
    // Light glassmorphic elevated control dock
    Box(
      modifier = Modifier
        .shadow(12.dp, RoundedCornerShape(32.dp), spotColor = Color.Black.copy(alpha = 0.12f))
        .clip(RoundedCornerShape(32.dp))
        .background(Color.White.copy(alpha = 0.96f))
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(32.dp))
        .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // 1. Mute Button
        OverlayControlButton(
          icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
          label = if (isMuted) "Unmute" else "Mute",
          isActive = isMuted,
          activeContainerColor = Color(0xFFFFECEB),
          activeContentColor = LiveError,
          defaultContainerColor = Color(0xFFF2F4FD),
          defaultContentColor = Color(0xFF131B2E),
          testTag = "mute_button",
          contentDescription = "Mute",
          onClick = onToggleMute
        )

        // Camera On/Off button (optional companion)
        if (onToggleCamera != null) {
          OverlayControlButton(
            icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
            label = if (isCameraOn) "Video On" else "Video Off",
            isActive = !isCameraOn,
            activeContainerColor = Color(0xFFFFECEB),
            activeContentColor = LiveError,
            defaultContainerColor = Color(0xFFF2F4FD),
            defaultContentColor = Color(0xFF131B2E),
            testTag = "camera_toggle_button",
            contentDescription = "Toggle Camera",
            onClick = onToggleCamera
          )
        }

        // 2. Camera Switch Button
        OverlayControlButton(
          icon = Icons.Default.Cameraswitch,
          label = "Camera Switch",
          isActive = false,
          activeContainerColor = Color(0xFFF2F4FD),
          activeContentColor = LivePrimaryContainer,
          defaultContainerColor = Color(0xFFF2F4FD),
          defaultContentColor = LivePrimaryContainer,
          testTag = "camera_switch_button",
          contentDescription = "Camera Switch",
          onClick = onSwitchCamera
        )

        // 3. End Call Button
        OverlayControlButton(
          icon = Icons.Default.CallEnd,
          label = "End Call",
          isActive = true,
          activeContainerColor = LiveError,
          activeContentColor = Color.White,
          defaultContainerColor = LiveError,
          defaultContentColor = Color.White,
          testTag = "end_call_button",
          contentDescription = "End Call",
          onClick = onEndCall
        )
      }
    }
  }
}

@Composable
private fun OverlayControlButton(
  icon: ImageVector,
  label: String,
  isActive: Boolean,
  activeContainerColor: Color,
  activeContentColor: Color,
  defaultContainerColor: Color,
  defaultContentColor: Color,
  testTag: String,
  contentDescription: String,
  onClick: () -> Unit
) {
  val containerColor = if (isActive) activeContainerColor else defaultContainerColor
  val contentColor = if (isActive) activeContentColor else defaultContentColor

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(containerColor)
        .border(1.dp, if (isActive && activeContainerColor == LiveError) Color.Transparent else Color(0xFFE2E7FF), CircleShape)
        .testTag(testTag),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = contentColor,
        modifier = Modifier.size(26.dp)
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 11.sp
    )
  }
}
