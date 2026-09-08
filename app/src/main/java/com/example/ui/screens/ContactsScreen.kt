package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AuthRepository
import com.example.data.repository.RealtimeContactsRepository
import com.example.model.Contact
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.ThemeManager
import com.example.util.HapticType
import com.example.util.HapticsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun fetchDeviceContacts(context: Context): List<Contact> {
  val list = mutableListOf<Contact>()
  try {
    val cursor = context.contentResolver.query(
      ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
      arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
      ),
      null,
      null,
      "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
    )
    cursor?.use {
      val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
      val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
      val seenNames = mutableSetOf<String>()

      while (it.moveToNext()) {
        val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "" else ""
        val number = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""

        if (name.isNotBlank() && seenNames.add(name.trim().lowercase())) {
          val initials = name.trim().split(" ")
            .filter { part -> part.isNotEmpty() }
            .take(2)
            .map { part -> part.first().uppercase() }
            .joinToString("")
            .ifEmpty { "U" }
          val firstChar = name.trim().firstOrNull()?.uppercase() ?: "A"
          val section = if (firstChar.first().isLetter()) firstChar else "#"
          list.add(
            Contact(
              id = "phone_${System.currentTimeMillis()}_${list.size}",
              name = name.trim(),
              initials = initials,
              phone = number.trim(),
              status = "Mobile Contact",
              avatarUrl = null,
              isSpatialReady = true,
              isOnline = false,
              isFavorite = false,
              section = section
            )
          )
        }
      }
    }
  } catch (e: Exception) {
    android.util.Log.e("ContactsScreen", "Error reading contacts: ${e.message}")
  }
  return list
}

@Composable
fun ContactsScreen(
  onStartCall: (contactName: String) -> Unit,
  onOpenProfile: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val contactsRepository = remember { RealtimeContactsRepository.getInstance(context) }
  val allContacts by contactsRepository.contactsFlow.collectAsStateWithLifecycle()
  val authRepository = remember { AuthRepository.getInstance(context) }
  val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()
  val activePrimary = ThemeManager.currentTheme.primaryContainer

  var searchQuery by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  var isImporting by remember { mutableStateOf(false) }

  val importPhoneContactsAction: () -> Unit = {
    scope.launch(Dispatchers.IO) {
      isImporting = true
      val phoneContacts = fetchDeviceContacts(context)
      withContext(Dispatchers.Main) {
        isImporting = false
        if (phoneContacts.isEmpty()) {
          Toast.makeText(context, "No contacts found on device", Toast.LENGTH_SHORT).show()
        } else {
          val added = contactsRepository.importPhoneContacts(phoneContacts)
          Toast.makeText(
            context,
            if (added > 0) "Imported $added contacts from phone!" else "All phone contacts are already in LiveVolume",
            Toast.LENGTH_SHORT
          ).show()
        }
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      importPhoneContactsAction()
    } else {
      Toast.makeText(
        context,
        "Contact read permission is required to import from your phone",
        Toast.LENGTH_LONG
      ).show()
    }
  }

  val checkAndImportContacts: () -> Unit = {
    val permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
    if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
      importPhoneContactsAction()
    } else {
      permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }
  }

  var selectedFilter by remember { mutableStateOf("All") }
  val filters = listOf("All", "3D Ready", "Favorites", "Recent")

  var showAddDialog by remember { mutableStateOf(false) }
  var newContactName by remember { mutableStateOf("") }
  var newContactPhone by remember { mutableStateOf("") }
  var newContact3DReady by remember { mutableStateOf(true) }

  val filteredContacts = remember(searchQuery, selectedFilter, allContacts) {
    allContacts.filter { contact ->
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

  val favoriteContacts = remember(allContacts) {
    allContacts.filter { it.isFavorite }
  }

  val groupedContacts = remember(filteredContacts) {
    filteredContacts.groupBy { it.section }
  }

  val alphabet = remember { ('A'..'Z').map { it.toString() } + "#" }
  val lettersPresent = remember(filteredContacts) {
    filteredContacts.mapNotNull { it.name.firstOrNull()?.uppercase() }.toSet()
  }

  if (showAddDialog) {
    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Add New Contact", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = newContactName,
            onValueChange = { newContactName = it },
            label = { Text("Full Name") },
            placeholder = { Text("e.g. Jordan Lee") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = newContactPhone,
            onValueChange = { newContactPhone = it },
            label = { Text("Phone Number") },
            placeholder = { Text("+1 (555) 000-0000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Ready for 3D Holographic Calls",
              style = MaterialTheme.typography.bodyMedium
            )
            Switch(
              checked = newContact3DReady,
              onCheckedChange = { newContact3DReady = it },
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activePrimary
              )
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (newContactName.isNotBlank()) {
              HapticsManager.trigger(context, HapticType.SELECTION)
              contactsRepository.addContact(
                name = newContactName.trim(),
                phone = newContactPhone.ifBlank { "+1 (555) 123-4567" },
                isSpatialReady = newContact3DReady
              )
              newContactName = ""
              newContactPhone = ""
              showAddDialog = false
            }
          }
        ) {
          Text("Save Contact", fontWeight = FontWeight.Bold, color = activePrimary)
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    )
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
        .padding(horizontal = 20.dp, vertical = 14.dp),
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
          text = "${allContacts.size} people • ${allContacts.count { it.isSpatialReady }} ready for 3D spatial calls",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Import phone contacts button
        IconButton(
          onClick = {
            HapticsManager.trigger(context, HapticType.LIGHT)
            checkAndImportContacts()
          },
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFFF2F3FF))
            .testTag("import_contacts_button")
        ) {
          Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = "Import from Phone",
            tint = LivePrimaryContainer,
            modifier = Modifier.size(19.dp)
          )
        }

        // Add contact button
        IconButton(
          onClick = {
            HapticsManager.trigger(context, HapticType.LIGHT)
            showAddDialog = true
          },
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFFF2F3FF))
            .testTag("add_contact_button")
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Contact",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
          )
        }
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
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = activePrimary,
        unfocusedBorderColor = Color(0xFFE2E7FF)
      ),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(10.dp))

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
          onClick = {
            HapticsManager.trigger(context, HapticType.SELECTION)
            selectedFilter = filter
          },
          label = {
            Text(
              text = filter,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
          },
          shape = RoundedCornerShape(99.dp),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = activePrimary,
            selectedLabelColor = Color.White,
            containerColor = Color.White,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = if (isSelected) activePrimary else Color(0xFFE2E7FF)
          )
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Main Directory List with Alphabet Fast-Scroll Rail
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(start = 20.dp, end = if (searchQuery.isEmpty()) 4.dp else 20.dp)
    ) {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .padding(end = if (searchQuery.isEmpty()) 6.dp else 0.dp)
      ) {
        // My Card Section (Compact & Breathable)
        if (searchQuery.isEmpty() && selectedFilter == "All") {
          item {
            val profileName = currentUser?.name?.ifBlank { "My Profile" } ?: "My Profile"
            val profileSubtitle = currentUser?.phone?.ifBlank { currentUser?.email } ?: currentUser?.email ?: "Tap to view profile"
            val profileInitials = profileName.split(" ")
              .filter { it.isNotEmpty() }
              .take(2)
              .map { it.first().uppercase() }
              .joinToString("")
              .ifEmpty { "ME" }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
                .clickable {
                  HapticsManager.trigger(context, HapticType.SELECTION)
                  onOpenProfile()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              LiveVolumeAvatar(
                avatarUrl = currentUser?.avatarUrl,
                initials = profileInitials,
                size = 46.dp,
                showOnlineBadge = true,
                isOnline = true
              )
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = profileName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "$profileSubtitle • ${currentUser?.status ?: "3D Live Ready"}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 11.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Edit Profile",
                tint = activePrimary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        // Pinned Favorites Quick Connect Section (ONLY show when favorites actually exist)
        if (favoriteContacts.isNotEmpty() && searchQuery.isEmpty()) {
          item {
            Text(
              text = "PINNED FAVORITES",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.outline,
              letterSpacing = 1.sp,
              modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(favoriteContacts, key = { it.id }) { favorite ->
                QuickConnectCard(
                  contact = favorite,
                  activePrimary = activePrimary,
                  onCallClick = {
                    HapticsManager.trigger(context, HapticType.CALL_START)
                    onStartCall(favorite.name)
                  },
                  onToggleFavorite = {
                    HapticsManager.trigger(context, HapticType.FAVORITE_PIN)
                    contactsRepository.toggleFavorite(favorite.id)
                  }
                )
              }
            }
          }
        }

        // Directory Alphabet Sections - Grouped with Generous Breathing Space
        groupedContacts.forEach { (section, contacts) ->
          item(key = "section_header_$section") {
            Text(
              text = section,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.outline,
              modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
            )

            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
            ) {
              contacts.forEachIndexed { index, contact ->
                ContactRow(
                  contact = contact,
                  activePrimary = activePrimary,
                  onCallClick = {
                    HapticsManager.trigger(context, HapticType.CALL_START)
                    onStartCall(contact.name)
                  },
                  onToggleFavorite = {
                    HapticsManager.trigger(context, HapticType.FAVORITE_PIN)
                    contactsRepository.toggleFavorite(contact.id)
                  }
                )

                if (index < contacts.lastIndex) {
                  HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = Color(0xFFF1F5F9),
                    thickness = 1.dp
                  )
                }
              }
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(28.dp))
        }
      }

      // Alphabet Fast-Scroll Rail (Slim, subtle, non-intrusive)
      if (searchQuery.isEmpty()) {
        Column(
          modifier = Modifier
            .width(14.dp)
            .padding(vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
          alphabet.forEach { char ->
            val isPresent = lettersPresent.contains(char)
            Text(
              text = char,
              fontSize = 9.sp,
              fontWeight = if (isPresent) FontWeight.Bold else FontWeight.Normal,
              color = if (isPresent) activePrimary else Color(0xFFCBD5E1),
              modifier = Modifier
                .clickable {
                  HapticsManager.trigger(context, HapticType.SELECTION)
                  searchQuery = if (char == "#") "" else char
                }
                .padding(vertical = 1.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QuickConnectCard(
  contact: Contact,
  activePrimary: Color,
  onCallClick: () -> Unit,
  onToggleFavorite: () -> Unit
) {
  Box(
    modifier = Modifier
      .width(116.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
      .padding(vertical = 12.dp, horizontal = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(modifier = Modifier.size(46.dp)) {
        LiveVolumeAvatar(
          avatarUrl = contact.avatarUrl,
          initials = contact.initials,
          size = 46.dp,
          showOnlineBadge = true,
          isOnline = contact.isOnline
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = contact.name,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Text(
        text = if (contact.isSpatialReady) "3D Ready" else "Voice",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Quick 1-tap call
        Box(
          modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (contact.isSpatialReady) activePrimary else Color(0xFFF2F3FF))
            .clickable { onCallClick() },
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = if (contact.isSpatialReady) Icons.Default.ViewInAr else Icons.Default.Call,
              contentDescription = "Call",
              tint = if (contact.isSpatialReady) Color.White else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Call",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (contact.isSpatialReady) Color.White else MaterialTheme.colorScheme.onSurface
            )
          }
        }

        // Unpin button
        IconButton(
          onClick = onToggleFavorite,
          modifier = Modifier.size(30.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Unpin",
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun ContactRow(
  contact: Contact,
  activePrimary: Color,
  onCallClick: () -> Unit,
  onToggleFavorite: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onCallClick() }
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left: Avatar with Status
    LiveVolumeAvatar(
      avatarUrl = contact.avatarUrl,
      initials = contact.initials,
      size = 46.dp,
      showOnlineBadge = contact.isOnline,
      isOnline = contact.isOnline
    )

    Spacer(modifier = Modifier.width(14.dp))

    // Middle: Contact Details (Takes all available width with zero compression)
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = contact.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (contact.isSpatialReady) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFFEAEDFF))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "3D Ready",
              style = MaterialTheme.typography.labelSmall,
              color = activePrimary,
              fontWeight = FontWeight.Bold,
              fontSize = 9.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(2.dp))

      Text(
        text = contact.phone.ifEmpty { contact.status },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    Spacer(modifier = Modifier.width(10.dp))

    // Right Actions: Favorite Star + Single 3D/Voice Call Button (Clean & Breathable)
    IconButton(
      onClick = onToggleFavorite,
      modifier = Modifier.size(36.dp)
    ) {
      Icon(
        imageVector = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
        contentDescription = if (contact.isFavorite) "Unpin" else "Pin",
        tint = if (contact.isFavorite) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
        modifier = Modifier.size(20.dp)
      )
    }

    Spacer(modifier = Modifier.width(4.dp))

    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(if (contact.isSpatialReady) activePrimary else Color(0xFFF2F3FF))
        .clickable { onCallClick() },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (contact.isSpatialReady) Icons.Default.ViewInAr else Icons.Default.Call,
        contentDescription = if (contact.isSpatialReady) "3D Spatial Call" else "Call",
        tint = if (contact.isSpatialReady) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}
