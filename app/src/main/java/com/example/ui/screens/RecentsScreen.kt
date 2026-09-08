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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CallDirection
import com.example.model.CallPeriod
import com.example.model.CallRecord
import com.example.model.CallType
import com.example.model.DataRepository
import com.example.ui.components.CameraPermissionRequestFlow
import com.example.ui.components.CameraPreview
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LiveError
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import com.example.util.AudioPermissionHelper

@Composable
fun RecentsScreen(
  onStartCall: (callerName: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("All") }
  val filters = listOf("All", "Missed", "3D Calls", "Standard")

  var isFrontCamera by remember { mutableStateOf(true) }
  var isCameraOn by remember { mutableStateOf(true) }
  var isCameraGranted by remember { mutableStateOf(false) }
  var isAudioGranted by remember { mutableStateOf(false) }

  val filteredCalls = remember(searchQuery, selectedFilter) {
    DataRepository.callRecords.filter { call ->
      val matchesSearch = call.contactName.contains(searchQuery, ignoreCase = true)
      val matchesFilter = when (selectedFilter) {
        "Missed" -> call.callType == CallType.MISSED
        "3D Calls" -> call.callType == CallType.SPATIAL_3D
        "Standard" -> call.callType == CallType.VIDEO || call.callType == CallType.AUDIO
        else -> true
      }
      matchesSearch && matchesFilter
    }
  }

  val todayCalls = filteredCalls.filter { it.period == CallPeriod.TODAY }
  val yesterdayCalls = filteredCalls.filter { it.period == CallPeriod.YESTERDAY }
  val earlierCalls = filteredCalls.filter { it.period == CallPeriod.EARLIER_THIS_WEEK }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Recents",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      IconButton(
        onClick = { onStartCall("Sarah Chen") },
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(Color(0xFFF2F3FF))
          .testTag("dialpad_button")
      ) {
        Icon(
          imageVector = Icons.Default.Dialpad,
          contentDescription = "Keypad",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp)
        )
      }
    }

    // Search Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search recents and contacts...") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.outline
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(
              imageVector = Icons.Default.Clear,
              contentDescription = "Clear",
              tint = MaterialTheme.colorScheme.outline
            )
          }
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .testTag("recents_search_input"),
      shape = RoundedCornerShape(16.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = LivePrimaryContainer,
        unfocusedBorderColor = Color(0xFFE2E7FF)
      ),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Filter Chips
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(filters) { filter ->
        val isSelected = filter == selectedFilter
        FilterChip(
          selected = isSelected,
          onClick = { selectedFilter = filter },
          label = {
            Text(
              text = filter,
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

    Spacer(modifier = Modifier.height(12.dp))

    // Main Content & Call History Logs Grouped
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 20.dp)
    ) {
      // Live Camera Feed & Runtime Permission Flow Section
      item {
        CameraPermissionRequestFlow(
          onPermissionsResult = { cam, aud ->
            isCameraGranted = cam
            isAudioGranted = aud
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
        ) {
          // Live Video Feed Container on Main Screen powered by CameraX
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(210.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(Color(0xFF0F121C))
              .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(20.dp))
              .testTag("main_screen_camera_preview")
          ) {
            // Live CameraX feed
            CameraPreview(
              isFrontCamera = isFrontCamera,
              isCameraOn = isCameraOn,
              modifier = Modifier.fillMaxSize()
            )

            // Scrim overlay for controls
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      Color.Black.copy(alpha = 0.5f),
                      Color.Transparent,
                      Color.Black.copy(alpha = 0.7f)
                    )
                  )
                )
            )

            // Top Status Badges Row
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Live camera readiness pill
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clip(RoundedCornerShape(99.dp))
                  .background(Color.Black.copy(alpha = 0.6f))
                  .padding(horizontal = 10.dp, vertical = 5.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isCameraOn) LiveSuccess else Color(0xFFFF5252))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (isCameraOn) "Live Camera Feed" else "Camera Paused",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  fontSize = 11.sp
                )
              }

              // Binaural Audio Status Badge
              val isAudioAuthorized = AudioPermissionHelper.isAudioCaptureAuthorized(context)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clip(RoundedCornerShape(99.dp))
                  .background(Color.Black.copy(alpha = 0.6f))
                  .padding(horizontal = 10.dp, vertical = 5.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.GraphicEq,
                  contentDescription = "Binaural Audio",
                  tint = if (isAudioAuthorized) LivePrimaryContainer else Color.White.copy(alpha = 0.6f),
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (isAudioAuthorized) "Binaural 3D Audio" else "Audio Pending",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Medium,
                  color = Color.White,
                  fontSize = 11.sp
                )
              }
            }

            // Bottom Actions Row over Live Camera
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.BottomCenter),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Switch Camera Button
                IconButton(
                  onClick = { isFrontCamera = !isFrontCamera },
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .testTag("main_camera_switch_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }

                // Pause/Resume Camera
                IconButton(
                  onClick = { isCameraOn = !isCameraOn },
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .testTag("main_camera_toggle_button")
                ) {
                  Icon(
                    imageVector = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = "Toggle Camera",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }

              // Quick Connect Button
              Button(
                onClick = { onStartCall("Sarah Chen") },
                modifier = Modifier
                  .height(38.dp)
                  .testTag("start_spatial_call_button"),
                shape = RoundedCornerShape(99.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Launch 3D Call",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
      }

      if (todayCalls.isNotEmpty()) {
        item {
          PeriodHeader(title = "TODAY")
        }
        items(todayCalls) { call ->
          CallRecordRow(
            call = call,
            onCallClick = { onStartCall(call.contactName) }
          )
          Spacer(modifier = Modifier.height(8.dp))
        }
      }

      if (yesterdayCalls.isNotEmpty()) {
        item {
          PeriodHeader(title = "YESTERDAY")
        }
        items(yesterdayCalls) { call ->
          CallRecordRow(
            call = call,
            onCallClick = { onStartCall(call.contactName) }
          )
          Spacer(modifier = Modifier.height(8.dp))
        }
      }

      if (earlierCalls.isNotEmpty()) {
        item {
          PeriodHeader(title = "EARLIER THIS WEEK")
        }
        items(earlierCalls) { call ->
          CallRecordRow(
            call = call,
            onCallClick = { onStartCall(call.contactName) }
          )
          Spacer(modifier = Modifier.height(8.dp))
        }
      }

      item {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun PeriodHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    letterSpacing = 1.sp,
    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
  )
}

@Composable
private fun CallRecordRow(
  call: CallRecord,
  onCallClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFF0F2FF), RoundedCornerShape(16.dp))
      .clickable(onClick = onCallClick)
      .padding(14.dp)
      .testTag("call_record_${call.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f)
    ) {
      LiveVolumeAvatar(
        avatarUrl = call.avatarUrl,
        initials = call.contactName.take(2).uppercase(),
        size = 46.dp,
        showOnlineBadge = call.isOnline
      )

      Spacer(modifier = Modifier.width(14.dp))

      Column {
        Text(
          text = call.contactName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = if (call.callType == CallType.MISSED) LiveError else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = when (call.direction) {
              CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
              CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
              CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed
            },
            contentDescription = null,
            tint = when (call.callType) {
              CallType.MISSED -> LiveError
              CallType.SPATIAL_3D -> LivePrimaryContainer
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(14.dp)
          )

          Spacer(modifier = Modifier.width(4.dp))

          Text(
            text = "${call.timestamp} • ${call.duration}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Call Action Badge / Button
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (call.callType == CallType.SPATIAL_3D) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEAEDFF))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.ViewInAr,
              contentDescription = "Spatial 3D",
              tint = LivePrimaryContainer,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "3D",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = LivePrimaryContainer,
              fontSize = 10.sp
            )
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
      }

      IconButton(
        onClick = onCallClick,
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(Color(0xFFF7F8FE))
      ) {
        Icon(
          imageVector = if (call.callType == CallType.AUDIO) Icons.Default.Call else Icons.Default.Videocam,
          contentDescription = "Call",
          tint = LivePrimaryContainer,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
