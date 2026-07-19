package com.corlang.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "corlang_prefs")

/** The learner's onboarding profile. gender is "m"/"f" (drives Croatian word forms). */
data class LearnerProfile(
    val name: String,
    val gender: String,
    val from: String,
    val livesIn: String,
    val reason: String,
)

/** Persists the user's selected language and small app settings across launches. */
class LanguagePrefs(private val context: Context) {

    private val selectedKey = stringPreferencesKey("selected_language")
    private val reminderKey = booleanPreferencesKey("daily_reminder_enabled")

    val selectedLanguage: Flow<String> =
        context.dataStore.data.map { it[selectedKey] ?: "hr" }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[selectedKey] = code }
    }

    val reminderEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[reminderKey] ?: false }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[reminderKey] = enabled }
    }

    // Which languages the daily reminder may nag about. Absent = follow the selected
    // language (pre-existing behavior); set = only these, so trying a French placement
    // test never turns into daily French nags for a Croatian learner.
    private val reminderLanguagesKey = stringSetPreferencesKey("reminder_languages")

    val reminderLanguages: Flow<Set<String>?> =
        context.dataStore.data.map { it[reminderLanguagesKey] }

    suspend fun setReminderLanguages(langs: Set<String>) {
        context.dataStore.edit { it[reminderLanguagesKey] = langs }
    }

    private val reminderHourKey = intPreferencesKey("reminder_hour")
    private val reminderMinuteKey = intPreferencesKey("reminder_minute")

    /** User-chosen reminder time (default 19:00), "when I plan to study", not a nag time. */
    val reminderTime: Flow<Pair<Int, Int>> =
        context.dataStore.data.map { (it[reminderHourKey] ?: 19) to (it[reminderMinuteKey] ?: 0) }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[reminderHourKey] = hour
            it[reminderMinuteKey] = minute
        }
    }

    // ----- SRS pace -----

    private val newWordsKey = intPreferencesKey("new_words_per_day")

    /**
     * How many brand-new words a lesson introduces. FIXED at [Fsrs.NEW_WORDS_PER_DAY]: the
     * course is paced around it, and letting learners raise it only burned through the finite
     * deck sooner. The dial they actually get is [maxReviewsPerDay]. The key is still read so
     * an existing install that had set 15 or 20 settles back to the paced default.
     */
    val newWordsPerDay: Flow<Int> =
        context.dataStore.data.map { com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY }

    private val maxReviewsKey = intPreferencesKey("max_reviews_per_day")

    /**
     * The learner's own dial: how many due words they are willing to review in a day, on top of
     * the fixed new words each lesson introduces. Bigger means a shorter backlog and faster
     * consolidation; smaller keeps a heavy review day bounded. Default [Fsrs.REVIEW_CAP].
     */
    val maxReviewsPerDay: Flow<Int> =
        context.dataStore.data.map { it[maxReviewsKey] ?: com.corlang.app.data.Fsrs.REVIEW_CAP }

    suspend fun setMaxReviewsPerDay(count: Int) {
        context.dataStore.edit { it[maxReviewsKey] = count }
    }

    // ----- Launch counter (rotates the splash tagline across languages) -----

    private val launchCountKey = intPreferencesKey("launch_count")

    val launchCount: Flow<Int> =
        context.dataStore.data.map { it[launchCountKey] ?: 0 }

    suspend fun bumpLaunchCount() {
        context.dataStore.edit { it[launchCountKey] = (it[launchCountKey] ?: 0) + 1 }
    }

    // ----- Haptic feedback -----

    private val hapticsKey = stringPreferencesKey("haptics_strength")

    /** Vibration strength for learning feedback: OFF / LIGHT / MEDIUM / STRONG. */
    val hapticsStrength: Flow<String> =
        context.dataStore.data.map { it[hapticsKey] ?: "MEDIUM" }

    suspend fun setHapticsStrength(value: String) {
        context.dataStore.edit { it[hapticsKey] = value }
    }

    // ----- Learner profile (onboarding) -----

    private val onboardedKey = booleanPreferencesKey("onboarding_done")
    private val profileNameKey = stringPreferencesKey("profile_name")
    private val profileGenderKey = stringPreferencesKey("profile_gender")       // "m" / "f"
    private val profileFromKey = stringPreferencesKey("profile_from")           // English country name
    private val profileLivesInKey = stringPreferencesKey("profile_lives_in")    // English country name
    private val profileReasonKey = stringPreferencesKey("profile_reason")

    /** True once the first-run intro (profile + goal + level) has been completed or skipped. */
    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[onboardedKey] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[onboardedKey] = done }
    }

    // ----- Per-language placement handling -----
    // Which languages the learner has already been offered placement for (took it, or chose to
    // start at Day 1). Selecting a language NOT in this set — and with no progress yet — triggers
    // the one-time "new language" placement prompt, so profile setup is never repeated.
    private val placementHandledKey = stringSetPreferencesKey("placement_handled_languages")

    val placementHandledLanguages: Flow<Set<String>> =
        context.dataStore.data.map { it[placementHandledKey] ?: emptySet() }

    suspend fun markPlacementHandled(lang: String) {
        context.dataStore.edit {
            it[placementHandledKey] = (it[placementHandledKey] ?: emptySet()) + lang
        }
    }

    /**
     * Who the learner is, captured at onboarding: the name (used by the daily reminder greeting
     * and the tutor prompt) and which word forms the course uses; editable from Settings.
     * from/livesIn/reason are legacy fields kept only so old backups parse.
     */
    val profile: Flow<LearnerProfile> = context.dataStore.data.map {
        LearnerProfile(
            name = it[profileNameKey] ?: "",
            gender = it[profileGenderKey] ?: "m",
            from = it[profileFromKey] ?: "",
            livesIn = it[profileLivesInKey] ?: "",
            reason = it[profileReasonKey] ?: ""
        )
    }

    suspend fun setProfile(p: LearnerProfile) {
        context.dataStore.edit {
            it[profileNameKey] = p.name
            it[profileGenderKey] = p.gender
            it[profileFromKey] = p.from
            it[profileLivesInKey] = p.livesIn
            it[profileReasonKey] = p.reason
        }
    }

    // ----- Premium (unlocks the AI tutor features) -----

    private val premiumKey = booleanPreferencesKey("premium_entitled")

    /**
     * Persisted Play-purchase entitlement. Written by the Play Billing connector once the
     * purchase is verified (see docs/server-ai.md); until billing ships this stays false and
     * the AI features stay dark.
     */
    val premiumEntitled: Flow<Boolean> =
        context.dataStore.data.map { it[premiumKey] ?: false }

    suspend fun setPremiumEntitled(entitled: Boolean) {
        context.dataStore.edit { it[premiumKey] = entitled }
    }

    // The active AI-subscription Play purchase token. Sent to the worker (x-corlang-sub) so the
    // 40-msg/day cap can be keyed per subscriber WITHOUT any user account — the token IS the
    // stable identity. Cleared on lapse/refund.
    private val subTokenKey = stringPreferencesKey("premium_sub_token")

    val subPurchaseToken: Flow<String?> =
        context.dataStore.data.map { it[subTokenKey] }

    suspend fun setSubPurchaseToken(token: String?) {
        context.dataStore.edit {
            if (token == null) it.remove(subTokenKey) else it[subTokenKey] = token
        }
    }

    // ----- Level unlocks (one-time IAP; global across languages) -----

    // A0/A1 are free forever; A2/B1/B2 are unlocked by a one-time purchase (or the bundle).
    // Stored as a comma-joined set of CEFR level ids. Global, not per-language, by design:
    // buying A2 unlocks A2 in every course. Written by the Billing connector after a verified
    // purchase; the bundle grants all three at once.
    private val unlockedLevelsKey = stringPreferencesKey("unlocked_levels")

    val unlockedLevels: Flow<Set<String>> =
        context.dataStore.data.map {
            (it[unlockedLevelsKey] ?: "").split(",").map { s -> s.trim() }
                .filter { s -> s.isNotEmpty() }.toSet()
        }

    suspend fun setUnlockedLevels(levels: Set<String>) {
        context.dataStore.edit { it[unlockedLevelsKey] = levels.joinToString(",") }
    }

    // ----- Placement word offset -----

    // Per-language: how many deck words the placement test skipped past. The new-word window
    // is deck[start, day*perLesson) — without this, a learner placed at Day 61 was served the
    // deck's day-1 words ("very basic words", field report 2026-07-16).
    //
    // Stored as the PLACEMENT DAY, not an absolute word index: the old absolute offset froze
    // the pace it was computed with — place at Day 61 on pace 20 (offset 1200), later lower
    // the pace to 10, and day*10 stays below 1200 until Day 120: zero new words for weeks,
    // silently. Deriving the offset from (day, current pace) at read time keeps the window
    // correct through any pace change. The legacy absolute key is still read as a fallback
    // for installs that placed before this change (their exact placement day is unrecoverable
    // from offset ÷ pace, so they keep the old semantics — no worse than before).
    private fun deckStartKey(lang: String) = intPreferencesKey("word_deck_start_$lang")
    private fun placementDayKey(lang: String) = intPreferencesKey("placement_day_$lang")

    /** The day the placement test placed the learner at (0 = never placed). */
    fun placementDay(lang: String): Flow<Int> =
        context.dataStore.data.map { it[placementDayKey(lang)] ?: 0 }

    suspend fun setPlacementDay(lang: String, day: Int) {
        context.dataStore.edit { it[placementDayKey(lang)] = day.coerceAtLeast(0) }
    }

    /**
     * First deck index the learner should ever be taught as NEW (0 = full deck from Day 1).
     *
     * Derived from the placement DAY at the course's fixed pace. It must NOT read the stored
     * new_words_per_day key: that key can still hold a 15 or 20 from before the pace was fixed,
     * and multiplying by it would skip up to twice as much of the deck as the learner actually
     * placed over, silently burying hundreds of words they were never taught.
     */
    fun wordDeckStart(lang: String): Flow<Int> =
        context.dataStore.data.map {
            val placedDay = it[placementDayKey(lang)] ?: 0
            if (placedDay > 0) (placedDay - 1) * com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY
            else it[deckStartKey(lang)] ?: 0   // legacy absolute offset
        }

    suspend fun setWordDeckStart(lang: String, index: Int) {
        context.dataStore.edit { it[deckStartKey(lang)] = index.coerceAtLeast(0) }
    }

    // ----- Words session snapshot (gym-proof resume) -----

    // Per-language so a Croatian session never bleeds into French (or vice-versa); the snapshot
    // is transient session state and each language keeps its own slot.
    private fun sessionKey(lang: String) = stringPreferencesKey("words_session_snapshot_$lang")

    /** JSON snapshot of the in-progress words session, so a force-kill mid-set resumes exactly. */
    fun wordsSessionSnapshot(lang: String): Flow<String?> =
        context.dataStore.data.map { it[sessionKey(lang)] }

    suspend fun setWordsSessionSnapshot(lang: String, json: String?) {
        context.dataStore.edit {
            if (json == null) it.remove(sessionKey(lang)) else it[sessionKey(lang)] = json
        }
    }
}
