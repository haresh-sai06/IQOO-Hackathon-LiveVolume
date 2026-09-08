package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.DataRepository
import com.example.ui.components.SpatialOrbCanvas
import com.example.ui.theme.LivePrimaryContainer

@Composable
fun WelcomeScreen(
  onGetStarted: () -> Unit,
  onSignIn: () -> Unit,
  onTermsClick: () -> Unit,
  onPrivacyClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .verticalScroll(scrollState)
      .padding(horizontal = 20.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    // Top Brand Header
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp, bottom = 16.dp)
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White)
          .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
          .padding(8.dp),
        contentAlignment = Alignment.Center
      ) {
        AsyncImage(
          model = DataRepository.LOGO_URL,
          contentDescription = "LiveVolume Logo",
          modifier = Modifier.fillMaxSize()
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "LiveVolume",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "See who you're talking to, in 3D",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }

    // Hero Spatial Visual Centerpiece
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(20.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(20.dp))
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      SpatialOrbCanvas(modifier = Modifier.fillMaxSize())

      // "Spatial Room" Pill (Top-Left)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .align(Alignment.TopStart)
          .clip(RoundedCornerShape(99.dp))
          .background(Color(0xFFF2F3FF))
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(LivePrimaryContainer)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Spatial Room",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold
        )
      }

      // "True Depth" Pill (Bottom-Right)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .clip(RoundedCornerShape(99.dp))
          .background(Color(0xFFF2F3FF))
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.ViewInAr,
          contentDescription = "True Depth",
          tint = LivePrimaryContainer,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "True Depth",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Value Props Column
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      ValuePropItem(
        icon = Icons.Default.Smartphone,
        title = "No headset required",
        subtitle = "Natural 3D view using your phone camera"
      )
      ValuePropItem(
        icon = Icons.Default.GraphicEq,
        title = "Crystal-clear real-time depth",
        subtitle = "Lifelike presence with zero perceptible delay"
      )
      ValuePropItem(
        icon = Icons.Default.Lock,
        title = "Private & encrypted on-device",
        subtitle = "Volumetric frames never touch the cloud"
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Bottom Action Area
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth()
    ) {
      Button(
        onClick = onGetStarted,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("get_started_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = LivePrimaryContainer,
          contentColor = Color.White
        )
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Text(
            text = "Get started",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      TextButton(
        onClick = onSignIn,
        modifier = Modifier.testTag("already_have_account_button")
      ) {
        Text(
          text = "Already have an account? Sign in",
          style = MaterialTheme.typography.labelLarge,
          color = LivePrimaryContainer,
          fontWeight = FontWeight.SemiBold
        )
      }

      Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "By continuing, you agree to our ",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        )
        Text(
          text = "Terms",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.clickable { onTermsClick() }
        )
        Text(
          text = " & ",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline
        )
        Text(
          text = "Privacy Policy",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.clickable { onPrivacyClick() }
        )
      }
    }
  }
}

@Composable
private fun ValuePropItem(
  icon: ImageVector,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
      .padding(horizontal = 16.dp, vertical = 14.dp)
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

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
