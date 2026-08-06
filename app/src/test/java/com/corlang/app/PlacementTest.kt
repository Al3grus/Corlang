package com.corlang.app

import com.corlang.app.data.Placement
import com.corlang.app.data.model.PlacementQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adaptive placement scoring: three items per band, 2 of 3 clears it, bands located by binary
 * search. Replaces "one item per band, first wrong answer ends the test", which gave a guesser
 * a 25% chance of jumping a band and an advanced learner a 34% chance of being placed too low
 * by a single mis-tap.
 */
class PlacementTest {

    private fun q(level: String, day: Int, difficulty: Int = 1) = PlacementQuestion(
        level = level, startDay = day, difficulty = difficulty,
        prompt = "p$level$day$difficulty", options = listOf("a", "b", "c", "d"), answer = "a"
    )

    private fun ladder(vararg bands: Pair<String, Int>): List<PlacementQuestion> =
        bands.flatMapIndexed { i, (lv, day) -> (1..3).map { q(lv, day, i + 1) } }

    // ---- band grouping ----

    @Test fun `questions group into bands, easiest first`() {
        val bands = Placement.bandsOf(ladder("B1" to 101, "A0" to 1, "A2" to 46))
        assertEquals(listOf(1, 46, 101), bands.map { it.startDay })
        assertEquals(listOf("A0", "A2", "B1"), bands.map { it.level })
        assertTrue("every band carries its items", bands.all { it.items.size == 3 })
    }

    // ---- the pass rule: 3 of 4, and 2 of 3 for a band a course has not grown yet ----

    @Test fun `a full band needs three of its four items`() {
        assertTrue(Placement.bandCleared(3, size = 4))
        assertTrue(Placement.bandCleared(4, size = 4))
        assertFalse("two of four is no longer a pass", Placement.bandCleared(2, size = 4))
        assertFalse(Placement.bandCleared(0, size = 4))
    }

    @Test fun `a three item band still passes on two`() {
        assertTrue(Placement.bandCleared(2, size = 3))
        assertFalse(Placement.bandCleared(1, size = 3))
    }

    @Test fun `a band stops early once its outcome cannot change`() {
        assertTrue("three right settles it", Placement.bandDecided(correct = 3, wrong = 0, size = 4))
        assertTrue("two wrong settles it", Placement.bandDecided(correct = 0, wrong = 2, size = 4))
        assertFalse("one wrong is still open", Placement.bandDecided(correct = 1, wrong = 1, size = 4))
        assertFalse("nothing answered is open", Placement.bandDecided(correct = 0, wrong = 0, size = 4))
        assertTrue("the smaller band keeps its own rule",
            Placement.bandDecided(correct = 2, wrong = 0, size = 3))
    }

    @Test fun `one careless slip no longer ends the test`() {
        // Miss the first item of a band, get the rest: the band is still cleared.
        assertFalse(Placement.bandDecided(correct = 0, wrong = 1, size = 4))
        assertTrue(Placement.bandCleared(3, size = 4))
    }

    /**
     * The whole point of the change: the chance of guessing through a band of four-option items.
     * 3-of-4 must be materially harder to fluke than the 2-of-3 it replaced.
     */
    @Test fun `guessing through a band is far less likely than it was`() {
        fun flukeOdds(size: Int): Double {
            val need = Placement.neededToPass(size)
            fun c(n: Int, k: Int): Double {
                var r = 1.0
                for (i in 0 until k) r = r * (n - i) / (i + 1)
                return r
            }
            return (need..size).sumOf { k ->
                c(size, k) * Math.pow(0.25, k.toDouble()) * Math.pow(0.75, (size - k).toDouble())
            }
        }
        val old = flukeOdds(3)
        val new = flukeOdds(4)
        assertEquals(0.156, old, 0.001)
        assertEquals(0.051, new, 0.001)
        assertTrue("the new rule must be at least twice as hard to fluke", new * 2 < old)
    }

    // ---- the search ----

    @Test fun `clearing every band places at the top`() {
        val bands = Placement.bandsOf(ladder("A0" to 1, "A1" to 15, "A2" to 61, "B1" to 151))
        var s = Placement.start(bands.size)
        while (!s.finished) s = Placement.advance(s, s.probe!!, cleared = true)
        assertEquals("B1" to 151, Placement.result(bands, s))
    }

    @Test fun `failing every band places at the course start`() {
        val bands = Placement.bandsOf(ladder("A0" to 1, "A1" to 15, "A2" to 61, "B1" to 151))
        var s = Placement.start(bands.size)
        while (!s.finished) s = Placement.advance(s, s.probe!!, cleared = false)
        assertEquals("A0" to 1, Placement.result(bands, s))
    }

    @Test fun `the search finds the exact boundary between known and unknown`() {
        val spec = listOf("A0" to 1, "A1" to 15, "A1" to 31, "A2" to 61, "A2" to 85,
                          "A2" to 91, "B1" to 151, "B1" to 184, "B1" to 207)
        val bands = Placement.bandsOf(ladder(*spec.toTypedArray()))
        // Simulate a learner who truly clears bands 0..4 and fails everything above.
        val trueCeiling = 4
        var s = Placement.start(bands.size)
        while (!s.finished) {
            val p = s.probe!!
            s = Placement.advance(s, p, cleared = p <= trueCeiling)
        }
        assertEquals(spec[trueCeiling], Placement.result(bands, s))
    }

    @Test fun `every possible ceiling is found exactly`() {
        val spec = (0 until 13).map { "L$it" to (it * 10 + 1) }
        val bands = Placement.bandsOf(ladder(*spec.toTypedArray()))
        for (ceiling in -1 until bands.size) {
            var s = Placement.start(bands.size)
            while (!s.finished) {
                val p = s.probe!!
                s = Placement.advance(s, p, cleared = p <= ceiling)
            }
            val expected = if (ceiling >= 0) spec[ceiling] else (spec.first().first to 1)
            assertEquals("ceiling $ceiling", expected, Placement.result(bands, s))
        }
    }

    @Test fun `a settled search offers no further probe`() {
        val bands = Placement.bandsOf(ladder("A0" to 1))
        var s = Placement.start(bands.size)
        s = Placement.advance(s, s.probe!!, cleared = true)
        assertTrue(s.finished)
        assertNull(s.probe)
    }

    // ---- length ----

    @Test fun `the test stays a short sitting at any ladder size`() {
        // Four probes settle a 9 to 13 band ladder, so the worst case is four full bands. That
        // is the price of the harder pass rule, and it is only the worst case: a band stops the
        // moment its outcome is decided, so three right in a row ends it at three.
        assertEquals(16, Placement.maxItems(9))
        assertEquals(16, Placement.maxItems(13))
        assertTrue("no ladder we ship may run long", Placement.maxItems(13) <= 16)
        // A course still authored at three items per band keeps the old, shorter worst case.
        assertEquals(12, Placement.maxItems(9, itemsPerBand = 3))
    }
}
