package com.example

import com.example.data.repository.CallInvite
import com.example.data.repository.CallSignalingTransport
import com.example.data.repository.Subscription
import com.example.model.CallSessionState

/**
 * In-memory [CallSignalingTransport] for tests.
 *
 * It records every write so a test can assert on the handshake, and exposes
 * [emitInvite] / [emitStateChange] so a test can play the role of the remote
 * peer (or of the backend pushing an invite down on the callee's device).
 */
class FakeCallSignalingTransport(
  override var isAvailable: Boolean = true
) : CallSignalingTransport {

  /** Current invites keyed by call id, mirroring the `calls` collection. */
  val invites = LinkedHashMap<String, CallInvite>()

  /** Ordered log of state writes. */
  val stateWrites = mutableListOf<Pair<String, CallSessionState>>()

  val heartbeatWrites = mutableListOf<Pair<String, Long>>()
  val deletedCallIds = mutableListOf<String>()
  val deviceTokens = mutableListOf<DeviceToken>()

  var incomingObserverActive = false
    private set
  var incomingObserverCancellations = 0
    private set
  var lastIncomingFilter: Pair<String, String>? = null
    private set

  data class DeviceToken(val uid: String, val token: String, val atMs: Long)

  private var inviteObserver: ((CallInvite?) -> Unit)? = null
  private var incomingObserver: ((List<CallInvite>) -> Unit)? = null

  fun stateOf(callId: String): CallSessionState? = invites[callId]?.state

  /** Registers an invite as if it arrived from the backend. */
  fun emitInvite(invite: CallInvite) {
    invites[invite.callId] = invite
    inviteObserver?.invoke(invite)
    publishRinging()
  }

  /** Simulates the remote peer changing the invite state (answer / reject / end). */
  fun emitStateChange(callId: String, state: CallSessionState) {
    val existing = invites[callId] ?: return
    invites[callId] = existing.copy(state = state)
    inviteObserver?.invoke(invites[callId])
    publishRinging()
  }

  private fun publishRinging() {
    incomingObserver?.invoke(invites.values.filter { it.state == CallSessionState.RINGING })
  }

  override suspend fun createInvite(invite: CallInvite) {
    invites[invite.callId] = invite
    inviteObserver?.invoke(invite)
    publishRinging()
  }

  override suspend fun updateInviteState(callId: String, state: CallSessionState) {
    stateWrites += callId to state
    invites[callId]?.let { invites[callId] = it.copy(state = state) }
    inviteObserver?.invoke(invites[callId])
    publishRinging()
  }

  override suspend fun updateInviteHeartbeat(callId: String, atMs: Long) {
    heartbeatWrites += callId to atMs
    invites[callId]?.let { invites[callId] = it.copy(heartbeatAt = atMs) }
  }

  override suspend fun deleteInvite(callId: String) {
    deletedCallIds += callId
    invites.remove(callId)
  }

  override suspend fun saveDeviceToken(uid: String, token: String, atMs: Long) {
    deviceTokens += DeviceToken(uid, token, atMs)
  }

  override fun observeInvite(callId: String, onChange: (CallInvite?) -> Unit): Subscription {
    inviteObserver = onChange
    return Subscription { inviteObserver = null }
  }

  override fun observeIncomingInvites(
    receiverId: String,
    receiverName: String,
    onChange: (List<CallInvite>) -> Unit
  ): Subscription {
    incomingObserverActive = true
    lastIncomingFilter = receiverId to receiverName
    incomingObserver = onChange
    return Subscription {
      incomingObserverCancellations++
      incomingObserverActive = false
      incomingObserver = null
    }
  }
}
