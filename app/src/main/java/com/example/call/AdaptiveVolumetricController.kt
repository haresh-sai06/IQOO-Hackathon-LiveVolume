package com.example.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Adaptive volumetric streaming policy.
 *
 * Point-cloud frames are heavy. On poor networks we shed load BEFORE Agora
 * drops packets. When the link is excellent we push denser clouds for a
 * fuller bust look (Grand Finale demo polish).
 *
 * Tiers (driven by [RtcNetworkSnapshot.tier]):
 * - EXCELLENT: 1800 pts @ ~6 fps (160ms)
 * - GOOD:      1500 pts @ 5 fps (200ms)
 * - FAIR:       800 pts @ 4 fps (250ms)
 * - POOR:       400 pts @ 2.5 fps (400ms)
 * - OFFLINE:    pause sending entirely
 */
class AdaptiveVolumetricController {

  data class Budget(val maxPoints: Int, val intervalMs: Long, val paused: Boolean)

  private val _budget = MutableStateFlow(Budget(maxPoints = 1500, intervalMs = 200L, paused = false))
  val budget: StateFlow<Budget> = _budget.asStateFlow()

  fun onNetworkSnapshot(snap: RtcNetworkSnapshot) {
    val next = when (snap.tier) {
      NetworkQualityTier.EXCELLENT -> Budget(1800, 160L, false)
      NetworkQualityTier.GOOD -> Budget(1500, 200L, false)
      NetworkQualityTier.FAIR -> Budget(800, 250L, false)
      NetworkQualityTier.POOR -> Budget(400, 400L, false)
      NetworkQualityTier.OFFLINE -> Budget(0, 500L, true)
    }
    if (next != _budget.value) _budget.value = next
  }

  companion object {
    const val MAX_POINTS_CAP = 1800
    const val MIN_POINTS_FLOOR = 250
  }
}
