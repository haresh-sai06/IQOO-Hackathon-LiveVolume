package com.example.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class InAppNotificationType {
  CALL_MISSED,
  CALL_INCOMING,
  SPATIAL_AUDIO,
  SYSTEM_ENGINE,
  GENERAL
}

data class InAppNotification(
  val id: String = System.currentTimeMillis().toString(),
  val title: String,
  val message: String,
  val type: InAppNotificationType = InAppNotificationType.GENERAL,
  val timestamp: Long = System.currentTimeMillis()
)

object InAppNotificationManager {

  private val _notification = MutableStateFlow<InAppNotification?>(null)
  val currentNotification: StateFlow<InAppNotification?> = _notification.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.Main)
  private var autoDismissJob: Job? = null

  fun postNotification(
    title: String,
    message: String,
    type: InAppNotificationType = InAppNotificationType.GENERAL,
    context: Context? = null
  ) {
    val notification = InAppNotification(
      title = title,
      message = message,
      type = type
    )

    autoDismissJob?.cancel()
    _notification.value = notification

    // Trigger gentle haptic feedback if context is provided
    if (context != null) {
      HapticsManager.trigger(context, HapticType.LIGHT)
    }

    // Auto-dismiss after 4.5 seconds
    autoDismissJob = scope.launch {
      delay(4500)
      if (_notification.value?.id == notification.id) {
        _notification.value = null
      }
    }
  }

  fun dismiss() {
    autoDismissJob?.cancel()
    _notification.value = null
  }
}
