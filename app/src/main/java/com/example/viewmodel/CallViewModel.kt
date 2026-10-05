package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.call.AdaptiveVolumetricController
import com.example.call.CallLifecycle
import com.example.model.VolumetricMeshMode
import com.example.ui.theme.LiveError
import com.example.ui.theme.LiveSuccess
import com.example.util.AgoraManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Latency sentinel meaning "no RTC statistics received yet". */
const val UNKNOWN_LATENCY_MS = -1

/**
 * Connection states for real-time video call session.
 */
enum class ConnectionStatus(val label: String) {
  CONNECTING("Connecting..."),
  CONNECTED("Connected • Live Agora RTC"),
  RECONNECTING("Reconnecting..."),
  DISCONNECTED("Call Ended")
}

/**
 * Visual tier representing connection latency and signal strength.
 */
enum class ConnectionQualityLevel(
  val label: String,
  val color: Color,
  val bars: Int
) {
  EXCELLENT("Excellent", LiveSuccess, 4),               // < 45ms (Green)
  GOOD("Good", Color(0xFF16A34A), 3),                   // 45 - 85ms (Light Green)
  MODERATE("Fair", Color(0xFFF59E0B), 2),               // 85 - 150ms (Amber/Yellow)
  POOR("Poor", LiveError, 1)                            // > 150ms, unknown, or reconnecting (Red)
}

/**
 * Complete state representation for an ongoing video call with Agora RTC.
 *
 * All numeric telemetry starts at an explicit "unknown" value rather than a
 * plausible-looking fabricated number, so the UI can never show fake link
 * quality before the first real Agora statistics arrive.
 */
data class CallUiState(
  val callerName: String = "",
  val channelName: String = "",
  val isMuted: Boolean = false,
  val isCameraOn: Boolean = true,
  val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
  val isFrontCamera: Boolean = true,
  val isSpeakerOn: Boolean = true,
  val is3DMode: Boolean = false,
  val remotePointCloud: com.example.ml.PointCloud? = null,
  val elapsedSeconds: Long = 0L,
  val cameraPermissionGranted: Boolean = false,
  val audioPermissionGranted: Boolean = false,
  val latencyMs: Int = UNKNOWN_LATENCY_MS,
  val connectionQuality: ConnectionQualityLevel = ConnectionQualityLevel.POOR,
  val remoteUid: Int? = null,
  val isRemoteVideoMuted: Boolean = false,
  val meshMode: VolumetricMeshMode = VolumetricMeshMode.HOLOGRAPHIC_MESH,
  val depthIntensity: Float = 1.0f,
  val isGyroTrackingEnabled: Boolean = false,
  val audioLevel: Float = 0f,
  val azimuth: Float = 0f,
  val elevation: Float = 0f
)

class CallViewModel(application: Application) : AndroidViewModel(application) {

  val agoraManager = AgoraManager.getInstance(application)

  /** Single source of truth for every call-screen value. */
  private val _uiState = MutableStateFlow(CallUiState())
  val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

  private val _elapsedSeconds = MutableStateFlow(0L)
  val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

  // Real network telemetry, sourced from Agora RtcStats via RtcConnectionMonitor.
  private val _rttMs = MutableStateFlow(UNKNOWN_LATENCY_MS)
  val rttMs: StateFlow<Int> = _rttMs.asStateFlow()

  private val _packetLossPct = MutableStateFlow(0)
  val packetLossPct: StateFlow<Int> = _packetLossPct.asStateFlow()

  private val _callLifecycle = MutableStateFlow(CallLifecycle.IDLE)
  val callLifecycle: StateFlow<CallLifecycle> = _callLifecycle.asStateFlow()

  private val adaptiveController = AdaptiveVolumetricController()
  val volumetricBudget: StateFlow<AdaptiveVolumetricController.Budget> =
    adaptiveController.budget

  private val _elapsedDurationFormatted = MutableStateFlow("00:00")
  val elapsedDurationFormatted: StateFlow<String> = _elapsedDurationFormatted.asStateFlow()

  private var timerJob: Job? = null

  init {
    startCallTimer()

    viewModelScope.launch {
      agoraManager.remoteUid.collect { uid ->
        _uiState.update { it.copy(remoteUid = uid) }
      }
    }

    viewModelScope.launch {
      agoraManager.isRemoteVideoMuted.collect { muted ->
        _uiState.update { it.copy(isRemoteVideoMuted = muted) }
      }
    }

    viewModelScope.launch {
      agoraManager.remotePointCloud.collect { pc ->
        _uiState.update { it.copy(remotePointCloud = pc) }
      }
    }

    // RTC lifecycle -> call banner status (CONNECTING/CONNECTED/RECONNECTING/...)
    viewModelScope.launch {
      agoraManager.lifecycle.collect { lc ->
        _callLifecycle.value = lc
        val status = when (lc) {
          CallLifecycle.CONNECTING,
          CallLifecycle.OUTGOING_RINGING,
          CallLifecycle.INCOMING_RINGING -> ConnectionStatus.CONNECTING
          CallLifecycle.CONNECTED -> ConnectionStatus.CONNECTED
          CallLifecycle.RECONNECTING -> ConnectionStatus.RECONNECTING
          CallLifecycle.ENDED,
          CallLifecycle.IDLE -> ConnectionStatus.DISCONNECTED
          CallLifecycle.FAILED -> ConnectionStatus.RECONNECTING
        }
        // Don't stomp an explicit ENDED after leaveChannel races with FAILED.
        if (_uiState.value.connectionStatus != ConnectionStatus.DISCONNECTED ||
          status != ConnectionStatus.RECONNECTING
        ) {
          setConnectionStatus(status)
        }
      }
    }

    // REAL network stats -> latency pill, quality bars, adaptive volumetric budget.
    viewModelScope.launch {
      agoraManager.connectionMonitor.snapshot.collect { snap ->
        _rttMs.value = snap.rttMs
        _packetLossPct.value = snap.worstLossPct
        adaptiveController.onNetworkSnapshot(snap)
        val budget = adaptiveController.budget.value
        agoraManager.setVolumetricBudget(budget.maxPoints, budget.paused)
        if (snap.rttMs >= 0) {
          updateLatency(snap.rttMs)
        }
        // Prefer the remote Agora level when available; fall back to local mic meter.
        val meter = if (snap.remoteAudioLevel > 0.01f) snap.remoteAudioLevel
          else snap.localAudioLevel
        if (meter > 0.01f) {
          _uiState.update { it.copy(audioLevel = meter) }
        }
      }
    }
  }

  fun sendLocalPointCloud(pc: com.example.ml.PointCloud) {
    agoraManager.sendPointCloud(pc)
  }

  fun toggle3DMode() {
    _uiState.update { it.copy(is3DMode = !it.is3DMode) }
  }

  fun set3DMode(enabled: Boolean) {
    _uiState.update { it.copy(is3DMode = enabled) }
  }

  fun initializeCall(callerName: String, customChannel: String? = null) {
    val sanitized = callerName.lowercase().replace(Regex("[^a-z0-9]"), "")
    val channel = customChannel ?: if (sanitized.isNotBlank()) "livevolume_$sanitized" else "livevolume_room"

    _elapsedSeconds.value = 0L
    _elapsedDurationFormatted.value = "00:00"
    _uiState.update {
      it.copy(
        callerName = callerName,
        channelName = channel,
        connectionStatus = ConnectionStatus.CONNECTING,
        elapsedSeconds = 0L,
        latencyMs = UNKNOWN_LATENCY_MS,
        connectionQuality = ConnectionQualityLevel.POOR
      )
    }

    agoraManager.joinChannel(channel)
    com.example.service.CallForegroundService.start(getApplication(), callerName, true)
    startCallTimer()
  }

  /**
   * Coroutine-based timer updating elapsed call duration every second.
   */
  private fun startCallTimer() {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
      while (true) {
        delay(1000L)
        if (_uiState.value.connectionStatus == ConnectionStatus.CONNECTED) {
          val newSeconds = _uiState.value.elapsedSeconds + 1
          val mins = newSeconds / 60
          val secs = newSeconds % 60
          val formatted = String.format(java.util.Locale.US, "%02d:%02d", mins, secs)

          _elapsedSeconds.value = newSeconds
          _elapsedDurationFormatted.value = formatted
          _uiState.update { it.copy(elapsedSeconds = newSeconds) }
        }
      }
    }
  }

  /**
   * Real latency/quality update driven by Agora RtcStats (see RtcConnectionMonitor).
   * Keeps the legacy ConnectionQualityLevel UI contract intact.
   */
  fun updateLatency(latency: Int) {
    val quality = calculateQuality(latency, _uiState.value.connectionStatus)
    _uiState.update {
      it.copy(
        latencyMs = latency,
        connectionQuality = quality
      )
    }
  }

  private fun calculateQuality(latency: Int, status: ConnectionStatus): ConnectionQualityLevel {
    if (status == ConnectionStatus.RECONNECTING ||
      status == ConnectionStatus.DISCONNECTED ||
      latency < 0
    ) {
      return ConnectionQualityLevel.POOR
    }
    return when {
      latency < 45 -> ConnectionQualityLevel.EXCELLENT
      latency < 85 -> ConnectionQualityLevel.GOOD
      latency < 150 -> ConnectionQualityLevel.MODERATE
      else -> ConnectionQualityLevel.POOR
    }
  }

  /**
   * Toggles microphone mute state with Agora RTC audio publication.
   */
  fun toggleMute() {
    val newMute = !_uiState.value.isMuted
    _uiState.update { it.copy(isMuted = newMute) }
    agoraManager.muteLocalAudio(newMute)
  }

  /**
   * Toggles device camera on/off with Agora RTC video publication.
   */
  fun toggleCamera() {
    val newCam = !_uiState.value.isCameraOn
    _uiState.update { it.copy(isCameraOn = newCam) }
    agoraManager.enableLocalVideo(newCam)
  }

  /**
   * Switches between front and rear cameras via Agora RTC.
   */
  fun switchCamera() {
    val newFront = !_uiState.value.isFrontCamera
    _uiState.update { it.copy(isFrontCamera = newFront) }
    agoraManager.switchCamera()
  }

  /**
   * Toggles audio route between speakerphone and earpiece.
   */
  fun toggleSpeaker() {
    val newSpeaker = !_uiState.value.isSpeakerOn
    _uiState.update { it.copy(isSpeakerOn = newSpeaker) }
    agoraManager.enableSpeakerphone(newSpeaker)
  }

  fun setConnectionStatus(status: ConnectionStatus) {
    val quality = calculateQuality(_uiState.value.latencyMs, status)
    _uiState.update {
      it.copy(
        connectionStatus = status,
        connectionQuality = quality
      )
    }
  }

  fun updateOrientation(rotX: Float, rotY: Float) {
    val azimuth = (rotY * 4.5f).coerceIn(-90f, 90f)
    val elevation = (-rotX * 4.5f).coerceIn(-90f, 90f)
    _uiState.update {
      it.copy(
        azimuth = azimuth,
        elevation = elevation
      )
    }
  }

  fun updatePermissions(cameraGranted: Boolean, audioGranted: Boolean) {
    _uiState.update {
      it.copy(
        cameraPermissionGranted = cameraGranted,
        audioPermissionGranted = audioGranted
      )
    }
  }

  /**
   * Ends the call session, leaves the Agora channel, and logs call history.
   */
  fun endCall(repository: com.example.data.local.CallHistoryRepository? = null) {
    timerJob?.cancel()
    agoraManager.leaveChannel()
    try {
      com.example.service.CallForegroundService.stop(getApplication())
    } catch (_: Exception) {}

    val finalSeconds = _uiState.value.elapsedSeconds
    val caller = _uiState.value.callerName

    _uiState.update {
      it.copy(
        connectionStatus = ConnectionStatus.DISCONNECTED,
        connectionQuality = ConnectionQualityLevel.POOR
      )
    }

    if (repository != null && finalSeconds > 0 && caller.isNotBlank() &&
      !caller.contains("unknown", ignoreCase = true) &&
      !caller.equals("Live Contact", ignoreCase = true)
    ) {
      viewModelScope.launch {
        val mins = finalSeconds / 60
        val secs = finalSeconds % 60
        val durationFormatted = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        repository.insertCall(
          com.example.data.local.CallHistoryEntity(
            contactName = caller,
            phoneNumber = if (caller.contains("@") || caller.startsWith("+")) caller
              else "Room ${_uiState.value.channelName.ifBlank { "Direct" }}",
            avatarUrl = null,
            initials = caller.take(2).uppercase().ifBlank { "3D" },
            callType = if (_uiState.value.is3DMode) "3D Volumetric" else "Live Video",
            direction = "Outgoing",
            durationSeconds = finalSeconds,
            durationFormatted = durationFormatted,
            timestamp = System.currentTimeMillis(),
            timestampFormatted = "Just now",
            period = "TODAY",
            isOnline = true,
            isSpatial = _uiState.value.is3DMode,
            latencyMs = _uiState.value.latencyMs
          )
        )
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    agoraManager.leaveChannel()
    timerJob?.cancel()
    try {
      com.example.service.CallForegroundService.stop(getApplication())
    } catch (_: Exception) {}
  }
}
