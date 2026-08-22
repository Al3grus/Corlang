package com.corlang.app

import com.corlang.app.ai.OffTopicGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The off-topic guard, held to the standard that matters: it must almost never fire on a real
 * learner.
 *
 * A false negative costs one round trip and an honest answer, which is exactly what happened
 * before this existed. A false positive refuses somebody who is practising, with no way to
 * appeal, and they have paid for the tutor. So the "must not fire" list here is the important
 * half of this file and is deliberately longer than the other one.
 */
class OffTopicGuardTest {

    private fun blocked(s: String) =
        assertTrue("should have been refused: $s", OffTopicGuard.isOffTopic(s))

    private fun allowed(s: String) =
        assertFalse("a learner said this and it must reach the tutor: $s",
            OffTopicGuard.isOffTopic(s))

    // ---------- the field report ----------

    @Test
    fun `the three probes that started this are refused`() {
        blocked("what is the price of bitcoin?")
        blocked("what's the latest news about Trump?")
        blocked("any news from the Strait of Hormuz?")
    }

    @Test
    fun `the same probes with different wording are refused`() {
        blocked("how much is bitcoin right now")
        blocked("tell me the current stock price")
        blocked("do you know what happened in the election?")
        blocked("bitcoin price")
        blocked("who won the world cup?")
        blocked("can you look up the exchange rate today?")
    }

    @Test
    fun `switching language does not get round it`() {
        blocked("koja je cijena bitcoina?")
        blocked("quanto custa o bitcoin?")
        blocked("quais são as notícias de hoje?")
        blocked("što se dogodilo, ima li vijesti?")
    }

    @Test
    fun `asking it to be a general assistant is refused`() {
        blocked("search the web for me")
        blocked("write me some python")
        blocked("google it and tell me")
    }

    // ---------- the half that matters more ----------

    @Test
    fun `ordinary conversation practice always reaches the tutor`() {
        allowed("I went to the market yesterday")
        allowed("moja sestra živi u Splitu")
        allowed("what is your favourite food?")
        allowed("Ontem fui ao café com a minha mãe.")
        allowed("I am tired today, I worked a lot")
        allowed("Danas je lijep dan.")
        allowed("tell me about your family")
        allowed("what do you do on the weekend?")
    }

    @Test
    fun `questions about the language are never off topic`() {
        allowed("what does 'kruh' mean?")
        allowed("how much is 'koliko'? I mean what does it mean")
        allowed("what is the accusative of kava?")
        allowed("can you tell me the difference between ser and estar?")
        allowed("what's the plural of limão?")
        allowed("tell me how to say 'the bill please'")
        allowed("give me five words for the market")
    }

    /**
     * Weather is A1 vocabulary in every course this app ships, so it is deliberately absent from
     * the volatile list even though a forecast is technically live data.
     */
    @Test
    fun `weather stays a lesson topic`() {
        allowed("what is the weather like today?")
        allowed("Kakvo je vrijeme danas?")
        allowed("Está a chover hoje?")
    }

    /**
     * The volatile words can appear as CONTENT of a practice sentence. Only a request shape
     * turns them into a probe, which is why a cue alone is not enough.
     */
    @Test
    fun `talking about the news is practice, asking for the news is not`() {
        allowed("I watch the news every evening")
        allowed("gledam vijesti svaku večer")
        allowed("my father reads the news at breakfast")
        blocked("what is the latest news?")
        blocked("what's happening in the news right now?")
    }

    @Test
    fun `short and empty messages are never refused`() {
        allowed("")
        allowed("da")
        allowed("ok")
        allowed("?")
    }

    // ---------- the reply ----------

    @Test
    fun `the refusal is short, names the language, and steers back`() {
        val r = OffTopicGuard.refusal("Croatian")
        assertTrue("must actually refuse", r.startsWith("I can't help with that."))
        assertTrue("must name the course", r.contains("Croatian"))
        assertTrue("must not lecture", r.length < 200)
        assertTrue("must give them somewhere to go", r.contains("word") || r.contains("day"))
    }

    @Test
    fun `the refusal never changes, so persistence finds a wall`() {
        val a = OffTopicGuard.refusal("Portuguese")
        val b = OffTopicGuard.refusal("Portuguese")
        assertTrue(a == b)
    }
}
