package com.corlang.app

import com.corlang.app.data.DeckOrder
import com.corlang.app.data.model.VocabPack
import com.corlang.app.data.model.VocabWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckOrderTest {

    private fun word(id: String) = VocabWord(id = id, hr = id, en = id)

    private fun pack(id: String, n: Int, fromDay: Int = 0, offset: Int = 0) = VocabPack(
        id = id, title = id, level = "A1", fromDay = fromDay,
        words = (1..n).map { word("$id-${it + offset}") }
    )

    @Test fun `an ungated deck is exactly the authored order`() {
        val packs = listOf(pack("a", 3), pack("b", 2))
        assertEquals(
            listOf("a-1", "a-2", "a-3", "b-1", "b-2"),
            DeckOrder.ordered(packs, perLesson = 10).map { it.id }
        )
    }

    @Test fun `a gated pack cannot appear before its lesson`() {
        // months gated to lesson 3 may not appear before slot 20, the first slot lesson 3 draws.
        val packs = listOf(pack("months", 4, fromDay = 3), pack("core", 40))
        val order = DeckOrder.ordered(packs, perLesson = 10)
        order.forEachIndexed { i, w ->
            if (w.id.startsWith("months")) {
                assertTrue("months at slot $i, before lesson 3 starts at 20", i >= 20)
            }
        }
    }

    @Test fun `the deck keeps every word exactly once`() {
        val packs = listOf(pack("months", 12, fromDay = 25), pack("core", 100), pack("late", 5, fromDay = 8))
        val order = DeckOrder.ordered(packs, perLesson = 10)
        assertEquals(117, order.size)
        assertEquals("no word may be dropped or duplicated", 117, order.map { it.id }.distinct().size)
    }

    @Test fun `the deck behind a held pack moves up to fill the gap`() {
        // With months held, the first ten slots must still be full: a learner is never short.
        val packs = listOf(pack("months", 12, fromDay = 25), pack("core", 100))
        val order = DeckOrder.ordered(packs, perLesson = 10)
        assertTrue("lesson 1 must still get ten real words",
            order.take(10).none { it.id.startsWith("months") })
        assertEquals(10, order.take(10).size)
    }

    @Test fun `an earlier gate is not stuck behind a later one authored before it`() {
        val packs = listOf(pack("late", 5, fromDay = 30), pack("early", 5, fromDay = 2), pack("core", 60))
        val order = DeckOrder.ordered(packs, perLesson = 10)
        val firstEarly = order.indexOfFirst { it.id.startsWith("early") }
        val firstLate = order.indexOfFirst { it.id.startsWith("late") }
        assertTrue("the day-2 pack must open before the day-30 one", firstEarly < firstLate)
        assertTrue(firstEarly >= 10)
    }

    /**
     * A gate is a FLOOR, not a summons. A first version pinned gated words to their gate slot,
     * which pulled French janvier from lesson 87 up to lesson 16 while trying to hold the months
     * back: the opposite of the point.
     */
    @Test fun `a gate never pulls a word earlier than it already was`() {
        val early = pack("core", 100)
        val lateSet = VocabPack(
            id = "months", title = "months", level = "A1",
            words = listOf(word("janvier")).map { it.copy(fromDay = 2) }
        )
        val packs = listOf(early, lateSet)
        val order = DeckOrder.ordered(packs, perLesson = 10)
        assertEquals("gated word must stay where the deck put it", 100,
            order.indexOfFirst { it.id == "janvier" })
    }

    @Test fun `a word's own gate overrides its pack's`() {
        val p = VocabPack(
            id = "core", title = "core", level = "A1", fromDay = 0,
            words = listOf(word("a"), word("b").copy(fromDay = 5), word("c"))
        )
        val order = DeckOrder.ordered(listOf(p, pack("filler", 60)), perLesson = 10)
        assertTrue("the gated word waits", order.indexOfFirst { it.id == "b" } >= 40)
        assertTrue("its neighbours do not", order.indexOfFirst { it.id == "c" } < 40)
    }

    @Test fun `a deck of nothing but held words still releases them`() {
        // No filler left, so being early beats leaving the learner with an empty lesson.
        val packs = listOf(pack("months", 12, fromDay = 25))
        val order = DeckOrder.ordered(packs, perLesson = 10)
        assertEquals(12, order.size)
    }

    @Test fun `gating changes nothing for a course that uses none`() {
        val packs = (1..5).map { pack("p$it", 20, offset = it * 100) }
        assertEquals(
            packs.flatMap { it.words }.map { it.id },
            DeckOrder.ordered(packs, perLesson = 10).map { it.id }
        )
    }
}
