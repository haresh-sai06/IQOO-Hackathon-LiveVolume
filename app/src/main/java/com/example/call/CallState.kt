package com.example.call

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LiveError
import com.example.ui.theme.LiveSuccess

/**
 * Production-grade call session state machine.
 *
 * Replaces ad-hoc Boolean flags with a single source of truth that mirrors
 * the Agora connection lifecycle + Firestore handshake state.
 */
enum class CallLifecycle {
  IDLE,
  OUTGOING_RINGING, // caller: invite written, waiting for callee
  INCOMING_RINGING, // callee: invite received, not yet answered
  CONNECTING,       // RTC joinChannel in flight
  CONNECTED,        // RTC joined + (remote joined || waiting)
  RECONNECTING,     // RTC connection interrupted, retrying
  ENDED,
  FAILED
}

enum class CallFailureReason {
  NONE,
  MISSING_APP_ID,
  TOKEN_EXPIRED,
  NETWORK_UNREACHABLE,
  PEER_DECLINED,
  PEER_TIMEOUT,
  ENGINE_ERROR
}

/**
 * Network quality tier derived from REAL Agora stats (RTT + loss + Agora
 * native quality), not simulated jitter. Thresholds follow Agora guidance:
 * excellent <80ms rtt & <2% loss, good <150ms & <5%, fair <300ms & <10%.
 */
enum class NetworkQualityTier(
  val label: String,
  val color: Color,
  val bars: Int
) {
  EXCELLENT("Excellent", LiveSuccess, 4),
  GOOD("Good", Color(0xFF16A34A), 3),
  FAIR("Fair", Color(0xFFF59E0B), 2),
  POOR("Poor", LiveError, 1),
  OFFLINE("Offline", LiveError, 0);

  companion object {
    fun fromStats(rttMs: Int, lossPct: Int, agoraQuality: Int?): NetworkQualityTier {
      if (rttMs < 0) return OFFLINE
      // Map Agora NETWORK_QUALITY (0 unknown, 1-2 excellent, 3-4 good/fair, 5-6 poor)
      val agoraHint = when (agoraQuality) {
        1, 2 -> EXCELLENT
        3 -> GOOD
        4 -> FAIR
        5, 6 -> POOR
        else -> null
      }
      val statTier = when {
        rttMs <= 80 && lossPct <= 2 -> EXCELLENT
        rttMs <= 150 && lossPct <= 5 -> GOOD
        rttMs <= 300 && lossPct <= 10 -> FAIR
        else -> POOR
      }
      // Take the worse of the two signals to avoid false-green UI.
      // Ordinal order runs EXCELLENT(0) -> POOR(4), so the larger ordinal is worse.
      if (agoraHint == null) return statTier
      return if (agoraHint.ordinal >= statTier.ordinal) agoraHint else statTier
    }
  }
}

data class RtcNetworkSnapshot(
  val rttMs: Int = -1,
  val txLossPct: Int = 0,
  val rxLossPct: Int = 0,
  val agoraTxQuality: Int? = null,
  val agoraRxQuality: Int? = null,
  val remoteAudioSilent: Boolean = false,
  val localAudioLevel: Float = 0f,
  val remoteAudioLevel: Float = 0f,
  val lastStatsAtMs: Long = 0L
) {
  val worstLossPct: Int get() = maxOf(txLossPct, rxLossPct)
  val tier: NetworkQualityTier
    get() = NetworkQualityTier.fromStats(
      rttMs,
      worstLossPct,
      agoraTxQuality?.let { tx -> agoraRxQuality?.let { rx -> maxOf(tx, rx) } ?: tx }
    )
}
