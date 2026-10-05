package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Keeps an active voice/video call alive when the app is backgrounded.
 *
 * Production notes:
 * - Uses FOREGROUND_SERVICE_TYPE_MICROPHONE (+ CAMERA when video is on) on
 *   Android 10+/14+ so the OS doesn't kill the mic/camera mid-call.
 * - Ongoing (non-dismissible) notification with a Hang-up action.
 * - Started from CallScreen/CallViewModel on join, stopped on leave.
 */
class CallForegroundService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        val caller = intent.getStringExtra(EXTRA_CALLER) ?: "LiveVolume call"
        val videoOn = intent.getBooleanExtra(EXTRA_VIDEO_ON, true)
        startAsForeground(caller, videoOn)
      }
      ACTION_STOP -> {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  private fun startAsForeground(caller: String, videoOn: Boolean) {
    val notification = buildNotification(caller)
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        var fgsType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (videoOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (Build.VERSION.SDK_INT >= 34) {
          startForeground(NOTIF_ID, notification, fgsType)
        } else {
          @Suppress("DEPRECATION")
          startForeground(NOTIF_ID, notification)
        }
      } else {
        startForeground(NOTIF_ID, notification)
      }
    } catch (e: Exception) {
      Log.w(TAG, "startForeground failed: ${e.message}")
      // Fallback: still post the notification so the user can return to the call.
      (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
        ?.notify(NOTIF_ID, notification)
    }
  }

  private fun buildNotification(caller: String): Notification {
    val openIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val openPi = PendingIntent.getActivity(
      this, 201, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val hangupIntent = Intent(this, CallForegroundService::class.java).apply {
      action = ACTION_STOP
    }
    val hangupPi = PendingIntent.getService(
      this, 202, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("LiveVolume call in progress")
      .setContentText(caller)
      .setOngoing(true)
      .setContentIntent(openPi)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hang up", hangupPi)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .build()
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID, "LiveVolume Active Call", NotificationManager.IMPORTANCE_LOW
      ).apply { description = "Keeps the current call alive in the background" }
      (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
        ?.createNotificationChannel(channel)
    }
  }

  companion object {
    private const val TAG = "CallFgService"
    const val CHANNEL_ID = "livevolume_active_call"
    const val NOTIF_ID = 4201
    const val ACTION_START = "com.example.livevolume.callfg.START"
    const val ACTION_STOP = "com.example.livevolume.callfg.STOP"
    const val EXTRA_CALLER = "extra_caller"
    const val EXTRA_VIDEO_ON = "extra_video_on"

    fun start(context: Context, caller: String, videoOn: Boolean) {
      val intent = Intent(context, CallForegroundService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_CALLER, caller)
        putExtra(EXTRA_VIDEO_ON, videoOn)
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        Log.w(TAG, "start failed: ${e.message}")
      }
    }

    fun stop(context: Context) {
      try {
        context.startService(
          Intent(context, CallForegroundService::class.java).apply { action = ACTION_STOP }
        )
      } catch (e: Exception) {
        Log.w(TAG, "stop failed: ${e.message}")
      }
    }
  }
}
