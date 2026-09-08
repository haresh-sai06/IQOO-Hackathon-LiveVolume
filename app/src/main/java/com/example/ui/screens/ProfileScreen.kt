package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AuthRepository
import com.example.model.UserProfile
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LiveError
import com.example.ui.theme.LiveSuccess
import com.example.ui.theme.ThemeManager
import com.example.util.HapticType
import com.example.util.HapticsManager

@Composable
fun ProfileScreen(
  onBack: () -> Unit,
  onLogOut: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val authRepository = remember { AuthRepository.getInstance(context) }
  val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()
  val activePrimary = ThemeManager.currentTheme.primaryContainer

  var name by remember(currentUser) { mutableStateOf(currentUser?.name ?: "") }
  var email by remember(currentUser) { mutableStateOf(currentUser?.email ?: "") }
  var phone by remember(currentUser) { mutableStateOf(currentUser?.phone ?: "") }
  var status by remember(currentUser) { mutableStateOf(currentUser?.status ?: "3D Live Ready") }
  var isSpatialReady by remember(currentUser) { mutableStateOf(currentUser?.isSpatialReady ?: true) }

  var savedSuccess by remember { mutableStateOf(false) }

  val computedInitials = remember(name) {
    name.trim().split(" ")
      .filter { it.isNotEmpty() }
      .take(2)
      .map { it.first().uppercase() }
      .joinToString("")
      .ifEmpty { "ME" }
  }

  val handleSave = {
    if (name.isNotBlank()) {
      val updated = (currentUser ?: UserProfile(
        id = "user_${System.currentTimeMillis()}",
        name = name.trim(),
        email = email.trim(),
        phone = phone.trim()
      )).copy(
        name = name.trim(),
        email = email.trim(),
        phone = phone.trim(),
        status = status.trim().ifBlank { "3D Live Ready" },
        isSpatialReady = isSpatialReady
      )
      authRepository.updateProfile(updated)
      HapticsManager.trigger(context, HapticType.SUCCESS)
      savedSuccess = true
      Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
    } else {
      Toast.makeText(context, "Full name cannot be empty", Toast.LENGTH_SHORT).show()
    }
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .verticalScroll(scrollState)
      .padding(horizontal = 20.dp, vertical = 16.dp)
      .testTag("profile_screen")
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("profile_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Edit Profile",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      IconButton(
        onClick = handleSave,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(activePrimary.copy(alpha = 0.12f))
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Save",
          tint = activePrimary,
          modifier = Modifier.size(20.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Avatar Centerpiece
    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.Center
    ) {
      Box(contentAlignment = Alignment.BottomEnd) {
        LiveVolumeAvatar(
          avatarUrl = currentUser?.avatarUrl,
          initials = computedInitials,
          size = 96.dp,
          showOnlineBadge = true,
          isOnline = true
        )

        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(activePrimary)
            .border(2.dp, Color.White, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Change photo",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // User badge
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(99.dp))
          .background(Color(0xFFEAEDFF))
          .padding(horizontal = 12.dp, vertical = 4.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = null,
            tint = activePrimary,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "3D Spatial ID Verified",
            style = MaterialTheme.typography.labelSmall,
            color = activePrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Form Fields
    Text(
      text = "PERSONAL INFORMATION",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.outline,
      letterSpacing = 1.sp,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Full Name
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Full Name") },
        leadingIcon = {
          Icon(Icons.Default.Person, contentDescription = null, tint = activePrimary)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = activePrimary,
          unfocusedBorderColor = Color(0xFFE2E7FF)
        )
      )

      // Email Address
      OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email Address") },
        leadingIcon = {
          Icon(Icons.Default.Email, contentDescription = null, tint = activePrimary)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = activePrimary,
          unfocusedBorderColor = Color(0xFFE2E7FF)
        )
      )

      // Phone Number
      OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Phone Number") },
        placeholder = { Text("+1 (555) 000-0000") },
        leadingIcon = {
          Icon(Icons.Default.Phone, contentDescription = null, tint = activePrimary)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = activePrimary,
          unfocusedBorderColor = Color(0xFFE2E7FF)
        )
      )

      // Status / Bio
      OutlinedTextField(
        value = status,
        onValueChange = { status = it },
        label = { Text("Status & Availability") },
        placeholder = { Text("e.g. 3D Live Ready") },
        leadingIcon = {
          Icon(Icons.Default.ShortText, contentDescription = null, tint = activePrimary)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = activePrimary,
          unfocusedBorderColor = Color(0xFFE2E7FF)
        )
      )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Spatial Settings Card
    Text(
      text = "CALL PREFERENCES",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.outline,
      letterSpacing = 1.sp,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(18.dp))
        .padding(16.dp),
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
            imageVector = Icons.Default.ViewInAr,
            contentDescription = null,
            tint = activePrimary,
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
          Text(
            text = "Enable 3D Volumetric Depth",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Allow others to position your video in spatial 3D",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Switch(
        checked = isSpatialReady,
        onCheckedChange = { isSpatialReady = it },
        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = activePrimary)
      )
    }

    Spacer(modifier = Modifier.height(28.dp))

    // Save Changes Button
    Button(
      onClick = handleSave,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("save_profile_button"),
      shape = RoundedCornerShape(14.dp),
      colors = ButtonDefaults.buttonColors(containerColor = activePrimary)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Save Changes",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Log Out Button
    OutlinedButton(
      onClick = {
        HapticsManager.trigger(context, HapticType.CALL_END)
        onLogOut()
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("profile_logout_button"),
      shape = RoundedCornerShape(14.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveError)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Log Out of LiveVolume",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}
