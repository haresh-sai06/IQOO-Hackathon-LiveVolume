package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages phone orientation sensors to provide smooth, natural head-tracking
 * allowing users to look around their caller by tilting their device in 3D space.
 */
class DeviceSensorManager(context: Context) : SensorEventListener {

  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

  private val _pitch = MutableStateFlow(0f)
  val pitch: StateFlow<Float> = _pitch.asStateFlow()

  private val _roll = MutableStateFlow(0f)
  val roll: StateFlow<Float> = _roll.asStateFlow()

  private var isListening = false

  // Exponential moving average filter weight for smooth, jitter-free rotation
  private val alpha = 0.15f
  private var filteredPitch = 0f
  private var filteredRoll = 0f

  fun startTracking(): Boolean {
    if (isListening || sensorManager == null || rotationSensor == null) return false
    isListening = sensorManager.registerListener(
      this,
      rotationSensor,
      SensorManager.SENSOR_DELAY_GAME
    )
    return isListening
  }

  fun stopTracking() {
    if (!isListening) return
    sensorManager?.unregisterListener(this)
    isListening = false
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (event == null) return

    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
      val rotationMatrix = FloatArray(9)
      SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

      val orientationValues = FloatArray(3)
      SensorManager.getOrientation(rotationMatrix, orientationValues)

      // Pitch is orientationValues[1], Roll is orientationValues[2]
      val rawPitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
      val rawRoll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()

      // Normalize tilt around normal holding position (~45° pitch)
      val targetPitch = (rawPitch - 45f).coerceIn(-20f, 20f)
      val targetRoll = (-rawRoll).coerceIn(-25f, 25f)

      filteredPitch = filteredPitch + alpha * (targetPitch - filteredPitch)
      filteredRoll = filteredRoll + alpha * (targetRoll - filteredRoll)

      _pitch.value = filteredPitch
      _roll.value = filteredRoll
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // No-op
  }
}
