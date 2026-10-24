package com.gkgsiva.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PunchEntity::class, DayEntity::class], version = 5)
abstract class AppDatabase : RoomDatabase() {
    abstract fun punchDao(): PunchDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE punches ADD COLUMN isLogout INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS days (epochDay INTEGER NOT NULL, isWfh INTEGER NOT NULL, PRIMARY KEY(epochDay))"
                )
            }
        }

        /** Replaces the WFH flag with a day type (OFFICE, WFH, HOLIDAY, LEAVE). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE days_new (epochDay INTEGER NOT NULL, type TEXT NOT NULL, PRIMARY KEY(epochDay))")
                db.execSQL(
                    "INSERT INTO days_new (epochDay, type) " +
                        "SELECT epochDay, CASE WHEN isWfh = 1 THEN 'WFH' ELSE 'OFFICE' END FROM days"
                )
                db.execSQL("DROP TABLE days")
                db.execSQL("ALTER TABLE days_new RENAME TO days")
            }
        }

        /** Lets an LOP day be marked regularized, with an optional note. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN regularized INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE days ADD COLUMN note TEXT")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tracker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
