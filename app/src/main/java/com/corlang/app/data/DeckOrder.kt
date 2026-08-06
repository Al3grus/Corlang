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
        // Fast path: nothing is gated, so this is exactly the authored deck.
        if (packs.none { p -> p.fromDay > 0 || p.words.any { it.fromDay > 0 } }) {
            return packs.flatMap { it.words }
        }

        // A word's own gate wins over its pack's, so a closed set can be held back without
        // dragging the rest of a core pack with it.
        val authored = packs.flatMap { pack ->
            pack.words.map { it to (if (it.fromDay > 0) it.fromDay else pack.fromDay) }
        }

        val out = ArrayList<VocabWord>(authored.size)
        // Words whose gate had not opened when the walk reached them, in authored order.
        val waiting = mutableListOf<Pair<VocabWord, Int>>()

        /** Emit anything whose gate has now opened, earliest-authored first. */
        fun release() {
            while (true) {
                val i = waiting.indexOfFirst { slotOf(it.second, perLesson) <= out.size }
                if (i < 0) return
                out += waiting.removeAt(i).first
            }
        }

        // The deck is walked in AUTHORED order and a gate only ever defers: it is a floor, not a
        // summons. Pinning a word to its gate would drag words FORWARD too, which is how a first
        // attempt moved French janvier from lesson 87 up to lesson 16 while trying to hold the
        // months back. Nothing gated may appear early; nothing else may be displaced by it.
        authored.forEach { (word, gate) ->
            if (slotOf(gate, perLesson) <= out.size) {
                out += word
                release()
            } else {
                waiting += word to gate
            }
        }
        // The deck ran out before some gates opened: release the stragglers rather than losing
        // them. A little early beats a word the learner never sees.
        release()
        waiting.forEach { out += it.first }
        return out
    }

    /** First deck slot lesson [fromDay] draws from; 0 means "not gated". */
    private fun slotOf(fromDay: Int, perLesson: Int): Int =
        if (fromDay > 0) (fromDay - 1) * perLesson else 0
}
