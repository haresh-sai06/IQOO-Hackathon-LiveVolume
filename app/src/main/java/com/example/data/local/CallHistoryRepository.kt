package com.example.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Repository abstracting Room database operations for call history
 * with optional Cloud Firestore synchronization.
 */
class CallHistoryRepository(
  private val dao: CallHistoryDao,
  private val context: Context? = null
) {

  val allCallHistory: Flow<List<CallHistoryEntity>> = dao.getAllCallHistory()

  fun searchCalls(query: String): Flow<List<CallHistoryEntity>> = dao.searchCallHistory(query)

  suspend fun insertCall(call: CallHistoryEntity): Long {
    val localId = dao.insertCall(call)
    syncCallToFirestore(call.copy(id = localId))
    return localId
  }

  private fun syncCallToFirestore(call: CallHistoryEntity) {
    try {
      val ctx = context ?: return
      if (FirebaseApp.getApps(ctx).isNotEmpty()) {
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
          "contactName" to call.contactName,
          "phoneNumber" to call.phoneNumber,
          "avatarUrl" to call.avatarUrl,
          "initials" to call.initials,
          "callType" to call.callType,
          "direction" to call.direction,
          "durationSeconds" to call.durationSeconds,
          "durationFormatted" to call.durationFormatted,
          "timestamp" to call.timestamp,
          "timestampFormatted" to call.timestampFormatted,
          "period" to call.period,
          "isOnline" to call.isOnline,
          "isSpatial" to call.isSpatial,
          "latencyMs" to call.latencyMs
        )
        db.collection("call_history").document("call_${call.id}").set(data)
      }
    } catch (e: Exception) {
      Log.w("CallHistoryRepo", "Firestore call history sync skipped: ${e.message}")
    }
  }

  suspend fun insertCalls(calls: List<CallHistoryEntity>) = dao.insertAll(calls)

  suspend fun deleteCall(id: Long) = dao.deleteCallById(id)

  suspend fun clearHistory() = dao.clearAll()

  suspend fun getCallCount(): Int = dao.getCount()

  suspend fun seedInitialDataIfEmpty() {
    // Real call data only: calls are recorded live as they occur
  }

  companion object {
    @Volatile
    private var INSTANCE: CallHistoryRepository? = null

    fun getInstance(context: Context): CallHistoryRepository {
      return INSTANCE ?: synchronized(this) {
        val database = AppDatabase.getDatabase(context)
        val repo = CallHistoryRepository(database.callHistoryDao(), context.applicationContext)
        INSTANCE = repo
        CoroutineScope(Dispatchers.IO).launch {
          repo.seedInitialDataIfEmpty()
        }
        repo
      }
    }
  }
}
