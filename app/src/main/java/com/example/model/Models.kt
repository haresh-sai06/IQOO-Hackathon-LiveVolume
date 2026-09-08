package com.example.model

enum class CallType {
  SPATIAL_3D,
  MISSED,
  VIDEO,
  AUDIO
}

enum class CallDirection {
  INCOMING,
  OUTGOING,
  MISSED
}

enum class CallPeriod {
  TODAY,
  YESTERDAY,
  EARLIER_THIS_WEEK
}

data class Contact(
  val id: String,
  val name: String,
  val initials: String,
  val phone: String,
  val status: String,
  val avatarUrl: String? = null,
  val isSpatialReady: Boolean = false,
  val isOnline: Boolean = false,
  val isFavorite: Boolean = false,
  val section: String = "A"
)

data class CallRecord(
  val id: String,
  val contactName: String,
  val initials: String,
  val avatarUrl: String? = null,
  val callType: CallType,
  val direction: CallDirection,
  val duration: String,
  val timestamp: String,
  val period: CallPeriod,
  val isOnline: Boolean = false
)

data class FaqItem(
  val id: String,
  val question: String,
  val answer: String
)

data class GuideItem(
  val id: String,
  val title: String,
  val summary: String,
  val category: String,
  val iconName: String
)

data class PrivacySection(
  val number: Int,
  val title: String,
  val content: String,
  val subItems: List<String> = emptyList()
)
