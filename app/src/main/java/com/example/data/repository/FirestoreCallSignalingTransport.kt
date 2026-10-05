package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.model.CallSessionState
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Cloud Firestore implementation of [CallSignalingTransport].
 *
 * Document schema (collection `calls`):
 * ```
 * callId, callerId, callerName, receiverId, receiverName, channelName,
 * state, startedAt, expiresAt, heartbeatAt
 * ```
 * `callerId` / `receiverId` are Firebase UIDs and are the routing identity;
 * display names are never used to address a call when a UID is known.
 *
 * When `google-services.json` is absent the app builds and runs without
 * Firebase, so this transport reports [isAvailable] as `false` and every
 * operation becomes a no-op instead of throwing.
 */
class FirestoreCallSignalingTransport(
  private val db: FirebaseFirestore?
) : CallSignalingTransport {

  /**
   * Production wiring: binds to the app's default Firebase project, or to
   * nothing at all when `google-services.json` is absent.
   */
  constructor(context: Context) : this(resolveFirestore(context))

  override val isAvailable: Boolean
    get() = db != null

  override suspend fun createInvite(invite: CallInvite) {
    val database = db ?: return
    database.collection(CALLS).document(invite.callId).set(invite.toFields()).await()
  }

  override suspend fun updateInviteState(callId: String, state: CallSessionState) {
    val database = db ?: return
    database.collection(CALLS).document(callId)
      .update("state", state.name)
      .await()
  }

  override suspend fun updateInviteHeartbeat(callId: String, atMs: Long) {
    val database = db ?: return
    database.collection(CALLS).document(callId)
      .update("heartbeatAt", atMs)
      .await()
  }

  override suspend fun deleteInvite(callId: String) {
    val database = db ?: return
    database.collection(CALLS).document(callId).delete().await()
  }

  override suspend fun saveDeviceToken(uid: String, token: String, atMs: Long) {
    val database = db ?: return
    val document = database.collection(USERS).document(uid)
    val fields = mapOf("fcmToken" to token, "lastSeenAt" to atMs)
    // The user document usually exists by now, but update() fails when it does
    // not, so fall back to a full overwrite.
    try {
      document.update(fields).await()
    } catch (e: Exception) {
      document.set(fields).await()
    }
  }

  override fun observeInvite(callId: String, onChange: (CallInvite?) -> Unit): Subscription {
    val database = db ?: return NoOpSubscription
    val registration: ListenerRegistration = database.collection(CALLS).document(callId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "Error listening to call session: ${error.message}")
          return@addSnapshotListener
        }
        onChange(snapshot?.takeIf { it.exists() }?.toInvite())
      }
    return Subscription { registration.remove() }
  }

  override fun observeIncomingInvites(
    receiverId: String,
    receiverName: String,
    onChange: (List<CallInvite>) -> Unit
  ): Subscription {
    val database = db ?: return NoOpSubscription

    // Scope on receiverId so a device never downloads the whole calls table;
    // fall back to the display name only when the account has no UID yet.
    val query = if (receiverId.isNotBlank()) {
      database.collection(CALLS)
        .whereEqualTo("receiverId", receiverId)
        .whereEqualTo("state", CallSessionState.RINGING.name)
    } else {
      database.collection(CALLS)
        .whereEqualTo("receiverName", receiverName)
        .whereEqualTo("state", CallSessionState.RINGING.name)
    }

    val registration = query.addSnapshotListener { snapshot, error ->
      if (error != null) {
        Log.w(TAG, "Error listening for incoming calls: ${error.message}")
        return@addSnapshotListener
      }
      onChange(snapshot?.documents?.mapNotNull { it.toInvite() }.orEmpty())
    }
    return Subscription { registration.remove() }
  }

  private companion object {
    const val TAG = "CallSignalingTransport"
    const val CALLS = "calls"
    const val USERS = "users"

    val NoOpSubscription = Subscription { }

    fun resolveFirestore(context: Context): FirebaseFirestore? = try {
      if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
    } catch (e: Exception) {
      Log.w(TAG, "Firestore signaling unavailable: ${e.message}")
      null
    }

    fun CallInvite.toFields(): Map<String, Any?> = mapOf(
      "callId" to callId,
      "callerId" to callerId,
      "callerName" to callerName,
      "receiverId" to receiverId,
      "receiverName" to receiverName,
      "channelName" to channelName,
      "state" to state.name,
      "startedAt" to startedAt,
      "expiresAt" to expiresAt,
      "heartbeatAt" to heartbeatAt
    )

    fun DocumentSnapshot.toInvite(): CallInvite? = try {
      val rawState = getString("state") ?: CallSessionState.RINGING.name
      CallInvite(
        callId = getString("callId") ?: id,
        callerId = getString("callerId").orEmpty(),
        callerName = getString("callerName") ?: "LiveVolume Peer",
        receiverId = getString("receiverId").orEmpty(),
        receiverName = getString("receiverName").orEmpty(),
        channelName = getString("channelName").orEmpty(),
        state = try {
          CallSessionState.valueOf(rawState)
        } catch (_: Exception) {
          CallSessionState.RINGING
        },
        startedAt = getLong("startedAt") ?: 0L,
        expiresAt = getLong("expiresAt") ?: 0L,
        heartbeatAt = getLong("heartbeatAt") ?: 0L
      )
    } catch (e: Exception) {
      null
    }
  }
}
