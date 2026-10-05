package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Single source of truth for every runtime permission a real-time call needs.
 *
 * - CALL_CORE: camera + mic (blocking — CallScreen shows the denial card).
 * - CALL_FULL: + notifications (Android 13+) + bluetooth-connect (Android 12+)
 *   for headset routing. Non-blocking: call can start without them.
 */
object CallPermissions {

  val CALL_CORE: Array<String> = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
  )

  val CALL_FULL: Array<String> by lazy {
    val list = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      list.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      list.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    list.toTypedArray()
  }

  fun hasCore(context: Context): Boolean = CALL_CORE.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }

  fun missingPermissions(context: Context): List<String> = CALL_FULL.filter {
    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
  }

  fun grantedFromResult(granted: Map<String, Boolean>, permission: String, fallback: Boolean): Boolean =
    granted[permission] ?: fallback
}
