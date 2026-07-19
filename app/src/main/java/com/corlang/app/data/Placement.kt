package com.corlang.app.data

import com.corlang.app.data.model.PlacementQuestion

/**
 * Adaptive placement scoring.
 *
 * The old rule was "walk the questions easiest to hardest, and the FIRST wrong answer ends the
 * test", with roughly one question per ability band. Two things were wrong with it:
 *
 *  - One item decided a whole band, so a lucky guess on a four-option question promoted a
 *    learner an entire band (25% of the time), and a careless slip demoted them. An advanced
 *    learner clearing eight bands had a 34% chance of being placed too low by ONE mis-tap.
 *  - Sudden death meant that slip ended the test outright; nothing later could redeem it.
 *
 * Now each band carries three independent items and is cleared on 2 of 3, which takes the
 * guess-through rate to 15.6% and the slip-induced misplacement to 3%. To keep the test short
 * despite tripling the items, bands are probed by BINARY SEARCH rather than walked in order:
 * about four band probes settle a nine to thirteen band ladder, so a learner answers around a
 * dozen items whatever their level, which is no more than the old test asked.
 *
 * All of this is pure so it can be tested without a UI.
 */
object Placement {

    /** Items sharing one (level, startDay) anchor: the unit a learner passes or fails. */
    data class Band(val level: String, val startDay: Int, val items: List<PlacementQuestion>)

    const val ITEMS_PER_BAND = 3
    const val NEEDED_TO_PASS = 2

    /** Groups a test's questions into bands, easiest first. */
    fun bandsOf(questions: List<PlacementQuestion>): List<Band> =
        questions.groupBy { it.level to it.startDay }
            .map { (key, items) -> Band(key.first, key.second, items.sortedBy { it.difficulty }) }
            .sortedWith(compareBy({ it.startDay }, { it.level }))

    /** True if enough of a band's items were answered correctly to clear it. */
    fun bandCleared(correct: Int): Boolean = correct >= NEEDED_TO_PASS

    /**
     * Whether a band's outcome is already decided, so the third item can be skipped: two right
     * clears it, two wrong fails it. Saves a question per band without changing any verdict.
     */
    fun bandDecided(correct: Int, wrong: Int): Boolean =
        correct >= NEEDED_TO_PASS || wrong > ITEMS_PER_BAND - NEEDED_TO_PASS

    /**
     * The search state. [lo] and [hi] bracket the bands still in question; [placedIndex] is the
     * highest band cleared so far, or -1 when none has been.
     */
    data class Search(val lo: Int, val hi: Int, val placedIndex: Int = -1) {
        val finished: Boolean get() = lo > hi
        /** The band to probe next, or null when the search has settled. */
        val probe: Int? get() = if (finished) null else (lo + hi) / 2
    }

    fun start(bandCount: Int) = Search(lo = 0, hi = bandCount - 1)

    /** Folds one band result into the search: cleared means look higher, failed means lower. */
    fun advance(state: Search, probeIndex: Int, cleared: Boolean): Search =
        if (cleared) state.copy(lo = probeIndex + 1, placedIndex = maxOf(state.placedIndex, probeIndex))
        else state.copy(hi = probeIndex - 1)

    /**
     * Where the learner lands. Falls back to the easiest band's own anchor when nothing was
     * cleared, which is the course's first lesson rather than a hardcoded day 1.
     */
    fun result(bands: List<Band>, state: Search): Pair<String, Int> {
        val i = state.placedIndex
        return if (i >= 0) bands[i].level to bands[i].startDay
        else (bands.firstOrNull()?.level ?: "A0") to 1
    }

    /**
     * Worst-case number of items a learner will answer: one binary search over [bandCount]
     * bands, three items per probe. Used to size the progress bar honestly.
     */
    fun maxItems(bandCount: Int): Int {
        var probes = 0
        var lo = 0
        var hi = bandCount - 1
        while (lo <= hi) { probes++; lo = (lo + hi) / 2 + 1 }   // deepest path: always clearing
        var lo2 = 0
        var hi2 = bandCount - 1
        var probes2 = 0
        while (lo2 <= hi2) { probes2++; hi2 = (lo2 + hi2) / 2 - 1 }  // deepest path: always failing
        return maxOf(probes, probes2) * ITEMS_PER_BAND
    }
}
