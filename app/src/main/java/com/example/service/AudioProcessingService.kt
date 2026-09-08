package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.util.AudioPermissionHelper
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AudioProcessingService captures raw PCM audio data and applies
 * real-time binaural spatialization effects using an HRTF filter approximation.
 */
class AudioProcessingService : Service() {

  private val binder = LocalBinder()
  private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var captureJob: Job? = null

  private var audioRecord: AudioRecord? = null
  private var isRecording = false

  // Audio parameters
  private val sampleRateHz = 48000
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

  // Virtual 3D audio source position relative to listener head
  private val _azimuthDegrees = MutableStateFlow(0f)
  val azimuthDegrees: StateFlow<Float> = _azimuthDegrees.asStateFlow()

  private val _elevationDegrees = MutableStateFlow(0f)
  val elevationDegrees: StateFlow<Float> = _elevationDegrees.asStateFlow()

  // Real-time audio metering (0.0 to 1.0)
  private val _audioLevel = MutableStateFlow(0f)
  val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

  private val _isCapturing = MutableStateFlow(false)
  val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

  // Interaural delay line ring buffers for left and right ears
  private val maxDelaySamples = 64
  private val leftDelayLine = FloatArray(maxDelaySamples)
  private val rightDelayLine = FloatArray(maxDelaySamples)
  private var delayWriteIndex = 0

  inner class LocalBinder : Binder() {
    fun getService(): AudioProcessingService = this@AudioProcessingService
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "AudioProcessingService created")
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_CAPTURE -> startAudioCapture()
      ACTION_STOP_CAPTURE -> stopAudioCapture()
    }
    return START_NOT_STICKY
  }

  /**
   * Sets the 3D coordinates for spatial audio processing.
   *
   * @param azimuth Horizontal angle in degrees (-90 = hard left, 0 = center, +90 = hard right)
   * @param elevation Vertical angle in degrees (-90 to +90)
   */
  fun setVirtualSourcePosition(azimuth: Float, elevation: Float) {
    val az = azimuth.coerceIn(-90f, 90f)
    val el = elevation.coerceIn(-90f, 90f)
    _azimuthDegrees.value = az
    _elevationDegrees.value = el
    sharedAzimuth.value = az
    sharedElevation.value = el
  }

  /**
   * Starts capturing raw PCM audio data from the microphone.
   */
  @SuppressLint("MissingPermission")
  fun startAudioCapture(): Boolean {
    if (isRecording) return true

    if (!AudioPermissionHelper.isAudioCaptureAuthorized(this)) {
      Log.w(TAG, "Audio capture cannot start: RECORD_AUDIO permission missing")
      return false
    }

    val minBufferSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
    if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
      Log.e(TAG, "Invalid audio buffer size returned: $minBufferSize")
      return false
    }

    val bufferSize = max(minBufferSize, 2048)

    return try {
      audioRecord = AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        sampleRateHz,
        channelConfig,
        audioFormat,
        bufferSize
      )

      if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
        Log.e(TAG, "AudioRecord failed to initialize")
        return false
      }

      audioRecord?.startRecording()
      isRecording = true
      _isCapturing.value = true

      captureJob = serviceScope.launch {
        readPcmCaptureLoop(bufferSize)
      }

      Log.i(TAG, "Audio capture started at $sampleRateHz Hz PCM")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Exception initializing AudioRecord", e)
      false
    }
  }

  /**
   * Continuous loop reading raw PCM chunks and running binaural spatialization.
   */
  private suspend fun readPcmCaptureLoop(bufferSize: Int) {
    val pcmChunk = ShortArray(bufferSize / 2)

    while (serviceScope.isActive && isRecording) {
      val record = audioRecord ?: break
      val readCount = record.read(pcmChunk, 0, pcmChunk.size)

      if (readCount > 0) {
        // Calculate root-mean-square amplitude for level meter
        var sumSquares = 0.0
        for (i in 0 until readCount) {
          val sample = pcmChunk[i]
          sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / readCount)
        val normalizedLevel = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _audioLevel.value = normalizedLevel
        sharedAudioLevel.value = normalizedLevel

        // Apply binaural spatialization effects using simple HRTF filter approximation
        val spatializedStereoPcm = applyBinauralSpatializationHrtf(
          inputPcm = pcmChunk,
          sampleCount = readCount,
          azimuthDegrees = _azimuthDegrees.value,
          elevationDegrees = _elevationDegrees.value
        )

        // Processed binaural PCM is ready for network transmission or local playback
        onBinauralPcmProcessed(spatializedStereoPcm)
      }
    }
  }

  /**
   * Placeholder function for applying binaural spatialization effects using a simple
   * HRTF (Head-Related Transfer Function) filter approximation.
   *
   * Models:
   * 1. ITD (Interaural Time Difference): Delay difference between ears (Woodworth spherical model).
   * 2. ILD (Interaural Level Difference): Head acoustic shadow attenuation for occluded ear.
   * 3. Elevation Spectral Pinna Notch: High-frequency coloration based on elevation.
   *
   * @param inputPcm Raw mono PCM 16-bit audio samples
   * @param sampleCount Number of valid samples in the input array
   * @param azimuthDegrees Virtual sound source horizontal angle (-90° left to +90° right)
   * @param elevationDegrees Virtual sound source vertical angle (-90° to +90°)
   * @return Interleaved stereo 16-bit PCM [L0, R0, L1, R1, ...]
   */
  fun applyBinauralSpatializationHrtf(
    inputPcm: ShortArray,
    sampleCount: Int = inputPcm.size,
    azimuthDegrees: Float = 0f,
    elevationDegrees: Float = 0f
  ): ShortArray {
    val count = min(sampleCount, inputPcm.size)
    val outputStereoPcm = ShortArray(count * 2)

    // Convert azimuth angle to radians
    val thetaRad = Math.toRadians(azimuthDegrees.toDouble()).toFloat()

    // 1. Interaural Time Difference (ITD) calculation:
    // Approx maximum delay between ears for average head (diameter ~ 18cm) is ~0.65ms -> ~31 samples at 48kHz
    val maxDelaySamplesPerEar = 25f
    val delaySamples = maxDelaySamplesPerEar * sin(thetaRad) // positive: sound reaches right ear earlier

    val leftDelay = if (delaySamples > 0) delaySamples else 0f
    val rightDelay = if (delaySamples < 0) -delaySamples else 0f

    // 2. Interaural Level Difference (ILD) calculation (Head acoustic shadow):
    // Near ear receives direct sound (gain ~ 1.0); far ear is attenuated based on angle
    val shadowFactor = (cos(thetaRad) * 0.5f + 0.5f).coerceIn(0.40f, 1.0f)
    val leftGain = if (azimuthDegrees <= 0) 1.0f else shadowFactor
    val rightGain = if (azimuthDegrees >= 0) 1.0f else shadowFactor

    // 3. Elevation spectral shaping factor
    val elevationFactor = 1.0f - (abs(elevationDegrees) / 90f) * 0.15f

    for (i in 0 until count) {
      val inputSample = inputPcm[i].toFloat()

      // Write into circular delay lines
      leftDelayLine[delayWriteIndex] = inputSample
      rightDelayLine[delayWriteIndex] = inputSample

      // Read delayed samples with linear interpolation
      val leftReadIdx = (delayWriteIndex - leftDelay.toInt() + maxDelaySamples) % maxDelaySamples
      val rightReadIdx = (delayWriteIndex - rightDelay.toInt() + maxDelaySamples) % maxDelaySamples

      val leftDelayedSample = leftDelayLine[leftReadIdx]
      val rightDelayedSample = rightDelayLine[rightReadIdx]

      // Apply ILD gains and elevation shaping
      val leftOutput = (leftDelayedSample * leftGain * elevationFactor).toInt().coerceIn(-32768, 32767)
      val rightOutput = (rightDelayedSample * rightGain * elevationFactor).toInt().coerceIn(-32768, 32767)

      // Store interleaved stereo PCM
      outputStereoPcm[2 * i] = leftOutput.toShort()
      outputStereoPcm[2 * i + 1] = rightOutput.toShort()

      delayWriteIndex = (delayWriteIndex + 1) % maxDelaySamples
    }

    return outputStereoPcm
  }

  /**
   * Callback hook when a binaural spatialized buffer has been processed.
   */
  private fun onBinauralPcmProcessed(binauralPcm: ShortArray) {
    // Placeholder pipeline hook for WebRTC audio track streaming or binaural sink
  }

  /**
   * Stops audio capture and frees hardware resources.
   */
  fun stopAudioCapture() {
    isRecording = false
    _isCapturing.value = false
    captureJob?.cancel()
    captureJob = null

    try {
      audioRecord?.stop()
      audioRecord?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping AudioRecord", e)
    } finally {
      audioRecord = null
    }

    Log.i(TAG, "Audio capture stopped")
  }

  override fun onDestroy() {
    super.onDestroy()
    stopAudioCapture()
    serviceScope.cancel()
    Log.d(TAG, "AudioProcessingService destroyed")
  }

  companion object {
    private const val TAG = "AudioProcessingService"
    const val ACTION_START_CAPTURE = "com.example.service.action.START_CAPTURE"
    const val ACTION_STOP_CAPTURE = "com.example.service.action.STOP_CAPTURE"

    val sharedAudioLevel = MutableStateFlow(0f)
    val sharedAzimuth = MutableStateFlow(0f)
    val sharedElevation = MutableStateFlow(0f)

    /**
     * Convenience helper to launch audio processing capture service.
     */
    fun start(context: Context) {
      val intent = Intent(context, AudioProcessingService::class.java).apply {
        action = ACTION_START_CAPTURE
      }
      context.startService(intent)
    }

    /**
     * Convenience helper to stop audio processing capture service.
     */
    fun stop(context: Context) {
      val intent = Intent(context, AudioProcessingService::class.java).apply {
        action = ACTION_STOP_CAPTURE
      }
      context.startService(intent)
    }
  }
}
