package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EmployeeEntity::class,
        AttendanceLogEntity::class,
        PayrollSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PulseAttendDatabase : RoomDatabase() {
    abstract fun pulseAttendDao(): PulseAttendDao

    companion object {
        @Volatile
        private var INSTANCE: PulseAttendDatabase? = null

        fun getInstance(context: Context): PulseAttendDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseAttendDatabase::class.java,
                    "pulse_attend.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
