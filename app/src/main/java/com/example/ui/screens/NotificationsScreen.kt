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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import com.example.util.HapticType
import com.example.util.HapticsManager
import com.example.util.InAppNotificationManager
import com.example.util.InAppNotificationType

@Composable
fun NotificationsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  // Notification Preference Toggles
  var incomingCallsEnabled by remember { mutableStateOf(true) }
  var missedCallsEnabled by remember { mutableStateOf(true) }
  var spatialAudioPromptEnabled by remember { mutableStateOf(true) }
  var latencyWarningEnabled by remember { mutableStateOf(false) }
  var hapticAlertsEnabled by remember { mutableStateOf(true) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .verticalScroll(scrollState)
      .padding(horizontal = 20.dp, vertical = 16.dp)
      .testTag("notifications_screen")
  ) {
    // Top Bar with Back Button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          onBack()
        },
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(Color.White)
          .border(1.dp, Color(0xFFE2E7FF), CircleShape)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column {
        Text(
          text = "Notifications",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "Alert preferences & system notifications",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Section 1: ALERT PREFERENCES
    Text(
      text = "ALERT PREFERENCES",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.outline,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
    ) {
      NotificationToggleRow(
        icon = Icons.Default.Call,
        title = "Incoming 3D Calls",
        subtitle = "Heads-up alert & ringing for incoming spatial calls",
        checked = incomingCallsEnabled,
        onCheckedChange = {
          incomingCallsEnabled = it
          HapticsManager.trigger(context, HapticType.SELECTION)
        }
      )

      NotificationToggleRow(
        icon = Icons.AutoMirrored.Filled.CallMissed,
        title = "Missed Call Reminders",
        subtitle = "Immediate push alert when a volumetric call is missed",
        checked = missedCallsEnabled,
        onCheckedChange = {
          missedCallsEnabled = it
          HapticsManager.trigger(context, HapticType.SELECTION)
        }
      )

      NotificationToggleRow(
        icon = Icons.Default.Headphones,
        title = "Spatial Audio Prompts",
        subtitle = "Notify when stereo earbuds or headphones are connected",
        checked = spatialAudioPromptEnabled,
        onCheckedChange = {
          spatialAudioPromptEnabled = it
          HapticsManager.trigger(context, HapticType.SELECTION)
        }
      )

      NotificationToggleRow(
        icon = Icons.Default.NetworkCheck,
        title = "High Latency Warnings",
        subtitle = "Alert if round-trip ping exceeds 100ms during calls",
        checked = latencyWarningEnabled,
        onCheckedChange = {
          latencyWarningEnabled = it
          HapticsManager.trigger(context, HapticType.SELECTION)
        }
      )

      NotificationToggleRow(
        icon = Icons.Default.Vibration,
        title = "Haptic Vibration",
        subtitle = "Vibrate on call connect, disconnect, and 3D gestures",
        checked = hapticAlertsEnabled,
        onCheckedChange = {
          hapticAlertsEnabled = it
          HapticsManager.trigger(context, HapticType.SELECTION)
        },
        showDivider = false
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Section 2: FIREBASE CLOUD MESSAGING & IN-APP NOTIFICATIONS
    Text(
      text = "CLOUD MESSAGING (FCM) & IN-APP NOTIFICATIONS",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.outline,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
        .padding(18.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFFDCFCE7)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = null,
            tint = LiveSuccess,
            modifier = Modifier.size(22.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "FCM Push Service",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xFFDCFCE7))
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                text = "CONNECTED",
                style = MaterialTheme.typography.labelSmall,
                color = LiveSuccess,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
              )
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = "Receives real-time push alerts and presents live in-app notification banners.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "Test FCM In-App Banner",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = {
            HapticsManager.trigger(context, HapticType.LIGHT)
            InAppNotificationManager.postNotification(
              title = "Missed 3D Spatial Call",
              message = "Sarah Chen called with live volumetric depth & binaural audio",
              type = InAppNotificationType.CALL_MISSED,
              context = context
            )
          },
          modifier = Modifier
            .weight(1f)
            .height(44.dp),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.CallMissed,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Missed Call", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        OutlinedButton(
          onClick = {
            HapticsManager.trigger(context, HapticType.LIGHT)
            InAppNotificationManager.postNotification(
              title = "Spatial Audio Linked",
              message = "Stereo headphones detected — HRTF azimuth tracking active",
              type = InAppNotificationType.SPATIAL_AUDIO,
              context = context
            )
          },
          modifier = Modifier
            .weight(1f)
            .height(44.dp),
          shape = RoundedCornerShape(12.dp),
          border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Headphones,
              contentDescription = null,
              tint = LivePrimaryContainer,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              "Spatial Link",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = LivePrimaryContainer
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(32.dp))
  }
}

@Composable
private fun NotificationToggleRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  showDivider: Boolean = true
) {
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFFF2F3FF)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LivePrimaryContainer,
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
          )
        }
      }

      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = LivePrimaryContainer
        )
      )
    }

    if (showDivider) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(Color(0xFFF2F3FF))
      )
    }
  }
}
