package com.gkgsiva.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PunchEntity::class, DayEntity::class, EditEntity::class], version = 7)
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

        /** Records when a day was regularized, and a log of changes made to entries by hand. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN regularizedOn INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS edits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, epochDay INTEGER NOT NULL, " +
                        "atMillis INTEGER NOT NULL, change TEXT NOT NULL, reason TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_edits_epochDay ON edits (epochDay)")
            }
        }

        /** A label (Lunch, Tea, …) on each break start. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE punches ADD COLUMN label TEXT")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tracker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { instance = it }
        }
    }
}
