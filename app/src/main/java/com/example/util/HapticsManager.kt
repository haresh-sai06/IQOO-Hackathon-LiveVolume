package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tactile & Vibration Haptic Feedback Engine for LiveVolume.
 * Implements native Android equivalents of the React tactile wave profiles.
 */
enum class HapticType {
  CALL_START,
  CALL_END,
  FAVORITE_PIN,
  MODE_SWITCH,
  TAB_CHANGE,
  OPTICS_TOGGLE,
  SELECTION,
  THEME_CHANGE,
  LIGHT,
  MEDIUM,
  HEAVY,
  SUCCESS
}

object HapticsManager {
  fun trigger(context: Context, type: HapticType = HapticType.LIGHT) {
    try {
      val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      } ?: return

      if (!vibrator.hasVibrator()) return

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = when (type) {
          HapticType.CALL_START -> {
            val timings = longArrayOf(0, 40, 50, 70, 40, 100)
            val amplitudes = intArrayOf(0, 120, 0, 180, 0, 255)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
          }
          HapticType.CALL_END -> {
            val timings = longArrayOf(0, 80, 50, 60)
            val amplitudes = intArrayOf(0, 220, 0, 180)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
          }
          HapticType.FAVORITE_PIN -> {
            val timings = longArrayOf(0, 25, 40, 35)
            val amplitudes = intArrayOf(0, 100, 0, 160)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
          }
          HapticType.MODE_SWITCH -> {
            val timings = longArrayOf(0, 30, 30, 40)
            val amplitudes = intArrayOf(0, 140, 0, 200)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
          }
          HapticType.TAB_CHANGE -> VibrationEffect.createOneShot(18, 90)
          HapticType.OPTICS_TOGGLE -> {
            val timings = longArrayOf(0, 20, 30, 25)
            val amplitudes = intArrayOf(0, 120, 0, 180)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
          }
          HapticType.SELECTION -> VibrationEffect.createOneShot(12, 70)
          HapticType.THEME_CHANGE -> {
            val timings = longArrayOf(0, 30, 40, 60)
            val amplitudes = intArrayOf(0, 100, 0, 200)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
          }
          HapticType.HEAVY -> VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
          HapticType.MEDIUM -> VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
          HapticType.LIGHT -> VibrationEffect.createOneShot(18, 80)
          HapticType.SUCCESS -> {
            val timings = longArrayOf(0, 30, 40, 50)
            val amplitudes = intArrayOf(0, 100, 0, 220)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
          }
        }
        vibrator.vibrate(effect)
      } else {
        @Suppress("DEPRECATION")
        when (type) {
          HapticType.CALL_START -> vibrator.vibrate(longArrayOf(0, 40, 50, 70, 40, 100), -1)
          HapticType.CALL_END -> vibrator.vibrate(longArrayOf(0, 80, 50, 60), -1)
          HapticType.FAVORITE_PIN -> vibrator.vibrate(longArrayOf(0, 25, 40, 35), -1)
          HapticType.MODE_SWITCH -> vibrator.vibrate(longArrayOf(0, 30, 30, 40), -1)
          HapticType.TAB_CHANGE -> vibrator.vibrate(18)
          HapticType.OPTICS_TOGGLE -> vibrator.vibrate(longArrayOf(0, 20, 30, 25), -1)
          HapticType.SELECTION -> vibrator.vibrate(12)
          HapticType.THEME_CHANGE -> vibrator.vibrate(longArrayOf(0, 30, 40, 60), -1)
          HapticType.HEAVY -> vibrator.vibrate(60)
          HapticType.MEDIUM -> vibrator.vibrate(35)
          HapticType.LIGHT -> vibrator.vibrate(18)
          HapticType.SUCCESS -> vibrator.vibrate(longArrayOf(0, 30, 40, 50), -1)
        }
      }
    } catch (_: Exception) {
      // Gracefully ignore on devices without vibration hardware
    }
  }
}
