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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Contact
import com.example.model.DataRepository
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LivePrimaryContainer

@Composable
fun ContactsScreen(
  onStartCall: (contactName: String) -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("All") }
  val filters = listOf("All", "3D Ready", "Favorites", "Recent")

  val filteredContacts = remember(searchQuery, selectedFilter) {
    DataRepository.allContacts.filter { contact ->
      val matchesSearch = contact.name.contains(searchQuery, ignoreCase = true) ||
        contact.phone.contains(searchQuery)
      val matchesFilter = when (selectedFilter) {
        "3D Ready" -> contact.isSpatialReady
        "Favorites" -> contact.isFavorite
        else -> true
      }
      matchesSearch && matchesFilter
    }
  }

  val groupedContacts = remember(filteredContacts) {
    filteredContacts.groupBy { it.section }
  }

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
      Column {
        Text(
          text = "Contacts",
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "142 people • 28 ready for 3D live spatial audio",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      IconButton(
        onClick = { },
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(Color(0xFFF2F3FF))
          .testTag("add_contact_button")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Add Contact",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(22.dp)
        )
      }
    }

    // Search Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search by name or number...") },
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
        .testTag("contacts_search_input"),
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

    // Main Content
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 20.dp)
    ) {
      // My Card Section
      item {
        Text(
          text = "MY CARD",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.outline,
          letterSpacing = 1.sp,
          modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
            .clickable { onStartCall(DataRepository.myProfile.name) }
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            LiveVolumeAvatar(
              avatarUrl = DataRepository.myProfile.avatarUrl,
              initials = DataRepository.myProfile.initials,
              size = 50.dp,
              showOnlineBadge = true,
              isOnline = true
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
              Text(
                text = DataRepository.myProfile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = "${DataRepository.myProfile.phone} • ${DataRepository.myProfile.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(99.dp))
              .background(Color(0xFFEAEDFF))
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.ViewInAr,
                contentDescription = null,
                tint = LivePrimaryContainer,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "3D",
                style = MaterialTheme.typography.labelSmall,
                color = LivePrimaryContainer,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }

      // Quick Connect Favorites
      item {
        Text(
          text = "QUICK CONNECT",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.outline,
          letterSpacing = 1.sp,
          modifier = Modifier.padding(bottom = 10.dp)
        )

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(DataRepository.quickConnectFavorites) { favorite ->
            QuickConnectCard(
              contact = favorite,
              onCallClick = { onStartCall(favorite.name) }
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }

      // Directory Alphabet Sections
      groupedContacts.forEach { (section, contacts) ->
        item {
          Text(
            text = section,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 8.dp)
          )
        }

        items(contacts) { contact ->
          ContactRow(
            contact = contact,
            onCallClick = { onStartCall(contact.name) }
          )
          Spacer(modifier = Modifier.height(8.dp))
        }
      }

      // Tip Card: Looking for 3D Audio?
      item {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF2F3FF))
            .border(1.dp, Color(0xFFDAE2FD), RoundedCornerShape(18.dp))
            .padding(18.dp)
        ) {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.ViewInAr,
                contentDescription = null,
                tint = LivePrimaryContainer,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Looking for 3D Audio?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = "Invite friends to LiveVolume to unlock real-time spatial positioning during group and 1-on-1 calls.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = { },
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Share,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Invite Contacts",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun QuickConnectCard(
  contact: Contact,
  onCallClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .width(108.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
      .clickable { onCallClick() }
      .padding(vertical = 12.dp, horizontal = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      LiveVolumeAvatar(
        avatarUrl = contact.avatarUrl,
        initials = contact.initials,
        size = 46.dp,
        showOnlineBadge = true,
        isOnline = contact.isOnline
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = contact.name,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(if (contact.isSpatialReady) LivePrimaryContainer else Color(0xFFF2F3FF)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (contact.isSpatialReady) Icons.Default.ViewInAr else Icons.Default.Call,
          contentDescription = "Call",
          tint = if (contact.isSpatialReady) Color.White else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}

@Composable
private fun ContactRow(
  contact: Contact,
  onCallClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
      .clickable { onCallClick() }
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f)
    ) {
      LiveVolumeAvatar(
        avatarUrl = contact.avatarUrl,
        initials = contact.initials,
        size = 44.dp,
        showOnlineBadge = contact.isOnline,
        isOnline = contact.isOnline
      )

      Spacer(modifier = Modifier.width(12.dp))

      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = contact.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (contact.isSpatialReady) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xFFEAEDFF))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = "3D",
                style = MaterialTheme.typography.labelSmall,
                color = LivePrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = contact.status,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(if (contact.isSpatialReady) LivePrimaryContainer else Color(0xFFF2F3FF))
        .clickable { onCallClick() },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (contact.isSpatialReady) Icons.Default.ViewInAr else Icons.Default.Call,
        contentDescription = "Call",
        tint = if (contact.isSpatialReady) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}
