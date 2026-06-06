package com.frenchai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "frenchai_prefs")

/** Persists the user's selected language across launches. */
class LanguagePrefs(private val context: Context) {

    private val selectedKey = stringPreferencesKey("selected_language")

    val selectedLanguage: Flow<String> =
        context.dataStore.data.map { it[selectedKey] ?: "fr" }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[selectedKey] = code }
    }
}
