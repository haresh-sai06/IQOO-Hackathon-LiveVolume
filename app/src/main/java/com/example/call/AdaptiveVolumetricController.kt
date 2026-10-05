package com.example.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Adaptive volumetric streaming policy.
 *
 * Point-cloud frames are heavy (~10 chunks x ~1KB at 1200 pts). On poor
 * networks we must shed load BEFORE Agora drops packets, otherwise the
 * Receiver stalls waiting for lost chunks and the hologram freezes.
 *
 * Tiers (driven by [RtcNetworkSnapshot.tier]):
 * - EXCELLENT/GOOD: 1200 pts @ 5 fps (200ms)
 * - FAIR:            700 pts @ 4 fps (250ms)
 * - POOR:            350 pts @ 2.5 fps (400ms)
 * - OFFLINE:         pause sending entirely
 */
class AdaptiveVolumetricController {

  data class Budget(val maxPoints: Int, val intervalMs: Long, val paused: Boolean)

  private val _budget = MutableStateFlow(Budget(maxPoints = 1200, intervalMs = 200L, paused = false))
  val budget: StateFlow<Budget> = _budget.asStateFlow()

  fun onNetworkSnapshot(snap: RtcNetworkSnapshot) {
    val next = when (snap.tier) {
      NetworkQualityTier.EXCELLENT -> Budget(1200, 200L, false)
      NetworkQualityTier.GOOD -> Budget(1200, 200L, false)
      NetworkQualityTier.FAIR -> Budget(700, 250L, false)
      NetworkQualityTier.POOR -> Budget(350, 400L, false)
      NetworkQualityTier.OFFLINE -> Budget(0, 500L, true)
    }
    if (next != _budget.value) _budget.value = next
  }

  companion object {
    const val MAX_POINTS_CAP = 1200
    const val MIN_POINTS_FLOOR = 250
  }
}
