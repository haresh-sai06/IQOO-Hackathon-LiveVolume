package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LiveError
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import com.example.util.InAppNotification
import com.example.util.InAppNotificationType

@Composable
fun InAppNotificationBanner(
  notification: InAppNotification?,
  onDismiss: () -> Unit,
  onNotificationClick: (InAppNotification) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    contentAlignment = Alignment.TopCenter
  ) {
    AnimatedVisibility(
      visible = notification != null,
      enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
      if (notification != null) {
        val (icon, iconColor, bgIconColor) = when (notification.type) {
          InAppNotificationType.CALL_MISSED ->
            Triple(Icons.AutoMirrored.Filled.CallMissed, LiveError, Color(0xFFFEE2E2))
          InAppNotificationType.CALL_INCOMING ->
            Triple(Icons.Default.Call, LiveSuccess, Color(0xFFDCFCE7))
          InAppNotificationType.SPATIAL_AUDIO ->
            Triple(Icons.Default.Headphones, Color(0xFF8B5CF6), Color(0xFFEDE9FE))
          InAppNotificationType.SYSTEM_ENGINE ->
            Triple(Icons.Default.ViewInAr, LivePrimaryContainer, Color(0xFFDBEAFE))
          InAppNotificationType.GENERAL ->
            Triple(Icons.Default.Notifications, LivePrimaryContainer, Color(0xFFEAEDFF))
        }

        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(20.dp))
            .clickable {
              onNotificationClick(notification)
              onDismiss()
            },
          color = Color.White,
          shape = RoundedCornerShape(20.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Icon with soft pill container
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(bgIconColor),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text column
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = notification.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "now",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.outline
                )
              }

              Text(
                text = notification.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
              )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Dismiss X button
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }
}
