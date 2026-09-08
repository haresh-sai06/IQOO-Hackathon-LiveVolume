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
    const val CHANNEL_ID = "livevolume_alerts_channel"
    const val PREFS_NAME = "livevolume_fcm_prefs"
    const val KEY_FCM_TOKEN = "fcm_device_token"

    fun getSavedToken(context: Context): String? {
      return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_FCM_TOKEN, null)
    }
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "New FCM Token received: $token")
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_FCM_TOKEN, token)
      .apply()
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

    val title = remoteMessage.notification?.title
      ?: remoteMessage.data["title"]
      ?: "LiveVolume Alert"

    val body = remoteMessage.notification?.body
      ?: remoteMessage.data["body"]
      ?: remoteMessage.data["message"]
      ?: "New spatial notification"

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
