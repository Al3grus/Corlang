package com.corlang.app.data

import com.corlang.app.data.model.VocabPack
import com.corlang.app.data.model.VocabWord

/**
 * The order words are introduced in, with themed packs held back until the course has taught them.
 *
 * Deck order is SRS introduction order and the deck is authored FREQUENCY-FIRST, deliberately
 * running alongside the lessons rather than behind them: only about half of any course's deck
 * ever appears in lesson text at all, so "nothing before its lesson" is not a rule the deck could
 * satisfy, and it is not meant to.
 *
 * What is a real defect is a THEMED BLOCK arriving long before the lesson that teaches it. Croatian
 * taught all twelve months in flashcards at lesson 8, sixteen lessons before "Days, months &
 * schedules" at lesson 25. Nobody learns siječanj cold from a card; they meet it, fail it, and it
 * comes back until the lesson finally explains what it is. That is not frequency-first, it is a
 * scheduling accident.
 *
 * A pack can therefore declare [VocabPack.fromDay], the earliest lesson its words may appear at.
 * Gating REORDERS rather than filters: a held pack's words are released at their day and the deck
 * behind them moves up to fill the gap, so the deck keeps its exact size (the course floor depends
 * on it) and a learner is never left short of new words. Nothing is dropped, nothing is duplicated.
 */
object DeckOrder {

    /**
     * The deck in introduction order. A pack with `fromDay = F` cannot appear before index
     * `(F - 1) * perLesson`, which is the first slot lesson F draws from.
     *
     * Stable: within the released and the held groups alike, authored order is preserved, so the
     * order is a pure function of the content and cannot drift between runs.
     */
    fun ordered(packs: List<VocabPack>, perLesson: Int): List<VocabWord> {
        // Fast path: no pack is gated, so this is exactly the authored deck.
        if (packs.none { it.fromDay > 0 }) return packs.flatMap { it.words }

        val open = ArrayDeque<VocabWord>()
        // Held packs stay separate, each with the slot it opens at, so a pack gated to day 10
        // is not stuck behind one gated to day 25 that happens to be authored before it.
        val held = mutableListOf<Pair<Int, ArrayDeque<VocabWord>>>()
        packs.forEach { pack ->
            if (pack.fromDay > 0) {
                held += (pack.fromDay - 1) * perLesson to ArrayDeque(pack.words)
            } else {
                pack.words.forEach { open.addLast(it) }
            }
        }

        val total = open.size + held.sumOf { it.second.size }
        val out = ArrayList<VocabWord>(total)
        while (out.size < total) {
            // Anything whose slot has arrived goes now, earliest gate first.
            val due = held.withIndex()
                .filter { it.value.second.isNotEmpty() && it.value.first <= out.size }
                .minByOrNull { it.value.first }
            if (due != null) {
                out += due.value.second.removeFirst()
                continue
            }
            if (open.isNotEmpty()) {
                out += open.removeFirst()
                continue
            }
            // Only held words remain and none is due yet: the deck has run out of filler, so
            // release the next one rather than leaving the learner with nothing. A little early
            // beats an empty lesson.
            held.filter { it.second.isNotEmpty() }.minByOrNull { it.first }
                ?.let { out += it.second.removeFirst() }
        }
        return out
    }
}
