package com.gkgsiva.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PunchEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun punchDao(): PunchDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE punches ADD COLUMN isLogout INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tracker.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
