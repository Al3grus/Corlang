package com.corlang.app.billing

import com.corlang.app.BuildConfig
import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for what this install has paid for. Two independent axes:
 *  1. [entitled] — the AI subscription ("Premium"): unlocks the Learn tab (tutor + feedback).
 *  2. [unlockedLevels] / [key] — one-time level unlocks, PER LANGUAGE.
 *
 * ## What is free
 *
 * The first `meta.json` → `freeLessons` lessons of each course, and nothing else. Two properties
 * of that rule are load-bearing:
 *
 *  - It is an **absolute** day index, never "the next N lessons from wherever you are". The
 *    placement test writes a start day, and `TodayScreen` takes `maxOf(currentDay, ...)`, so a
 *    relative window would hand a placed learner lessons 150-164 of a level nobody paid for.
 *    Everything here takes the day's own number, which placement cannot move.
 *  - It is **per-language data**, so an author can land the cut on a level boundary. Croatian
 *    sets 16 and gives away exactly A0; Portuguese has no A0, so its 15 falls inside A1 and the
 *    A1 product sells the remaining 30 lessons. A flat constant would leave Croatian with one
 *    orphaned A0 lesson that no product on the store could unlock.
 *
 * ## Why unlocks are per-language
 *
 * They were global until v0.49.0: one `unlock_a2` opened A2 in every course. With two live
 * courses that meant a single payment handed over 584 lessons, and almost nobody studies both,
 * so it was a discount for a case that does not happen. Keys are now `"<lang>:<LEVEL>"`.
 *
 * Everything that gates reads this layer; nothing else knows where entitlement came from, so
 * [BillingManager] writes here and the rest of the app is unchanged. DEV_PREMIUM (sideload
 * flavor, gitignored local.properties) force-unlocks everything for the developer's own
 * testing against the deployed proxy.
 */
class PremiumManager(private val prefs: LanguagePrefs) {

    companion object {
        /**
         * The entitlement key for one course level. Language codes and CEFR ids never contain a
         * colon or a comma, so this round-trips through the comma-joined preference safely.
         */
        fun key(lang: String, levelId: String) = "$lang:$levelId"

        /**
         * Is the lesson on [day] behind the paywall?
         *
         * [day] is the lesson's own number in the course, never a count from where the learner
         * happens to be standing. That distinction is the whole point: placement writes a start
         * day, so a window measured from it would give someone dropped at lesson 150 a free run
         * through 150..164 of a level they never bought.
         *
         * Pure, and deliberately free of `BuildConfig.DEV_PREMIUM` — the caller applies that.
         * Folding it in here would make this gate pass vacuously on a developer's machine, which
         * is precisely the machine the test runs on.
         */
        fun dayLocked(
            lang: String,
            levelId: String,
            day: Int,
            freeLessons: Int,
            unlocked: Set<String>,
        ): Boolean = day > freeLessons && key(lang, levelId) !in unlocked

        /**
         * Every level up to and including [levelId], in course order — what one purchase grants.
         *
         * Unlocks are CUMULATIVE. A course is a ladder, so owning A2 but not A1 is a state that
         * should not exist: the learner's reviews are full of A1 words by construction (the deck
         * introduces in course order), placement explicitly queues the run-up to their level for
         * review, and a mis-placed learner is told to go back and fill the gaps. Selling a rung
         * without the ladder beneath it makes all three incoherent.
         *
         * A level the course does not list grants only itself: the product named it, so honour
         * that, but never invent an order for it.
         */
        fun levelsThrough(order: List<String>, levelId: String): Set<String> {
            val i = order.indexOf(levelId)
            return if (i < 0) setOf(levelId) else order.take(i + 1).toSet()
        }

        /**
         * The last lesson the learner can open, walking from day 1 and stopping at the first
         * locked one.
         *
         * Deliberately a PREFIX rather than "every unlocked day": it answers "how far does this
         * course go for this learner", which is what the placement seed needs, and cumulative
         * unlocks make the accessible set a prefix anyway. Returns 0 when even day 1 is locked,
         * which cannot happen today but keeps callers total.
         */
        fun accessibleThroughDay(
            days: List<com.corlang.app.data.model.StudyDay>,
            lang: String,
            freeLessons: Int,
            unlocked: Set<String>,
        ): Int = days.sortedBy { it.day }
            .takeWhile { !dayLocked(lang, it.level, it.day, freeLessons, unlocked) }
            .lastOrNull()?.day ?: 0
    }

    // ---- Axis 1: AI subscription ----

    val entitled: Flow<Boolean> =
        if (BuildConfig.DEV_PREMIUM) prefs.premiumEntitled.map { true }
        else prefs.premiumEntitled

    /** Written by BillingManager after a subscription purchase, with its Play token. */
    suspend fun grantSubscription(purchaseToken: String) {
        prefs.setPremiumEntitled(true)
        prefs.setSubPurchaseToken(purchaseToken)
    }

    /** Called when Play reports the subscription expired/refunded/not present. */
    suspend fun revokeSubscription() {
        prefs.setPremiumEntitled(false)
        prefs.setSubPurchaseToken(null)
    }

    // ---- Axis 2: one-time level unlocks ----

    /** Levels the learner has bought, as `"<lang>:<LEVEL>"` keys. */
    val unlockedLevels: Flow<Set<String>> = prefs.unlockedLevels

    /** Adds [levels] of [lang] to the purchased set (union; a purchase passes its whole ladder). */
    suspend fun grantLevels(lang: String, levels: Set<String>) {
        val current = prefs.unlockedLevels.first()
        prefs.setUnlockedLevels(current + levels.map { key(lang, it) })
    }
}
