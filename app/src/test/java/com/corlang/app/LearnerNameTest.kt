package com.corlang.app

import com.corlang.app.data.prefs.LearnerName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The name field accepted anything non-blank, so "." was a name and the app said
 * "Good morning, ." and put it in the daily notification.
 *
 * The half of this that matters is the second half: a name rule that keeps out "." by demanding
 * two letters, or letters and nothing else, throws out real people with it. Those cases are
 * tested first-class here so nobody tightens the rule later without seeing who it excludes.
 */
class LearnerNameTest {

    @Test
    fun `punctuation alone is not a name`() {
        listOf(".", "..", "-", "'", " . ", "---", "’").forEach {
            assertFalse("accepted $it as a name", LearnerName.isValid(it))
            assertNotNull("no reason given for $it", LearnerName.problem(it))
        }
    }

    @Test
    fun `digits are not a name`() {
        listOf("123", "Ana2", "007").forEach {
            assertFalse("accepted $it", LearnerName.isValid(it))
        }
    }

    @Test
    fun `ordinary names pass`() {
        listOf("Ana", "Marko", "João", "Đorđe", "Siobhán").forEach {
            assertTrue("rejected $it", LearnerName.isValid(it))
            assertNull(LearnerName.problem(it))
        }
    }

    @Test
    fun `names containing a space hyphen or apostrophe pass`() {
        listOf("Anne-Marie", "O'Brien", "O’Brien", "Jan Willem", "Maria da Silva").forEach {
            assertTrue("rejected $it", LearnerName.isValid(it))
        }
    }

    /**
     * The reason the floor is one letter and not two. Single-character given names are ordinary
     * in Chinese and Korean, and rejecting a real name to keep out a full stop is the worse of
     * the two errors.
     */
    @Test
    fun `a single letter is a name`() {
        listOf("李", "A", "Ó").forEach {
            assertTrue("rejected the one-letter name $it", LearnerName.isValid(it))
        }
    }

    @Test
    fun `a joiner cannot open or close a name`() {
        listOf("-Ana", "Ana-", "'Ana", "Ana'").forEach {
            assertFalse("accepted $it", LearnerName.isValid(it))
        }
    }

    @Test
    fun `blank is not an error, it is just unfinished`() {
        listOf("", "   ").forEach {
            assertNull("blank should not scold the learner", LearnerName.problem(it))
            assertFalse("but it is not saveable either", LearnerName.isValid(it))
        }
    }

    @Test
    fun `clean trims and collapses whitespace`() {
        assertEquals("Anne Marie", LearnerName.clean("  Anne   Marie "))
        assertEquals("Ana", LearnerName.clean("Ana\n"))
    }

    @Test
    fun `a name too long for the notification is refused`() {
        val long = "A".repeat(LearnerName.MAX_LENGTH + 1)
        assertFalse(LearnerName.isValid(long))
        assertTrue(LearnerName.isValid("A".repeat(LearnerName.MAX_LENGTH)))
    }
}
