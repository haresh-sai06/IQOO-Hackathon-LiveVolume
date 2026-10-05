package com.example.data.repository

import com.example.model.CallSessionState

/**
 * Immutable view of a single call invite as stored by the signaling backend.
 */
data class CallInvite(
  val callId: String,
  val callerId: String = "",
  val callerName: String = "",
  val receiverId: String = "",
  val receiverName: String = "",
  val channelName: String = "",
  val state: CallSessionState = CallSessionState.RINGING,
  val startedAt: Long = 0L,
  val expiresAt: Long = 0L,
  val heartbeatAt: Long = 0L
)

/** Handle that tears down a realtime subscription. */
fun interface Subscription {
  fun cancel()
}

/**
 * Persistence seam for the call-ringing handshake.
 *
 * [CallSignalingRepository] owns the state machine (ring → answer/decline →
 * connected/ended, plus TTL expiry); this interface owns the transport. Keeping
 * them apart means the handshake can be exercised deterministically against an
 * in-memory transport, without a live Firestore project or a mocking framework.
 *
 * Every method must be safe to call when the backend is unavailable: an
 * implementation that cannot reach its store should no-op rather than throw,
 * and report [isAvailable] as `false` so callers can keep working offline.
 */
interface CallSignalingTransport {

  /**
   * `false` when the backend is unconfigured or unreachable. The repository uses
   * this to avoid scheduling backend-only work (invite writes, TTL cleanup)
   * that would otherwise fire against a store that will never acknowledge it.
   */
  val isAvailable: Boolean

  suspend fun createInvite(invite: CallInvite)

  suspend fun updateInviteState(callId: String, state: CallSessionState)

  suspend fun updateInviteHeartbeat(callId: String, atMs: Long)

  suspend fun deleteInvite(callId: String)

  /** Registers this device's push token for the given account. */
  suspend fun saveDeviceToken(uid: String, token: String, atMs: Long)

  /**
   * Observes one invite document. Emits `null` when the document no longer
   * exists (for example once a finished call has been cleaned up).
   */
  fun observeInvite(callId: String, onChange: (CallInvite?) -> Unit): Subscription

  /**
   * Observes invites addressed to this account that are still ringing. An empty
   * [receiverId] selects the legacy display-name lookup.
   */
  fun observeIncomingInvites(
    receiverId: String,
    receiverName: String,
    onChange: (List<CallInvite>) -> Unit
  ): Subscription
}
