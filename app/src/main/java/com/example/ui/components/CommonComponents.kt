package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpatialOrbCanvas(
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "orbit")
  val rotationAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 12000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "orbit_rotation"
  )
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2400, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )

  Canvas(modifier = modifier.fillMaxSize()) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = (minOf(size.width, size.height) / 2f) * 0.85f

    // Outer faint ambient disc
    drawCircle(
      color = Color(0xFFF2F3FF),
      radius = maxRadius * 0.72f * pulseScale,
      center = center
    )
    drawCircle(
      color = Color(0xFFDBE1FF).copy(alpha = 0.45f),
      radius = maxRadius * 0.50f,
      center = center
    )

    // Dashed outer depth orbit ring (ellipse)
    drawOval(
      color = Color(0xFFDAE2FD),
      topLeft = Offset(center.x - maxRadius * 0.96f, center.y - maxRadius * 0.42f),
      size = Size(maxRadius * 1.92f, maxRadius * 0.84f),
      style = Stroke(
        width = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
      )
    )

    // Equatorial Orbit Plane
    drawOval(
      color = Color(0xFFEAEDFF),
      topLeft = Offset(center.x - maxRadius * 0.98f, center.y - maxRadius * 0.98f),
      size = Size(maxRadius * 1.96f, maxRadius * 1.96f),
      style = Stroke(width = 1.5.dp.toPx())
    )

    // Vertical Meridian Ellipse
    drawOval(
      color = Color(0xFFDAE2FD),
      topLeft = Offset(center.x - maxRadius * 0.45f, center.y - maxRadius * 0.96f),
      size = Size(maxRadius * 0.90f, maxRadius * 1.92f),
      style = Stroke(width = 1.5.dp.toPx())
    )

    // Tilted primary plane
    drawOval(
      color = LivePrimaryContainer.copy(alpha = 0.35f),
      topLeft = Offset(center.x - maxRadius * 0.95f, center.y - maxRadius * 0.30f),
      size = Size(maxRadius * 1.90f, maxRadius * 0.60f),
      style = Stroke(width = 1.8.dp.toPx())
    )

    // Central Presence Silhouette (Stylized Head and Shoulders)
    val headRadius = maxRadius * 0.20f
    val headCenter = Offset(center.x, center.y - headRadius * 0.7f)
    drawCircle(
      color = LivePrimaryContainer,
      radius = headRadius,
      center = headCenter
    )

    val torsoPath = Path().apply {
      val topTorsoY = headCenter.y + headRadius + 4.dp.toPx()
      val shoulderWidth = maxRadius * 0.55f
      val bottomTorsoY = topTorsoY + maxRadius * 0.45f

      moveTo(center.x - shoulderWidth / 2f, bottomTorsoY)
      cubicTo(
        center.x - shoulderWidth / 2f, topTorsoY,
        center.x - shoulderWidth / 4f, topTorsoY - 4.dp.toPx(),
        center.x, topTorsoY - 4.dp.toPx()
      )
      cubicTo(
        center.x + shoulderWidth / 4f, topTorsoY - 4.dp.toPx(),
        center.x + shoulderWidth / 2f, topTorsoY,
        center.x + shoulderWidth / 2f, bottomTorsoY
      )
      close()
    }
    drawPath(torsoPath, color = LivePrimaryContainer)

    // Dynamic Orbiting Satellite Nodes
    val radAngle = Math.toRadians(rotationAngle.toDouble())
    val radAngle2 = Math.toRadians((rotationAngle + 180).toDouble())

    val node1X = (center.x + maxRadius * 0.88f * cos(radAngle)).toFloat()
    val node1Y = (center.y + maxRadius * 0.28f * sin(radAngle)).toFloat()

    drawCircle(
      color = LivePrimaryContainer.copy(alpha = 0.25f),
      radius = 9.dp.toPx(),
      center = Offset(node1X, node1Y)
    )
    drawCircle(
      color = LivePrimaryContainer,
      radius = 5.dp.toPx(),
      center = Offset(node1X, node1Y)
    )

    val node2X = (center.x + maxRadius * 0.88f * cos(radAngle2)).toFloat()
    val node2Y = (center.y + maxRadius * 0.28f * sin(radAngle2)).toFloat()

    drawCircle(
      color = Color(0xFF5BB8FE).copy(alpha = 0.35f),
      radius = 8.dp.toPx(),
      center = Offset(node2X, node2Y)
    )
    drawCircle(
      color = Color(0xFF5BB8FE),
      radius = 4.5f.dp.toPx(),
      center = Offset(node2X, node2Y)
    )
  }
}

@Composable
fun LiveVolumeAvatar(
  avatarUrl: String?,
  initials: String,
  modifier: Modifier = Modifier,
  size: Dp = 48.dp,
  showOnlineBadge: Boolean = false,
  isOnline: Boolean = false,
  backgroundColor: Color = Color(0xFFE2E7FF),
  textColor: Color = LivePrimaryContainer
) {
  Box(modifier = modifier.size(size)) {
    if (!avatarUrl.isNullOrEmpty()) {
      AsyncImage(
        model = avatarUrl,
        contentDescription = "Avatar",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .clip(CircleShape)
          .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(CircleShape)
          .background(backgroundColor),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = initials,
          style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
            color = textColor
          )
        )
      }
    }

    if (showOnlineBadge && isOnline) {
      Box(
        modifier = Modifier
          .size(size * 0.28f)
          .align(Alignment.BottomEnd)
          .offset(x = 1.dp, y = 1.dp)
          .clip(CircleShape)
          .background(LiveSuccess)
          .border(2.dp, Color.White, CircleShape)
      )
    }
  }
}

enum class NavigationTab(val label: String, val testTag: String) {
  RECENTS("Recents", "tab_recents"),
  CONTACTS("Contacts", "tab_contacts"),
  HISTORY("History", "tab_history"),
  SETTINGS("Settings", "tab_settings")
}

@Composable
fun LiveVolumeBottomBar(
  currentTab: NavigationTab,
  onTabSelected: (NavigationTab) -> Unit,
  modifier: Modifier = Modifier
) {
  NavigationBar(
    modifier = modifier.testTag("main_bottom_nav_bar"),
    containerColor = Color.White.copy(alpha = 0.96f),
    tonalElevation = 8.dp
  ) {
    NavigationTab.entries.forEach { tab ->
      val isSelected = tab == currentTab
      NavigationBarItem(
        selected = isSelected,
        onClick = { onTabSelected(tab) },
        icon = {
          when (tab) {
            NavigationTab.RECENTS -> Icon(
              imageVector = Icons.Filled.Phone,
              contentDescription = "Recents"
            )
            NavigationTab.CONTACTS -> Icon(
              imageVector = Icons.Filled.Person,
              contentDescription = "Contacts"
            )
            NavigationTab.HISTORY -> Icon(
              imageVector = Icons.Filled.History,
              contentDescription = "History"
            )
            NavigationTab.SETTINGS -> Icon(
              imageVector = Icons.Filled.Settings,
              contentDescription = "Settings"
            )
          }
        },
        label = {
          Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = LivePrimaryContainer,
          selectedTextColor = LivePrimaryContainer,
          indicatorColor = LivePrimaryContainer.copy(alpha = 0.12f),
          unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
          unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
      )
    }
  }
}
