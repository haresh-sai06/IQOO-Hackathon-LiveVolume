package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for persisting and querying call history records.
 */
@Dao
interface CallHistoryDao {

  @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
  fun getAllCallHistory(): Flow<List<CallHistoryEntity>>

  @Query("SELECT * FROM call_history WHERE contactName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
  fun searchCallHistory(query: String): Flow<List<CallHistoryEntity>>

  @Query("SELECT * FROM call_history WHERE contactName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
  suspend fun searchCallHistoryDirect(query: String): List<CallHistoryEntity>

  @Query("SELECT * FROM call_history WHERE id = :id")
  suspend fun getCallById(id: Long): CallHistoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCall(call: CallHistoryEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(calls: List<CallHistoryEntity>)

  @Query("DELETE FROM call_history WHERE id = :id")
  suspend fun deleteCallById(id: Long)

  @Query("DELETE FROM call_history")
  suspend fun clearAll()

  @Query("SELECT COUNT(*) FROM call_history")
  suspend fun getCount(): Int
}
