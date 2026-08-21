package com.corlang.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.billing.BillingManager

/** Walk up the Context wrappers to the hosting Activity (needed by launchBillingFlow). */
private fun Context.activity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * The purchase surface. Two modes:
 *  - [levelId] non-null → the one-time unlock for that CEFR level of [lang], and that course's
 *    bundle when it would not make the learner pay twice (see below).
 *  - [levelId] null      → the AI Premium subscription (monthly only + 7-day trial).
 *
 * Unlocks are per language and ADDITIVE: buying A2 grants A2 of this course, nothing else. That
 * avoids the upgrade trap of cumulative tiers — Play has no upgrade pricing for one-time
 * products, so "A2 includes A1" would make anyone who bought A1 first pay for it twice. The cost
 * of additive is the reverse: someone who owns a level could still buy the bundle and overpay
 * for what they already have, so the bundle is offered ONLY to a learner who owns none of it.
 *
 * Prices are read LIVE from Play (BillingManager.prices); if a product isn't resolved yet
 * (Play Console products not created, or billing still connecting) its button is disabled with a
 * gentle placeholder rather than showing a wrong or free price.
 */
@Composable
fun PaywallScreen(
    container: AppContainer,
    lang: String,
    levelId: String?,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx.activity()
    val prices by container.billing.prices.collectAsState()
    val owned by container.premium.unlockedLevels.collectAsState(initial = emptySet())
    val meta = remember(lang) { container.content.meta(lang) }
    // The levels this course charges for, in course order, so `.last()` names the finish line.
    val paidLevels = remember(lang) {
        container.content.plan(lang).days
            .filter { it.day > meta.freeLessons }.map { it.level }.distinct()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (levelId != null) {
            Text("Unlock ${meta.name} $levelId", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(
                "The first ${meta.freeLessons} lessons are free. Unlock $levelId to keep going: " +
                    "its lessons, words, quizzes and the end-of-level exam. One payment, yours " +
                    "for good, and it stays if you reinstall.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val single = if (levelId in paidLevels) {
                BillingManager.levelProduct(lang, levelId)
            } else null
            // Owning any level rules the bundle out: additive pricing means it would re-charge
            // for what is already paid for, and Play would happily take the money.
            val ownsSome = paidLevels.any {
                com.corlang.app.billing.PremiumManager.key(lang, it) in owned
            }
            if (single != null) {
                PurchaseCard(
                    title = "Unlock $levelId",
                    price = prices[single],
                    primary = true,
                    onBuy = { activity?.let { container.billing.purchaseLevel(it, single) } }
                )
            }
            if (!ownsSome) {
                val top = paidLevels.lastOrNull()
                PurchaseCard(
                    title = "The whole ${meta.name} course",
                    subtitle = if (top != null) {
                        "Best value: every level through $top, one payment."
                    } else "Best value: one payment.",
                    price = prices[BillingManager.bundleProduct(lang)],
                    primary = single == null,
                    onBuy = {
                        activity?.let {
                            container.billing.purchaseLevel(it, BillingManager.bundleProduct(lang))
                        }
                    }
                )
            }
        } else {
            Text("Corlang Premium", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(
                "Your personal AI tutor: chat in the language, get exam-writing feedback, and " +
                    "check your explanations, all graded for your target level. Cancel anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Monthly ONLY, deliberately: AI models and costs can shift within a year, and a
            // sold annual locks us into serving 12 months at 2026 economics. Monthly keeps
            // repricing freedom on both sides. The fair-use cap is disclosed HERE, before
            // purchase — an undisclosed hard stop on a paid AI tutor is refund-request and
            // Play-policy material.
            PurchaseCard(
                title = "Monthly",
                subtitle = "Starts with a 7-day free trial.",
                footnote = "Fair use: up to 30 AI messages a day.",
                price = prices["${BillingManager.SUB_PREMIUM}:${BillingManager.BASE_MONTHLY}"],
                priceSuffix = "/month",
                primary = true,
                onBuy = {
                    activity?.let {
                        container.billing.purchaseSubscription(it, BillingManager.BASE_MONTHLY)
                    }
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        Text(
            "Prices shown include tax and are set by Google Play for your region. " +
                "Subscriptions renew until cancelled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PurchaseCard(
    title: String,
    price: String?,
    onBuy: () -> Unit,
    subtitle: String? = null,
    /** Fine print under the subtitle, on its own line and smaller (e.g. the fair-use cap). */
    footnote: String? = null,
    priceSuffix: String = "",
    primary: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (price != null) {
                Text("$price$priceSuffix", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            } else {
                // Product not resolved from Play yet — never show a wrong/free price.
                Text("Price unavailable", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Unavailable")
                }
            }
            // Fine print CLOSES the card, after the price and button, so the pitch reads
            // trial -> price -> act, with the cap as the last word rather than mid-pitch.
            if (footnote != null) {
                Text(footnote, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
