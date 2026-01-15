package com.zyra.app.data.brain

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BrainTask::class, BrainTaskStep::class], version = 1, exportSchema = false)
abstract class BrainDatabase : RoomDatabase() {
    abstract fun brainDao(): BrainDao

    companion object {
        @Volatile
        private var INSTANCE: BrainDatabase? = null

        fun getDatabase(context: Context): BrainDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrainDatabase::class.java,
                    "brain_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
