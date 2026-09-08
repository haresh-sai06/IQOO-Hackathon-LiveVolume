package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.model.CallSession
import com.example.model.CallSessionState
import com.example.model.VolumetricMeshMode
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time call signaling repository managing active 3D sessions,
 * live telemetry synchronization, and incoming call notifications via Cloud Firestore.
 */
class CallSignalingRepository private constructor(private val context: Context) {

  private val _activeSession = MutableStateFlow<CallSession?>(null)
  val activeSession: StateFlow<CallSession?> = _activeSession.asStateFlow()

  private val _incomingCall = MutableStateFlow<CallSession?>(null)
  val incomingCall: StateFlow<CallSession?> = _incomingCall.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.IO)
  private var firestore: FirebaseFirestore? = null
  private var activeSessionListener: ListenerRegistration? = null

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
   * Initiates a new real-time call session.
   */
  fun startCall(callerName: String, receiverName: String, is3D: Boolean = true): CallSession {
    val callId = "call_${System.currentTimeMillis()}"
    val session = CallSession(
      callId = callId,
      callerId = "my_uid",
      callerName = callerName,
      receiverId = "receiver_${receiverName.hashCode()}",
      receiverName = receiverName,
      state = CallSessionState.INITIATING,
      is3D = is3D,
      azimuth = 0f,
      elevation = 0f,
      depthIntensity = 1.0f,
      meshMode = VolumetricMeshMode.HOLOGRAPHIC_MESH,
      startedAt = System.currentTimeMillis()
    )

    _activeSession.value = session

    // Push to Firestore if available
    firestore?.let { db ->
      scope.launch {
        try {
          val data = hashMapOf(
            "callId" to session.callId,
            "callerName" to session.callerName,
            "receiverName" to session.receiverName,
            "state" to session.state.name,
            "is3D" to session.is3D,
            "azimuth" to session.azimuth,
            "elevation" to session.elevation,
            "depthIntensity" to session.depthIntensity,
            "meshMode" to session.meshMode.name,
            "startedAt" to session.startedAt
          )
          db.collection("calls").document(callId).set(data)
          listenToCallDocument(callId)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to start call in Firestore: ${e.message}")
        }
      }
    }

    // Auto-transition from INITIATING to RINGING, then to CONNECTED for smooth UX
    scope.launch {
      delay(800)
      if (_activeSession.value?.callId == callId) {
        updateCallState(CallSessionState.RINGING)
      }
      delay(1200)
      if (_activeSession.value?.callId == callId) {
        updateCallState(CallSessionState.CONNECTED)
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
          val stateStr = snapshot.getString("state") ?: CallSessionState.CONNECTED.name
          val state = try { CallSessionState.valueOf(stateStr) } catch (e: Exception) { CallSessionState.CONNECTED }
          val azimuth = snapshot.getDouble("azimuth")?.toFloat() ?: 0f
          val elevation = snapshot.getDouble("elevation")?.toFloat() ?: 0f
          val depth = snapshot.getDouble("depthIntensity")?.toFloat() ?: 1f

          _activeSession.value = _activeSession.value?.copy(
            state = state,
            azimuth = azimuth,
            elevation = elevation,
            depthIntensity = depth
          )
        }
      }
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

  fun updateSpatialTelemetry(azimuth: Float, elevation: Float, depthIntensity: Float) {
    val current = _activeSession.value ?: return
    _activeSession.value = current.copy(
      azimuth = azimuth,
      elevation = elevation,
      depthIntensity = depthIntensity
    )

    firestore?.let { db ->
      scope.launch {
        try {
          db.collection("calls").document(current.callId).update(
            mapOf(
              "azimuth" to azimuth,
              "elevation" to elevation,
              "depthIntensity" to depthIntensity
            )
          )
        } catch (e: Exception) {
          // Ignore frequent telemetry sync errors
        }
      }
    }
  }

  fun updateMeshMode(mode: VolumetricMeshMode) {
    _activeSession.value = _activeSession.value?.copy(meshMode = mode)
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
