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
    version = 6,
    // Schemas are committed (app/schemas/) so migrations stay testable — after Play launch a
    // botched migration is unrecoverable, so never flip this back off.
    exportSchema = true
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

        /**
         * v4 → v5: FSRS memory state on word_review. Adds stability/difficulty/lastReviewEpochDay/
         * reps and back-fills them from the legacy Leitner box (box→interval as the initial
         * stability) so existing learners keep their schedule — dueEpochDay is preserved untouched.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `word_review` ADD COLUMN `stability` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `word_review` ADD COLUMN `difficulty` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `word_review` ADD COLUMN `lastReviewEpochDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `word_review` ADD COLUMN `reps` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE `word_review` SET " +
                        "stability = CASE box WHEN 1 THEN 1.0 WHEN 2 THEN 2.0 WHEN 3 THEN 5.0 " +
                        "WHEN 4 THEN 10.0 WHEN 5 THEN 21.0 WHEN 6 THEN 45.0 ELSE 1.0 END, " +
                        "difficulty = 5.0, " +
                        "reps = box, " +
                        "lastReviewEpochDay = MAX(0, dueEpochDay - (CASE box WHEN 1 THEN 1 WHEN 2 THEN 2 " +
                        "WHEN 3 THEN 5 WHEN 4 THEN 10 WHEN 5 THEN 21 WHEN 6 THEN 45 ELSE 1 END))"
                )
            }
        }

        /**
         * v5 → v6: unique (langCode, day) on day_completion, so double-completing a day is a
         * DB-level no-op (the autoincrement PK gave OnConflict.IGNORE nothing to conflict on).
         * Existing duplicates are removed first — keeping the EARLIEST row per (lang, day),
         * the original completion — or the index creation would fail.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM `day_completion` WHERE `id` NOT IN (" +
                        "SELECT MIN(`id`) FROM `day_completion` GROUP BY `langCode`, `day`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_day_completion_langCode_day` " +
                        "ON `day_completion` (`langCode`, `day`)"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "corlang.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build().also { instance = it }
        }
    }
}
