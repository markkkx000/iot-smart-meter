package com.sabado.kuryentrol.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sabado.kuryentrol.data.model.DeviceName

@Database(entities = [DeviceName::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceNameDao(): DeviceNameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kuryentrol_database"
                )
                    .fallbackToDestructiveMigration() // Simple migration strategy for development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
