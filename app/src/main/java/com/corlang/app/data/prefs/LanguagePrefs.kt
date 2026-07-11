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

    // ----- AI tutor (optional; user supplies their own Anthropic API key) -----

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

    private val sessionKey = stringPreferencesKey("words_session_snapshot")

    /** JSON snapshot of the in-progress words session, so a force-kill mid-set resumes exactly. */
    val wordsSessionSnapshot: Flow<String?> =
        context.dataStore.data.map { it[sessionKey] }

    suspend fun setWordsSessionSnapshot(json: String?) {
        context.dataStore.edit {
            if (json == null) it.remove(sessionKey) else it[sessionKey] = json
        }
    }
}
