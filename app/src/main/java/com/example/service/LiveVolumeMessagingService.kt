package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.util.InAppNotificationManager
import com.example.util.InAppNotificationType
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class LiveVolumeMessagingService : FirebaseMessagingService() {

  companion object {
    private const val TAG = "LiveVolumeFCM"
    const val CHANNEL_ID = "livevolume_calls_channel"
    const val PREFS_NAME = "livevolume_fcm_prefs"
    const val KEY_FCM_TOKEN = "fcm_device_token"

    const val ACTION_ANSWER_CALL = "com.example.livevolume.ACTION_ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.example.livevolume.ACTION_DECLINE_CALL"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_CHANNEL_NAME = "extra_channel_name"

    fun getSavedToken(context: Context): String? {
      return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_FCM_TOKEN, null)
    }

    /** Dismisses the ringing notification once the user answers/declines elsewhere. */
    fun cancelIncomingCallNotification(context: Context, callId: String) {
      try {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
          .cancel(callId.hashCode())
      } catch (_: Exception) {}
    }
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "New FCM Token received: $token")
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_FCM_TOKEN, token)
      .apply()
    // Sync to Firestore so a token server / Cloud Function can target this device.
    try {
      val repo = com.example.data.repository.CallSignalingRepository.getInstance(applicationContext)
      val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
      if (uid.isNotBlank()) repo.syncFcmToken(uid, token)
    } catch (e: Exception) {
      Log.w(TAG, "FCM token sync skipped: ${e.message}")
    }
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

    val data = remoteMessage.data
    val channelName = data["channelName"] ?: ""
    val callId = data["callId"] ?: "call_${System.currentTimeMillis()}"
    val callerName = data["callerName"] ?: data["title"] ?: "LiveVolume Peer"
    val isCallInvite = data["type"] == "call_invite" || channelName.isNotBlank() ||
        remoteMessage.notification?.title?.contains("call", ignoreCase = true) == true

    if (isCallInvite) {
      val resolvedChannel = channelName.ifBlank { "livevolume_" + callerName.lowercase().replace(Regex("[^a-z0-9]"), "") }
      // 1. Notify signaling repository
      com.example.data.repository.CallSignalingRepository.getInstance(applicationContext)
        .onIncomingCallInviteReceived(callId, callerName, resolvedChannel)

      // 2. Post foreground banner
      InAppNotificationManager.postNotification(
        title = "Incoming Video Call",
        message = "$callerName is calling you on room $resolvedChannel",
        type = InAppNotificationType.CALL_INCOMING,
        context = applicationContext
      )

      // 3. Show heads-up notification with Answer and Decline
      showIncomingCallNotification(callId, callerName, resolvedChannel)
      return
    }

    val title = remoteMessage.notification?.title
      ?: data["title"]
      ?: "LiveVolume Alert"

    val body = remoteMessage.notification?.body
      ?: data["body"]
      ?: data["message"]
      ?: "New notification"

    val type = when {
      title.contains("missed", ignoreCase = true) || body.contains("missed", ignoreCase = true) ->
        InAppNotificationType.CALL_MISSED
      title.contains("call", ignoreCase = true) || body.contains("calling", ignoreCase = true) ->
        InAppNotificationType.CALL_INCOMING
      title.contains("spatial", ignoreCase = true) || title.contains("audio", ignoreCase = true) ->
        InAppNotificationType.SPATIAL_AUDIO
      title.contains("engine", ignoreCase = true) || title.contains("ready", ignoreCase = true) ->
        InAppNotificationType.SYSTEM_ENGINE
      else ->
        InAppNotificationType.GENERAL
    }

    // 1. Post to foreground In-App Banner system
    InAppNotificationManager.postNotification(
      title = title,
      message = body,
      type = type,
      context = applicationContext
    )

    // 2. Post system tray notification for background/lock-screen visibility
    showSystemNotification(title, body)
  }

  private fun showIncomingCallNotification(callId: String, callerName: String, channelName: String) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "LiveVolume Video Calls",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "High-priority ringing alerts for incoming video calls"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 500, 250, 500)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
      }
      notificationManager.createNotificationChannel(channel)
    }

    // Full-screen intent: shows the incoming-call UI even on the lock screen
    // (requires USE_FULL_SCREEN_INTENT on Android 14+).
    val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
      action = ACTION_ANSWER_CALL
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_CALL_ID, callId)
      putExtra(EXTRA_CALLER_NAME, callerName)
      putExtra(EXTRA_CHANNEL_NAME, channelName)
    }
    val fullScreenPi = PendingIntent.getActivity(
      this,
      100,
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Answer Intent (unique requestCode per callId so concurrent invites don't collide)
    val answerIntent = Intent(this, MainActivity::class.java).apply {
      action = ACTION_ANSWER_CALL
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_CALL_ID, callId)
      putExtra(EXTRA_CALLER_NAME, callerName)
      putExtra(EXTRA_CHANNEL_NAME, channelName)
    }
    val answerPendingIntent = PendingIntent.getActivity(
      this,
      1000 + (callId.hashCode() and 0x7FFF),
      answerIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Decline Intent (also dismisses the ringing notification via service hook)
    val declineIntent = Intent(this, MainActivity::class.java).apply {
      action = ACTION_DECLINE_CALL
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_CALL_ID, callId)
    }
    val declinePendingIntent = PendingIntent.getActivity(
      this,
      2000 + (callId.hashCode() and 0x7FFF),
      declineIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Incoming Video Call")
      .setContentText("$callerName is calling you...")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setAutoCancel(true)
      .setOngoing(true)
      .setFullScreenIntent(fullScreenPi, true)
      .setContentIntent(answerPendingIntent)
      .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
      .build()

    notificationManager.notify(callId.hashCode(), notification)
  }

  private fun showSystemNotification(title: String, body: String) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "LiveVolume Calls & Alerts",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Push notifications for incoming 3D calls and spatial alerts"
        enableVibration(true)
      }
      notificationManager.createNotificationChannel(channel)
    }

    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(body)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(pendingIntent)
      .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
  }
}
