package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.model.CallSession
import com.example.model.CallSessionState
import com.example.util.InAppNotificationManager
import com.example.util.InAppNotificationType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time call signaling repository: owns the ringing handshake and the
 * channel coordination that accompanies it.
 *
 * State machine:
 * ```
 *   caller: startCall -> RINGING --(peer CONNECTED)--> CONNECTED
 *                              \--(peer REJECTED)---> REJECTED
 *                              \--(no answer in TTL) -> ENDED
 *   callee: invite observed -> ring -> acceptCall -> CONNECTED
 *                                  \-> declineCall -> REJECTED
 * ```
 *
 * The backend is reached through a [CallSignalingTransport], and the dispatcher
 * plus clock are injectable, so every transition above is covered by
 * deterministic unit tests without a live Firestore project.
 */
class CallSignalingRepository internal constructor(
  private val context: Context,
  private val transport: CallSignalingTransport,
  private val ioDispatcher: CoroutineDispatcher,
  private val clock: () -> Long,
  /**
   * Surfaces the incoming-call alert. Injected rather than called statically so
   * that duplicate ringing for a repeatedly-delivered invite is directly
   * observable in tests; production wiring posts the in-app banner.
   */
  private val alertIncomingCall: (CallSession) -> Unit
) {

  private val _activeSession = MutableStateFlow<CallSession?>(null)
  val activeSession: StateFlow<CallSession?> = _activeSession.asStateFlow()

  private val _incomingCall = MutableStateFlow<CallSession?>(null)
  val incomingCall: StateFlow<CallSession?> = _incomingCall.asStateFlow()

  /**
   * One-shot event raised the moment a call is answered — whether from the
   * in-app dialog or from a notification action. The UI collects this and
   * routes into the call screen, so answering a push notification no longer
   * silently drops the user back on the home screen.
   */
  private val _acceptedCall = MutableStateFlow<CallSession?>(null)
  val acceptedCall: StateFlow<CallSession?> = _acceptedCall.asStateFlow()

  /** Clears [acceptedCall] once the UI has consumed it. */
  fun consumeAcceptedCall() {
    _acceptedCall.value = null
  }

  private val scope = CoroutineScope(ioDispatcher + SupervisorJob())
  private var activeSubscription: Subscription? = null
  private var incomingSubscription: Subscription? = null

  private val suppressionLock = Any()

  /**
   * Call ids this device has already dismissed or declined.
   *
   * A dismissed invite is still RINGING on the backend (only the dialog was
   * hidden), so every later delivery of that document — for example after the
   * caller's periodic heartbeat write — would otherwise pop the call back up.
   */
  private val suppressedCallIds = LinkedHashSet<String>()

  /**
   * Starts listening in real-time for incoming call invites directed to this user.
   *
   * Production: scoped query on `receiverId` so one device never downloads the
   * whole global `calls` table. Falls back to legacy name-match only when the
   * account has no UID yet.
   */
  fun startListeningForIncomingCalls(myUserName: String, myUserId: String = "") {
    incomingSubscription?.cancel()
    clearSuppressions()
    val cleanName = myUserName.trim().lowercase()

    incomingSubscription = transport.observeIncomingInvites(myUserId, myUserName) { invites ->
      for (invite in invites) {
        val addressedToMe = if (myUserId.isNotBlank()) {
          invite.receiverId == myUserId
        } else {
          invite.receiverName.trim().lowercase() == cleanName
        }
        if (!addressedToMe) continue

        // Stale invites (older than the TTL) must stop ringing, so mark them
        // ENDED on the backend rather than presenting them as a live call.
        val cutoff = clock() - INVITE_TTL_MS
        if (invite.startedAt <= cutoff) {
          scope.launch {
            runCatching { transport.updateInviteState(invite.callId, CallSessionState.ENDED) }
          }
          continue
        }

        // Never ring for the call this device is already driving, nor for one
        // the user has already dealt with.
        if (_activeSession.value?.callId == invite.callId) continue
        if (isSuppressed(invite.callId)) continue
        if (_incomingCall.value?.callId == invite.callId) continue

        val session = CallSession(
          callId = invite.callId,
          callerId = invite.callerId,
          callerName = invite.callerName.ifBlank { "LiveVolume Peer" },
          receiverId = invite.receiverId,
          receiverName = myUserName,
          channelName = invite.channelName,
          state = CallSessionState.RINGING,
          startedAt = invite.startedAt
        )

        Log.i(
          TAG,
          "Incoming call invite for $myUserName from ${session.callerName} on ${session.channelName}"
        )
        _incomingCall.value = session

        alertIncomingCall(session)
      }
    }
  }

  /**
   * Initiates a new call invite in the backend and starts tracking peer state.
   *
   * @param callerId Firebase UID of the caller (routing identity — never a display name).
   */
  fun startCall(
    callerName: String,
    receiverName: String,
    receiverId: String,
    callerId: String = "",
    ttlMs: Long = INVITE_TTL_MS
  ): CallSession {
    require(receiverName.isNotBlank()) { "A saved contact is required to start a call." }
    require(receiverId.isNotBlank()) { "A registered contact identity is required to start a call." }

    val now = clock()
    val callId = "call_$now"
    val sanitizedReceiver = receiverId.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "user" }
    val channelName = "livevolume_$sanitizedReceiver"

    val session = CallSession(
      callId = callId,
      callerId = callerId.ifBlank { "uid_$now" },
      callerName = callerName,
      receiverId = receiverId,
      receiverName = receiverName,
      channelName = channelName,
      state = CallSessionState.RINGING,
      is3D = false,
      startedAt = now
    )

    _activeSession.value = session

    // Without a reachable backend the call still works locally (Agora handles
    // media); there is simply nobody to ring and nothing to expire server-side.
    if (!transport.isAvailable) return session

    scope.launch {
      try {
        transport.createInvite(session.toInvite(ttlMs))
        listenToCallDocument(callId)

        // Auto-cancel if the callee never answers within the TTL.
        launch {
          delay(ttlMs)
          val current = _activeSession.value
          if (current != null && current.callId == callId && current.state == CallSessionState.RINGING) {
            runCatching { transport.updateInviteState(callId, CallSessionState.ENDED) }
            _activeSession.value = current.copy(state = CallSessionState.ENDED)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to write call invite: ${e.message}")
      }
    }

    return session
  }

  /** Periodic heartbeat so the peer can detect caller death (no presence infra yet). */
  fun heartbeat() {
    val current = _activeSession.value ?: return
    if (!transport.isAvailable) return
    scope.launch {
      runCatching { transport.updateInviteHeartbeat(current.callId, clock()) }
    }
  }

  /** Uploads this device's FCM token for server-side push fan-out. */
  fun syncFcmToken(uid: String, token: String) {
    if (uid.isBlank() || token.isBlank() || !transport.isAvailable) return
    scope.launch {
      runCatching { transport.saveDeviceToken(uid, token, clock()) }
    }
  }

  private fun listenToCallDocument(callId: String) {
    activeSubscription?.cancel()
    activeSubscription = transport.observeInvite(callId) { invite ->
      if (invite == null) return@observeInvite
      val current = _activeSession.value ?: return@observeInvite
      _activeSession.value = current.copy(
        state = invite.state,
        channelName = invite.channelName.ifBlank { current.channelName }
      )
    }
  }

  /**
   * Called by the callee to accept an incoming call.
   */
  fun acceptCall(session: CallSession) {
    _incomingCall.value = null
    _activeSession.value = session.copy(state = CallSessionState.CONNECTED)
    _acceptedCall.value = session

    // Stop the ringing notification when the user answers from anywhere else.
    try {
      com.example.service.LiveVolumeMessagingService
        .cancelIncomingCallNotification(context, session.callId)
    } catch (_: Exception) {}

    if (!transport.isAvailable) return

    scope.launch {
      try {
        transport.updateInviteState(session.callId, CallSessionState.CONNECTED)
        listenToCallDocument(session.callId)
      } catch (e: Exception) {
        Log.w(TAG, "Failed accepting call: ${e.message}")
      }
    }
  }

  /**
   * Called by the callee to decline an incoming call.
   */
  fun declineCall(session: CallSession) {
    suppress(session.callId)
    _incomingCall.value = null
    if (!transport.isAvailable) return
    scope.launch {
      runCatching { transport.updateInviteState(session.callId, CallSessionState.REJECTED) }
    }
  }

  /**
   * Called when an FCM push message with a call invite payload is received.
   */
  fun onIncomingCallInviteReceived(callId: String, callerName: String, channelName: String) {
    _incomingCall.value = CallSession(
      callId = callId,
      callerName = callerName,
      channelName = channelName,
      state = CallSessionState.RINGING,
      startedAt = clock()
    )
  }

  fun updateCallState(newState: CallSessionState) {
    val current = _activeSession.value ?: return
    _activeSession.value = current.copy(state = newState)

    if (!transport.isAvailable) return
    scope.launch {
      runCatching { transport.updateInviteState(current.callId, newState) }
    }
  }

  fun endCall(deleteInvite: Boolean = true) {
    val current = _activeSession.value
    if (current != null) {
      updateCallState(CallSessionState.ENDED)
      // TTL cleanup: remove the invite doc so the callee's listener stops ringing.
      if (deleteInvite && transport.isAvailable) {
        scope.launch {
          // Small delay lets the peer observe ENDED before the doc vanishes.
          delay(TEARDOWN_GRACE_MS)
          runCatching { transport.deleteInvite(current.callId) }
        }
      }
    }
    activeSubscription?.cancel()
    activeSubscription = null
    _activeSession.value = null
  }

  fun dismissIncomingCall() {
    suppress(_incomingCall.value?.callId.orEmpty())
    _incomingCall.value = null
  }

  private fun suppress(callId: String) {
    if (callId.isBlank()) return
    synchronized(suppressionLock) {
      suppressedCallIds.add(callId)
      // Bound the set: invites expire long before this many calls accumulate.
      while (suppressedCallIds.size > MAX_SUPPRESSED_CALLS) {
        val oldest = suppressedCallIds.firstOrNull() ?: break
        suppressedCallIds.remove(oldest)
      }
    }
  }

  private fun isSuppressed(callId: String): Boolean =
    synchronized(suppressionLock) { callId in suppressedCallIds }

  private fun clearSuppressions() {
    synchronized(suppressionLock) { suppressedCallIds.clear() }
  }

  /** Releases realtime listeners; intended for sign-out and tests. */
  fun dispose() {
    activeSubscription?.cancel()
    activeSubscription = null
    incomingSubscription?.cancel()
    incomingSubscription = null
  }

  private fun CallSession.toInvite(ttlMs: Long): CallInvite = CallInvite(
    callId = callId,
    callerId = callerId,
    callerName = callerName,
    receiverId = receiverId,
    receiverName = receiverName,
    channelName = channelName,
    state = state,
    startedAt = startedAt,
    expiresAt = startedAt + ttlMs,
    heartbeatAt = startedAt
  )

  companion object {
    private const val TAG = "CallSignalingRepo"

    /** How long an unanswered invite keeps ringing before it self-cancels. */
    const val INVITE_TTL_MS = 60_000L

    /** Grace period so the peer can observe ENDED before the invite is deleted. */
    const val TEARDOWN_GRACE_MS = 5_000L

    private const val MAX_SUPPRESSED_CALLS = 32

    @Volatile
    private var INSTANCE: CallSignalingRepository? = null

    fun getInstance(context: Context): CallSignalingRepository {
      return INSTANCE ?: synchronized(this) {
        val appContext = context.applicationContext
        INSTANCE ?: CallSignalingRepository(
          context = appContext,
          transport = FirestoreCallSignalingTransport(appContext),
          ioDispatcher = Dispatchers.IO,
          clock = System::currentTimeMillis,
          alertIncomingCall = { session ->
            InAppNotificationManager.postNotification(
              title = "Incoming Video Call",
              message = "${session.callerName} is calling you...",
              type = InAppNotificationType.CALL_INCOMING,
              context = appContext
            )
          }
        ).also { INSTANCE = it }
      }
    }
  }
}
