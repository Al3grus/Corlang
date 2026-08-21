package com.corlang.app

import com.corlang.app.ai.ReplyGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Both directions, because a guard that rejects everything is as broken as one that rejects
 * nothing. The GOOD cases are the important half: they are real tutor replies, and if the guard
 * starts eating those the learner gets a retry loop instead of a lesson.
 */
class ReplyGuardTest {

    private fun rejects(reply: String) = ReplyGuard.inspect(reply, "hr") is ReplyGuard.Verdict.Reject
    private fun reason(reply: String) =
        (ReplyGuard.inspect(reply, "hr") as ReplyGuard.Verdict.Reject).reason

    // ---------- must be rejected ----------

    @Test
    fun `the reported failure is caught`() {
        // The exact shape of the field report: a Russian word, then the model narrating recovery.
        val bad = "Volim кофе... russian word, wait, let me stay in croatian/english properly. " +
            "Volim kavu!"
        assertTrue(rejects(bad))
    }

    @Test
    fun `a single foreign letter is enough`() {
        assertTrue(rejects("Dobar dan! Kako si? Да, dobro."))
        assertTrue(rejects("Volim kavu. 好"))
    }

    @Test
    fun `self correction never reaches the learner`() {
        listOf(
            "Oops, that came out wrong. Volim kavu.",
            "My mistake, I wrote that badly. Volim kavu.",
            "Let me try that again. Volim kavu.",
            "Scratch that, here is a better example.",
            "I apologise, that was unclear.",
            "As an AI, I should mention that Croatian has seven cases.",
            "Hmm, I should stay in Croatian. Volim kavu."
        ).forEach { assertTrue("should reject: $it", rejects(it)) }
    }

    @Test
    fun `leaked scaffolding is caught`() {
        assertTrue(rejects("assistant: Dobar dan!"))
        assertTrue(rejects("<thinking>the student wants coffee</thinking> Volim kavu."))
    }

    @Test
    fun `an empty reply is a failure not a message`() {
        assertTrue(rejects("   "))
        assertEquals("empty reply", reason(""))
    }

    @Test
    fun `the tutor never hands over the answer it is asking for`() {
        // The field report, verbatim in shape: the ask, then the words to build it from.
        assertTrue(rejects(
            "Can you try saying \"i dont work, i look for a job\" using \"ne radim\" " +
                "and \"trazim posao\"?"
        ))
        listOf(
            "Now write it using 'volim kavu'.",
            "Try answering with the words 'dobar dan' and 'hvala'.",
            "How would you say that? Type 'idem u trgovinu'.",
            "Make a sentence using ‘moja sestra’.",
            "Repeat it, starting with 'ja sam'."
        ).forEach { assertTrue("should reject: $it", rejects(it)) }
    }

    @Test
    fun `the reason names the fragment that was handed over`() {
        val reply = "Can you try saying \"i dont work\" using \"ne radim\"?"
        assertTrue(reason(reply).contains("ne radim"))
    }

    // ---------- must NOT be rejected ----------

    @Test
    fun `asking for a meaning is the exercise working, not a leak`() {
        listOf(
            // The English side may be quoted freely: that IS the prompt.
            "How would you say 'I like coffee'?",
            "Can you tell me \"I don't work\" in Croatian?",
            "Try saying it using the accusative.",
            "Write a sentence using the past tense.",
            "Answer using three words.",
            // Quoting to correct or to teach is not quoting to hand over.
            "Small correction: it is 'Volim kavu', not 'Volim kava'. Now, what do you drink?",
            "'Kruh' means bread. What do you eat for breakfast?",
            "You wrote 'Ja sam iz Amerike' and that is perfect. Where do you live now?",
            // Describing the language, not asking for it.
            "In Croatian we write 'ije' in vrijeme, never 'je'. Where do you live?",
            "You can write 'volim kavu' or 'volim caj', both work. Which do you prefer?",
            "Answer with 'da' or 'ne'."
        ).forEach {
            assertTrue("should PASS: $it", ReplyGuard.inspect(it, "hr") is ReplyGuard.Verdict.Ok)
        }
    }


    @Test
    fun `ordinary teaching passes`() {
        listOf(
            "Bok! Kako si danas?",
            "Dobar dan! Ja sam tvoj tutor. O čemu želiš razgovarati?",
            "Let me explain: in Croatian, the object of a verb takes the accusative. " +
                "How would you say 'I drink water'?",
            "Small correction: it is 'Volim kavu', not 'Volim kava'. The object takes -u. " +
                "Now, what do you drink in the morning?",
            "Točno! Odlično. A što piješ ujutro?",
            "You wrote 'Ja sam iz Amerike' and that is perfect. Where do you live now?"
        ).forEach { assertTrue("should PASS: $it", ReplyGuard.inspect(it, "hr") is ReplyGuard.Verdict.Ok) }
    }

    @Test
    fun `correcting the student is not correcting itself`() {
        val reply = "That is not right, but you are close: it is 'Volim kavu'. " +
            "The -u ending marks the object. Try again with tea."
        assertTrue(ReplyGuard.inspect(reply, "hr") is ReplyGuard.Verdict.Ok)
    }

    @Test
    fun `answering a question about another language is allowed`() {
        // A learner may reasonably ask whether Croatian is like Russian. Naming the language is
        // only a signal when it appears beside the model hedging about what it is writing in.
        val reply = "Yes, Croatian and Russian are both Slavic, so some roots look familiar. " +
            "But the grammar differs. Kako se kaže 'voda' na tvom jeziku?"
        assertTrue(ReplyGuard.inspect(reply, "hr") is ReplyGuard.Verdict.Ok)
    }

    @Test
    fun `diacritics and punctuation are not foreign scripts`() {
        val reply = "Čaj ili kava? Đak uči, žena čita, a dijete spava. Što ti voliš?"
        assertTrue(ReplyGuard.inspect(reply, "hr") is ReplyGuard.Verdict.Ok)
    }

    @Test
    fun `french and portuguese replies pass their own guard`() {
        assertTrue(ReplyGuard.inspect("Bonjour ! Ça va ? J'aime le café.", "fr")
            is ReplyGuard.Verdict.Ok)
        assertTrue(ReplyGuard.inspect("Olá! Como estás? Gosto de café.", "pt")
            is ReplyGuard.Verdict.Ok)
    }
}
