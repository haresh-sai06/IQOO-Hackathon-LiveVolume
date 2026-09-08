package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity representing a persisted call history record.
 * Stores duration, timestamp, contact information, call type, and spatial status.
 */
@Entity(tableName = "call_history")
data class CallHistoryEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val contactName: String,
  val phoneNumber: String = "",
  val avatarUrl: String? = null,
  val initials: String = "",
  val callType: String = "3D Spatial",     // "3D Spatial", "Video", "Audio", "Missed"
  val direction: String = "Outgoing",      // "Incoming", "Outgoing", "Missed"
  val durationSeconds: Long = 0L,
  val durationFormatted: String = "0m 00s",
  val timestamp: Long = System.currentTimeMillis(),
  val timestampFormatted: String = "Just now",
  val period: String = "TODAY",            // "TODAY", "YESTERDAY", "EARLIER_THIS_WEEK"
  val isOnline: Boolean = false,
  val isSpatial: Boolean = true
)
