package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database holder for LiveVolume local call persistence.
 */
@Database(
  entities = [CallHistoryEntity::class],
  version = 2,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun callHistoryDao(): CallHistoryDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "live_volume_call_history.db"
        )
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
