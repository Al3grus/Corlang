package com.corlang.app.billing

import com.corlang.app.BuildConfig
import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for "does this install have Premium" (today: the AI tutor features).
 * Everything that gates on Premium reads [entitled]; nothing else in the app knows where the
 * entitlement came from, so swapping in real billing later touches only this layer.
 *
 * Entitlement sources:
 *  - Play Billing purchase (persisted flag, written after server-side receipt verification).
 *    NOT WIRED YET; checklist in docs/server-ai.md. The purchase flow calls
 *    [grantFromPlayPurchase]; the billing connector calls [revoke] on lapse.
 *  - DEV_PREMIUM build flag (sideload flavor only, from gitignored local.properties): the
 *    pre-billing test unlock so the AI surfaces can be exercised against the deployed proxy.
 */
class PremiumManager(private val prefs: LanguagePrefs) {

    val entitled: Flow<Boolean> =
        if (BuildConfig.DEV_PREMIUM) prefs.premiumEntitled.map { true }
        else prefs.premiumEntitled

    /** Call after a Play purchase has been verified server-side (never on the raw callback). */
    suspend fun grantFromPlayPurchase() = prefs.setPremiumEntitled(true)

    /** Call when Play reports the subscription expired/refunded. */
    suspend fun revoke() = prefs.setPremiumEntitled(false)
}
