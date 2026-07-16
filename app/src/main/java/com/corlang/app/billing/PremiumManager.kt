package com.corlang.app.billing

import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for "does this install have Premium" (today: the AI tutor features).
 * Everything that gates on Premium reads [entitled]; nothing else in the app knows where the
 * entitlement came from, so swapping in real billing later touches only this layer.
 *
 * Sole entitlement source: a Play Billing purchase (persisted flag, written after server-side
 * receipt verification). NOT WIRED YET, blocked on the Play Console + hosting accounts. The
 * wiring checklist lives in docs/server-ai.md; when done, the purchase flow calls
 * [grantFromPlayPurchase] and the billing connector calls [revoke] if Play reports the
 * subscription lapsed.
 */
class PremiumManager(private val prefs: LanguagePrefs) {

    val entitled: Flow<Boolean> = prefs.premiumEntitled

    /** Call after a Play purchase has been verified server-side (never on the raw callback). */
    suspend fun grantFromPlayPurchase() = prefs.setPremiumEntitled(true)

    /** Call when Play reports the subscription expired/refunded. */
    suspend fun revoke() = prefs.setPremiumEntitled(false)
}
