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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AuthRepository
import com.example.model.DataRepository
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LiveError
import com.example.ui.theme.LivePrimaryContainer
import com.example.util.HapticType
import com.example.util.HapticsManager


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onNavigateToProfile: () -> Unit = {},
  onNavigateToGuides: () -> Unit = {},
  onNavigateToHelp: () -> Unit,
  onNavigateToAbout: () -> Unit,
  onNavigateToPrivacy: () -> Unit,
  onNavigateToNotifications: () -> Unit = {},
  onNavigateToDepthDebug: () -> Unit = {},
  onLogOut: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val authRepository = remember { AuthRepository.getInstance(context) }
  val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()

  var optimizeMobileData by remember { mutableStateOf(false) }
  var videoQuality by remember { mutableStateOf("Auto (1080p HD)") }
  var spatialQuality by remember { mutableStateOf("High Volumetric Depth") }
  var showQualityDialog by remember { mutableStateOf(false) }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .verticalScroll(scrollState)
      .padding(horizontal = 20.dp, vertical = 16.dp)
  ) {
    // Header
    Text(
      text = "Settings",
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(vertical = 8.dp)
    )

    // User Profile Card
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(20.dp))
        .clickable { onNavigateToProfile() }
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        val profileName = currentUser?.name ?: "My Profile"
        val profileEmail = currentUser?.email?.ifBlank { currentUser?.phone } ?: currentUser?.phone ?: "Tap to edit profile"
        val profileInitials = (currentUser?.name?.take(2)?.uppercase()) ?: "ME"

        LiveVolumeAvatar(
          avatarUrl = currentUser?.avatarUrl,
          initials = profileInitials,
          size = 56.dp,
          showOnlineBadge = true,
          isOnline = true
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = profileName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = "Verified",
              tint = LivePrimaryContainer,
              modifier = Modifier.size(16.dp)
            )
          }

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = profileEmail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = "Profile",
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(16.dp)
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Section 1: CALL & VIDEO QUALITY
    SettingsSectionTitle("CALL & VIDEO QUALITY")

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
    ) {
      SettingsRow(
        icon = Icons.Default.Videocam,
        title = "Video Quality",
        subtitle = videoQuality,
        onClick = { showQualityDialog = true }
      )

      SettingsRow(
        icon = Icons.Default.ViewInAr,
        title = "3D Quality",
        subtitle = spatialQuality,
        onClick = { showQualityDialog = true }
      )

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
              imageVector = Icons.Default.CellTower,
              contentDescription = null,
              tint = LivePrimaryContainer,
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column {
            Text(
              text = "Optimize for mobile data",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Compress 3D point cloud when on cellular",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Switch(
          checked = optimizeMobileData,
          onCheckedChange = { optimizeMobileData = it },
          colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LivePrimaryContainer)
        )
      }

      var performanceFallback by remember { mutableStateOf(com.example.ml.DepthEstimator.isPerformanceFallbackEnabled) }

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
              imageVector = Icons.Default.Speed,
              contentDescription = null,
              tint = LivePrimaryContainer,
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column {
            Text(
              text = "Performance Fallback Mode",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Caps 3D point density to prevent device thermal throttling",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Switch(
          checked = performanceFallback,
          onCheckedChange = {
            performanceFallback = it
            com.example.ml.DepthEstimator.isPerformanceFallbackEnabled = it
          },
          colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LivePrimaryContainer)
        )
      }

      SettingsRow(
        icon = Icons.Default.ViewInAr,
        title = "Neural Depth Estimation (MiDaS AI)",
        subtitle = "Live on-device depth map inference & latency telemetry",
        onClick = onNavigateToDepthDebug
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Section 2: PREFERENCES & PRIVACY
    SettingsSectionTitle("PREFERENCES & PRIVACY")

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
    ) {
      SettingsRow(
        icon = Icons.Default.Notifications,
        title = "Notifications",
        subtitle = "Calls, missed calls, and connection alerts",
        onClick = onNavigateToNotifications
      )

      SettingsRow(
        icon = Icons.Default.Security,
        title = "Privacy & Camera Permissions",
        subtitle = "Manage camera, mic, and on-device processing",
        onClick = onNavigateToPrivacy
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Section 3: SUPPORT & ABOUT
    SettingsSectionTitle("SUPPORT & ABOUT")

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
    ) {
      SettingsRow(
        icon = Icons.AutoMirrored.Filled.MenuBook,
        title = "Guides & Tutorials",
        subtitle = "Volumetric calling, spatial audio & setup tips",
        onClick = onNavigateToGuides,
        modifier = Modifier.testTag("settings_guides_row")
      )

      SettingsRow(
        icon = Icons.AutoMirrored.Filled.Help,
        title = "Help Center",
        subtitle = "Guides, FAQs, and contact support",
        onClick = onNavigateToHelp
      )

      SettingsRow(
        icon = Icons.Default.Info,
        title = "About LiveVolume",
        subtitle = "Version 1.4.2",
        onClick = onNavigateToAbout
      )
    }

    Spacer(modifier = Modifier.height(28.dp))

    // Log Out Button
    Button(
      onClick = {
        authRepository.signOut()
        onLogOut()
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("logout_button"),
      shape = RoundedCornerShape(16.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFFFFDAD6),
        contentColor = LiveError
      )
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Logout,
          contentDescription = null,
          tint = LiveError,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Log Out",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = LiveError
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "LiveVolume Inc. • Device Encrypted",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.outline,
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )

    Spacer(modifier = Modifier.height(24.dp))
  }

  // Quality settings bottom sheet
  if (showQualityDialog) {
    ModalBottomSheet(
      onDismissRequest = { showQualityDialog = false },
      sheetState = rememberModalBottomSheetState()
    ) {
      Column(modifier = Modifier.padding(24.dp)) {
        Text(
          text = "Streaming Quality Settings",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        listOf(
          "Auto (1080p HD based on bandwidth)",
          "High Fidelity (Max 3D Point Density)",
          "Data Saver (Compressed Depth Grid)"
        ).forEach { option ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                videoQuality = option
                showQualityDialog = false
              }
              .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.HighQuality,
              contentDescription = null,
              tint = LivePrimaryContainer,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = option,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsSectionTitle(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.outline,
    letterSpacing = 1.sp,
    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
  )
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 14.dp),
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

      Spacer(modifier = Modifier.width(14.dp))

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
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outline,
      modifier = Modifier.size(16.dp)
    )
  }
}
