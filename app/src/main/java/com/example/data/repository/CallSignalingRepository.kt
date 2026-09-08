package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.model.CallSession
import com.example.model.CallSessionState
import com.example.util.InAppNotificationManager
import com.example.util.InAppNotificationType
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time call signaling repository managing call invites, channel coordination,
 * and live ringing/handshake state synchronization via Cloud Firestore.
 */
class CallSignalingRepository private constructor(private val context: Context) {

  private val _activeSession = MutableStateFlow<CallSession?>(null)
  val activeSession: StateFlow<CallSession?> = _activeSession.asStateFlow()

  private val _incomingCall = MutableStateFlow<CallSession?>(null)
  val incomingCall: StateFlow<CallSession?> = _incomingCall.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.IO)
  private var firestore: FirebaseFirestore? = null
  private var activeSessionListener: ListenerRegistration? = null
  private var incomingCallsListener: ListenerRegistration? = null

  init {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        firestore = FirebaseFirestore.getInstance()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Firestore signaling unavailable: ${e.message}")
    }
  }

  /**
   * Starts listening in real-time for incoming call invites directed to this user.
   */
  fun startListeningForIncomingCalls(myUserName: String) {
    val db = firestore ?: return
    incomingCallsListener?.remove()

    val cutoff = System.currentTimeMillis() - 45000L // last 45 seconds
    val cleanName = myUserName.trim().lowercase()

    incomingCallsListener = db.collection("calls")
      .whereEqualTo("state", CallSessionState.RINGING.name)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "Error listening for incoming calls: ${error.message}")
          return@addSnapshotListener
        }

        snapshot?.documents?.forEach { doc ->
          val receiver = doc.getString("receiverName")?.trim()?.lowercase() ?: ""
          val startedAt = doc.getLong("startedAt") ?: 0L

          if (receiver.contains(cleanName) || cleanName.contains(receiver) || receiver == "me" || cleanName == "me") {
            if (startedAt > cutoff && _activeSession.value?.callId != doc.id) {
              val callId = doc.getString("callId") ?: doc.id
              val callerName = doc.getString("callerName") ?: "Live Contact"
              val channelName = doc.getString("channelName") ?: ("livevolume_" + callerName.lowercase().replace(Regex("[^a-z0-9]"), ""))

              val session = CallSession(
                callId = callId,
                callerName = callerName,
                receiverName = myUserName,
                channelName = channelName,
                state = CallSessionState.RINGING,
                startedAt = startedAt
              )

              if (_incomingCall.value?.callId != callId) {
                Log.i(TAG, "Incoming call invite detected for $myUserName from $callerName on channel $channelName")
                _incomingCall.value = session

                InAppNotificationManager.postNotification(
                  title = "Incoming Video Call",
                  message = "$callerName is calling you...",
                  type = InAppNotificationType.CALL_INCOMING,
                  context = context
                )
              }
            }
          }
        }
      }
  }

  /**
   * Initiates a new real-time call invite in Firestore and starts tracking peer state.
   */
  fun startCall(callerName: String, receiverName: String): CallSession {
    val callId = "call_${System.currentTimeMillis()}"
    val sanitizedReceiver = receiverName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "user" }
    val channelName = "livevolume_$sanitizedReceiver"

    val session = CallSession(
      callId = callId,
      callerId = "my_uid",
      callerName = callerName,
      receiverId = "receiver_${receiverName.hashCode()}",
      receiverName = receiverName,
      channelName = channelName,
      state = CallSessionState.RINGING,
      is3D = false,
      startedAt = System.currentTimeMillis()
    )

    _activeSession.value = session

    // Push call invite document to Firestore
    firestore?.let { db ->
      scope.launch {
        try {
          val data = hashMapOf(
            "callId" to session.callId,
            "callerName" to session.callerName,
            "receiverName" to session.receiverName,
            "channelName" to session.channelName,
            "state" to session.state.name,
            "startedAt" to session.startedAt
          )
          db.collection("calls").document(callId).set(data)
          listenToCallDocument(callId)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to write call invite to Firestore: ${e.message}")
        }
      }
    }

    return session
  }

  private fun listenToCallDocument(callId: String) {
    val db = firestore ?: return
    activeSessionListener?.remove()

    activeSessionListener = db.collection("calls").document(callId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "Error listening to call session: ${error.message}")
          return@addSnapshotListener
        }

        if (snapshot != null && snapshot.exists()) {
          val stateStr = snapshot.getString("state") ?: CallSessionState.RINGING.name
          val state = try { CallSessionState.valueOf(stateStr) } catch (e: Exception) { CallSessionState.RINGING }
          val channelName = snapshot.getString("channelName") ?: _activeSession.value?.channelName ?: ""

          _activeSession.value = _activeSession.value?.copy(
            state = state,
            channelName = channelName
          )
        }
      }
  }

  /**
   * Called by the callee to accept an incoming call.
   */
  fun acceptCall(session: CallSession) {
    _incomingCall.value = null
    _activeSession.value = session.copy(state = CallSessionState.CONNECTED)

    firestore?.let { db ->
      scope.launch {
        try {
          db.collection("calls").document(session.callId).update("state", CallSessionState.CONNECTED.name)
          listenToCallDocument(session.callId)
        } catch (e: Exception) {
          Log.w(TAG, "Failed accepting call in Firestore: ${e.message}")
        }
      }
    }
  }

  /**
   * Called by the callee to decline an incoming call.
   */
  fun declineCall(session: CallSession) {
    _incomingCall.value = null

    firestore?.let { db ->
      scope.launch {
        try {
          db.collection("calls").document(session.callId).update("state", CallSessionState.REJECTED.name)
        } catch (e: Exception) {
          Log.w(TAG, "Failed declining call in Firestore: ${e.message}")
        }
      }
    }
  }

  /**
   * Called when an FCM push message with call invite payload is received.
   */
  fun onIncomingCallInviteReceived(callId: String, callerName: String, channelName: String) {
    val session = CallSession(
      callId = callId,
      callerName = callerName,
      channelName = channelName,
      state = CallSessionState.RINGING,
      startedAt = System.currentTimeMillis()
    )
    _incomingCall.value = session
  }

  fun updateCallState(newState: CallSessionState) {
    val current = _activeSession.value ?: return
    _activeSession.value = current.copy(state = newState)

    firestore?.let { db ->
      scope.launch {
        try {
          db.collection("calls").document(current.callId).update("state", newState.name)
        } catch (e: Exception) {
          Log.w(TAG, "Failed updating call state in Firestore: ${e.message}")
        }
      }
    }
  }

  fun endCall() {
    val current = _activeSession.value
    if (current != null) {
      updateCallState(CallSessionState.ENDED)
    }
    activeSessionListener?.remove()
    activeSessionListener = null
    _activeSession.value = null
  }

  fun dismissIncomingCall() {
    _incomingCall.value = null
  }

  companion object {
    private const val TAG = "CallSignalingRepo"

    @Volatile
    private var INSTANCE: CallSignalingRepository? = null

    fun getInstance(context: Context): CallSignalingRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: CallSignalingRepository(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
