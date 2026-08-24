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

    /** What a band is authored to hold. Scoring reads the band's REAL size, see [neededToPass]. */
    const val ITEMS_PER_BAND = 4

    /** Groups a test's questions into bands, easiest first. */
    fun bandsOf(questions: List<PlacementQuestion>): List<Band> =
        questions.groupBy { it.level to it.startDay }
            .map { (key, items) -> Band(key.first, key.second, items.sortedBy { it.difficulty }) }
            .sortedWith(compareBy({ it.startDay }, { it.level }))

    /**
     * How many correct answers clear a band of [size] items.
     *
     * Four items needing three is the rule the courses are authored to: it takes the odds of
     * guessing through a band on four-option items from 15.6% (the old three-needing-two) to
     * 5.1%, which is the difference between a lucky learner being promoted a whole band once in
     * six probes and once in twenty. Three-needing-two is kept for smaller bands rather than
     * demanding a clean sweep: requiring every item to be right would fail a learner who knows
     * the material and mis-taps once, which is the exact failure this design exists to avoid.
     */
    fun neededToPass(size: Int): Int = when {
        size >= 4 -> 3
        size >= 2 -> 2
        else -> 1
    }

    /** True if enough of a band's items were answered correctly to clear it. */
    fun bandCleared(correct: Int, size: Int): Boolean = correct >= neededToPass(size)

    /**
     * Whether a band's outcome is already decided, so its remaining items can be skipped: enough
     * right clears it, enough wrong makes clearing impossible. Saves a question or two per band
     * without changing any verdict.
     */
    fun bandDecided(correct: Int, wrong: Int, size: Int): Boolean {
        val need = neededToPass(size)
        return correct >= need || wrong > size - need
    }

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
     *
     * [courseEnd] is the course's FINAL lesson as (level, day), and it exists for one case: the
     * top band being cleared. A band anchor answers "you cleared this much, so start here", which
     * is right in the middle of the ladder and wrong at the end of it. The top band is authored
     * some way short of the last lesson (Portuguese anchors it at 225 of 250, Croatian at 310 of
     * 344), so a learner who answered every question the test has was told "you're at the top of
     * this course" and then placed twenty-five lessons short of it, with material they had just
     * demonstrated still ahead of them. Clearing the top band means the test ran out of
     * questions, not that the learner ran out of course: they belong at the end.
     *
     * Only ever moves the placement FORWARD (`maxOf`), so a course whose last lesson somehow sat
     * below the top band's anchor could not drag a learner backwards.
     */
    fun result(
        bands: List<Band>,
        state: Search,
        courseEnd: Pair<String, Int>? = null,
    ): Pair<String, Int> {
        val i = state.placedIndex
        if (i < 0) return (bands.firstOrNull()?.level ?: "A0") to 1
        val band = bands[i]
        if (i == bands.lastIndex && courseEnd != null && courseEnd.second > band.startDay) {
            return courseEnd
        }
        return band.level to band.startDay
    }

    /**
     * Worst-case number of items a learner will answer: one binary search over [bandCount]
     * bands, [itemsPerBand] items per probe. Used to size the progress bar honestly, so it takes
     * the real band size rather than assuming every course has caught up to [ITEMS_PER_BAND].
     */
    fun maxItems(bandCount: Int, itemsPerBand: Int = ITEMS_PER_BAND): Int {
        var probes = 0
        var lo = 0
        var hi = bandCount - 1
        while (lo <= hi) { probes++; lo = (lo + hi) / 2 + 1 }   // deepest path: always clearing
        var lo2 = 0
        var hi2 = bandCount - 1
        var probes2 = 0
        while (lo2 <= hi2) { probes2++; hi2 = (lo2 + hi2) / 2 - 1 }  // deepest path: always failing
        return maxOf(probes, probes2) * itemsPerBand
    }
}
