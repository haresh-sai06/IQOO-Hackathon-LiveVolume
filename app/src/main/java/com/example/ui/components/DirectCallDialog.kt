package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Contact
import com.example.model.UserProfile
import com.example.ui.theme.LivePrimaryContainer
import com.example.util.HapticType
import com.example.util.HapticsManager
import kotlin.math.absoluteValue

/**
 * Modern, interactive Direct Call & Room Code Bottom Sheet.
 * Allows connecting to another phone by entering a shared Room Code,
 * or by calling a saved contact directly with real-time 3D volumetric streaming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectCallDialog(
  currentUser: UserProfile?,
  contacts: List<Contact> = emptyList(),
  onDismiss: () -> Unit,
  onLaunchCall: (displayName: String, channelName: String) -> Unit
) {
  val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val context = LocalContext.current
  var roomInput by remember { mutableStateOf("") }

  // Generate a clean personal room code derived from the user's name/ID
  val myPersonalRoomCode = remember(currentUser) {
    val cleanName = currentUser?.name?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.ifBlank { "user" } ?: "user"
    val hashSuffix = (currentUser?.id ?: "me").hashCode().absoluteValue % 9000 + 1000
    "room_${cleanName.take(6)}_$hashSuffix"
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 36.dp)
    ) {
      // Header with icon
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(LivePrimaryContainer.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.ViewInAr,
            contentDescription = null,
            tint = LivePrimaryContainer,
            modifier = Modifier.size(24.dp)
          )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
          Text(
            text = "Direct 3D Spatial Call",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Connect with any peer using a shared Room Code",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Personal Room Code Card (Share with another phone)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFFF1F5F9))
          .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "YOUR SHAREABLE ROOM CODE",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = LivePrimaryContainer,
              letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = myPersonalRoomCode,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF0F172A)
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("LiveVolume Room Code", myPersonalRoomCode)
                clipboard.setPrimaryClip(clip)
                HapticsManager.trigger(context, HapticType.SELECTION)
                Toast.makeText(context, "Room code copied to clipboard!", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White)
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy Room Code",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
              onClick = {
                HapticsManager.trigger(context, HapticType.CALL_START)
                onDismiss()
                onLaunchCall(
                  currentUser?.name ?: "My Room",
                  "livevolume_$myPersonalRoomCode"
                )
              },
              shape = RoundedCornerShape(99.dp),
              colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer),
              modifier = Modifier.height(36.dp)
            ) {
              Text(
                text = "Host",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Room Input Field
      Text(
        text = "Join or Call Peer Room",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedTextField(
        value = roomInput,
        onValueChange = { roomInput = it },
        placeholder = { Text("Enter room code (e.g. 1001 or room_alex)") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.MeetingRoom,
            contentDescription = null,
            tint = LivePrimaryContainer
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("direct_call_room_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = LivePrimaryContainer,
          unfocusedBorderColor = Color(0xFFCBD5E1)
        ),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Connect Button
      Button(
        onClick = {
          val cleanRoom = roomInput.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
          if (cleanRoom.isBlank()) {
            Toast.makeText(context, "Please enter a valid room code", Toast.LENGTH_SHORT).show()
            return@Button
          }
          HapticsManager.trigger(context, HapticType.CALL_START)
          onDismiss()
          val targetChannel = if (cleanRoom.startsWith("livevolume_")) cleanRoom else "livevolume_$cleanRoom"
          onLaunchCall("Room ${cleanRoom.replace("livevolume_", "").replace("room_", "")}", targetChannel)
        },
        enabled = roomInput.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("direct_call_connect_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = LivePrimaryContainer,
          disabledContainerColor = Color(0xFFE2E8F0)
        )
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.ViewInAr,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Connect in 3D Call",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Quick Saved Contacts section (if any available)
      if (contacts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFFE2E8F0))
        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "Or Call Saved Contact",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height((contacts.size * 56).coerceAtMost(180).dp)
        ) {
          items(contacts.take(5)) { contact ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  HapticsManager.trigger(context, HapticType.CALL_START)
                  onDismiss()
                  val sanitized = contact.id.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank {
                    contact.name.lowercase().replace(Regex("[^a-z0-9]"), "")
                  }
                  onLaunchCall(contact.name, "livevolume_$sanitized")
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LivePrimaryContainer.copy(alpha = 0.1f)),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = contact.initials,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = LivePrimaryContainer
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = if (contact.isSpatialReady) "3D Spatial Ready" else contact.phone,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Icon(
                imageVector = Icons.AutoMirrored.Filled.CallMade,
                contentDescription = "Call",
                tint = LivePrimaryContainer,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }
    }
  }
}
