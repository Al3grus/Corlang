package com.corlang.app

import com.corlang.app.data.DrillGen
import com.corlang.app.data.model.Example
import com.corlang.app.data.model.VocabWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillGenTest {

    private fun word(hr: String, target: String, gloss: String = "gloss") =
        VocabWord(id = hr, hr = hr, en = "x", example = Example(target, gloss))

    @Test
    fun `cloze blanks the inflected form and keeps the answer among options`() {
        val c = DrillGen.clozeFor(word("kava", "Pijem kavu s mlijekom."), shuffle = false)!!
        assertEquals("Pijem ___ s mlijekom.", c.sentence)
        assertEquals("kavu", c.answer)
        assertTrue(c.answer in c.options)
        assertTrue(c.options.size >= 3)
        assertEquals(c.options.size, c.options.distinct().size)
    }

    @Test
    fun `cloze handles locative and instrumental forms`() {
        val c = DrillGen.clozeFor(word("prijatelj", "Putujem s prijateljem na more."), shuffle = false)!!
        assertEquals("prijateljem", c.answer)
        assertTrue("Putujem s ___ na more." == c.sentence)
    }

    @Test
    fun `cloze returns null when the form cannot be located`() {
        // Suppletive stem: pisati → pišem shares too short a prefix.
        assertNull(DrillGen.clozeFor(word("pisati", "Pišem pismo bratu.")))
        // Multi-word heads are skipped.
        assertNull(DrillGen.clozeFor(word("dobar dan", "Dobar dan svima!")))
        // No example → null.
        assertNull(DrillGen.clozeFor(VocabWord(id = "x", hr = "kava", en = "coffee")))
    }

    @Test
    fun `distractors are ending mutations, never equal to the answer`() {
        val muts = DrillGen.endingMutations("kavu")
        assertTrue(muts.contains("kava"))
        assertTrue(muts.contains("kavom"))
        val c = DrillGen.clozeFor(word("žena", "Vidim ženu na trgu."), shuffle = false)!!
        assertTrue(c.options.none { it != c.answer && it.equals(c.answer, ignoreCase = true) })
    }

    @Test
    fun `recall items carry gloss, answer and pos hint`() {
        val items = DrillGen.buildRecallItems(
            listOf(VocabWord(id = "kuća", hr = "kuća", en = "house", pos = "n. f.")), 5
        )
        assertEquals(1, items.size)
        assertEquals("kuća", items[0].answerHr)
        assertEquals("n. f.", items[0].posHint)
    }
}
