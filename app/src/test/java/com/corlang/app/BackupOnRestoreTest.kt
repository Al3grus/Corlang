package com.corlang.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Auto Backup has to give a learner back the same app they left, and it is backed by a promise
 * that lives in three files at once: the two backup rule XMLs name which files travel, and
 * AppDatabase decides which file the rows are actually IN. They drifted apart once and the
 * symptom was silent - a reinstall restored the learner's name and reset their lessons to day 1,
 * because the rules named corlang.db while Room's default WAL mode was still holding the recent
 * writes in the un-backed-up corlang.db-wal.
 *
 * Nothing in the compiler couples those three files, so this does. See docs/error-registry.md.
 */
class BackupOnRestoreTest {

    private fun repoFile(vararg candidates: String): File =
        candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("none of ${candidates.toList()} found (cwd=${File(".").absolutePath})")

    private fun appFile(relative: String): File =
        repoFile("app/$relative", relative)

    private val backupRules get() = appFile("src/main/res/xml/backup_rules.xml").readText()
    private val extractionRules get() = appFile("src/main/res/xml/data_extraction_rules.xml").readText()
    private val appDatabase
        get() = appFile("src/main/java/com/corlang/app/data/db/AppDatabase.kt").readText()

    /**
     * The heart of it: if the rules back the database up BY NAME, every committed row has to be
     * inside that one file. Only TRUNCATE (or another non-WAL mode) guarantees that.
     */
    @Test
    fun `database named in the backup rules is not left in WAL mode`() {
        val namesTheDb = listOf(backupRules, extractionRules).all { it.contains("corlang.db") }
        assertTrue("backup rules no longer name corlang.db - is the DB still backed up?", namesTheDb)

        assertTrue(
            "corlang.db is backed up by name, so Room must NOT be in WAL mode: a commit would " +
                "sit in the un-backed-up corlang.db-wal and a restore would silently lose it. " +
                "Set JournalMode.TRUNCATE in AppDatabase, or stop backing the DB up by file name.",
            appDatabase.contains("JournalMode.TRUNCATE")
        )
    }

    /**
     * The other way out of the bug - shipping the -wal alongside - is worse, not better: Auto
     * Backup copies the two files at different instants, so the restored pair can disagree. If
     * someone reaches for it, they should read the comment in AppDatabase first.
     */
    @Test
    fun `backup rules do not try to carry the sqlite sidecar files`() {
        for ((name, xml) in listOf("backup_rules" to backupRules, "data_extraction_rules" to extractionRules)) {
            for (sidecar in listOf("corlang.db-wal", "corlang.db-shm", "corlang.db-journal")) {
                assertFalse(
                    "$name.xml includes $sidecar. The sidecars are snapshotted at a different " +
                        "instant than corlang.db, so the restored set can be inconsistent. " +
                        "TRUNCATE mode keeps the rows in corlang.db and makes them unnecessary.",
                    xml.contains(sidecar)
                )
            }
        }
    }

    /**
     * DataStore holds the profile and the onboarding flag. It restoring while the database did
     * not is exactly what made the original defect confusing (name present, progress gone), so
     * the include that carries it is worth pinning too.
     */
    @Test
    fun `backup rules carry the datastore prefs as well as the database`() {
        for ((name, xml) in listOf("backup_rules" to backupRules, "data_extraction_rules" to extractionRules)) {
            assertTrue(
                "$name.xml must include datastore/ - it holds the learner profile, the " +
                    "onboarding flag and every per-language preference.",
                xml.contains("datastore/")
            )
        }
    }
}
