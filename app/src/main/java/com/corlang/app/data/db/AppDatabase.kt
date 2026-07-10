package com.corlang.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LanguageProgress::class,
        DayCompletion::class,
        QuizAttempt::class,
        FeynmanAttempt::class,
        WordReview::class,
        ExamSectionAttempt::class,
        CanDoCheck::class,
        DayTaskCheck::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2: adds the spaced-repetition word_review table. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `word_review` (" +
                        "`langCode` TEXT NOT NULL, " +
                        "`wordId` TEXT NOT NULL, " +
                        "`box` INTEGER NOT NULL, " +
                        "`dueEpochDay` INTEGER NOT NULL, " +
                        "`introducedEpochDay` INTEGER NOT NULL, " +
                        "`lapses` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`langCode`, `wordId`))"
                )
            }
        }

        /** v2 → v3: mock-exam section attempts, can-do self-checklist, streak freezes. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `language_progress` ADD COLUMN `streakFreezes` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exam_section_attempt` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`langCode` TEXT NOT NULL, " +
                        "`examId` TEXT NOT NULL, " +
                        "`sectionId` TEXT NOT NULL, " +
                        "`score` INTEGER NOT NULL, " +
                        "`total` INTEGER NOT NULL, " +
                        "`passed` INTEGER NOT NULL, " +
                        "`takenAtEpoch` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `can_do_check` (" +
                        "`langCode` TEXT NOT NULL, " +
                        "`levelId` TEXT NOT NULL, " +
                        "`itemId` TEXT NOT NULL, " +
                        "`checkedAtEpoch` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`langCode`, `levelId`, `itemId`))"
                )
            }
        }

        /** v3 → v4: per-day plan-task checklist. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_task_check` (" +
                        "`langCode` TEXT NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`itemId` TEXT NOT NULL, " +
                        "`checkedAtEpoch` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`langCode`, `day`, `itemId`))"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "corlang.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
