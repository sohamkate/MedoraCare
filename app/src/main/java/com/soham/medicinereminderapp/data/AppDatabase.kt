
package com.soham.medicinereminderapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Medicine::class,
        MedicineTime::class,
        DoseLog::class,
        MedicineScheduledDate::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicineDao(): MedicineDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medicines ADD COLUMN imagePath TEXT"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE dose_logs ADD COLUMN reminderTimeId INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE dose_logs ADD COLUMN reminderKey TEXT"
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_dose_logs_reminderKey
                    ON dose_logs(reminderKey)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medicines ADD COLUMN notes TEXT"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE medicines
                    ADD COLUMN weekdays TEXT NOT NULL
                    DEFAULT '1,2,3,4,5,6,7'
                    """.trimIndent()
                )
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medicines ADD COLUMN currentStock REAL"
                )
                db.execSQL(
                    "ALTER TABLE medicines ADD COLUMN unitsPerDose REAL"
                )
                db.execSQL(
                    """
            ALTER TABLE medicines
            ADD COLUMN stockUnit TEXT NOT NULL DEFAULT 'tablets'
            """.trimIndent()
                )
                db.execSQL(
                    """
            ALTER TABLE medicines
            ADD COLUMN lowStockDays INTEGER NOT NULL DEFAULT 2
            """.trimIndent()
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            ALTER TABLE medicines
            ADD COLUMN lowStockAlertActive INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
            ALTER TABLE medicines
            ADD COLUMN scheduleType TEXT NOT NULL DEFAULT 'DATE_RANGE'
        """.trimIndent())

                db.execSQL("""
            ALTER TABLE medicines
            ADD COLUMN startDate TEXT
        """.trimIndent())

                db.execSQL("""
            ALTER TABLE medicines
            ADD COLUMN endDate TEXT
        """.trimIndent())

                db.execSQL("""
            CREATE TABLE IF NOT EXISTS medicine_scheduled_dates (
                medicineId INTEGER NOT NULL,
                date TEXT NOT NULL,
                PRIMARY KEY(medicineId, date),
                FOREIGN KEY(medicineId)
                    REFERENCES medicines(id)
                    ON UPDATE NO ACTION
                    ON DELETE CASCADE
            )
        """.trimIndent())

                db.execSQL("""
            CREATE INDEX IF NOT EXISTS
            index_medicine_scheduled_dates_medicineId
            ON medicine_scheduled_dates(medicineId)
        """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medicine_reminder_database"
                )
                    .addMigrations(
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
