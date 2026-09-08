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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.example.model.DataRepository
import com.example.model.GuideItem
import com.example.ui.theme.LivePrimaryContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidesScreen(
  onBack: (() -> Unit)? = null,
  onNavigateToHelp: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedCategory by remember { mutableStateOf("All Guides") }
  val categories = listOf("All Guides", "Camera & Lighting", "Spatial Audio", "Battery & Data")
  var selectedGuide by remember { mutableStateOf<GuideItem?>(null) }

  val filteredGuides = remember(selectedCategory) {
    if (selectedCategory == "All Guides") {
      DataRepository.guides
    } else {
      DataRepository.guides.filter { it.category == selectedCategory }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
  ) {
    // Top Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("guides_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
          text = "User Guides & Tips",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      IconButton(
        onClick = { },
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(Color(0xFFF2F3FF))
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp)
        )
      }
    }

    // Category Filter Chips
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(categories) { category ->
        val isSelected = category == selectedCategory
        FilterChip(
          selected = isSelected,
          onClick = { selectedCategory = category },
          label = {
            Text(
              text = category,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
          },
          shape = RoundedCornerShape(99.dp),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LivePrimaryContainer,
            selectedLabelColor = Color.White,
            containerColor = Color.White,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = if (isSelected) LivePrimaryContainer else Color(0xFFE2E7FF)
          )
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Main Content
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 20.dp)
    ) {
      // Featured Walkthrough Card
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LivePrimaryContainer)
            .padding(18.dp)
        ) {
          Column {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.20f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text(
                text = "FEATURED WALKTHROUGH",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "Getting Started with 3D Holographic Calls",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Visual steps
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              StepItem(
                number = "1",
                label = "Position Face",
                desc = "Center in frame",
                modifier = Modifier.weight(1f)
              )
              Spacer(modifier = Modifier.width(6.dp))
              StepItem(
                number = "2",
                label = "Depth Sync",
                desc = "Lock in 2s",
                modifier = Modifier.weight(1f)
              )
              Spacer(modifier = Modifier.width(6.dp))
              StepItem(
                number = "3",
                label = "Orbit & Talk",
                desc = "Drag to rotate",
                modifier = Modifier.weight(1f)
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
              onClick = {
                selectedGuide = GuideItem(
                  id = "feat",
                  title = "Getting Started with 3D Holographic Calls",
                  summary = "Learn how to position your device, sync depth in 2 seconds, and smoothly orbit around your caller during a live call.",
                  category = "Camera & Lighting",
                  iconName = "view_in_ar"
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = LivePrimaryContainer
              )
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Read Full Walkthrough",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))
      }

      // Guides List
      items(filteredGuides) { guide ->
        GuideCard(
          guide = guide,
          onClick = { selectedGuide = guide }
        )
        Spacer(modifier = Modifier.height(10.dp))
      }

      // Bottom Support Card
      item {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF2F3FF))
            .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
            .padding(16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Still have questions?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Our technical support team is available 24/7.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Button(
              onClick = onNavigateToHelp,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
            ) {
              Text(
                text = "Help Center",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }

  // Guide Detail Bottom Sheet
  if (selectedGuide != null) {
    ModalBottomSheet(
      onDismissRequest = { selectedGuide = null },
      sheetState = rememberModalBottomSheetState()
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0xFFEAEDFF))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = selectedGuide?.category ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = LivePrimaryContainer,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = selectedGuide?.title ?: "",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = selectedGuide?.summary ?: "",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Pro Tip:\n• Hold your phone between 18 and 28 inches from your face for optimal depth triangulation.\n• Ambient natural light from the front creates the sharpest 3D contours with zero sensor noise.\n• Put on standard stereo headphones to enable binaural spatial audio and pinpoint the speaker in 3D.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = { selectedGuide = null },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
        ) {
          Text("Got It", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun StepItem(
  number: String,
  label: String,
  desc: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color.White.copy(alpha = 0.16f))
      .padding(8.dp)
  ) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color.White),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = number,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = LivePrimaryContainer
          )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = desc,
        style = MaterialTheme.typography.bodySmall,
        fontSize = 11.sp,
        color = Color.White.copy(alpha = 0.82f)
      )
    }
  }
}

@Composable
private fun GuideCard(
  guide: GuideItem,
  onClick: () -> Unit
) {
  val icon: ImageVector = when (guide.iconName) {
    "pan_tool" -> Icons.Default.PanTool
    "headphones" -> Icons.Default.Headphones
    "light_mode" -> Icons.Default.LightMode
    "signal_cellular_alt" -> Icons.Default.SignalCellularAlt
    else -> Icons.Default.ViewInAr
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(42.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFFF2F3FF)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = LivePrimaryContainer,
        modifier = Modifier.size(22.dp)
      )
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = guide.title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = guide.summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outline,
      modifier = Modifier.size(16.dp)
    )
  }
}
