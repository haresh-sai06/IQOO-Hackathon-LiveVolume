package com.example.call

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aggregates raw Agora callbacks (RtcStats, NetworkQuality, volume indication)
 * into a debounced [RtcNetworkSnapshot] the UI can render directly.
 *
 * Production rules:
 * - Only emits when values actually change (avoids recomposition storms).
 * - Marks stats stale if no RtcStats for > 5s (shows OFFLINE instead of frozen green).
 * - All methods are thread-safe; Agora callbacks arrive on native threads.
 */
class RtcConnectionMonitor {

  private val _snapshot = MutableStateFlow(RtcNetworkSnapshot())
  val snapshot: StateFlow<RtcNetworkSnapshot> = _snapshot.asStateFlow()

  private val lock = Any()
  private var lastRtt = -1
  private var lastTxLoss = 0
  private var lastRxLoss = 0
  private var lastTxQuality: Int? = null
  private var lastRxQuality: Int? = null
  private var lastStatsAt = 0L

  fun onRtcStats(rttMs: Int, txLoss: Int, rxLoss: Int) {
    val now = System.currentTimeMillis()
    var changed = false
    synchronized(lock) {
      // Clamp absurd SDK spikes (e.g. 9999 on join) so UI doesn't flash red.
      val rtt = rttMs.coerceIn(0, 2000)
      val tx = txLoss.coerceIn(0, 100)
      val rx = rxLoss.coerceIn(0, 100)
      changed = (rtt != lastRtt || tx != lastTxLoss || rx != lastRxLoss)
      lastRtt = rtt
      lastTxLoss = tx
      lastRxLoss = rx
      lastStatsAt = now
    }
    if (changed) publish()
  }

  fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
    var changed = false
    synchronized(lock) {
      if (uid == 0) {
        changed = (lastTxQuality != txQuality)
        lastTxQuality = txQuality
      } else {
        changed = (lastRxQuality != rxQuality)
        lastRxQuality = rxQuality
      }
    }
    if (changed) publish()
  }

  fun onAudioVolumes(localLevel: Float, remoteLevel: Float, remoteSilent: Boolean) {
    val cur = _snapshot.value
    if (kotlin.math.abs(cur.localAudioLevel - localLevel) < 0.02f &&
      kotlin.math.abs(cur.remoteAudioLevel - remoteLevel) < 0.02f &&
      cur.remoteAudioSilent == remoteSilent
    ) return
    _snapshot.value = cur.copy(
      localAudioLevel = localLevel,
      remoteAudioLevel = remoteLevel,
      remoteAudioSilent = remoteSilent
    )
  }

  /** Called on leaveChannel / engine destroy so UI never shows stale "Excellent". */
  fun reset() {
    synchronized(lock) {
      lastRtt = -1
      lastTxLoss = 0
      lastRxLoss = 0
      lastTxQuality = null
      lastRxQuality = null
      lastStatsAt = 0L
    }
    _snapshot.value = RtcNetworkSnapshot()
  }

  /** Returns true if stats are fresh enough to trust. */
  fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean {
    synchronized(lock) {
      if (lastStatsAt == 0L) return false
      return (nowMs - lastStatsAt) <= STALE_AFTER_MS
    }
  }

  private fun publish() {
    val rtt: Int
    val tx: Int
    val rx: Int
    val txQ: Int?
    val rxQ: Int?
    val at: Long
    synchronized(lock) {
      rtt = lastRtt; tx = lastTxLoss; rx = lastRxLoss
      txQ = lastTxQuality; rxQ = lastRxQuality; at = lastStatsAt
    }
    val cur = _snapshot.value
    val next = cur.copy(
      rttMs = rtt,
      txLossPct = tx,
      rxLossPct = rx,
      agoraTxQuality = txQ,
      agoraRxQuality = rxQ,
      lastStatsAtMs = at
    )
    if (next.rttMs != cur.rttMs || next.worstLossPct != cur.worstLossPct ||
      next.agoraTxQuality != cur.agoraTxQuality || next.agoraRxQuality != cur.agoraRxQuality
    ) {
      _snapshot.value = next
    }
    Log.d(TAG, "net rtt=${rtt}ms loss=${tx}/${rx}% txQ=$txQ rxQ=$rxQ tier=${next.tier}")
  }

  companion object {
    private const val TAG = "RtcConnMonitor"
    const val STALE_AFTER_MS = 5_000L
  }
}
