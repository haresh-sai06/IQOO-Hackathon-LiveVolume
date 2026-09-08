package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.DataRepository
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess

@Composable
fun HelpSupportScreen(
  onBack: () -> Unit,
  onNavigateToLightingGuide: () -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  val expandedFaqs = remember { mutableStateMapOf<String, Boolean>() }
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .verticalScroll(scrollState)
      .padding(horizontal = 20.dp, vertical = 16.dp)
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.testTag("help_back_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = MaterialTheme.colorScheme.onSurface
        )
      }
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = "Help & Support",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Hero Banner Card
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(LivePrimaryContainer)
        .padding(20.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "We're here to help you connect in 3D",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Explore answers or reach out to our dedicated support engineers.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.88f)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        AsyncImage(
          model = DataRepository.SUPPORT_HERO_URL,
          contentDescription = "Support Specialist",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Search Input
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search articles, topics, or FAQs...") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.outline
        )
      },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("help_search_input"),
      shape = RoundedCornerShape(16.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = LivePrimaryContainer,
        unfocusedBorderColor = Color(0xFFE2E7FF)
      ),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Browse by Topic
    Text(
      text = "BROWSE BY TOPIC",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.outline,
      letterSpacing = 1.sp,
      modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      TopicCard(
        icon = Icons.Default.RocketLaunch,
        title = "Getting Started",
        desc = "Set up profile & 1st call",
        modifier = Modifier.weight(1f)
      )
      TopicCard(
        icon = Icons.Default.ViewInAr,
        title = "3D Video Calls",
        desc = "Spatial depth & gestures",
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      TopicCard(
        icon = Icons.Default.Headphones,
        title = "Audio & Camera",
        desc = "Clarity & lighting guide",
        modifier = Modifier.weight(1f)
      )
      TopicCard(
        icon = Icons.Default.Lock,
        title = "Account & Privacy",
        desc = "On-device protections",
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(28.dp))

    // Interactive Guide Feature
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
        .clickable { onNavigateToLightingGuide() }
        .padding(16.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
          model = DataRepository.LIGHTING_GUIDE_URL,
          contentDescription = "Lighting Guide",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(99.dp))
              .background(Color(0xFFF2F3FF))
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(
              text = "PRO TIP",
              style = MaterialTheme.typography.labelSmall,
              color = LivePrimaryContainer,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Optimizing Call Lighting",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          Text(
            text = "Tips for crisp real-time depth capture",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(28.dp))

    // Frequently Asked Questions
    Text(
      text = "FREQUENTLY ASKED QUESTIONS",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.outline,
      letterSpacing = 1.sp,
      modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
    )

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      DataRepository.faqs.forEach { faq ->
        val isExpanded = expandedFaqs[faq.id] == true
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
            .clickable { expandedFaqs[faq.id] = !isExpanded }
            .padding(16.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = faq.question,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
              )
              Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.outline
              )
            }

            AnimatedVisibility(visible = isExpanded) {
              Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                  text = faq.answer,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 22.sp
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(28.dp))

    // Still need help? Contact Cards
    Text(
      text = "STILL NEED HELP?",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.outline,
      letterSpacing = 1.sp,
      modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      ContactChannelCard(
        icon = Icons.AutoMirrored.Filled.Chat,
        title = "Chat with Us",
        subtitle = "Online 24/7",
        modifier = Modifier.weight(1f)
      )
      ContactChannelCard(
        icon = Icons.Default.Email,
        title = "Email Support",
        subtitle = "Response in ~2h",
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Relay Status Badge
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(99.dp))
        .background(Color(0xFFF2F3FF))
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(LiveSuccess)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "All 3D Relays Operational",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun TopicCard(
  icon: ImageVector,
  title: String,
  desc: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
      .clickable { }
      .padding(14.dp)
  ) {
    Column {
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
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = desc,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun ContactChannelCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
      .clickable { }
      .padding(14.dp)
  ) {
    Column {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = LivePrimaryContainer,
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
