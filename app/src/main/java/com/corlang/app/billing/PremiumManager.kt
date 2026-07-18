package com.corlang.app.billing

import com.corlang.app.BuildConfig
import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for what this install has paid for. Two independent axes:
 *  1. [entitled] — the AI subscription ("Premium"): unlocks the Learn tab (tutor + feedback).
 *  2. [unlockedLevels] / [levelAccessible] — one-time level unlocks: A0/A1 free forever,
 *     A2/B1/B2 each unlocked by a purchase (or the bundle). Global across languages.
 *
 * Everything that gates reads this layer; nothing else knows where entitlement came from, so
 * [BillingManager] writes here and the rest of the app is unchanged. DEV_PREMIUM (sideload
 * flavor, gitignored local.properties) force-unlocks everything for the developer's own
 * testing against the deployed proxy.
 */
class PremiumManager(private val prefs: LanguagePrefs) {

    /** CEFR levels that never require a purchase. */
    val freeLevels = setOf("A0", "A1")

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

    /** Levels the learner has bought (does NOT include the always-free A0/A1). */
    val unlockedLevels: Flow<Set<String>> = prefs.unlockedLevels

    /** True if [levelId] is playable: always-free, dev-unlocked, or purchased. */
    fun levelAccessible(levelId: String): Flow<Boolean> =
        prefs.unlockedLevels.map { bought ->
            BuildConfig.DEV_PREMIUM || levelId in freeLevels || levelId in bought
        }

    /** Adds [levels] to the purchased set (union; the bundle passes A2+B1+B2 at once). */
    suspend fun grantLevels(levels: Set<String>) {
        val current = prefs.unlockedLevels.first()
        prefs.setUnlockedLevels(current + levels)
    }
}
