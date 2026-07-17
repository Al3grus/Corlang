package com.corlang.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.corlang.app.AppContainer
import com.corlang.app.data.model.LanguageMeta
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the globally-selected language and exposes the list of available languages.
 * Screens observe [selected] and re-render for whichever language is active.
 */
class AppState(private val container: AppContainer) : ViewModel() {

    val languages: List<LanguageMeta> = container.content.allMeta()

    // NULL until DataStore's first emission, not seeded "hr": the eager "hr" seed made every
    // pt/fr cold start briefly run the app as Croatian — a synchronous parse of the full hr
    // plan on the main thread, a flag flash, and (because per-tab state is keyed on lang) all
    // process-death-restored state discarded when the value flipped hr→pt one frame later.
    // The UI gates on the first real value; the splash covers the (sub-frame) gap.
    val selected: StateFlow<String?> = container.languagePrefs.selectedLanguage
        // A previously-persisted language may since have been hidden (e.g. "fr") —
        // fall back to the first shipped language instead of showing hidden content.
        .map { if (it in container.content.availableLanguages) it else container.content.availableLanguages.first() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Ensure a progress row exists for every shipped language.
        viewModelScope.launch {
            container.content.availableLanguages.forEach { container.progress.ensure(it) }
        }
    }

    fun selectLanguage(code: String) {
        viewModelScope.launch { container.languagePrefs.setLanguage(code) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppState(container) as T
    }
}
