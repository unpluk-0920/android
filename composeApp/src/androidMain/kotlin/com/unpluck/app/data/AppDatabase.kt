package com.unpluck.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.unpluck.app.defs.Space

@Database(entities = [Space::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spaceDao(): SpaceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unpluk_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}