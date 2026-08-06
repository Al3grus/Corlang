package com.corlang.app

import com.corlang.app.speech.PronunciationScore
import com.corlang.app.speech.PronunciationScore.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The point of this scorer is that it must not fail correct speech. The old rule (80% of the
 * target's words present verbatim) did exactly that, so each case here is a real recogniser
 * behaviour that used to be reported to the learner as bad pronunciation.
 */
class PronunciationScoreTest {

    private fun verdict(target: String, vararg heard: String) =
        PronunciationScore.verdict(target, heard.toList())

    @Test fun `an exact match passes`() {
        assertEquals(Verdict.PASS, verdict("Dobar dan", "Dobar dan"))
    }

    @Test fun `casing and punctuation are not pronunciation`() {
        assertEquals(Verdict.PASS, verdict("Dobar dan", "dobar dan!"))
        assertEquals(Verdict.PASS, verdict("Como está?", "Como esta"))
    }

    @Test fun `a recogniser that drops diacritics still passes`() {
        // Recognisers routinely return unaccented text; the learner said it correctly.
        assertEquals(Verdict.PASS, verdict("Où est la gare ?", "Ou est la gare"))
        assertEquals(Verdict.PASS, verdict("Želim kavu", "Zelim kavu"))
    }

    @Test fun `merged or split words still pass`() {
        // "de l'eau" comes back as one token often enough to matter; word-set overlap scored
        // this as a total miss, which is the defect this scorer exists to fix.
        assertEquals(Verdict.PASS, verdict("Je voudrais de l'eau", "Je voudrais deleau"))
    }

    @Test fun `one wrong ending is close, not a miss`() {
        val v = verdict("Ich möchte einen Kaffee", "Ich möchte ein Kaffee")
        assertTrue("a single wrong ending must not read as a miss", v != Verdict.MISS)
    }

    @Test fun `unrelated speech misses`() {
        assertEquals(Verdict.MISS, verdict("Dobar dan", "hello there my friend"))
        assertEquals(Verdict.MISS, verdict("Bonjour", "spaghetti"))
    }

    @Test fun `every hypothesis is considered, not just the first`() {
        // The recogniser ranks by its own confidence, which is tuned for native speech. The
        // second guess being the right one is the normal case for a learner.
        assertEquals(Verdict.PASS, verdict("Bom dia", "bondi", "bom dia", "bom dial"))
    }

    @Test fun `the best hypothesis is the one shown back`() {
        assertEquals(
            "bom dia",
            PronunciationScore.bestHypothesis("Bom dia", listOf("bondi", "bom dia", "bonjour"))
        )
    }

    @Test fun `nothing heard is a miss, not a crash`() {
        assertEquals(Verdict.MISS, PronunciationScore.verdict("Bom dia", emptyList()))
        assertEquals(null, PronunciationScore.bestHypothesis("Bom dia", emptyList()))
    }

    @Test fun `an empty target cannot pass`() {
        assertEquals(0.0, PronunciationScore.similarity("", "anything"), 0.0001)
    }
}
