package com.corlang.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /** How many brand-new words the SRS may introduce per day (10 default; 15/20 to go faster). */
    val newWordsPerDay: Flow<Int> =
        context.dataStore.data.map { it[newWordsKey] ?: 10 }

    suspend fun setNewWordsPerDay(count: Int) {
        context.dataStore.edit { it[newWordsKey] = count }
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

    /**
     * Who the learner is, captured at onboarding. Drives the personalized first phrases
     * (Zovem se…, Ja sam iz…) and correct gendered Croatian forms; editable from Settings.
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
     * the developer API key below doubles as the dev entitlement.
     */
    val premiumEntitled: Flow<Boolean> =
        context.dataStore.data.map { it[premiumKey] ?: false }

    suspend fun setPremiumEntitled(entitled: Boolean) {
        context.dataStore.edit { it[premiumKey] = entitled }
    }

    // ----- AI tutor (developer fallback; user supplies an Anthropic API key) -----

    private val apiKeyKey = stringPreferencesKey("anthropic_api_key")

    /**
     * The user's own Anthropic API key, enabling the AI tutor and writing feedback. Stored
     * locally only and deliberately excluded from backups (it's a secret). Empty = AI off.
     */
    val anthropicApiKey: Flow<String> =
        context.dataStore.data.map { it[apiKeyKey] ?: "" }

    suspend fun setAnthropicApiKey(key: String) {
        context.dataStore.edit {
            if (key.isBlank()) it.remove(apiKeyKey) else it[apiKeyKey] = key.trim()
        }
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
