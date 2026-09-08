package com.example.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Repository abstracting Room database operations for call history.
 */
class CallHistoryRepository(private val dao: CallHistoryDao) {

  val allCallHistory: Flow<List<CallHistoryEntity>> = dao.getAllCallHistory()

  fun searchCalls(query: String): Flow<List<CallHistoryEntity>> = dao.searchCallHistory(query)

  suspend fun insertCall(call: CallHistoryEntity): Long = dao.insertCall(call)

  suspend fun insertCalls(calls: List<CallHistoryEntity>) = dao.insertAll(calls)

  suspend fun deleteCall(id: Long) = dao.deleteCallById(id)

  suspend fun clearHistory() = dao.clearAll()

  suspend fun getCallCount(): Int = dao.getCount()

  suspend fun seedInitialDataIfEmpty() {
    if (dao.getCount() == 0) {
      val now = System.currentTimeMillis()
      val initialData = listOf(
        CallHistoryEntity(
          contactName = "Sarah Chen",
          phoneNumber = "+1 (555) 234-5678",
          avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
          initials = "SC",
          callType = "3D Spatial",
          direction = "Incoming",
          durationSeconds = 872L,
          durationFormatted = "14m 32s",
          timestamp = now - 1000L * 60 * 45,
          timestampFormatted = "Today, 10:45 AM",
          period = "TODAY",
          isOnline = true,
          isSpatial = true
        ),
        CallHistoryEntity(
          contactName = "Marcus Vance",
          phoneNumber = "+1 (555) 876-5432",
          avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80",
          initials = "MV",
          callType = "3D Spatial",
          direction = "Outgoing",
          durationSeconds = 1845L,
          durationFormatted = "30m 45s",
          timestamp = now - 1000L * 60 * 60 * 3,
          timestampFormatted = "Today, 08:15 AM",
          period = "TODAY",
          isOnline = true,
          isSpatial = true
        ),
        CallHistoryEntity(
          contactName = "Dr. Elena Rostova",
          phoneNumber = "+1 (555) 345-6789",
          avatarUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80",
          initials = "ER",
          callType = "Missed",
          direction = "Missed",
          durationSeconds = 0L,
          durationFormatted = "Missed",
          timestamp = now - 1000L * 60 * 60 * 26,
          timestampFormatted = "Yesterday, 04:30 PM",
          period = "YESTERDAY",
          isOnline = false,
          isSpatial = false
        ),
        CallHistoryEntity(
          contactName = "Kai Tanaka",
          phoneNumber = "+1 (555) 456-7890",
          avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80",
          initials = "KT",
          callType = "3D Spatial",
          direction = "Incoming",
          durationSeconds = 1338L,
          durationFormatted = "22m 18s",
          timestamp = now - 1000L * 60 * 60 * 30,
          timestampFormatted = "Yesterday, 11:20 AM",
          period = "YESTERDAY",
          isOnline = true,
          isSpatial = true
        ),
        CallHistoryEntity(
          contactName = "Amara Okafor",
          phoneNumber = "+1 (555) 567-8901",
          avatarUrl = "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?auto=format&fit=crop&w=400&q=80",
          initials = "AO",
          callType = "Video",
          direction = "Outgoing",
          durationSeconds = 542L,
          durationFormatted = "9m 02s",
          timestamp = now - 1000L * 60 * 60 * 50,
          timestampFormatted = "May 14, 02:10 PM",
          period = "EARLIER_THIS_WEEK",
          isOnline = false,
          isSpatial = false
        )
      )
      dao.insertAll(initialData)
    }
  }

  companion object {
    @Volatile
    private var INSTANCE: CallHistoryRepository? = null

    fun getInstance(context: Context): CallHistoryRepository {
      return INSTANCE ?: synchronized(this) {
        val database = AppDatabase.getDatabase(context)
        val repo = CallHistoryRepository(database.callHistoryDao())
        INSTANCE = repo
        CoroutineScope(Dispatchers.IO).launch {
          repo.seedInitialDataIfEmpty()
        }
        repo
      }
    }
  }
}
